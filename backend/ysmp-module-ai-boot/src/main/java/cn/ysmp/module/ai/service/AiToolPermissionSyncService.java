package cn.ysmp.module.ai.service;

import cn.ysmp.ai.constants.AiAdminChannelPaths;
import cn.ysmp.ai.constants.AiChannels;
import cn.ysmp.ai.guard.AiAccessGuard;
import cn.ysmp.ai.spi.AiEndpointPermissionResolver;
import cn.ysmp.module.ai.service.catalog.AiCatalogCache;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 将 SPI 解析出的权限码固化到 ai_tool_api.required_permissions。
 * <p>空权限码或非 ADMIN 合法路径的 Tool 强制停用（fail-closed 自愈）。
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

    /** 解析库内 JSON 权限码是否含非空项 */
    public boolean hasBoundPermissionsJson(String json) {
        return AiAccessGuard.hasBoundPermissionCodes(parsePerms(json));
    }

    /**
     * 回填所有 OPENAPI Tool 权限码；空权限或非法路径强制 enabled=0。
     *
     * @return 更新行数
     */
    public int resyncAllOpenApiTools() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, http_method, path FROM ai_tool_api "
                        + "WHERE deleted=0 AND tool_type='OPENAPI' AND http_method IS NOT NULL AND path IS NOT NULL");
        int updated = 0;
        int disabled = 0;
        for (Map<String, Object> row : rows) {
            String method = String.valueOf(row.get("http_method")).toUpperCase(Locale.ROOT);
            String path = String.valueOf(row.get("path"));
            List<String> perms = resolveAndSerialize(method, path);
            String json = toJson(perms);
            Long id = ((Number) row.get("id")).longValue();
            boolean pathOk = AiAdminChannelPaths.isAllowed(path);
            boolean permOk = AiAccessGuard.hasBoundPermissionCodes(perms);
            if (!pathOk || !permOk) {
                jdbcTemplate.update(
                        "UPDATE ai_tool_api SET required_permissions=?, enabled=0 WHERE id=?",
                        json, id);
                disabled++;
                log.info("[ai] disable tool id={} pathOk={} permOk={} {} {}", id, pathOk, permOk, method, path);
            } else {
                jdbcTemplate.update("UPDATE ai_tool_api SET required_permissions=? WHERE id=?", json, id);
            }
            // 尽量回填 channel=ADMIN（列不存在时忽略）
            trySetAdminChannel(id);
            updated++;
            log.debug("[ai] resync tool id={} {} {} -> {}", id, method, path, json);
        }
        log.info("[ai] resync OpenAPI tool permissions: {} rows, forced-disabled {}", updated, disabled);
        if (updated > 0) {
            catalogCache.invalidate();
        }
        return updated;
    }

    private void trySetAdminChannel(Long id) {
        try {
            jdbcTemplate.update(
                    "UPDATE ai_tool_api SET channel=? WHERE id=? AND (channel IS NULL OR channel='')",
                    AiChannels.ADMIN, id);
        } catch (Exception ignored) {
            // 未升级 channel 列
        }
    }

    private List<String> parsePerms(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> list = objectMapper.readValue(json, new TypeReference<>() {});
            return list == null ? List.of() : list;
        } catch (Exception e) {
            return List.of();
        }
    }
}
