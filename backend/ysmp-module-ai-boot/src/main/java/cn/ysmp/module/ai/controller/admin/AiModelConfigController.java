package cn.ysmp.module.ai.controller.admin;

import cn.ysmp.ai.constants.AiApiPaths;
import cn.ysmp.module.ai.framework.web.AiCommonResult;
import cn.ysmp.module.ai.service.DbAiLlmConfigProvider;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 模型配置：支持多条连接配置，其中一条为默认（对话运行时读取默认且已启用的配置）。
 */
@RestController
@RequestMapping(AiApiPaths.MODEL_CONFIG)
@RequiredArgsConstructor
public class AiModelConfigController {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectProvider<DbAiLlmConfigProvider> llmConfigProvider;

    @GetMapping("/list")
    public AiCommonResult<List<ModelConfigVO>> list() {
        List<Map<String, Object>> rows = queryListRows();
        List<ModelConfigVO> list = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            list.add(toVo(row, true));
        }
        return AiCommonResult.ok(list);
    }

    /**
     * 兼容：无 id 时返回默认模型；有 id 时返回指定配置。
     */
    @GetMapping("/get")
    public AiCommonResult<ModelConfigVO> get(@RequestParam(value = "id", required = false) Long id) {
        Map<String, Object> row;
        if (id != null) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, name, enabled, default_flag, base_url, api_key, model, timeout_ms, remark "
                            + "FROM ai_model_config WHERE id=? AND deleted=0", id);
            row = rows.isEmpty() ? null : rows.get(0);
        } else {
            row = loadDefaultRow();
        }
        if (row == null) {
            ModelConfigVO empty = new ModelConfigVO();
            empty.setEnabled(false);
            empty.setDefaultFlag(false);
            empty.setTimeoutMs(60000);
            return AiCommonResult.ok(empty);
        }
        return AiCommonResult.ok(toVo(row, true));
    }

    @PostMapping("/create")
    public AiCommonResult<Long> create(@RequestBody ModelConfigSaveReq req) {
        boolean asDefault = Boolean.TRUE.equals(req.getDefaultFlag()) || countActive() == 0;
        boolean enabled = req.getEnabled() == null || req.getEnabled();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO ai_model_config(name, enabled, default_flag, base_url, api_key, model, timeout_ms, remark, deleted) "
                            + "VALUES (?,?,?,?,?,?,?,?,0)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, blankToNull(req.getName()));
            ps.setBoolean(2, enabled);
            ps.setBoolean(3, asDefault);
            ps.setString(4, req.getBaseUrl());
            ps.setString(5, req.getApiKey());
            ps.setString(6, req.getModel());
            ps.setInt(7, req.getTimeoutMs() == null ? 60000 : req.getTimeoutMs());
            ps.setString(8, req.getRemark());
            return ps;
        }, keyHolder);
        Long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        if (asDefault) {
            clearOtherDefaults(id);
        }
        invalidateCache();
        return AiCommonResult.ok(id);
    }

    @PutMapping("/update")
    public AiCommonResult<Boolean> update(@RequestBody ModelConfigSaveReq req) {
        if (req.getId() == null) {
            // 兼容旧单行保存：无 id 时 upsert 默认行
            return upsertLegacy(req);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, api_key FROM ai_model_config WHERE id=? AND deleted=0", req.getId());
        if (rows.isEmpty()) {
            return AiCommonResult.fail("模型配置不存在");
        }
        String apiKey = resolveApiKey(req.getApiKey(), asStr(rows.get(0).get("api_key")));
        boolean enabled = req.getEnabled() == null || req.getEnabled();
        boolean asDefault = Boolean.TRUE.equals(req.getDefaultFlag());
        jdbcTemplate.update(
                "UPDATE ai_model_config SET name=?, enabled=?, default_flag=?, base_url=?, api_key=?, model=?, timeout_ms=?, remark=? "
                        + "WHERE id=? AND deleted=0",
                blankToNull(req.getName()), enabled, asDefault,
                req.getBaseUrl(), apiKey, req.getModel(),
                req.getTimeoutMs() == null ? 60000 : req.getTimeoutMs(),
                req.getRemark(), req.getId());
        if (asDefault) {
            clearOtherDefaults(req.getId());
        } else {
            ensureOneDefault();
        }
        invalidateCache();
        return AiCommonResult.ok(true);
    }

    /** 设为默认模型（并启用） */
    @PutMapping("/set-default")
    public AiCommonResult<Boolean> setDefault(@RequestParam("id") Long id) {
        int n = jdbcTemplate.update(
                "UPDATE ai_model_config SET default_flag=1, enabled=1 WHERE id=? AND deleted=0", id);
        if (n == 0) {
            return AiCommonResult.fail("模型配置不存在");
        }
        clearOtherDefaults(id);
        invalidateCache();
        return AiCommonResult.ok(true);
    }

    private AiCommonResult<Boolean> upsertLegacy(ModelConfigSaveReq req) {
        List<Map<String, Object>> rows = queryListRows();
        if (rows.isEmpty()) {
            create(req);
            return AiCommonResult.ok(true);
        }
        Long id = ((Number) rows.get(0).get("id")).longValue();
        req.setId(id);
        if (req.getDefaultFlag() == null) {
            req.setDefaultFlag(true);
        }
        return update(req);
    }

    private List<Map<String, Object>> queryListRows() {
        try {
            return jdbcTemplate.queryForList(
                    "SELECT id, name, enabled, default_flag, base_url, api_key, model, timeout_ms, remark "
                            + "FROM ai_model_config WHERE deleted = 0 ORDER BY default_flag DESC, id ASC");
        } catch (Exception e) {
            // 未升级 default_flag / name 列时的回退
            return jdbcTemplate.queryForList(
                    "SELECT id, enabled, base_url, api_key, model, timeout_ms, remark "
                            + "FROM ai_model_config WHERE deleted = 0 ORDER BY id ASC");
        }
    }

    private Map<String, Object> loadDefaultRow() {
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(
                    "SELECT id, name, enabled, default_flag, base_url, api_key, model, timeout_ms, remark "
                            + "FROM ai_model_config WHERE deleted=0 AND default_flag=1 ORDER BY id ASC LIMIT 1");
            if (!rows.isEmpty()) {
                return rows.get(0);
            }
            rows = jdbcTemplate.queryForList(
                    "SELECT id, name, enabled, default_flag, base_url, api_key, model, timeout_ms, remark "
                            + "FROM ai_model_config WHERE deleted=0 ORDER BY enabled DESC, id ASC LIMIT 1");
        } catch (Exception e) {
            rows = jdbcTemplate.queryForList(
                    "SELECT id, enabled, base_url, api_key, model, timeout_ms, remark "
                            + "FROM ai_model_config WHERE deleted=0 ORDER BY id ASC LIMIT 1");
        }
        return rows.isEmpty() ? null : rows.get(0);
    }

    private int countActive() {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM ai_model_config WHERE deleted=0", Integer.class);
        return n == null ? 0 : n;
    }

    private void clearOtherDefaults(Long keepId) {
        try {
            jdbcTemplate.update(
                    "UPDATE ai_model_config SET default_flag=0 WHERE deleted=0 AND id<>?", keepId);
        } catch (Exception ignored) {
            // 列不存在时忽略
        }
    }

    /** 取消默认后若已无默认，自动指定第一条为默认 */
    private void ensureOneDefault() {
        try {
            Integer n = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM ai_model_config WHERE deleted=0 AND default_flag=1", Integer.class);
            if (n != null && n > 0) {
                return;
            }
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id FROM ai_model_config WHERE deleted=0 ORDER BY enabled DESC, id ASC LIMIT 1");
            if (!rows.isEmpty()) {
                Long id = ((Number) rows.get(0).get("id")).longValue();
                jdbcTemplate.update("UPDATE ai_model_config SET default_flag=1 WHERE id=?", id);
            }
        } catch (Exception ignored) {
            // ignore
        }
    }

    private void invalidateCache() {
        DbAiLlmConfigProvider cached = llmConfigProvider.getIfAvailable();
        if (cached != null) {
            cached.invalidate();
        }
    }

    private ModelConfigVO toVo(Map<String, Object> row, boolean maskKey) {
        ModelConfigVO vo = new ModelConfigVO();
        vo.setId(((Number) row.get("id")).longValue());
        vo.setName(asStr(row.get("name")));
        vo.setEnabled(asBool(row.get("enabled")));
        vo.setDefaultFlag(asBool(row.get("default_flag")));
        vo.setBaseUrl(asStr(row.get("base_url")));
        String key = asStr(row.get("api_key"));
        vo.setApiKey(maskKey ? mask(key) : key);
        vo.setModel(asStr(row.get("model")));
        vo.setTimeoutMs(row.get("timeout_ms") == null ? 60000 : ((Number) row.get("timeout_ms")).intValue());
        vo.setRemark(asStr(row.get("remark")));
        return vo;
    }

    private static String resolveApiKey(String incoming, String existing) {
        if (incoming != null && incoming.contains("****")) {
            return existing;
        }
        return incoming;
    }

    private static String mask(String key) {
        if (key == null || key.length() < 8) {
            return key == null || key.isEmpty() ? "" : "****";
        }
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
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

    private static String blankToNull(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        return v.trim();
    }

    @Data
    public static class ModelConfigVO {
        private Long id;
        private String name;
        private boolean enabled;
        private boolean defaultFlag;
        private String baseUrl;
        private String apiKey;
        private String model;
        private Integer timeoutMs;
        private String remark;
    }

    @Data
    public static class ModelConfigSaveReq {
        private Long id;
        private String name;
        private Boolean enabled;
        private Boolean defaultFlag;
        private String baseUrl;
        private String apiKey;
        private String model;
        private Integer timeoutMs;
        private String remark;
    }
}
