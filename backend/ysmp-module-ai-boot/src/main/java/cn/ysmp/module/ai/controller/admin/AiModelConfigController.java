package cn.ysmp.module.ai.controller.admin;

import cn.ysmp.ai.constants.AiApiPaths;
import cn.ysmp.module.ai.framework.web.AiCommonResult;
import cn.ysmp.module.ai.service.DbAiLlmConfigProvider;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 模型配置（专用页，不走 system_config）。
 */
@RestController
@RequestMapping(AiApiPaths.MODEL_CONFIG)
@RequiredArgsConstructor
public class AiModelConfigController {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectProvider<DbAiLlmConfigProvider> llmConfigProvider;

    @GetMapping("/get")
    public AiCommonResult<ModelConfigVO> get() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, enabled, base_url, api_key, model, timeout_ms, remark FROM ai_model_config WHERE deleted = 0 ORDER BY id ASC LIMIT 1");
        if (rows.isEmpty()) {
            ModelConfigVO empty = new ModelConfigVO();
            empty.setEnabled(false);
            empty.setTimeoutMs(60000);
            return AiCommonResult.ok(empty);
        }
        Map<String, Object> row = rows.get(0);
        ModelConfigVO vo = new ModelConfigVO();
        vo.setId(((Number) row.get("id")).longValue());
        vo.setEnabled(asBool(row.get("enabled")));
        vo.setBaseUrl(asStr(row.get("base_url")));
        vo.setApiKey(mask(asStr(row.get("api_key"))));
        vo.setModel(asStr(row.get("model")));
        vo.setTimeoutMs(row.get("timeout_ms") == null ? 60000 : ((Number) row.get("timeout_ms")).intValue());
        vo.setRemark(asStr(row.get("remark")));
        return AiCommonResult.ok(vo);
    }

    @PutMapping("/update")
    public AiCommonResult<Boolean> update(@RequestBody ModelConfigUpdateReq req) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, api_key FROM ai_model_config WHERE deleted = 0 ORDER BY id ASC LIMIT 1");
        String apiKey = req.getApiKey();
        if (apiKey != null && apiKey.contains("****") && !rows.isEmpty()) {
            apiKey = asStr(rows.get(0).get("api_key"));
        }
        if (rows.isEmpty()) {
            jdbcTemplate.update(
                    "INSERT INTO ai_model_config(enabled, base_url, api_key, model, timeout_ms, remark, deleted) VALUES (?,?,?,?,?,?,0)",
                    req.isEnabled(), req.getBaseUrl(), apiKey, req.getModel(),
                    req.getTimeoutMs() == null ? 60000 : req.getTimeoutMs(), req.getRemark());
        } else {
            Long id = ((Number) rows.get(0).get("id")).longValue();
            jdbcTemplate.update(
                    "UPDATE ai_model_config SET enabled=?, base_url=?, api_key=?, model=?, timeout_ms=?, remark=? WHERE id=?",
                    req.isEnabled(), req.getBaseUrl(), apiKey, req.getModel(),
                    req.getTimeoutMs() == null ? 60000 : req.getTimeoutMs(), req.getRemark(), id);
        }
        DbAiLlmConfigProvider cached = llmConfigProvider.getIfAvailable();
        if (cached != null) {
            cached.invalidate();
        }
        return AiCommonResult.ok(true);
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

    @Data
    public static class ModelConfigVO {
        private Long id;
        private boolean enabled;
        private String baseUrl;
        private String apiKey;
        private String model;
        private Integer timeoutMs;
        private String remark;
    }

    @Data
    public static class ModelConfigUpdateReq {
        private boolean enabled;
        private String baseUrl;
        private String apiKey;
        private String model;
        private Integer timeoutMs;
        private String remark;
    }
}
