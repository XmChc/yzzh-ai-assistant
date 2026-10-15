package cn.ysmp.ai.constants;

/**
 * 写操作确认协议（与 SSE {@link AiSseEventTypes#TOOL_CONFIRM}、请求字段 confirmWriteTools 对齐）。
 */
public final class AiWriteConfirmConstants {

    private AiWriteConfirmConstants() {
    }

    /** Tool 结果 JSON 中的 status：待用户确认、尚未执行 */
    public static final String STATUS_NEED_CONFIRM = "NEED_USER_CONFIRM";

    /** JSON 字段名 */
    public static final String FIELD_STATUS = "status";
    public static final String FIELD_TOOL = "tool";
    public static final String FIELD_MESSAGE = "message";
}
