package cn.ysmp.ai.constants;

/**
 * AI 模块权限码（与菜单 permission / 前端 constants 对齐）。
 * <p>Agent 使用权限为 {@code ai:agent:{agentCode}}，由配置动态生成。
 */
public final class AiPermissionConstants {

    private AiPermissionConstants() {
    }

    /** 打开对话 / 调用 chat */
    public static final String ASSISTANT_QUERY = "ai:assistant:query";

    public static final String MODEL_CONFIG_QUERY = "ai:model-config:query";
    public static final String MODEL_CONFIG_UPDATE = "ai:model-config:update";

    public static final String TOOL_API_QUERY = "ai:tool-api:query";
    public static final String TOOL_API_UPDATE = "ai:tool-api:update";

    public static final String SKILL_QUERY = "ai:skill:query";
    public static final String SKILL_UPDATE = "ai:skill:update";

    public static final String AGENT_QUERY = "ai:agent:query";
    public static final String AGENT_UPDATE = "ai:agent:update";

    /** 默认 Agent 访问码（可与 ASSISTANT_QUERY 相同或独立） */
    public static final String AGENT_DEFAULT = "ai:agent:default";

    public static String agentAccess(String agentCode) {
        return "ai:agent:" + agentCode;
    }
}
