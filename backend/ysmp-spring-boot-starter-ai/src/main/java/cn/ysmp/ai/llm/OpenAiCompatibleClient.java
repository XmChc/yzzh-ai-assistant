package cn.ysmp.ai.llm;

import cn.ysmp.ai.spi.AiLlmRuntimeConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 Chat Completions 客户端（非流式完整消息，含 tool_calls）。
 */
@Slf4j
@RequiredArgsConstructor
public class OpenAiCompatibleClient {

    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    /**
     * 非流式补全，返回 content 与 tool_calls。
     */
    public LlmCompletionResult complete(AiLlmRuntimeConfig config,
                                        List<Map<String, Object>> messages,
                                        List<Map<String, Object>> tools) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", config.getModel());
        body.put("stream", false);
        body.set("messages", objectMapper.valueToTree(messages));
        if (tools != null && !tools.isEmpty()) {
            body.set("tools", objectMapper.valueToTree(tools));
        }

        String base = trimTrailingSlash(config.getBaseUrl());
        WebClient client = webClientBuilder
                .baseUrl(base)
                .defaultHeader("Authorization", "Bearer " + config.getApiKey())
                .build();

        String raw = client.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofMillis(Math.max(config.getTimeoutMs(), 1000)));

        return parseCompletion(raw);
    }

    /**
     * @deprecated 使用 {@link #complete}；保留兼容仅取 content 的旧调用
     */
    @Deprecated
    public String chat(AiLlmRuntimeConfig config, List<Map<String, Object>> messages,
                       List<Map<String, Object>> tools) {
        LlmCompletionResult r = complete(config, messages, tools);
        if (r.getContent() != null && !r.getContent().isBlank()) {
            return r.getContent();
        }
        if (r.hasToolCalls()) {
            return "";
        }
        return r.getContent() == null ? "" : r.getContent();
    }

    LlmCompletionResult parseCompletion(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode message = root.path("choices").path(0).path("message");
            String finish = root.path("choices").path(0).path("finish_reason").asText(null);
            String content = textOrNull(message.path("content"));

            List<LlmToolCall> calls = new ArrayList<>();
            JsonNode toolCalls = message.path("tool_calls");
            if (toolCalls.isArray()) {
                for (JsonNode tc : toolCalls) {
                    String id = textOrNull(tc.path("id"));
                    String name = textOrNull(tc.path("function").path("name"));
                    String args = textOrNull(tc.path("function").path("arguments"));
                    if (name == null || name.isBlank()) {
                        continue;
                    }
                    calls.add(LlmToolCall.builder()
                            .id(id == null || id.isBlank() ? ("call_" + calls.size()) : id)
                            .name(name)
                            .argumentsJson(args == null ? "{}" : args)
                            .build());
                }
            }

            Map<String, Object> assistantMsg = new LinkedHashMap<>();
            assistantMsg.put("role", "assistant");
            if (content != null) {
                assistantMsg.put("content", content);
            } else {
                assistantMsg.put("content", null);
            }
            if (!calls.isEmpty()) {
                List<Map<String, Object>> tcMaps = new ArrayList<>();
                for (LlmToolCall c : calls) {
                    Map<String, Object> fn = new LinkedHashMap<>();
                    fn.put("name", c.getName());
                    fn.put("arguments", c.getArgumentsJson());
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", c.getId());
                    item.put("type", "function");
                    item.put("function", fn);
                    tcMaps.add(item);
                }
                assistantMsg.put("tool_calls", tcMaps);
            }

            JsonNode usage = root.path("usage");
            Integer promptTokens = usage.isMissingNode() ? null : intOrNull(usage.path("prompt_tokens"));
            Integer completionTokens = usage.isMissingNode() ? null : intOrNull(usage.path("completion_tokens"));
            Integer totalTokens = usage.isMissingNode() ? null : intOrNull(usage.path("total_tokens"));

            return LlmCompletionResult.builder()
                    .content(content)
                    .toolCalls(calls)
                    .finishReason(finish)
                    .assistantMessage(assistantMsg)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(totalTokens)
                    .build();
        } catch (Exception e) {
            log.warn("[ai] parse completion failed: {}", e.getMessage());
            return LlmCompletionResult.builder()
                    .content(raw)
                    .toolCalls(List.of())
                    .finishReason("error")
                    .build();
        }
    }

    private static Integer intOrNull(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull() || !n.isNumber()) {
            return null;
        }
        return n.asInt();
    }

    private static String textOrNull(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) {
            return null;
        }
        return n.asText();
    }

    private static String trimTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
