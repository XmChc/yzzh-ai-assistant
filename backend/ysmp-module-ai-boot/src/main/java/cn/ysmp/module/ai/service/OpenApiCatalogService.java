package cn.ysmp.module.ai.service;

import cn.ysmp.ai.constants.AiApiPaths;
import cn.ysmp.ai.constants.AiHttpMethods;
import cn.ysmp.ai.constants.AiToolTypes;
import cn.ysmp.module.ai.service.catalog.AiCatalogCache;
import cn.ysmp.module.ai.service.openapi.OpenApiToolSchemaFactory;
import cn.ysmp.module.ai.service.openapi.OpenApiToolSchemaFactory.ToolSchemas;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 从宿主 OpenAPI 列出可勾选候选接口（GET + POST），按功能模块分组；
 * 真正可执行范围 = 已注册且 enabled 的白名单。
 */
@Slf4j
@RequiredArgsConstructor
public class OpenApiCatalogService {

    private static final Pattern QUERY_PATH = Pattern.compile(
            ".*/(page|list|get|query|export|detail|info|search)(/.*)?$",
            Pattern.CASE_INSENSITIVE);

    private final ObjectProvider<OpenAPI> openAPIProvider;
    private final JdbcTemplate jdbcTemplate;
    private final AiToolPermissionSyncService permissionSyncService;
    private final AiCatalogCache catalogCache;
    private final OpenApiToolSchemaFactory schemaFactory;

    /**
     * 可勾选候选：GET 全部 + POST 全部（写操作需人工甄别）；按 OpenAPI tag / 路径模块分组。
     */
    public List<CatalogModule> listCatalogGrouped() {
        OpenAPI openAPI = openAPIProvider.getIfAvailable();
        Map<String, String> tagDescriptions = tagDescriptionMap(openAPI);
        Set<String> registered = loadRegisteredKeys();

        List<CatalogItem> flat = new ArrayList<>();
        if (openAPI == null || openAPI.getPaths() == null) {
            log.warn("[ai] OpenAPI bean not found; ensure springdoc is on classpath");
            return List.of();
        }
        for (Map.Entry<String, PathItem> e : openAPI.getPaths().entrySet()) {
            String path = e.getKey();
            if (AiApiPaths.isAiModulePath(path)) {
                continue;
            }
            PathItem item = e.getValue();
            addIfAllowed(flat, path, item.getGet(), "GET", tagDescriptions, registered);
            addIfAllowed(flat, path, item.getPost(), "POST", tagDescriptions, registered);
        }

        Map<String, CatalogModule> groups = new LinkedHashMap<>();
        flat.stream()
                .sorted(Comparator
                        .comparing(CatalogItem::getModule, Comparator.nullsLast(String::compareTo))
                        .thenComparing(CatalogItem::getPath)
                        .thenComparing(CatalogItem::getMethod))
                .forEach(c -> {
                    CatalogModule m = groups.computeIfAbsent(c.getModule(), k -> {
                        CatalogModule cm = new CatalogModule();
                        cm.setModule(k);
                        cm.setModuleLabel(c.getModuleLabel());
                        cm.setItems(new ArrayList<>());
                        return cm;
                    });
                    m.getItems().add(c);
                });
        return new ArrayList<>(groups.values());
    }

    /** @deprecated 使用 {@link #listCatalogGrouped()} */
    @Deprecated
    public List<CatalogItem> listGetOperations() {
        return listCatalogGrouped().stream().flatMap(m -> m.getItems().stream()).toList();
    }

    private void addIfAllowed(List<CatalogItem> items, String path, Operation op, String method,
                              Map<String, String> tagDescriptions, Set<String> registered) {
        if (op == null) {
            return;
        }
        String moduleKey = resolveModuleKey(op, path);
        String moduleLabel = resolveModuleLabel(moduleKey, tagDescriptions);

        CatalogItem c = new CatalogItem();
        c.setMethod(method);
        c.setPath(path);
        c.setSummary(op.getSummary() != null ? op.getSummary() : op.getOperationId());
        c.setOperationId(op.getOperationId());
        c.setModule(moduleKey);
        c.setModuleLabel(moduleLabel);
        c.setSuggested(isSuggestedReadOnly(method, path));
        c.setRegistered(registered.contains(method.toUpperCase(Locale.ROOT) + " " + path));
        List<String> perms = permissionSyncService.resolveAndSerialize(method, path);
        c.setRequiredPermissions(perms);
        items.add(c);
    }

    static boolean isSuggestedReadOnly(String method, String path) {
        if (AiHttpMethods.GET.equalsIgnoreCase(method)) {
            return true;
        }
        if (AiHttpMethods.POST.equalsIgnoreCase(method) && path != null) {
            return QUERY_PATH.matcher(path).matches();
        }
        return false;
    }

    static String resolveModuleKey(Operation op, String path) {
        if (op != null && op.getTags() != null && !op.getTags().isEmpty()) {
            String tag = op.getTags().get(0);
            if (tag != null && !tag.isBlank()) {
                return tag.trim();
            }
        }
        return moduleFromPath(path);
    }

    static String moduleFromPath(String path) {
        if (path == null || path.isBlank()) {
            return "未分类";
        }
        String p = path;
        if (p.startsWith("/admin-api/")) {
            p = p.substring("/admin-api/".length());
        } else if (p.startsWith("/")) {
            p = p.substring(1);
        }
        int slash = p.indexOf('/');
        String seg = slash >= 0 ? p.substring(0, slash) : p;
        return seg == null || seg.isBlank() ? "未分类" : seg;
    }

    private static String resolveModuleLabel(String moduleKey, Map<String, String> tagDescriptions) {
        if (moduleKey == null) {
            return "未分类";
        }
        String desc = tagDescriptions.get(moduleKey);
        if (desc != null && !desc.isBlank()) {
            return moduleKey + " — " + desc;
        }
        return moduleKey;
    }

    private static Map<String, String> tagDescriptionMap(OpenAPI openAPI) {
        Map<String, String> map = new HashMap<>();
        if (openAPI == null || openAPI.getTags() == null) {
            return map;
        }
        for (Tag tag : openAPI.getTags()) {
            if (tag.getName() != null) {
                map.put(tag.getName(), tag.getDescription());
            }
        }
        return map;
    }

    private Set<String> loadRegisteredKeys() {
        Set<String> keys = new HashSet<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT http_method, path FROM ai_tool_api WHERE deleted=0 AND tool_type='OPENAPI'");
            for (Map<String, Object> row : rows) {
                Object m = row.get("http_method");
                Object p = row.get("path");
                if (m != null && p != null) {
                    keys.add(String.valueOf(m).toUpperCase(Locale.ROOT) + " " + p);
                }
            }
        } catch (Exception e) {
            log.warn("[ai] load registered tools failed: {}", e.getMessage());
        }
        return keys;
    }

    public void register(String method, String path, String summary, String operationId, String module) {
        if (method == null || path == null || path.isBlank()) {
            throw new IllegalArgumentException("method/path required");
        }
        String m = method.toUpperCase(Locale.ROOT);
        if (!AiHttpMethods.isAllowedForOpenApiTool(m)) {
            throw new IllegalArgumentException("only GET/POST can be registered as tools");
        }
        String toolCode = buildToolCode(m, path);
        String moduleKey = (module == null || module.isBlank()) ? moduleFromPath(path) : module.trim();
        String permJson = permissionSyncService.toJson(permissionSyncService.resolveAndSerialize(m, path));
        ToolSchemas schemas = resolveSchemas(m, path);
        String paramJson = schemaFactory.toJson(schemas.parametersSchema());
        String responseJson = schemaFactory.toJson(schemas.responseSchema());
        List<Map<String, Object>> exists = jdbcTemplate.queryForList(
                "SELECT id FROM ai_tool_api WHERE tool_code=? AND deleted=0 LIMIT 1", toolCode);
        String name = summary == null || summary.isBlank() ? toolCode : summary;
        if (exists.isEmpty()) {
            insertTool(toolCode, name, m, path, operationId, moduleKey, permJson, paramJson, responseJson);
        } else {
            updateTool(toolCode, name, m, path, operationId, moduleKey, permJson, paramJson, responseJson);
        }
        catalogCache.invalidate();
    }

    /** 兼容旧调用（无 module） */
    public void register(String method, String path, String summary, String operationId) {
        register(method, path, summary, operationId, null);
    }

    /**
     * 按当前 OpenAPI 回填已注册 OPENAPI Tool 的入参/出参 Schema。
     *
     * @return 更新行数
     */
    public int resyncAllSchemas() {
        OpenAPI openAPI = openAPIProvider.getIfAvailable();
        if (openAPI == null || openAPI.getPaths() == null) {
            log.warn("[ai] resync schemas skipped: OpenAPI bean not found");
            return 0;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, http_method, path FROM ai_tool_api "
                        + "WHERE deleted=0 AND tool_type='OPENAPI' AND http_method IS NOT NULL AND path IS NOT NULL");
        int updated = 0;
        for (Map<String, Object> row : rows) {
            String method = String.valueOf(row.get("http_method")).toUpperCase(Locale.ROOT);
            String path = String.valueOf(row.get("path"));
            Long id = ((Number) row.get("id")).longValue();
            Operation op = findOperation(openAPI, method, path);
            ToolSchemas schemas = op == null
                    ? new ToolSchemas(Map.of("type", "object", "properties", Map.of()), Map.of())
                    : schemaFactory.extract(openAPI, op);
            String paramJson = schemaFactory.toJson(schemas.parametersSchema());
            String responseJson = schemaFactory.toJson(schemas.responseSchema());
            try {
                jdbcTemplate.update(
                        "UPDATE ai_tool_api SET param_schema_json=?, response_schema_json=? WHERE id=?",
                        paramJson, responseJson, id);
            } catch (Exception e) {
                // 未升级 response_schema_json 列时仅写入参
                jdbcTemplate.update(
                        "UPDATE ai_tool_api SET param_schema_json=? WHERE id=?",
                        paramJson, id);
            }
            updated++;
        }
        log.info("[ai] resync OpenAPI tool schemas: {} rows", updated);
        if (updated > 0) {
            catalogCache.invalidate();
        }
        return updated;
    }

    private ToolSchemas resolveSchemas(String method, String path) {
        OpenAPI openAPI = openAPIProvider.getIfAvailable();
        if (openAPI == null) {
            return new ToolSchemas(Map.of("type", "object", "properties", Map.of()), Map.of());
        }
        Operation op = findOperation(openAPI, method, path);
        if (op == null) {
            log.warn("[ai] OpenAPI operation not found for {} {}", method, path);
            return new ToolSchemas(Map.of("type", "object", "properties", Map.of()), Map.of());
        }
        return schemaFactory.extract(openAPI, op);
    }

    private static Operation findOperation(OpenAPI openAPI, String method, String path) {
        if (openAPI.getPaths() == null) {
            return null;
        }
        PathItem item = openAPI.getPaths().get(path);
        if (item == null) {
            return null;
        }
        if (AiHttpMethods.GET.equalsIgnoreCase(method)) {
            return item.getGet();
        }
        if (AiHttpMethods.POST.equalsIgnoreCase(method)) {
            return item.getPost();
        }
        return null;
    }

    private void insertTool(String toolCode, String name, String method, String path,
                            String operationId, String moduleKey, String permJson,
                            String paramJson, String responseJson) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO ai_tool_api(tool_code, tool_type, name, description, enabled, http_method, path, "
                            + "operation_id, module, param_schema_json, response_schema_json, required_permissions, sort, deleted) "
                            + "VALUES (?,?,?,?,1,?,?,?,?,?,?,?,?,0)",
                    toolCode, AiToolTypes.OPENAPI, name, name, method, path, operationId, moduleKey,
                    paramJson, responseJson, permJson, 0);
        } catch (Exception e) {
            jdbcTemplate.update(
                    "INSERT INTO ai_tool_api(tool_code, tool_type, name, description, enabled, http_method, path, "
                            + "operation_id, module, param_schema_json, required_permissions, sort, deleted) "
                            + "VALUES (?,?,?,?,1,?,?,?,?,?,?,?,0)",
                    toolCode, AiToolTypes.OPENAPI, name, name, method, path, operationId, moduleKey,
                    paramJson, permJson, 0);
        }
    }

    private void updateTool(String toolCode, String name, String method, String path,
                            String operationId, String moduleKey, String permJson,
                            String paramJson, String responseJson) {
        try {
            jdbcTemplate.update(
                    "UPDATE ai_tool_api SET name=?, description=?, http_method=?, path=?, operation_id=?, module=?, "
                            + "param_schema_json=?, response_schema_json=?, required_permissions=?, enabled=1 WHERE tool_code=?",
                    name, name, method, path, operationId, moduleKey, paramJson, responseJson, permJson, toolCode);
        } catch (Exception e) {
            jdbcTemplate.update(
                    "UPDATE ai_tool_api SET name=?, description=?, http_method=?, path=?, operation_id=?, module=?, "
                            + "param_schema_json=?, required_permissions=?, enabled=1 WHERE tool_code=?",
                    name, name, method, path, operationId, moduleKey, paramJson, permJson, toolCode);
        }
    }

    static String buildToolCode(String method, String path) {
        String p = path.replaceAll("[^a-zA-Z0-9]+", "_");
        if (p.startsWith("_")) {
            p = p.substring(1);
        }
        if (p.endsWith("_")) {
            p = p.substring(0, p.length() - 1);
        }
        return (method + "_" + p).toLowerCase(Locale.ROOT);
    }

    @Data
    public static class CatalogModule {
        private String module;
        private String moduleLabel;
        private List<CatalogItem> items;
    }

    @Data
    public static class CatalogItem {
        private String method;
        private String path;
        private String summary;
        private String operationId;
        /** 功能模块 key（OpenAPI tag 或路径首段） */
        private String module;
        private String moduleLabel;
        /** 建议优先勾选：GET，或路径像 page/list/query 的 POST */
        private boolean suggested;
        private boolean registered;
        /** SPI 预览的权限码（未落库） */
        private List<String> requiredPermissions;
    }
}
