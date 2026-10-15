package cn.ysmp.ai.constants;

/**
 * 对话 SSE event 名（与前端约定）。
 */
public final class AiSseEventTypes {

    private AiSseEventTypes() {
    }

    public static final String META = "meta";
    public static final String USAGE = "usage";
    public static final String CLARIFY = "clarify";
    public static final String TOOL_CALL = "tool_call";
    public static final String TOOL_RESULT = "tool_result";
    /** 大数据结果暂存引用（前端分页面板） */
    public static final String TOOL_DATA = "tool_data";
    /** 写操作待用户确认（未执行） */
    public static final String TOOL_CONFIRM = "tool_confirm";
    public static final String DELTA = "delta";
    public static final String DONE = "done";
    public static final String ERROR = "error";
}
