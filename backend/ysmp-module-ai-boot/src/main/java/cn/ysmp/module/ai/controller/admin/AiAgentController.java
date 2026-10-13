package cn.ysmp.module.ai.controller.admin;

import cn.ysmp.ai.constants.AiApiPaths;
import cn.ysmp.module.ai.framework.web.AiCommonResult;
import cn.ysmp.module.ai.service.agent.AiAgentAdminService;
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
 * Agent 配置：多 Skill + access_permission。
 */
@RestController
@RequestMapping(AiApiPaths.AGENT)
@RequiredArgsConstructor
public class AiAgentController {

    private final AiAgentAdminService agentAdminService;

    @GetMapping("/list")
    public AiCommonResult<List<Map<String, Object>>> list() {
        return AiCommonResult.ok(agentAdminService.list());
    }

    @GetMapping("/get")
    public AiCommonResult<Map<String, Object>> get(@RequestParam Long id) {
        return AiCommonResult.ok(agentAdminService.get(id));
    }

    /**
     * 当前用户可用的 Agent 列表（对话入口下拉）。
     */
    @GetMapping("/available")
    public AiCommonResult<List<Map<String, Object>>> available() {
        return AiCommonResult.ok(agentAdminService.listAvailableForCurrentUser());
    }

    @PostMapping("/create")
    public AiCommonResult<Long> create(@RequestBody AgentSaveReq req) {
        return AiCommonResult.ok(agentAdminService.create(req));
    }

    @PutMapping("/update")
    public AiCommonResult<Boolean> update(@RequestBody AgentSaveReq req) {
        agentAdminService.update(req);
        return AiCommonResult.ok(true);
    }

    @Data
    public static class AgentSaveReq {
        private Long id;
        private String agentCode;
        private String name;
        private Boolean enabled;
        private String systemPrompt;
        private String accessPermission;
        /** SCENARIO | GENERAL */
        private String agentMode;
        private Boolean defaultFlag;
        private String remark;
        private List<Long> skillIds;
    }
}
