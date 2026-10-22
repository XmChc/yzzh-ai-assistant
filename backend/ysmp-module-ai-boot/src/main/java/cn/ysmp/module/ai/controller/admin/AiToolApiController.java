package cn.ysmp.module.ai.controller.admin;

import cn.ysmp.ai.constants.AiAdminChannelPaths;
import cn.ysmp.ai.constants.AiApiPaths;
import cn.ysmp.module.ai.framework.web.AiCommonResult;
import cn.ysmp.module.ai.service.AiToolPermissionSyncService;
import cn.ysmp.module.ai.service.OpenApiCatalogService;
import cn.ysmp.module.ai.service.catalog.AiCatalogCache;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Tool 接口配置：列表、启停、从 OpenAPI 按模块勾选注册（白名单）。
 * <p>启用门禁：须已同步非空权限码，且路径符合 ADMIN 渠道规则。
 */
@RestController
@RequestMapping(AiApiPaths.TOOL_API)
@RequiredArgsConstructor
public class AiToolApiController {

    private final JdbcTemplate jdbcTemplate;
    private final OpenApiCatalogService openApiCatalogService;
    private final AiToolPermissionSyncService permissionSyncService;
    private final AiCatalogCache catalogCache;

    @GetMapping("/list")
    public AiCommonResult<List<Map<String, Object>>> list() {
        try {
            return AiCommonResult.ok(jdbcTemplate.queryForList(
                    "SELECT id, tool_code, tool_type, name, description, enabled, http_method, path, module, channel, "
                            + "param_schema_json, response_schema_json, required_permissions, sort "
                            + "FROM ai_tool_api WHERE deleted = 0 ORDER BY module ASC, sort ASC, id ASC"));
        } catch (Exception e0) {
            try {
                return AiCommonResult.ok(jdbcTemplate.queryForList(
                        "SELECT id, tool_code, tool_type, name, description, enabled, http_method, path, module, "
                                + "param_schema_json, response_schema_json, required_permissions, sort "
                                + "FROM ai_tool_api WHERE deleted = 0 ORDER BY module ASC, sort ASC, id ASC"));
            } catch (Exception e1) {
                try {
                    return AiCommonResult.ok(jdbcTemplate.queryForList(
                            "SELECT id, tool_code, tool_type, name, description, enabled, http_method, path, module, "
                                    + "param_schema_json, required_permissions, sort "
                                    + "FROM ai_tool_api WHERE deleted = 0 ORDER BY module ASC, sort ASC, id ASC"));
                } catch (Exception e2) {
                    return AiCommonResult.ok(jdbcTemplate.queryForList(
                            "SELECT id, tool_code, tool_type, name, description, enabled, http_method, path, sort "
                                    + "FROM ai_tool_api WHERE deleted = 0 ORDER BY sort ASC, id ASC"));
                }
            }
        }
    }

    /**
     * 启停 Tool。启用时须已绑定非空权限码，且 path 符合 ADMIN 渠道白名单。
     */
    @PutMapping("/update-enabled")
    public AiCommonResult<Boolean> updateEnabled(@RequestParam("id") Long id, @RequestParam("enabled") Boolean enabled) {
        if (Boolean.TRUE.equals(enabled)) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT path, required_permissions FROM ai_tool_api WHERE id=? AND deleted=0", id);
            if (rows.isEmpty()) {
                return AiCommonResult.fail("接口不存在或已注销");
            }
            Map<String, Object> row = rows.get(0);
            String path = row.get("path") == null ? null : String.valueOf(row.get("path"));
            if (!AiAdminChannelPaths.isAllowed(path)) {
                return AiCommonResult.fail(AiAdminChannelPaths.rejectReason(path));
            }
            String permJson = row.get("required_permissions") == null
                    ? null : String.valueOf(row.get("required_permissions"));
            if (!permissionSyncService.hasBoundPermissionsJson(permJson)) {
                return AiCommonResult.fail("无权限码，无法启用：请先为宿主接口补充 @PreAuthorize 并同步权限码");
            }
        }
        jdbcTemplate.update("UPDATE ai_tool_api SET enabled=? WHERE id=? AND deleted=0", enabled, id);
        catalogCache.invalidate();
        return AiCommonResult.ok(true);
    }

    /**
     * 注销已注册 Tool：软删白名单，并清理技能绑定（{@code ai_skill_tool}）。
     * <p>注销后可再次从 OpenAPI 目录重新注册（同 tool_code 会复活该行）。
     */
    @DeleteMapping("/delete")
    public AiCommonResult<Boolean> delete(@RequestParam("id") Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, tool_code FROM ai_tool_api WHERE id=? AND deleted=0", id);
        if (rows.isEmpty()) {
            return AiCommonResult.fail("接口不存在或已注销");
        }
        String toolCode = String.valueOf(rows.get(0).get("tool_code"));
        jdbcTemplate.update("UPDATE ai_tool_api SET deleted=1, enabled=0 WHERE id=? AND deleted=0", id);
        jdbcTemplate.update("DELETE FROM ai_skill_tool WHERE tool_code=?", toolCode);
        openApiCatalogService.invalidateCatalogCache();
        catalogCache.invalidate();
        return AiCommonResult.ok(true);
    }

    /**
     * 扫描本机 OpenAPI：GET + POST 候选，按功能模块分组；真正执行范围 = 已注册且启用。
     */
    @GetMapping("/openapi-catalog")
    public AiCommonResult<List<OpenApiCatalogService.CatalogModule>> openapiCatalog() {
        return AiCommonResult.ok(openApiCatalogService.listCatalogGrouped());
    }

    /**
     * 勾选注册为 OPENAPI Tool（写入白名单并启用；仅允许 GET/POST）。
     */
    @PostMapping("/register-openapi")
    public AiCommonResult<Boolean> register(@RequestBody RegisterReq req) {
        try {
            openApiCatalogService.register(
                    req.getMethod(), req.getPath(), req.getSummary(), req.getOperationId(), req.getModule());
            return AiCommonResult.ok(true);
        } catch (IllegalArgumentException e) {
            return AiCommonResult.fail(e.getMessage());
        }
    }

    /**
     * 从宿主 SPI（{@code @PreAuthorize} 等）同步已注册 OPENAPI Tool 的 {@code required_permissions}。
     * <p>禁止管理端手填权限码；宿主改注解后调用本接口刷新固化结果。
     */
    @PostMapping("/resync-permissions")
    public AiCommonResult<Integer> resyncPermissions() {
        return AiCommonResult.ok(permissionSyncService.resyncAllOpenApiTools());
    }

    /**
     * 从当前 OpenAPI 同步已注册 OPENAPI Tool 的入参/出参 JSON Schema。
     * <p>禁止管理端手填 Schema；接口契约变更后调用本接口刷新。
     */
    @PostMapping("/resync-schemas")
    public AiCommonResult<Integer> resyncSchemas() {
        return AiCommonResult.ok(openApiCatalogService.resyncAllSchemas());
    }

    @Data
    public static class RegisterReq {
        private String method;
        private String path;
        private String summary;
        private String operationId;
        /** 功能模块；空则按路径推断 */
        private String module;
    }
}
