package cn.ysmp.module.ai.controller.admin;

import cn.ysmp.ai.constants.AiApiPaths;
import cn.ysmp.module.ai.framework.web.AiCommonResult;
import cn.ysmp.module.ai.service.skill.AiSkillAdminService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
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
 * 技能配置：多 Tool 白名单 + 提示词。
 */
@RestController
@RequestMapping(AiApiPaths.SKILL)
@RequiredArgsConstructor
public class AiSkillController {

    private final AiSkillAdminService skillAdminService;

    @GetMapping("/list")
    public AiCommonResult<List<Map<String, Object>>> list() {
        return AiCommonResult.ok(skillAdminService.list());
    }

    @GetMapping("/get")
    public AiCommonResult<Map<String, Object>> get(@RequestParam("id") Long id) {
        return AiCommonResult.ok(skillAdminService.get(id));
    }

    @PostMapping("/create")
    public AiCommonResult<Long> create(@RequestBody SkillSaveReq req) {
        try {
            return AiCommonResult.ok(skillAdminService.create(req));
        } catch (IllegalArgumentException e) {
            return AiCommonResult.fail(e.getMessage());
        }
    }

    @PutMapping("/update")
    public AiCommonResult<Boolean> update(@RequestBody SkillSaveReq req) {
        try {
            skillAdminService.update(req);
            return AiCommonResult.ok(true);
        } catch (IllegalArgumentException e) {
            return AiCommonResult.fail(e.getMessage());
        }
    }

    @Data
    public static class SkillSaveReq {
        private Long id;
        private String skillCode;
        private String name;
        private Boolean enabled;
        private String systemPrompt;
        private String remark;
        /** 绑定的 tool_code 列表 */
        private List<String> toolCodes;
    }
}
