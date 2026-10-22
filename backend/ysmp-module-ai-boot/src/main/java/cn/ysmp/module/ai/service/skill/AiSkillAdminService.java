package cn.ysmp.module.ai.service.skill;

import cn.ysmp.module.ai.controller.admin.AiSkillController.SkillSaveReq;
import cn.ysmp.module.ai.service.catalog.AiCatalogCache;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RequiredArgsConstructor
public class AiSkillAdminService {

    private final JdbcTemplate jdbcTemplate;
    private final AiCatalogCache catalogCache;

    public List<Map<String, Object>> list() {
        return jdbcTemplate.queryForList(
                "SELECT id, skill_code, name, enabled, remark, create_time FROM ai_skill WHERE deleted = 0 ORDER BY id DESC");
    }

    public Map<String, Object> get(Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, skill_code, name, enabled, system_prompt, remark FROM ai_skill WHERE id=? AND deleted=0", id);
        if (rows.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> vo = new HashMap<>(rows.get(0));
        vo.put("toolCodes", jdbcTemplate.queryForList(
                "SELECT tool_code FROM ai_skill_tool WHERE skill_id=? ORDER BY sort ASC, id ASC", id)
                .stream().map(r -> String.valueOf(r.get("tool_code"))).toList());
        return vo;
    }

    @Transactional
    public Long create(SkillSaveReq req) {
        String skillCode = requireCodeName(req);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO ai_skill(skill_code, name, enabled, system_prompt, remark, deleted) VALUES (?,?,?,?,?,0)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, skillCode);
            ps.setString(2, req.getName().trim());
            ps.setBoolean(3, req.getEnabled() == null || req.getEnabled());
            ps.setString(4, req.getSystemPrompt());
            ps.setString(5, req.getRemark());
            return ps;
        }, keyHolder);
        Long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        replaceTools(id, req.getToolCodes());
        catalogCache.invalidate();
        return id;
    }

    @Transactional
    public void update(SkillSaveReq req) {
        if (req.getId() == null) {
            throw new IllegalArgumentException("技能 id 不能为空");
        }
        String skillCode = requireCodeName(req);
        jdbcTemplate.update(
                "UPDATE ai_skill SET skill_code=?, name=?, enabled=?, system_prompt=?, remark=? WHERE id=? AND deleted=0",
                skillCode, req.getName().trim(),
                req.getEnabled() == null || req.getEnabled(),
                req.getSystemPrompt(), req.getRemark(), req.getId());
        replaceTools(req.getId(), req.getToolCodes());
        catalogCache.invalidate();
    }

    /** 编码、名称必填；禁止空串脏数据落库 */
    private static String requireCodeName(SkillSaveReq req) {
        if (req.getSkillCode() == null || req.getSkillCode().isBlank()) {
            throw new IllegalArgumentException("技能编码不能为空");
        }
        if (req.getName() == null || req.getName().isBlank()) {
            throw new IllegalArgumentException("技能名称不能为空");
        }
        return req.getSkillCode().trim();
    }

    private void replaceTools(Long skillId, List<String> toolCodes) {
        jdbcTemplate.update("DELETE FROM ai_skill_tool WHERE skill_id=?", skillId);
        if (toolCodes == null) {
            return;
        }
        int sort = 0;
        for (String code : toolCodes) {
            if (code == null || code.isBlank()) {
                continue;
            }
            jdbcTemplate.update("INSERT INTO ai_skill_tool(skill_id, tool_code, sort) VALUES (?,?,?)",
                    skillId, code, sort++);
        }
    }
}
