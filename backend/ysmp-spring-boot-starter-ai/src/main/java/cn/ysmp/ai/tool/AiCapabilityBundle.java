package cn.ysmp.ai.tool;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 某 Agent 下当前用户的能力快照（对话 / 可选 Agent / @ 引用共用）。
 */
@Data
@Builder
public class AiCapabilityBundle {

    private String agentCode;
    private String agentMode;
    /** Agent 级 system_prompt */
    private String agentSystemPrompt;
    private boolean agentAccessible;
    private String error;

    /** 可注册到 OpenAI tools 的定义（均为 available） */
    private List<AiToolDefinition> callableTools;

    /** 已激活 Skill */
    private List<AiSkillHint> activatedSkills;

    /** SCENARIO：未激活 Skill 短缺权；GENERAL 为空 */
    private List<AiSkillHint> inactiveSkills;
}
