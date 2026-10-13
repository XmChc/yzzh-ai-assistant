package cn.ysmp.ai.constants;

/**
 * 管理端 API 路径前缀（Controller {@code @RequestMapping} 与 OpenAPI 过滤共用）。
 */
public final class AiApiPaths {

    private AiApiPaths() {
    }

    /** AI 模块根路径 */
    public static final String ADMIN_AI_ROOT = "/admin-api/ai";

    public static final String ASSISTANT = ADMIN_AI_ROOT + "/assistant";
    /** 相对 ASSISTANT 的子路径 */
    public static final String ASSISTANT_CHAT = "/chat";
    public static final String ASSISTANT_CHAT_STREAM = "/chat/stream";
    public static final String ASSISTANT_CAPABILITIES = "/capabilities";
    public static final String ASSISTANT_SESSIONS = "/sessions";
    public static final String ASSISTANT_SESSION_MESSAGES = "/session/messages";
    public static final String ASSISTANT_SESSION_DELETE = "/session/delete";
    public static final String ASSISTANT_TOOL_RESULT_PAGE = "/tool-result/page";
    public static final String ASSISTANT_TOOL_RESULT_FIELD = "/tool-result/field";

    public static final String MODEL_CONFIG = ADMIN_AI_ROOT + "/model-config";
    public static final String TOOL_API = ADMIN_AI_ROOT + "/tool-api";
    public static final String SKILL = ADMIN_AI_ROOT + "/skill";
    public static final String AGENT = ADMIN_AI_ROOT + "/agent";

    public static boolean isAiModulePath(String path) {
        return path != null && path.startsWith(ADMIN_AI_ROOT);
    }
}
