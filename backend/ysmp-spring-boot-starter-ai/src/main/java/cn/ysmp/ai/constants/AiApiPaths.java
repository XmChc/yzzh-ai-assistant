package cn.ysmp.ai.constants;

/**
 * 管理端 API 路径。
 * <p>
 * Controller {@code @RequestMapping} 只写模块路径 {@code /ai/**}；
 * 宿主框架（如 ysmp）会按包名自动加 {@code /admin-api} 前缀。
 * OpenAPI 文档里可能出现带前缀的完整路径，过滤时两者都认。
 */
public final class AiApiPaths {

    private AiApiPaths() {
    }

    /** Controller 使用的模块根路径（不含 /admin-api） */
    public static final String MODULE_ROOT = "/ai";

    /** HTTP 完整前缀（文档/对外说明用） */
    public static final String ADMIN_AI_ROOT = "/admin-api/ai";

    public static final String ASSISTANT = MODULE_ROOT + "/assistant";
    public static final String ASSISTANT_CHAT = "/chat";
    public static final String ASSISTANT_CHAT_STREAM = "/chat/stream";
    public static final String ASSISTANT_CAPABILITIES = "/capabilities";
    public static final String ASSISTANT_SESSIONS = "/sessions";
    public static final String ASSISTANT_SESSION_MESSAGES = "/session/messages";
    public static final String ASSISTANT_SESSION_DELETE = "/session/delete";
    public static final String ASSISTANT_TOOL_RESULT_PAGE = "/tool-result/page";
    public static final String ASSISTANT_TOOL_RESULT_FIELD = "/tool-result/field";

    public static final String MODEL_CONFIG = MODULE_ROOT + "/model-config";
    public static final String TOOL_API = MODULE_ROOT + "/tool-api";
    public static final String SKILL = MODULE_ROOT + "/skill";
    public static final String AGENT = MODULE_ROOT + "/agent";

    public static boolean isAiModulePath(String path) {
        if (path == null) {
            return false;
        }
        return path.startsWith(ADMIN_AI_ROOT) || path.startsWith(MODULE_ROOT + "/");
    }
}
