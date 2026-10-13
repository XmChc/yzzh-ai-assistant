package cn.ysmp.ai.constants;

/**
 * Agent 运行模式。
 */
public final class AiAgentModes {

    private AiAgentModes() {
    }

    /** 场景型：仅 Agent→Skill 白名单；未激活 Skill 可短缺权提示 */
    public static final String SCENARIO = "SCENARIO";

    /** 通用型：用户全部可用 Tool + 已激活 Skill 路由；不报缺权 */
    public static final String GENERAL = "GENERAL";

    public static boolean isScenario(String mode) {
        return SCENARIO.equalsIgnoreCase(mode);
    }

    public static boolean isGeneral(String mode) {
        return mode == null || mode.isBlank() || GENERAL.equalsIgnoreCase(mode);
    }

    public static String normalize(String mode) {
        if (isScenario(mode)) {
            return SCENARIO;
        }
        return GENERAL;
    }
}
