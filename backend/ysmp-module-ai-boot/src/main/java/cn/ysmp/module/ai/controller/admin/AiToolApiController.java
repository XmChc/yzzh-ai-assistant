package cn.ysmp.module.ai.controller.admin;

import cn.ysmp.ai.constants.AiApiPaths;
import cn.ysmp.module.ai.framework.web.AiCommonResult;
import cn.ysmp.module.ai.service.AiToolPermissionSyncService;
import cn.ysmp.module.ai.service.OpenApiCatalogService;
import cn.ysmp.module.ai.service.catalog.AiCatalogCache;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
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

    @PutMapping("/update-enabled")
    public AiCommonResult<Boolean> updateEnabled(@RequestParam Long id, @RequestParam Boolean enabled) {
        jdbcTemplate.update("UPDATE ai_tool_api SET enabled=? WHERE id=? AND deleted=0", enabled, id);
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
     * 手动回填已注册 OPENAPI Tool 的 required_permissions（SPI 固化）。
     */
    @PostMapping("/resync-permissions")
    public AiCommonResult<Integer> resyncPermissions() {
        return AiCommonResult.ok(permissionSyncService.resyncAllOpenApiTools());
    }

    /**
     * 按当前 OpenAPI 回填已注册 OPENAPI Tool 的入参/出参 JSON Schema。
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
