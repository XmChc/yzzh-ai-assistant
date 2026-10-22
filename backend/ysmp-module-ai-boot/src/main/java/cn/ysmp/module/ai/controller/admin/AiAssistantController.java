package cn.ysmp.module.ai.controller.admin;

import cn.ysmp.ai.chat.AiChatEventListener;
import cn.ysmp.ai.chat.AiChatOrchestrator;
import cn.ysmp.ai.chat.AiChatRequest;
import cn.ysmp.ai.chat.AiChatResponse;
import cn.ysmp.ai.chat.AiClarifyForm;
import cn.ysmp.ai.chat.AiContextUsage;
import cn.ysmp.ai.constants.AiApiPaths;
import cn.ysmp.ai.constants.AiLimits;
import cn.ysmp.ai.constants.AiSseEventTypes;
import cn.ysmp.ai.spi.AiChatMemory;
import cn.ysmp.ai.spi.AiSecurityAdapter;
import cn.ysmp.ai.tool.AiCapabilityBundle;
import cn.ysmp.ai.tool.AiToolCatalog;
import cn.ysmp.ai.tool.result.AiToolResultStore;
import cn.ysmp.module.ai.framework.web.AiCommonResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 对话入口：同步 JSON + SSE 流式 + 会话历史 + Tool 结果分页。
 */
@Slf4j
@RestController
@RequestMapping(AiApiPaths.ASSISTANT)
@RequiredArgsConstructor
public class AiAssistantController {

    private static final long SSE_TIMEOUT_MS = 300_000L;

    private final AiChatOrchestrator chatOrchestrator;
    private final AiToolCatalog toolCatalog;
    private final AiChatMemory chatMemory;
    private final AiSecurityAdapter securityAdapter;
    private final AiToolResultStore toolResultStore;
    private final ObjectMapper objectMapper;
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ai-sse");
        t.setDaemon(true);
        return t;
    });

    @PostMapping(AiApiPaths.ASSISTANT_CHAT)
    public AiCommonResult<AiChatResponse> chat(@RequestBody AiChatRequest request) {
        AiChatResponse resp = chatOrchestrator.chat(request);
        if (resp.getError() != null && (resp.getContent() == null || resp.getContent().isBlank())) {
            return AiCommonResult.fail(resp.getError());
        }
        return AiCommonResult.ok(resp);
    }

    @PostMapping(value = AiApiPaths.ASSISTANT_CHAT_STREAM, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody AiChatRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        sseExecutor.execute(() -> {
            try {
                chatOrchestrator.run(request, new AiChatEventListener() {
                    @Override
                    public void onMeta(String sessionId) {
                        send(emitter, AiSseEventTypes.META, Map.of("sessionId", sessionId));
                    }

                    @Override
                    public void onUsage(AiContextUsage usage) {
                        if (usage != null) {
                            send(emitter, AiSseEventTypes.USAGE, usage.toMap());
                        }
                    }

                    @Override
                    public void onClarify(AiClarifyForm form) {
                        if (form != null) {
                            send(emitter, AiSseEventTypes.CLARIFY, form.toMap());
                        }
                    }

                    @Override
                    public void onToolCall(String callId, String toolName, String argumentsJson) {
                        Map<String, Object> p = new LinkedHashMap<>();
                        p.put("id", callId);
                        p.put("name", toolName);
                        p.put("arguments", argumentsJson);
                        send(emitter, AiSseEventTypes.TOOL_CALL, p);
                    }

                    @Override
                    public void onToolConfirm(String callId, String toolName, String argumentsJson) {
                        Map<String, Object> p = new LinkedHashMap<>();
                        p.put("id", callId);
                        p.put("name", toolName);
                        p.put("arguments", argumentsJson);
                        send(emitter, AiSseEventTypes.TOOL_CONFIRM, p);
                    }

                    @Override
                    public void onToolResult(String callId, String toolName, String resultPreview) {
                        Map<String, Object> p = new LinkedHashMap<>();
                        p.put("id", callId);
                        p.put("name", toolName);
                        p.put("preview", resultPreview);
                        send(emitter, AiSseEventTypes.TOOL_RESULT, p);
                    }

                    @Override
                    public void onToolData(Map<String, Object> dataRef) {
                        if (dataRef != null) {
                            send(emitter, AiSseEventTypes.TOOL_DATA, dataRef);
                        }
                    }

                    @Override
                    public void onDelta(String textChunk) {
                        send(emitter, AiSseEventTypes.DELTA, Map.of("text", textChunk == null ? "" : textChunk));
                    }

                    @Override
                    public void onDone(String fullContent) {
                        send(emitter, AiSseEventTypes.DONE, Map.of(
                                "content", fullContent == null ? "" : fullContent));
                        emitter.complete();
                    }

                    @Override
                    public void onError(String message) {
                        send(emitter, AiSseEventTypes.ERROR, Map.of("message", message == null ? "" : message));
                        emitter.complete();
                    }
                }, true);
            } catch (Exception e) {
                log.warn("[ai] sse failed: {}", e.getMessage());
                try {
                    send(emitter, AiSseEventTypes.ERROR, Map.of("message", e.getMessage() == null ? "sse error" : e.getMessage()));
                } catch (Exception ignored) {
                    // ignore
                }
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    @GetMapping(AiApiPaths.ASSISTANT_CAPABILITIES)
    public AiCommonResult<AiCapabilityBundle> capabilities(
            @RequestParam(value = "agentCode", required = false) String agentCode) {
        return AiCommonResult.ok(toolCatalog.resolveCapabilities(agentCode, null, null));
    }

    @GetMapping(AiApiPaths.ASSISTANT_SESSIONS)
    public AiCommonResult<List<Map<String, Object>>> sessions(
            @RequestParam(value = "limit", required = false) Integer limit) {
        int lim = limit == null ? AiLimits.DEFAULT_SESSION_LIST_LIMIT : limit;
        return AiCommonResult.ok(chatMemory.listSessions(securityAdapter.getLoginUserId(), lim));
    }

    @GetMapping(AiApiPaths.ASSISTANT_SESSION_MESSAGES)
    public AiCommonResult<List<Map<String, Object>>> sessionMessages(@RequestParam("sessionId") String sessionId) {
        return AiCommonResult.ok(chatMemory.listMessages(sessionId, securityAdapter.getLoginUserId()));
    }

    @PostMapping(AiApiPaths.ASSISTANT_SESSION_DELETE)
    public AiCommonResult<Boolean> deleteSession(@RequestParam("sessionId") String sessionId) {
        chatMemory.deleteSession(sessionId, securityAdapter.getLoginUserId());
        return AiCommonResult.ok(true);
    }

    /** Tool 暂存结果分页 */
    @GetMapping(AiApiPaths.ASSISTANT_TOOL_RESULT_PAGE)
    public AiCommonResult<Map<String, Object>> toolResultPage(
            @RequestParam("refId") String refId,
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize) {
        var stored = toolResultStore.get(refId);
        if (stored.isEmpty() || !owns(stored.get().userId())) {
            return AiCommonResult.fail("结果不存在或已过期");
        }
        return toolResultStore.page(refId, page == null ? 1 : page, pageSize == null ? 10 : pageSize)
                .map(p -> AiCommonResult.ok(pageToMap(p)))
                .orElseGet(() -> AiCommonResult.fail("结果不存在或已过期"));
    }

    /** 嵌套字段分页（行内大数组/对象） */
    @GetMapping(AiApiPaths.ASSISTANT_TOOL_RESULT_FIELD)
    public AiCommonResult<Map<String, Object>> toolResultField(
            @RequestParam("refId") String refId,
            @RequestParam("rowIndex") Integer rowIndex,
            @RequestParam("field") String field,
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize) {
        var stored = toolResultStore.get(refId);
        if (stored.isEmpty() || !owns(stored.get().userId())) {
            return AiCommonResult.fail("结果不存在或已过期");
        }
        return toolResultStore.pageField(refId, rowIndex == null ? 0 : rowIndex, field,
                        page == null ? 1 : page, pageSize == null ? 10 : pageSize)
                .map(p -> AiCommonResult.ok(pageToMap(p)))
                .orElseGet(() -> AiCommonResult.fail("字段不存在或无法展开"));
    }

    private boolean owns(Long ownerId) {
        Long uid = securityAdapter.getLoginUserId();
        if (ownerId == null || uid == null) {
            return true;
        }
        return Objects.equals(ownerId, uid);
    }

    private static Map<String, Object> pageToMap(AiToolResultStore.ToolResultPage p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("refId", p.refId());
        m.put("toolCode", p.toolCode());
        m.put("shape", p.shape());
        m.put("fields", p.fields());
        m.put("rows", p.rows());
        m.put("page", p.page());
        m.put("pageSize", p.pageSize());
        m.put("total", p.total());
        if (p.reportedTotal() != null) {
            m.put("reportedTotal", p.reportedTotal());
        }
        if (p.parentField() != null) {
            m.put("parentField", p.parentField());
        }
        return m;
    }

    private void send(SseEmitter emitter, String event, Object data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            emitter.send(SseEmitter.event().name(event).data(json, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            throw new IllegalStateException("sse send failed: " + e.getMessage(), e);
        }
    }
}
