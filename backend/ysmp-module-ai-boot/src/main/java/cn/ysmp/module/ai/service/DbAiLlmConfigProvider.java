package cn.ysmp.module.ai.service;

import cn.ysmp.ai.config.AiProperties;
import cn.ysmp.ai.spi.AiLlmConfigProvider;
import cn.ysmp.ai.spi.AiLlmRuntimeConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

/**
 * 从 ai_model_config 读取模型配置（带 TTL 缓存，写后 invalidate）。
 */
@Slf4j
@RequiredArgsConstructor
public class DbAiLlmConfigProvider implements AiLlmConfigProvider {

    private final JdbcTemplate jdbcTemplate;
    private final AiProperties properties;

    private volatile AiLlmRuntimeConfig cached;
    private volatile long loadedAtMs;

    @Override
    public AiLlmRuntimeConfig getRuntimeConfig() {
        long ttl = properties.getLlmConfigCacheTtlMs();
        AiLlmRuntimeConfig c = cached;
        long now = System.currentTimeMillis();
        if (c != null && (ttl <= 0 || now - loadedAtMs < ttl)) {
            return c;
        }
        synchronized (this) {
            c = cached;
            now = System.currentTimeMillis();
            if (c != null && (ttl <= 0 || now - loadedAtMs < ttl)) {
                return c;
            }
            AiLlmRuntimeConfig loaded = load();
            cached = loaded;
            loadedAtMs = System.currentTimeMillis();
            return loaded;
        }
    }

    public void invalidate() {
        cached = null;
        loadedAtMs = 0L;
        log.debug("[ai] llm config cache invalidated");
    }

    private AiLlmRuntimeConfig load() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT enabled, base_url, api_key, model, timeout_ms FROM ai_model_config WHERE deleted = 0 ORDER BY id ASC LIMIT 1");
        if (rows.isEmpty()) {
            return AiLlmRuntimeConfig.builder().enabled(false).timeoutMs(60000).build();
        }
        Map<String, Object> row = rows.get(0);
        return AiLlmRuntimeConfig.builder()
                .enabled(asBool(row.get("enabled")))
                .baseUrl(asStr(row.get("base_url")))
                .apiKey(asStr(row.get("api_key")))
                .model(asStr(row.get("model")))
                .timeoutMs(row.get("timeout_ms") == null ? 60000 : ((Number) row.get("timeout_ms")).intValue())
                .build();
    }

    private static boolean asBool(Object v) {
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Number n) {
            return n.intValue() != 0;
        }
        return false;
    }

    private static String asStr(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
