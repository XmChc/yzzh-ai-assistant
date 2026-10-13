package cn.ysmp.module.ai.service.agent;

import cn.ysmp.ai.constants.AiAgentModes;
import cn.ysmp.ai.constants.AiPermissionConstants;
import cn.ysmp.ai.guard.AiAccessGuard;
import cn.ysmp.ai.spi.AiSecurityAdapter;
import cn.ysmp.ai.tool.AiToolCatalog;
import cn.ysmp.module.ai.controller.admin.AiAgentController.AgentSaveReq;
import cn.ysmp.module.ai.service.catalog.AiCatalogCache;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RequiredArgsConstructor
public class AiAgentAdminService {

    private final JdbcTemplate jdbcTemplate;
    private final AiSecurityAdapter securityAdapter;
    private final AiToolCatalog toolCatalog;
    private final AiCatalogCache catalogCache;

    public List<Map<String, Object>> list() {
        try {
            return jdbcTemplate.queryForList(
                    "SELECT id, agent_code, name, enabled, access_permission, agent_mode, default_flag, remark, create_time "
                            + "FROM ai_agent WHERE deleted = 0 ORDER BY id DESC");
        } catch (Exception e) {
            return jdbcTemplate.queryForList(
                    "SELECT id, agent_code, name, enabled, access_permission, default_flag, remark, create_time "
                            + "FROM ai_agent WHERE deleted = 0 ORDER BY id DESC");
        }
    }

    public Map<String, Object> get(Long id) {
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(
                    "SELECT id, agent_code, name, enabled, system_prompt, access_permission, agent_mode, default_flag, remark "
                            + "FROM ai_agent WHERE id=? AND deleted=0", id);
        } catch (Exception e) {
            rows = jdbcTemplate.queryForList(
                    "SELECT id, agent_code, name, enabled, system_prompt, access_permission, default_flag, remark "
                            + "FROM ai_agent WHERE id=? AND deleted=0", id);
        }
        if (rows.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> vo = new HashMap<>(rows.get(0));
        if (!vo.containsKey("agent_mode")) {
            vo.put("agent_mode", AiAgentModes.GENERAL);
        }
        vo.put("skillIds", jdbcTemplate.queryForList(
                        "SELECT skill_id FROM ai_agent_skill WHERE agent_id=? ORDER BY sort ASC, id ASC", id)
                .stream().map(r -> ((Number) r.get("skill_id")).longValue()).toList());
        return vo;
    }

    /**
     * 可选 Agent：assistant 权限 + access_permission + 至少 1 个可用能力（短路）。
     */
    public List<Map<String, Object>> listAvailableForCurrentUser() {
        if (!AiAccessGuard.canUseAssistant(securityAdapter)) {
            return List.of();
        }
        List<Map<String, Object>> all;
        try {
            all = jdbcTemplate.queryForList(
                    "SELECT id, agent_code, name, access_permission, agent_mode, default_flag FROM ai_agent "
                            + "WHERE deleted = 0 AND enabled = 1 ORDER BY default_flag DESC, id ASC");
        } catch (Exception e) {
            all = jdbcTemplate.queryForList(
                    "SELECT id, agent_code, name, access_permission, default_flag FROM ai_agent "
                            + "WHERE deleted = 0 AND enabled = 1 ORDER BY default_flag DESC, id ASC");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : all) {
            String perm = row.get("access_permission") == null
                    ? AiPermissionConstants.ASSISTANT_QUERY
                    : String.valueOf(row.get("access_permission"));
            if (!AiAccessGuard.canAccessAgent(securityAdapter, perm)) {
                continue;
            }
            String code = String.valueOf(row.get("agent_code"));
            if (!toolCatalog.hasUsableCapability(code)) {
                continue;
            }
            Map<String, Object> vo = new HashMap<>(row);
            if (!vo.containsKey("agent_mode")) {
                vo.put("agent_mode", AiAgentModes.GENERAL);
            }
            result.add(vo);
        }
        return result;
    }

    @Transactional
    public Long create(AgentSaveReq req) {
        String access = req.getAccessPermission() == null || req.getAccessPermission().isBlank()
                ? AiPermissionConstants.agentAccess(req.getAgentCode())
                : req.getAccessPermission();
        String mode = AiAgentModes.normalize(req.getAgentMode());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO ai_agent(agent_code, name, enabled, system_prompt, access_permission, agent_mode, default_flag, remark, deleted) "
                                + "VALUES (?,?,?,?,?,?,?,?,0)",
                        Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, req.getAgentCode());
                ps.setString(2, req.getName());
                ps.setBoolean(3, req.getEnabled() == null || req.getEnabled());
                ps.setString(4, req.getSystemPrompt());
                ps.setString(5, access);
                ps.setString(6, mode);
                ps.setBoolean(7, Boolean.TRUE.equals(req.getDefaultFlag()));
                ps.setString(8, req.getRemark());
                return ps;
            }, keyHolder);
        } catch (Exception e) {
            jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO ai_agent(agent_code, name, enabled, system_prompt, access_permission, default_flag, remark, deleted) "
                                + "VALUES (?,?,?,?,?,?,?,0)",
                        Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, req.getAgentCode());
                ps.setString(2, req.getName());
                ps.setBoolean(3, req.getEnabled() == null || req.getEnabled());
                ps.setString(4, req.getSystemPrompt());
                ps.setString(5, access);
                ps.setBoolean(6, Boolean.TRUE.equals(req.getDefaultFlag()));
                ps.setString(7, req.getRemark());
                return ps;
            }, keyHolder);
        }
        Long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        if (Boolean.TRUE.equals(req.getDefaultFlag())) {
            clearOtherDefaults(id);
        }
        replaceSkills(id, req.getSkillIds());
        catalogCache.invalidate();
        return id;
    }

    @Transactional
    public void update(AgentSaveReq req) {
        String access = req.getAccessPermission() == null || req.getAccessPermission().isBlank()
                ? AiPermissionConstants.agentAccess(req.getAgentCode())
                : req.getAccessPermission();
        String mode = AiAgentModes.normalize(req.getAgentMode());
        try {
            jdbcTemplate.update(
                    "UPDATE ai_agent SET agent_code=?, name=?, enabled=?, system_prompt=?, access_permission=?, agent_mode=?, default_flag=?, remark=? "
                            + "WHERE id=? AND deleted=0",
                    req.getAgentCode(), req.getName(),
                    req.getEnabled() == null || req.getEnabled(),
                    req.getSystemPrompt(), access, mode,
                    Boolean.TRUE.equals(req.getDefaultFlag()),
                    req.getRemark(), req.getId());
        } catch (Exception e) {
            jdbcTemplate.update(
                    "UPDATE ai_agent SET agent_code=?, name=?, enabled=?, system_prompt=?, access_permission=?, default_flag=?, remark=? "
                            + "WHERE id=? AND deleted=0",
                    req.getAgentCode(), req.getName(),
                    req.getEnabled() == null || req.getEnabled(),
                    req.getSystemPrompt(), access,
                    Boolean.TRUE.equals(req.getDefaultFlag()),
                    req.getRemark(), req.getId());
        }
        if (Boolean.TRUE.equals(req.getDefaultFlag())) {
            clearOtherDefaults(req.getId());
        }
        replaceSkills(req.getId(), req.getSkillIds());
        catalogCache.invalidate();
    }

    private void clearOtherDefaults(Long keepId) {
        jdbcTemplate.update("UPDATE ai_agent SET default_flag=0 WHERE id<>? AND deleted=0", keepId);
    }

    private void replaceSkills(Long agentId, List<Long> skillIds) {
        jdbcTemplate.update("DELETE FROM ai_agent_skill WHERE agent_id=?", agentId);
        if (skillIds == null) {
            return;
        }
        int sort = 0;
        for (Long skillId : skillIds) {
            if (skillId == null) {
                continue;
            }
            jdbcTemplate.update("INSERT INTO ai_agent_skill(agent_id, skill_id, sort) VALUES (?,?,?)",
                    agentId, skillId, sort++);
        }
    }
}
