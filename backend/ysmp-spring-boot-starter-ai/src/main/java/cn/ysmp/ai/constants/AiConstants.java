package cn.ysmp.ai.constants;

/**
 * 本模块<strong>全局常量入口</strong>。业务代码禁止散落魔法字符串，统一从本包引用。
 *
 * <ul>
 *   <li>{@link AiPermissionConstants} — 门禁权限码</li>
 *   <li>{@link AiAgentModes} — Agent 模式</li>
 *   <li>{@link AiToolTypes} — Tool 类型</li>
 *   <li>{@link AiApiPaths} — 管理端路径</li>
 *   <li>{@link AiHttpMethods} — 允许的 HTTP 方法</li>
 *   <li>{@link AiLimits} — 数值上限（含 tool 多轮 / SSE 分块）</li>
 *   <li>{@link AiToolQueryParamNames} — 分页/列表查询参数名约定</li>
 *   <li>{@link AiQueryIntents} — 查询意图（含大屏参数类）</li>
 *   <li>{@link AiDashboardToolMarkers} — 大屏 Tool 识别关键词</li>
 *   <li>{@link AiSseEventTypes} — SSE 事件名</li>
 *   <li>{@link AiClarifyConstants} — 澄清补充协议</li>
 *   <li>{@link AiChartConstants} / {@link AiChartFormatSpecs} — 内置图表协议与格式说明</li>
 *   <li>{@link AiMentionKeywords} — @ 引用关键词</li>
 * </ul>
 *
 * <p>运行时鉴权辅助见 {@link cn.ysmp.ai.guard.AiAccessGuard}；
 * 分页查询约束见 {@link cn.ysmp.ai.guard.AiToolQueryGuard}。
 *
 * <pre>{@code
 * import static cn.ysmp.ai.constants.AiPermissionConstants.ASSISTANT_QUERY;
 * import cn.ysmp.ai.constants.AiAgentModes;
 * import cn.ysmp.ai.guard.AiAccessGuard;
 * }</pre>
 */
public final class AiConstants {

    private AiConstants() {
    }

    /** @see AiPermissionConstants */
    public static final class Permission {
        private Permission() {
        }

        public static final String ASSISTANT_QUERY = AiPermissionConstants.ASSISTANT_QUERY;
        public static final String MODEL_CONFIG_QUERY = AiPermissionConstants.MODEL_CONFIG_QUERY;
        public static final String MODEL_CONFIG_UPDATE = AiPermissionConstants.MODEL_CONFIG_UPDATE;
        public static final String TOOL_API_QUERY = AiPermissionConstants.TOOL_API_QUERY;
        public static final String TOOL_API_UPDATE = AiPermissionConstants.TOOL_API_UPDATE;
        public static final String SKILL_QUERY = AiPermissionConstants.SKILL_QUERY;
        public static final String SKILL_UPDATE = AiPermissionConstants.SKILL_UPDATE;
        public static final String AGENT_QUERY = AiPermissionConstants.AGENT_QUERY;
        public static final String AGENT_UPDATE = AiPermissionConstants.AGENT_UPDATE;
        public static final String AGENT_DEFAULT = AiPermissionConstants.AGENT_DEFAULT;

        public static String agentAccess(String agentCode) {
            return AiPermissionConstants.agentAccess(agentCode);
        }
    }

    /** @see AiAgentModes */
    public static final class Mode {
        private Mode() {
        }

        public static final String SCENARIO = AiAgentModes.SCENARIO;
        public static final String GENERAL = AiAgentModes.GENERAL;
    }

    /** @see AiToolTypes */
    public static final class ToolType {
        private ToolType() {
        }

        public static final String BUILTIN = AiToolTypes.BUILTIN;
        public static final String OPENAPI = AiToolTypes.OPENAPI;
    }
}
