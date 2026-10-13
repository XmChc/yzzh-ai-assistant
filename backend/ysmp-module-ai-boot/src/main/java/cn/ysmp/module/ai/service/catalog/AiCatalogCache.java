package cn.ysmp.module.ai.service.catalog;

import cn.ysmp.ai.config.AiProperties;
import cn.ysmp.ai.constants.AiAgentModes;
import cn.ysmp.ai.constants.AiHttpMethods;
import cn.ysmp.ai.constants.AiToolTypes;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent/Skill/Tool 配置快照缓存：对话热路径只读内存，不打库。
 * <p>管理端写操作应调用 {@link #invalidate()}；TTL 兜底防漏刷。
 */
@Slf4j
@RequiredArgsConstructor
public class AiCatalogCache {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AiProperties properties;

    private volatile Snapshot snapshot;
    private volatile long loadedAtMs;

    public Snapshot get() {
        long ttl = properties.getCatalogCacheTtlMs();
        Snapshot s = snapshot;
        long now = System.currentTimeMillis();
        if (s != null && (ttl <= 0 || now - loadedAtMs < ttl)) {
            return s;
        }
        synchronized (this) {
            s = snapshot;
            now = System.currentTimeMillis();
            if (s != null && (ttl <= 0 || now - loadedAtMs < ttl)) {
                return s;
            }
            Snapshot loaded = load();
            snapshot = loaded;
            loadedAtMs = System.currentTimeMillis();
            return loaded;
        }
    }

    public void invalidate() {
        snapshot = null;
        loadedAtMs = 0L;
        log.debug("[ai] catalog cache invalidated");
    }

    private Snapshot load() {
        long t0 = System.currentTimeMillis();
        String defaultAgentCode = null;
        Map<String, AgentSnap> agentsByCode = new LinkedHashMap<>();
        List<Map<String, Object>> agentRows;
        try {
            agentRows = jdbcTemplate.queryForList(
                    "SELECT id, agent_code, access_permission, agent_mode, system_prompt, default_flag "
                            + "FROM ai_agent WHERE deleted = 0 AND enabled = 1 ORDER BY default_flag DESC, id ASC");
        } catch (Exception e) {
            agentRows = jdbcTemplate.queryForList(
                    "SELECT id, agent_code, access_permission, system_prompt, default_flag "
                            + "FROM ai_agent WHERE deleted = 0 AND enabled = 1 ORDER BY default_flag DESC, id ASC");
        }
        for (Map<String, Object> row : agentRows) {
            String code = asStr(row.get("agent_code"));
            AgentSnap agent = AgentSnap.builder()
                    .id(((Number) row.get("id")).longValue())
                    .agentCode(code)
                    .accessPermission(asStr(row.get("access_permission")))
                    .agentMode(AiAgentModes.normalize(asStr(row.get("agent_mode"))))
                    .systemPrompt(asStr(row.get("system_prompt")))
                    .defaultFlag(asBool(row.get("default_flag")))
                    .skillIds(new ArrayList<>())
                    .build();
            agentsByCode.put(code, agent);
            if (agent.isDefaultFlag() && defaultAgentCode == null) {
                defaultAgentCode = code;
            }
        }
        if (defaultAgentCode == null && !agentsByCode.isEmpty()) {
            defaultAgentCode = agentsByCode.values().iterator().next().getAgentCode();
        }

        Map<Long, List<Long>> agentSkillIds = new LinkedHashMap<>();
        List<Map<String, Object>> agsRows = jdbcTemplate.queryForList(
                "SELECT agent_id, skill_id FROM ai_agent_skill ORDER BY sort ASC, id ASC");
        for (Map<String, Object> row : agsRows) {
            Long agentId = ((Number) row.get("agent_id")).longValue();
            Long skillId = ((Number) row.get("skill_id")).longValue();
            agentSkillIds.computeIfAbsent(agentId, k -> new ArrayList<>()).add(skillId);
        }
        for (AgentSnap agent : agentsByCode.values()) {
            List<Long> ids = agentSkillIds.getOrDefault(agent.getId(), List.of());
            agent.getSkillIds().addAll(ids);
        }

        Map<Long, SkillSnap> skillsById = new LinkedHashMap<>();
        List<Long> allEnabledSkillIds = new ArrayList<>();
        List<Map<String, Object>> skillRows = jdbcTemplate.queryForList(
                "SELECT id, skill_code, name, system_prompt FROM ai_skill "
                        + "WHERE deleted = 0 AND enabled = 1 ORDER BY id ASC");
        for (Map<String, Object> row : skillRows) {
            Long id = ((Number) row.get("id")).longValue();
            SkillSnap skill = SkillSnap.builder()
                    .id(id)
                    .skillCode(asStr(row.get("skill_code")))
                    .name(asStr(row.get("name")))
                    .systemPrompt(asStr(row.get("system_prompt")))
                    .toolCodes(new ArrayList<>())
                    .build();
            skillsById.put(id, skill);
            allEnabledSkillIds.add(id);
        }

        // 一次查出全部 skill_tool，消除 N+1
        List<Map<String, Object>> stRows = jdbcTemplate.queryForList(
                "SELECT skill_id, tool_code FROM ai_skill_tool ORDER BY skill_id ASC, sort ASC, id ASC");
        for (Map<String, Object> row : stRows) {
            Long skillId = ((Number) row.get("skill_id")).longValue();
            SkillSnap skill = skillsById.get(skillId);
            if (skill != null) {
                skill.getToolCodes().add(String.valueOf(row.get("tool_code")));
            }
        }

        Map<String, ToolSnap> toolsByCode = new LinkedHashMap<>();
        List<Map<String, Object>> toolRows = loadToolRows();
        for (Map<String, Object> row : toolRows) {
            String type = asStr(row.get("tool_type"));
            String method = asStr(row.get("http_method"));
            if (AiToolTypes.OPENAPI.equalsIgnoreCase(type)
                    && method != null
                    && !AiHttpMethods.isAllowedForOpenApiTool(method)) {
                continue;
            }
            String code = String.valueOf(row.get("tool_code"));
            String module = asStr(row.get("module"));
            String desc = asStr(row.get("description"));
            if (module != null && !module.isBlank()) {
                desc = (desc == null || desc.isBlank())
                        ? ("[" + module + "]")
                        : ("[" + module + "] " + desc);
            }
            toolsByCode.put(code, ToolSnap.builder()
                    .toolCode(code)
                    .toolType(type)
                    .name(asStr(row.get("name")))
                    .description(desc)
                    .httpMethod(method)
                    .path(asStr(row.get("path")))
                    .module(module)
                    .requiredPermissions(parsePerms(asStr(row.get("required_permissions"))))
                    .parametersSchema(parseSchema(asStr(row.get("param_schema_json"))))
                    .responseSchema(parseSchemaOrEmpty(asStr(row.get("response_schema_json"))))
                    .build());
        }

        log.debug("[ai] catalog snapshot loaded in {}ms: agents={}, skills={}, tools={}",
                System.currentTimeMillis() - t0, agentsByCode.size(), skillsById.size(), toolsByCode.size());
        return Snapshot.builder()
                .defaultAgentCode(defaultAgentCode)
                .agentsByCode(Collections.unmodifiableMap(agentsByCode))
                .skillsById(Collections.unmodifiableMap(skillsById))
                .allEnabledSkillIds(Collections.unmodifiableList(allEnabledSkillIds))
                .toolsByCode(Collections.unmodifiableMap(toolsByCode))
                .build();
    }

    private List<Map<String, Object>> loadToolRows() {
        try {
            return jdbcTemplate.queryForList(
                    "SELECT tool_code, tool_type, name, description, http_method, path, module, "
                            + "param_schema_json, response_schema_json, required_permissions "
                            + "FROM ai_tool_api WHERE deleted = 0 AND enabled = 1 ORDER BY module ASC, sort ASC, id ASC");
        } catch (Exception e1) {
            try {
                return jdbcTemplate.queryForList(
                        "SELECT tool_code, tool_type, name, description, http_method, path, module, "
                                + "param_schema_json, required_permissions "
                                + "FROM ai_tool_api WHERE deleted = 0 AND enabled = 1 ORDER BY module ASC, sort ASC, id ASC");
            } catch (Exception e2) {
                return jdbcTemplate.queryForList(
                        "SELECT tool_code, tool_type, name, description, http_method, path, "
                                + "param_schema_json, required_permissions "
                                + "FROM ai_tool_api WHERE deleted = 0 AND enabled = 1 ORDER BY sort ASC, id ASC");
            }
        }
    }

    private List<String> parsePerms(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> list = objectMapper.readValue(json, new TypeReference<>() {});
            return list == null ? List.of() : List.copyOf(list);
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, Object> parseSchema(String json) {
        if (json == null || json.isBlank()) {
            return Map.of("type", "object", "properties", Map.of());
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
            return map == null ? Map.of("type", "object", "properties", Map.of()) : map;
        } catch (Exception e) {
            return Map.of("type", "object", "properties", Map.of());
        }
    }

    /** 出参缺省为空 Map（不强制 object），避免误导压缩逻辑 */
    private Map<String, Object> parseSchemaOrEmpty(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
            return map == null ? Map.of() : map;
        } catch (Exception e) {
            return Map.of();
        }
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

    @Getter
    @Builder
    public static final class Snapshot {
        private final String defaultAgentCode;
        private final Map<String, AgentSnap> agentsByCode;
        private final Map<Long, SkillSnap> skillsById;
        private final List<Long> allEnabledSkillIds;
        private final Map<String, ToolSnap> toolsByCode;
    }

    @Getter
    @Builder
    public static final class AgentSnap {
        private final long id;
        private final String agentCode;
        private final String accessPermission;
        private final String agentMode;
        private final String systemPrompt;
        private final boolean defaultFlag;
        private final List<Long> skillIds;
    }

    @Getter
    @Builder
    public static final class SkillSnap {
        private final long id;
        private final String skillCode;
        private final String name;
        private final String systemPrompt;
        private final List<String> toolCodes;
    }

    @Getter
    @Builder
    public static final class ToolSnap {
        private final String toolCode;
        private final String toolType;
        private final String name;
        private final String description;
        private final String httpMethod;
        private final String path;
        private final String module;
        private final List<String> requiredPermissions;
        private final Map<String, Object> parametersSchema;
        private final Map<String, Object> responseSchema;
    }
}
