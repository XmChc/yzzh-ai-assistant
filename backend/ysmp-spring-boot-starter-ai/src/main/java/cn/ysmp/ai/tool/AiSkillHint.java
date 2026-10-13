package cn.ysmp.ai.tool;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Skill 激活/未激活摘要（供编排与前端引用）。
 */
@Data
@Builder
public class AiSkillHint {

    private String skillCode;
    private String name;
    /** Skill 级 system_prompt（仅激活时注入编排） */
    private String systemPrompt;
    private boolean activated;
    /** 未激活时：缺权限的简短说明行（Tool 名或权限码） */
    private List<String> missingHints;
    private List<String> toolCodes;
}
