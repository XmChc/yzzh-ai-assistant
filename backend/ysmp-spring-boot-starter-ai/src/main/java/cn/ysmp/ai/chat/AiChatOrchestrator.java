package cn.ysmp.ai.chat;

import cn.ysmp.ai.config.AiProperties;
import cn.ysmp.ai.constants.AiAgentModes;
import cn.ysmp.ai.constants.AiChartFormatSpecs;
import cn.ysmp.ai.constants.AiClarifyConstants;
import cn.ysmp.ai.constants.AiLimits;
import cn.ysmp.ai.constants.AiQueryIntents;
import cn.ysmp.ai.constants.AiToolTypes;
import cn.ysmp.ai.constants.AiWriteConfirmConstants;
import cn.ysmp.ai.guard.AiAccessGuard;
import cn.ysmp.ai.guard.AiToolQueryGuard;
import cn.ysmp.ai.llm.LlmCompletionResult;
import cn.ysmp.ai.llm.LlmToolCall;
import cn.ysmp.ai.llm.OpenAiCompatibleClient;
import cn.ysmp.ai.spi.AiChatMemory;
import cn.ysmp.ai.spi.AiDataScope;
import cn.ysmp.ai.spi.AiDataScopeProvider;
import cn.ysmp.ai.spi.AiGovernedQueryExecutor;
import cn.ysmp.ai.spi.AiGovernedQueryRequest;
import cn.ysmp.ai.spi.AiGovernedQueryResult;
import cn.ysmp.ai.spi.AiLlmConfigProvider;
import cn.ysmp.ai.spi.AiLlmRuntimeConfig;
import cn.ysmp.ai.spi.AiQueryCapability;
import cn.ysmp.ai.spi.AiQueryCapabilityCatalog;
import cn.ysmp.ai.spi.AiSecurityAdapter;
import cn.ysmp.ai.spi.NoOpAiChatMemory;
import cn.ysmp.ai.spi.NoOpAiDataScopeProvider;
import cn.ysmp.ai.spi.NoOpAiGovernedQueryExecutor;
import cn.ysmp.ai.spi.NoOpAiQueryCapabilityCatalog;
import cn.ysmp.ai.tool.AiCapabilityBundle;
import cn.ysmp.ai.tool.AiSkillHint;
import cn.ysmp.ai.tool.AiTool;
import cn.ysmp.ai.tool.AiToolCatalog;
import cn.ysmp.ai.tool.AiToolDefinition;
import cn.ysmp.ai.tool.AiToolKindHints;
import cn.ysmp.ai.tool.AiToolSchemaPrompt;
import cn.ysmp.ai.tool.openapi.OpenApiToolExecutor;
import cn.ysmp.ai.tool.result.AiToolResultCompactor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 对话编排：会话上下文 → 能力解析 → tool_call 多轮 → 落库 → 用量事件。
 */
@Slf4j
public class AiChatOrchestrator {

    private final AiLlmConfigProvider llmConfigProvider;
    private final AiToolCatalog toolCatalog;
    private final AiSecurityAdapter securityAdapter;
    private final OpenAiCompatibleClient llmClient;
    private final OpenApiToolExecutor openApiToolExecutor;
    private final List<AiTool> builtinTools;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<AiChatMemory> chatMemoryProvider;
    private final AiProperties properties;
    private final AiConversationCompressor conversationCompressor;
    private final AiToolResultCompactor toolResultCompactor;
    private final AiQueryCapabilityCatalog queryCapabilityCatalog;
    private final AiDataScopeProvider dataScopeProvider;
    private final AiGovernedQueryExecutor governedQueryExecutor;

    public AiChatOrchestrator(AiLlmConfigProvider llmConfigProvider,
                              AiToolCatalog toolCatalog,
                              AiSecurityAdapter securityAdapter,
                              OpenAiCompatibleClient llmClient,
                              OpenApiToolExecutor openApiToolExecutor,
                              List<AiTool> builtinTools,
                              ObjectMapper objectMapper,
                              ObjectProvider<AiChatMemory> chatMemoryProvider,
                              AiProperties properties,
                              AiConversationCompressor conversationCompressor,
                              AiToolResultCompactor toolResultCompactor) {
        this(llmConfigProvider, toolCatalog, securityAdapter, llmClient, openApiToolExecutor,
                builtinTools, objectMapper, chatMemoryProvider, properties, conversationCompressor,
                toolResultCompactor, null, null, null);
    }

    public AiChatOrchestrator(AiLlmConfigProvider llmConfigProvider,
                              AiToolCatalog toolCatalog,
                              AiSecurityAdapter securityAdapter,
                              OpenAiCompatibleClient llmClient,
                              OpenApiToolExecutor openApiToolExecutor,
                              List<AiTool> builtinTools,
                              ObjectMapper objectMapper,
                              ObjectProvider<AiChatMemory> chatMemoryProvider,
                              AiProperties properties,
                              AiConversationCompressor conversationCompressor,
                              AiToolResultCompactor toolResultCompactor,
                              AiQueryCapabilityCatalog queryCapabilityCatalog,
                              AiDataScopeProvider dataScopeProvider,
                              AiGovernedQueryExecutor governedQueryExecutor) {
        this.llmConfigProvider = llmConfigProvider;
        this.toolCatalog = toolCatalog;
        this.securityAdapter = securityAdapter;
        this.llmClient = llmClient;
        this.openApiToolExecutor = openApiToolExecutor;
        this.builtinTools = builtinTools == null ? List.of() : builtinTools;
        this.objectMapper = objectMapper;
        this.chatMemoryProvider = chatMemoryProvider;
        this.properties = properties;
        this.conversationCompressor = conversationCompressor;
        this.toolResultCompactor = toolResultCompactor;
        this.queryCapabilityCatalog = queryCapabilityCatalog != null
                ? queryCapabilityCatalog : NoOpAiQueryCapabilityCatalog.INSTANCE;
        this.dataScopeProvider = dataScopeProvider != null
                ? dataScopeProvider : NoOpAiDataScopeProvider.INSTANCE;
        this.governedQueryExecutor = governedQueryExecutor != null
                ? governedQueryExecutor : NoOpAiGovernedQueryExecutor.INSTANCE;
    }

    private AiChatMemory memory() {
        AiChatMemory m = chatMemoryProvider.getIfAvailable();
        return m != null ? m : NoOpAiChatMemory.INSTANCE;
    }

    public AiChatResponse chat(AiChatRequest request) {
        StringBuilder content = new StringBuilder();
        String[] sessionHolder = new String[1];
        String[] errorHolder = new String[1];
        AiContextUsage[] usageHolder = new AiContextUsage[1];
        AiClarifyForm[] clarifyHolder = new AiClarifyForm[1];
        run(request, new AiChatEventListener() {
            @Override
            public void onMeta(String sessionId) {
                sessionHolder[0] = sessionId;
            }

            @Override
            public void onUsage(AiContextUsage usage) {
                usageHolder[0] = usage;
            }

            @Override
            public void onClarify(AiClarifyForm form) {
                clarifyHolder[0] = form;
            }

            @Override
            public void onDelta(String textChunk) {
                if (textChunk != null) {
                    content.append(textChunk);
                }
            }

            @Override
            public void onDone(String fullContent) {
                if (fullContent != null && content.length() == 0) {
                    content.append(fullContent);
                }
            }

            @Override
            public void onError(String message) {
                errorHolder[0] = message;
            }
        }, false);

        if (errorHolder[0] != null) {
            return AiChatResponse.builder()
                    .sessionId(sessionHolder[0])
                    .error(errorHolder[0])
                    .content(content.length() > 0 ? content.toString() : null)
                    .usage(usageHolder[0])
                    .clarify(clarifyHolder[0])
                    .build();
        }
        return AiChatResponse.builder()
                .sessionId(sessionHolder[0])
                .content(content.toString())
                .usage(usageHolder[0])
                .clarify(clarifyHolder[0])
                .build();
    }

    /**
     * 执行对话；{@code emitChunkedContent=true} 时将最终正文按块回调 {@link AiChatEventListener#onDelta}。
     */
    public void run(AiChatRequest request, AiChatEventListener listener, boolean emitChunkedContent) {
        AiChatEventListener sink = listener == null ? new AiChatEventListener() {
        } : listener;

        if (request == null || request.getMessage() == null || request.getMessage().isBlank()) {
            sink.onError("message 不能为空");
            return;
        }
        if (!AiAccessGuard.canUseAssistant(securityAdapter)) {
            sink.onError("无权限使用 AI 助手");
            return;
        }

        AiLlmRuntimeConfig cfg = llmConfigProvider.getRuntimeConfig();
        if (cfg == null || !cfg.isReady()) {
            sink.onError("模型未配置或未启用，请先在「模型配置」中填写");
            return;
        }

        String sessionId = request.getSessionId() == null || request.getSessionId().isBlank()
                ? UUID.randomUUID().toString().replace("-", "")
                : request.getSessionId().trim();
        Long userId = securityAdapter.getLoginUserId();
        AiChatMemory chatMemory = memory();
        chatMemory.ensureSession(sessionId, userId, request.getAgentCode(), request.getMessage());
        sink.onMeta(sessionId);

        AiCapabilityBundle bundle = toolCatalog.resolveCapabilities(
                request.getAgentCode(), request.getSkillCode(), request.getToolCode());
        if (bundle.getError() != null && !bundle.getError().isBlank()) {
            sink.onError(bundle.getError());
            return;
        }

        List<AiToolDefinition> callableTools = new ArrayList<>(
                bundle.getCallableTools() == null ? List.of() : bundle.getCallableTools());
        mergeGovernedCapabilities(callableTools);
        if (callableTools.isEmpty()) {
            sink.onError("当前 Agent 无可用工具（Skill 未激活、权限不足或未配置受控查询能力）");
            return;
        }
        Map<String, AiToolDefinition> callableMap = callableTools.stream()
                .collect(Collectors.toMap(AiToolDefinition::getToolCode, Function.identity(), (a, b) -> a,
                        LinkedHashMap::new));
        AiQueryIntentDetector.Detection queryDetection = AiQueryIntentDetector.analyze(request.getMessage());
        String queryIntent = queryDetection.intent();
        List<String> dashboardToolCodes = AiQueryIntentDetector.listDashboardToolCodes(callableTools);
        AiQueryFallback.Plan fallbackPlan = AiQueryFallback.resolve(queryDetection, callableTools);
        List<Map<String, Object>> openAiTools = toOpenAiToolMaps(callableTools, queryIntent);
        boolean writeConfirmed = Boolean.TRUE.equals(request.getConfirmWriteTools());
        SystemParts systemParts = buildSystemParts(bundle);

        PreparedContext ctx = prepareContext(chatMemory, cfg, sessionId);
        String systemWithSummary = systemParts.full;
        if (ctx.summary() != null && !ctx.summary().isBlank()) {
            systemWithSummary = systemParts.full + "\n\n# 对话摘要（早期轮次压缩）\n" + ctx.summary().trim();
        }
        systemWithSummary = systemWithSummary
                + AiQueryIntentDetector.promptHint(queryDetection, dashboardToolCodes)
                + AiQueryFallback.promptSection(fallbackPlan);

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(mutableMessage("system", systemWithSummary));
        for (AiChatMemory.StoredMessage h : ctx.recent()) {
            messages.add(mutableMessage(h.role(), h.content()));
        }
        messages.add(mutableMessage("user", request.getMessage()));

        AiContextUsage usage = estimateUsage(systemParts, openAiTools, ctx.summary(), ctx.recent(),
                request.getMessage());
        sink.onUsage(usage);

        try {
            String finalContent = null;
            int sumPrompt = 0;
            int sumCompletion = 0;
            int sumTotal = 0;
            boolean hasApiUsage = false;
            int toolRefusalCount = 0;
            boolean awaitingWriteConfirm = false;
            Set<String> pendingWriteNames = new LinkedHashSet<>();

            // 无合适 Tool 且无兜底：直接拒绝并说明原因，不调用模型硬编
            if (fallbackPlan.skipLlm()) {
                finalContent = AiQueryFallback.buildRefuseMessage(fallbackPlan, null);
                chatMemory.appendMessage(sessionId, "user", request.getMessage(), null);
                chatMemory.appendMessage(sessionId, "assistant", finalContent, null);
                sink.onUsage(usage);
                if (emitChunkedContent) {
                    emitChunked(sink, finalContent);
                }
                sink.onDone(finalContent);
                return;
            }

            for (int round = 0; round < AiLimits.MAX_TOOL_ROUNDS; round++) {
                // 兜底：需澄清/降级/连续拒调 → 不给 tools，避免空转
                boolean noTools = fallbackPlan.forceNoTools() || toolRefusalCount >= 2;
                List<Map<String, Object>> toolsThisRound = noTools ? List.of() : openAiTools;
                LlmCompletionResult result = llmClient.complete(cfg, messages, toolsThisRound);
                if (result.getPromptTokens() != null) {
                    sumPrompt += result.getPromptTokens();
                    hasApiUsage = true;
                }
                if (result.getCompletionTokens() != null) {
                    sumCompletion += result.getCompletionTokens();
                    hasApiUsage = true;
                }
                if (result.getTotalTokens() != null) {
                    sumTotal += result.getTotalTokens();
                    hasApiUsage = true;
                }

                if (result.hasToolCalls() && round < AiLimits.MAX_TOOL_ROUNDS - 1
                        && toolRefusalCount < 2 && !fallbackPlan.forceNoTools()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> assistantMsg = result.getAssistantMessage() instanceof Map
                            ? (Map<String, Object>) result.getAssistantMessage()
                            : mutableMessage("assistant", result.getContent());
                    messages.add(assistantMsg);

                    for (LlmToolCall call : result.getToolCalls()) {
                        sink.onToolCall(call.getId(), call.getName(), call.getArgumentsJson());
                        AiToolDefinition def = callableMap.get(call.getName());
                        boolean needWriteConfirm = properties.isRequireWriteConfirm()
                                && !writeConfirmed
                                && AiToolKindHints.looksLikeWrite(def);
                        String rawResult;
                        if (needWriteConfirm) {
                            awaitingWriteConfirm = true;
                            pendingWriteNames.add(call.getName());
                            sink.onToolConfirm(call.getId(), call.getName(), call.getArgumentsJson());
                            rawResult = "{\"" + AiWriteConfirmConstants.FIELD_STATUS + "\":\""
                                    + AiWriteConfirmConstants.STATUS_NEED_CONFIRM + "\",\""
                                    + AiWriteConfirmConstants.FIELD_TOOL + "\":\""
                                    + escape(call.getName()) + "\",\""
                                    + AiWriteConfirmConstants.FIELD_MESSAGE
                                    + "\":\"写操作待用户确认，尚未执行\"}";
                        } else {
                            rawResult = executeToolAllowed(
                                    call.getName(), call.getArgumentsJson(), callableMap, queryIntent,
                                    fallbackPlan.blockDataFetch(), sessionId, userId);
                        }
                        if (isToolRefusal(rawResult)) {
                            toolRefusalCount++;
                            rawResult = AiQueryFallback.enrichRefusal(rawResult, fallbackPlan, toolRefusalCount);
                        }
                        AiToolResultCompactor.CompactOutcome compacted = toolResultCompactor.compact(
                                rawResult,
                                call.getName(),
                                sessionId,
                                userId,
                                def == null ? null : def.getResponseSchema());
                        String forLlm = compacted.llmContent() == null ? "" : compacted.llmContent();
                        sink.onToolResult(call.getId(), call.getName(),
                                preview(forLlm, AiLimits.TOOL_RESULT_PREVIEW_CHARS));
                        if (compacted.dataRef() != null) {
                            sink.onToolData(compacted.dataRef().toMap());
                        }
                        Map<String, Object> toolMsg = new LinkedHashMap<>();
                        toolMsg.put("role", "tool");
                        toolMsg.put("tool_call_id", call.getId());
                        toolMsg.put("content", forLlm);
                        messages.add(toolMsg);
                    }
                    if (awaitingWriteConfirm) {
                        finalContent = buildWriteConfirmMessage(new ArrayList<>(pendingWriteNames));
                        break;
                    }
                    continue;
                }

                finalContent = result.getContent() == null ? "" : result.getContent();
                if (result.hasToolCalls()) {
                    finalContent = (finalContent.isBlank() ? "" : finalContent + "\n")
                            + "（已达工具调用轮次上限，请简化问题后重试）";
                }
                break;
            }

            if (finalContent == null) {
                finalContent = "";
            }
            if (!awaitingWriteConfirm) {
                finalContent = AiQueryFallback.ensureUserFacingFallback(
                        finalContent, fallbackPlan, toolRefusalCount);
            }

            AiClarifyParser.Result clarifyResult = AiClarifyParser.parse(finalContent, objectMapper);
            String displayContent = clarifyResult.displayContent();
            if (clarifyResult.hasForm()) {
                sink.onClarify(clarifyResult.form());
            }

            // 只持久化 user + 最终 assistant（正文不含 fence，澄清表单由前端交互）
            chatMemory.appendMessage(sessionId, "user", request.getMessage(), null);
            chatMemory.appendMessage(sessionId, "assistant", displayContent, null);

            if (hasApiUsage) {
                usage.setPromptTokens(sumPrompt);
                usage.setCompletionTokens(sumCompletion);
                usage.setTotalTokens(sumTotal > 0 ? sumTotal : sumPrompt + sumCompletion);
                sink.onUsage(usage);
            }

            if (emitChunkedContent) {
                emitChunked(sink, displayContent);
            } else {
                sink.onDelta(displayContent);
            }
            sink.onDone(displayContent);
        } catch (Exception e) {
            log.warn("[ai] chat failed: {}", e.getMessage());
            sink.onError("调用模型失败: " + e.getMessage());
        }
    }

    /**
     * 构建上下文：摘要 + 最近原文。未摘要消息超过阈值时先 LLM 压缩再拼装。
     */
    private PreparedContext prepareContext(AiChatMemory chatMemory,
                                           AiLlmRuntimeConfig cfg,
                                           String sessionId) {
        AiChatMemory.SummaryState state = chatMemory.loadSummaryState(sessionId);
        int keep = properties.getSummaryKeepRecentMessages() > 0
                ? properties.getSummaryKeepRecentMessages()
                : AiLimits.SUMMARY_KEEP_RECENT_MESSAGES;
        int trigger = properties.getSummaryTriggerMessages() > 0
                ? properties.getSummaryTriggerMessages()
                : AiLimits.SUMMARY_TRIGGER_MESSAGES;
        if (trigger < keep + 1) {
            trigger = keep + 1;
        }
        int loadLimit = Math.max(AiLimits.MAX_CONTEXT_MESSAGES, trigger + keep);
        List<AiChatMemory.StoredMessage> pending = chatMemory.loadMessagesAfter(
                sessionId, state.untilMessageId(), loadLimit);

        String summary = state.hasSummary() ? state.summary() : null;
        List<AiChatMemory.StoredMessage> recent = pending;

        boolean enabled = properties.isSummaryEnabled();
        if (enabled && pending.size() >= trigger && pending.size() > keep) {
            List<AiChatMemory.StoredMessage> overflow = pending.subList(0, pending.size() - keep);
            recent = new ArrayList<>(pending.subList(pending.size() - keep, pending.size()));
            long untilId = overflow.get(overflow.size() - 1).id();
            if (untilId > 0) {
                int maxChars = properties.getSummaryMaxChars() > 0
                        ? properties.getSummaryMaxChars()
                        : AiLimits.MAX_SUMMARY_CHARS;
                summary = conversationCompressor.compress(cfg, summary, overflow, maxChars);
                chatMemory.saveSummary(sessionId, summary, untilId);
                log.debug("[ai] session {} summarized through message id {}, overflow={}",
                        sessionId, untilId, overflow.size());
            }
        } else if (pending.size() > keep) {
            // 未到 trigger 或摘要关闭：仍裁剪过长原文，避免撑爆上下文
            recent = trimHistoryByChars(pending, AiLimits.MAX_CONTEXT_CHARS);
            if (recent.size() > keep) {
                recent = new ArrayList<>(recent.subList(recent.size() - keep, recent.size()));
            }
        } else {
            recent = trimHistoryByChars(pending, AiLimits.MAX_CONTEXT_CHARS);
        }

        return new PreparedContext(summary, recent);
    }

    private AiContextUsage estimateUsage(SystemParts systemParts,
                                         List<Map<String, Object>> openAiTools,
                                         String summary,
                                         List<AiChatMemory.StoredMessage> history,
                                         String currentMessage) {
        int systemTok = AiTokenEstimator.estimate(systemParts.agentOnly);
        int skillTok = AiTokenEstimator.estimate(systemParts.skillsOnly);
        int toolTok = AiTokenEstimator.estimateObject(objectMapper, openAiTools);
        int summaryTok = AiTokenEstimator.estimate(summary);
        int convTok = 0;
        for (AiChatMemory.StoredMessage h : history) {
            convTok += AiTokenEstimator.estimate(h.content());
        }
        int curTok = AiTokenEstimator.estimate(currentMessage);
        int total = systemTok + skillTok + toolTok + summaryTok + convTok + curTok;
        int window = properties.getContextWindowTokens() > 0
                ? properties.getContextWindowTokens()
                : AiLimits.DEFAULT_CONTEXT_WINDOW_TOKENS;
        int pct = window <= 0 ? 0 : Math.min(100, (int) Math.round(total * 100.0 / window));
        return AiContextUsage.builder()
                .estimatedTotal(total)
                .contextWindow(window)
                .percentFull(pct)
                .systemPrompt(systemTok)
                .skillPrompts(skillTok)
                .toolDefinitions(toolTok)
                .summarizedConversation(summaryTok)
                .conversation(convTok)
                .currentMessage(curTok)
                .build();
    }

    private static List<AiChatMemory.StoredMessage> trimHistoryByChars(
            List<AiChatMemory.StoredMessage> history, int maxChars) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        int total = 0;
        for (AiChatMemory.StoredMessage m : history) {
            total += m.content() == null ? 0 : m.content().length();
        }
        if (total <= maxChars) {
            return history;
        }
        List<AiChatMemory.StoredMessage> kept = new ArrayList<>();
        int used = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            AiChatMemory.StoredMessage m = history.get(i);
            int len = m.content() == null ? 0 : m.content().length();
            if (used + len > maxChars && !kept.isEmpty()) {
                break;
            }
            kept.add(0, m);
            used += len;
        }
        return kept;
    }

    private void emitChunked(AiChatEventListener sink, String content) {
        if (content == null || content.isEmpty()) {
            return;
        }
        int size = AiLimits.SSE_CONTENT_CHUNK_CHARS;
        for (int i = 0; i < content.length(); i += size) {
            int end = Math.min(i + size, content.length());
            sink.onDelta(content.substring(i, end));
        }
    }

    private String executeToolAllowed(String toolCode, String argumentsJson,
                                      Map<String, AiToolDefinition> callableMap) {
        return executeToolAllowed(toolCode, argumentsJson, callableMap, null, false, null, null);
    }

    private String executeToolAllowed(String toolCode, String argumentsJson,
                                      Map<String, AiToolDefinition> callableMap,
                                      String queryIntent,
                                      boolean blockDataFetch,
                                      String sessionId,
                                      Long userId) {
        Map<String, Object> arguments = parseJson(argumentsJson);
        Map<String, AiTool> builtinMap = builtinTools.stream()
                .collect(Collectors.toMap(AiTool::getCode, Function.identity(), (a, b) -> a));
        if (builtinMap.containsKey(toolCode)) {
            try {
                return builtinMap.get(toolCode).execute(arguments);
            } catch (Exception e) {
                return "{\"error\":\"" + escape(e.getMessage()) + "\"}";
            }
        }
        AiToolDefinition def = callableMap.get(toolCode);
        if (def == null) {
            return "{\"error\":\"tool not found or not in current callable set\"}";
        }
        if (!def.isAvailable()) {
            return "{\"error\":\"tool not available: "
                    + (def.getUnavailableReason() == null ? "permission denied" : def.getUnavailableReason())
                    + "\"}";
        }
        if (AiToolTypes.GOVERNED.equals(def.getToolType())) {
            return executeGoverned(toolCode, arguments, sessionId, userId);
        }
        if (AiToolTypes.OPENAPI.equals(def.getToolType())) {
            AiToolQueryGuard.CheckResult gate = AiToolQueryGuard.check(
                    def, arguments, queryIntent, blockDataFetch);
            if (!gate.allowed()) {
                return gate.refusalJson();
            }
            return openApiToolExecutor.execute(def, gate.sanitizedArgs());
        }
        return "{\"error\":\"unsupported tool type\"}";
    }

    private String executeGoverned(String capabilityCode,
                                   Map<String, Object> arguments,
                                   String sessionId,
                                   Long userId) {
        if (!governedQueryExecutor.supports(capabilityCode)) {
            return "{\"error\":\"governed capability not supported: " + escape(capabilityCode) + "\"}";
        }
        AiDataScope scope = dataScopeProvider.resolve(capabilityCode);
        if (scope == null || !scope.isAllowed()) {
            String reason = scope == null ? "数据权限不足" : scope.getReason();
            return "{\"error\":\"DATA_SCOPE_DENIED\",\"message\":\"" + escape(reason) + "\"}";
        }
        AiGovernedQueryResult result = governedQueryExecutor.execute(AiGovernedQueryRequest.builder()
                .capabilityCode(capabilityCode)
                .params(arguments == null ? Map.of() : arguments)
                .scope(scope)
                .sessionId(sessionId)
                .userId(userId)
                .build());
        if (result == null) {
            return "{\"error\":\"governed query returned null\"}";
        }
        if (!result.isSuccess()) {
            return result.getContent() != null ? result.getContent()
                    : "{\"error\":\"" + escape(result.getError()) + "\"}";
        }
        return result.getContent() == null ? "{}" : result.getContent();
    }

    private void mergeGovernedCapabilities(List<AiToolDefinition> callableTools) {
        List<AiQueryCapability> caps = queryCapabilityCatalog.listAvailable();
        if (caps == null || caps.isEmpty()) {
            return;
        }
        for (AiQueryCapability cap : caps) {
            if (cap == null || cap.getCode() == null || cap.getCode().isBlank()) {
                continue;
            }
            if (!governedQueryExecutor.supports(cap.getCode())) {
                continue;
            }
            boolean exists = callableTools.stream()
                    .anyMatch(t -> cap.getCode().equals(t.getToolCode()));
            if (exists) {
                continue;
            }
            callableTools.add(AiToolDefinition.builder()
                    .toolCode(cap.getCode())
                    .toolType(AiToolTypes.GOVERNED)
                    .name(cap.getName() == null ? cap.getCode() : cap.getName())
                    .description(cap.getDescription() == null
                            ? ("受控查询能力 " + cap.getCode())
                            : cap.getDescription())
                    .requiredPermissions(cap.getRequiredPermissions())
                    .parametersSchema(cap.getParametersSchema() == null
                            ? Map.of("type", "object", "properties", Map.of())
                            : cap.getParametersSchema())
                    .available(true)
                    .build());
        }
    }

    private static String buildWriteConfirmMessage(List<String> toolNames) {
        String names = toolNames == null || toolNames.isEmpty()
                ? "写操作"
                : String.join("、", toolNames);
        return "以下写操作需要你确认后才会执行：**" + names + "**。\n\n"
                + "请点击下方「确认执行」继续，或「取消」放弃本次写操作。";
    }

    private static boolean isToolRefusal(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        return raw.contains(AiToolQueryGuard.ERROR_CODE)
                || raw.contains(AiToolQueryGuard.ERROR_WRONG_TOOL)
                || raw.contains(AiToolQueryGuard.ERROR_NEED_CLARIFY);
    }

    /**
     * 执行单个 Tool（调试接口；按当前默认 Agent 能力集过滤）。
     */
    public String executeTool(String toolCode, Map<String, Object> arguments) {
        List<AiToolDefinition> all = toolCatalog.listAvailableTools(null);
        Map<String, AiToolDefinition> map = all.stream()
                .collect(Collectors.toMap(AiToolDefinition::getToolCode, Function.identity(), (a, b) -> a));
        String json;
        try {
            json = objectMapper.writeValueAsString(arguments == null ? Map.of() : arguments);
        } catch (Exception e) {
            json = "{}";
        }
        return executeToolAllowed(toolCode, json, map);
    }

    private SystemParts buildSystemParts(AiCapabilityBundle bundle) {
        StringBuilder agentOnly = new StringBuilder();
        if (bundle.getAgentSystemPrompt() != null && !bundle.getAgentSystemPrompt().isBlank()) {
            agentOnly.append(bundle.getAgentSystemPrompt().trim());
        } else {
            agentOnly.append("你是企业管理端助手。只能基于工具返回的事实回答；无数据时说明查不到；不要编造。");
        }
        agentOnly.append("\n\n## 回答格式\n")
                .append("请使用 Markdown 组织回答：标题、列表、表格、**加粗**、`代码`、代码块。")
                .append("结构化数据优先用表格；步骤用有序列表；关键结论放在开头。");
        agentOnly.append(clarifyPromptSection());
        agentOnly.append(AiChartFormatSpecs.promptSection());
        agentOnly.append(toolQuerySafetyPromptSection());
        agentOnly.append(toolDataPromptSection());

        StringBuilder skillsOnly = new StringBuilder();
        if (bundle.getActivatedSkills() != null && !bundle.getActivatedSkills().isEmpty()) {
            boolean anySkillPrompt = false;
            for (AiSkillHint s : bundle.getActivatedSkills()) {
                if (s.getSystemPrompt() != null && !s.getSystemPrompt().isBlank()) {
                    if (!anySkillPrompt) {
                        skillsOnly.append("\n\n# 已激活技能指令");
                        anySkillPrompt = true;
                    }
                    skillsOnly.append("\n## ").append(s.getSkillCode());
                    if (s.getName() != null && !s.getName().isBlank()) {
                        skillsOnly.append("（").append(s.getName()).append("）");
                    }
                    skillsOnly.append('\n').append(s.getSystemPrompt().trim());
                }
            }

            skillsOnly.append("\n\n可用技能（路由参考，实际调用以 tools 为准）：");
            for (AiSkillHint s : bundle.getActivatedSkills()) {
                skillsOnly.append("\n- ").append(s.getSkillCode());
                if (s.getName() != null && !s.getName().isBlank()) {
                    skillsOnly.append("（").append(s.getName()).append("）");
                }
            }
        }

        if (AiAgentModes.isScenario(bundle.getAgentMode())
                && bundle.getInactiveSkills() != null
                && !bundle.getInactiveSkills().isEmpty()) {
            skillsOnly.append("\n\n以下技能因权限未激活（勿调用其工具；若用户问起请说明缺哪些权限）：");
            int n = 0;
            for (AiSkillHint s : bundle.getInactiveSkills()) {
                if (n >= AiLimits.MAX_INACTIVE_SKILL_HINTS) {
                    int rest = bundle.getInactiveSkills().size() - n;
                    skillsOnly.append("\n另有 ").append(rest).append(" 个技能因权限未激活");
                    break;
                }
                skillsOnly.append("\n- ").append(s.getSkillCode());
                if (s.getName() != null) {
                    skillsOnly.append("（").append(s.getName()).append("）");
                }
                skillsOnly.append("：");
                if (s.getMissingHints() != null && !s.getMissingHints().isEmpty()) {
                    skillsOnly.append(String.join("；", s.getMissingHints()));
                } else {
                    skillsOnly.append("权限不足");
                }
                n++;
            }
        }

        String full = agentOnly.toString() + skillsOnly;
        return new SystemParts(agentOnly.toString(), skillsOnly.toString(), full);
    }

    private List<Map<String, Object>> toOpenAiToolMaps(List<AiToolDefinition> tools, String queryIntent) {
        List<AiToolDefinition> ordered = new ArrayList<>(tools == null ? List.of() : tools);
        if (AiQueryIntents.isDashboard(queryIntent)) {
            ordered.sort((a, b) -> Boolean.compare(
                    AiToolKindHints.looksLikeDashboard(b),
                    AiToolKindHints.looksLikeDashboard(a)));
        } else if (AiQueryIntents.AGGREGATE.equals(queryIntent)) {
            ordered.sort((a, b) -> Boolean.compare(
                    AiToolKindHints.looksLikeAggregate(b) || AiToolTypes.GOVERNED.equals(b.getToolType()),
                    AiToolKindHints.looksLikeAggregate(a) || AiToolTypes.GOVERNED.equals(a.getToolType())));
        } else if (AiQueryIntents.LOOKUP.equals(queryIntent) || AiQueryIntents.STATUS.equals(queryIntent)) {
            ordered.sort((a, b) -> Boolean.compare(
                    AiToolKindHints.looksLikeDetailLookup(b),
                    AiToolKindHints.looksLikeDetailLookup(a)));
        }
        List<Map<String, Object>> list = new ArrayList<>(ordered.size());
        for (AiToolDefinition t : ordered) {
            Map<String, Object> fn = new HashMap<>();
            fn.put("name", t.getToolCode());
            String baseDesc = t.getDescription() == null ? t.getName() : t.getDescription();
            if (AiToolKindHints.looksLikeDashboard(t)) {
                baseDesc = "【大屏推荐】" + (baseDesc == null ? "" : baseDesc);
            } else if (AiToolKindHints.looksLikeAggregate(t) || AiToolTypes.GOVERNED.equals(t.getToolType())) {
                baseDesc = "【汇总推荐】" + (baseDesc == null ? "" : baseDesc);
            } else if (AiToolKindHints.looksLikeDetailLookup(t)) {
                baseDesc = "【详情推荐】" + (baseDesc == null ? "" : baseDesc);
            } else if (AiToolKindHints.looksLikePagedList(t)) {
                baseDesc = "【明细分页】须带筛选约束。" + (baseDesc == null ? "" : baseDesc);
            }
            fn.put("description", AiToolSchemaPrompt.withReturns(baseDesc, t.getResponseSchema()));
            fn.put("parameters", t.getParametersSchema() == null
                    ? Map.of("type", "object", "properties", Map.of())
                    : t.getParametersSchema());
            Map<String, Object> item = new HashMap<>();
            item.put("type", "function");
            item.put("function", fn);
            list.add(item);
        }
        return list;
    }

    private static Map<String, Object> mutableMessage(String role, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    private Map<String, Object> parseJson(String json) {
        try {
            if (json == null || json.isBlank()) {
                return Map.of();
            }
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String preview(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "...";
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String toolQuerySafetyPromptSection() {
        return "\n\n## 查询安全：按问题意图分流（必须遵守）\n"
                + "有明确范围的查询允许执行；禁止无范围全表扫描。\n"
                + "意图与 Tool 选型：\n"
                + "- " + AiQueryIntents.LIST + "「最近1个月的订单」→ 分页/列表 Tool + 时间约束，面板翻页。\n"
                + "- " + AiQueryIntents.AGGREGATE + "「最近1个月订单金额总数」→ 优先聚合 Tool；"
                + "仅有分页时单页 + coverage；勿多页加总（N+1）；"
                + "complete=false 可报本批合计但须写覆盖行数/总数。\n"
                + "- " + AiQueryIntents.TREND + "/" + AiQueryIntents.COMPARE + "/"
                + AiQueryIntents.RANK + "/" + AiQueryIntents.DISTRIBUTION
                + "（趋势/对比/排行/分布）→ **大屏参数类**，必须优先大屏/看板/指标类 Tool，"
                + "禁止用明细分页拼数；可配合 ai-chart。\n"
                + "- " + AiQueryIntents.LOOKUP + "/" + AiQueryIntents.STATUS
                + " → 详情/状态接口，勿大列表。\n"
                + "- " + AiQueryIntents.EXPORT + " → 导出接口；" + AiQueryIntents.ACTION
                + " → 先澄清再写操作；" + AiQueryIntents.CLARIFY_NEEDED
                + " → 本轮不调分页 Tool。\n"
                + "规则：分页须带约束；大屏走大屏 Tool。"
                + "分不清场景 → 澄清兜底；有场景无匹配 Tool → 可降级则澄清改问；"
                + "连兜底都没有 → 明确拒绝并说明原因（禁止编造）。\n"
                + "门禁：" + AiToolQueryGuard.ERROR_CODE + " / "
                + AiToolQueryGuard.ERROR_WRONG_TOOL + " / "
                + AiToolQueryGuard.ERROR_NEED_CLARIFY + "；"
                + "编排拒绝码：" + AiQueryFallback.REFUSE_CODE + "。\n";
    }

    private static String toolDataPromptSection() {
        return "\n\n## Tool 大数据结果（必须遵守）\n"
                + "压缩摘要含 refId / fields / stats / sample / rowCount / coverage。\n"
                + "- LIST：sample + 结果面板分页。\n"
                + "- AGGREGATE：优先聚合 Tool；否则用本批 stats + coverage。"
                + "coverage.complete=true 可将本批 sum/avg 作为范围内结论；"
                + "complete=false 必须同时报「已覆盖 coveredRows/reportedTotal」与本批合计，"
                + "禁止说成全量总额，禁止为凑全量多次翻页同一列表 Tool。\n"
                + "- 大屏类（TREND/COMPARE/RANK/DISTRIBUTION）：以大屏 Tool 结果为准，"
                + "不要用分页明细的 stats 冒充大屏指标。\n"
                + "- 禁止再要全量原始 JSON。\n";
    }

    private static String clarifyPromptSection() {
        return "\n\n## 信息不足时的补充说明（必须遵守）\n"
                + "当问题缺少关键参数、范围不清、存在多种合理解释，或调用工具前必须由用户确认时："
                + "不要猜测，先用简短 Markdown 说明缺什么，然后在回答末尾输出唯一一个 "
                + AiClarifyConstants.FENCE_LANG + " 代码块（JSON），供界面渲染填写/选择表单。\n"
                + "字段 type 仅允许：text / textarea / number / select / single / multi；"
                + "options 用于 select/single/multi，形如 {\"value\":\"x\",\"label\":\"展示名\"}；"
                + "fields 最多 " + AiClarifyConstants.MAX_FIELDS + " 个；只问继续执行所必需的项。\n"
                + "信息已充分时禁止输出该代码块。用户若回复以「" + AiClarifyConstants.REPLY_PREFIX
                + "」开头，视为对表单的作答，请据此继续完成任务。\n"
                + "示例：\n"
                + "```" + AiClarifyConstants.FENCE_LANG + "\n"
                + "{\n"
                + "  \"id\": \"need_order\",\n"
                + "  \"title\": \"请补充查询条件\",\n"
                + "  \"description\": \"缺少订单号或时间范围将无法准确查询\",\n"
                + "  \"fields\": [\n"
                + "    {\"id\":\"orderNo\",\"label\":\"订单号\",\"type\":\"text\",\"required\":true,\"placeholder\":\"例如 SO20260101001\"},\n"
                + "    {\"id\":\"range\",\"label\":\"时间范围\",\"type\":\"single\",\"required\":true,"
                + "\"options\":[{\"value\":\"7d\",\"label\":\"近7天\"},{\"value\":\"30d\",\"label\":\"近30天\"}]}\n"
                + "  ]\n"
                + "}\n"
                + "```\n";
    }

    private record SystemParts(String agentOnly, String skillsOnly, String full) {
    }

    private record PreparedContext(String summary, List<AiChatMemory.StoredMessage> recent) {
    }
}
