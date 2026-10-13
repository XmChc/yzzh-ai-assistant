package cn.ysmp.module.ai.service;

import cn.ysmp.ai.spi.AiEndpointPermissionResolver;
import cn.ysmp.module.ai.service.catalog.AiCatalogCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 将 SPI 解析出的权限码固化到 ai_tool_api.required_permissions。
 */
@Slf4j
@RequiredArgsConstructor
public class AiToolPermissionSyncService {

    private final JdbcTemplate jdbcTemplate;
    private final AiEndpointPermissionResolver permissionResolver;
    private final ObjectMapper objectMapper;
    private final AiCatalogCache catalogCache;

    public List<String> resolveAndSerialize(String method, String path) {
        List<String> perms = permissionResolver.resolve(method, path);
        if (perms == null) {
            return List.of();
        }
        return perms;
    }

    public String toJson(List<String> perms) {
        try {
            return objectMapper.writeValueAsString(perms == null ? List.of() : perms);
        } catch (Exception e) {
            return "[]";
        }
    }

    /**
     * 回填所有已启用 OPENAPI Tool。
     *
     * @return 更新行数
     */
    public int resyncAllOpenApiTools() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, http_method, path FROM ai_tool_api "
                        + "WHERE deleted=0 AND tool_type='OPENAPI' AND http_method IS NOT NULL AND path IS NOT NULL");
        int updated = 0;
        for (Map<String, Object> row : rows) {
            String method = String.valueOf(row.get("http_method")).toUpperCase(Locale.ROOT);
            String path = String.valueOf(row.get("path"));
            List<String> perms = resolveAndSerialize(method, path);
            String json = toJson(perms);
            Long id = ((Number) row.get("id")).longValue();
            jdbcTemplate.update("UPDATE ai_tool_api SET required_permissions=? WHERE id=?", json, id);
            updated++;
            log.debug("[ai] resync tool id={} {} {} -> {}", id, method, path, json);
        }
        log.info("[ai] resync OpenAPI tool permissions: {} rows", updated);
        if (updated > 0) {
            catalogCache.invalidate();
        }
        return updated;
    }
}
