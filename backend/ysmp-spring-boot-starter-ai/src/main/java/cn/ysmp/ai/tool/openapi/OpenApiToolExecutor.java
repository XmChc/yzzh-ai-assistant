package cn.ysmp.ai.tool.openapi;

import cn.ysmp.ai.spi.AiSecurityAdapter;
import cn.ysmp.ai.tool.AiToolDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * 将 OpenAPI 勾选的 Tool 转为对宿主本机 admin-api 的 HTTP 调用（转发当前用户 Authorization）。
 */
@Slf4j
@RequiredArgsConstructor
public class OpenApiToolExecutor {

    private final AiSecurityAdapter securityAdapter;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    /** 内调基址，如 http://127.0.0.1:58080 */
    private final String internalBaseUrl;
    /** 返回给编排的最大字符（建议用 store 上限，由编排再压缩） */
    private final int maxResponseChars;

    public String execute(AiToolDefinition tool, Map<String, Object> arguments) {
        if (tool.getPath() == null || tool.getHttpMethod() == null) {
            return "{\"error\":\"invalid openapi tool\"}";
        }
        String methodName = tool.getHttpMethod().toUpperCase();
        // 已启用白名单中的 OPENAPI Tool：仅允许 GET/POST（分页查询常用 POST）
        if (!"GET".equals(methodName) && !"POST".equals(methodName)) {
            return "{\"error\":\"openapi tool method not allowed: " + methodName + "\"}";
        }
        String auth = securityAdapter.getAuthorizationHeader();
        HttpMethod method = HttpMethod.valueOf(methodName);

        UriComponentsBuilder uriBuilder = UriComponentsBuilder
                .fromUriString(trimTrailingSlash(internalBaseUrl) + ensureLeadingSlash(tool.getPath()));
        if (arguments != null && (method == HttpMethod.GET || method == HttpMethod.DELETE)) {
            arguments.forEach((k, v) -> {
                if (v != null) {
                    uriBuilder.queryParam(k, v);
                }
            });
        }

        WebClient client = webClientBuilder.build();
        WebClient.RequestBodySpec spec = client.method(method)
                .uri(uriBuilder.build(true).toUri())
                .accept(MediaType.APPLICATION_JSON);
        if (auth != null && !auth.isBlank()) {
            spec = spec.header("Authorization", auth);
        }

        String body;
        try {
            if (method == HttpMethod.GET || method == HttpMethod.DELETE) {
                body = spec.retrieve().bodyToMono(String.class)
                        .block(Duration.ofSeconds(60));
            } else {
                // objectMapper 预留序列化扩展；当前直接传 Map
                body = spec.contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(arguments == null ? Map.of() : arguments)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(Duration.ofSeconds(60));
            }
        } catch (Exception e) {
            log.warn("[ai] openapi tool call failed code={} err={}", tool.getToolCode(), e.getMessage());
            return "{\"error\":\"" + escape(e.getMessage()) + "\"}";
        }
        return truncate(body == null ? "" : body);
    }

    private String truncate(String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxResponseChars) {
            return s;
        }
        int limit = Math.min(s.length(), maxResponseChars);
        return s.substring(0, limit) + "...(truncated)";
    }

    private static String ensureLeadingSlash(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }

    private static String trimTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            return "http://127.0.0.1:58080";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String escape(String msg) {
        if (msg == null) {
            return "";
        }
        return msg.replace("\"", "'");
    }
}
