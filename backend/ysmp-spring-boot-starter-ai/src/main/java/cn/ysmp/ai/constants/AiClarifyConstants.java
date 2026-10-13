package cn.ysmp.ai.constants;

/**
 * 澄清补充（ai-clarify）协议常量。
 * <p>模型在正文中输出 fenced JSON；编排剥离后经 SSE {@link AiSseEventTypes#CLARIFY} 下发。
 */
public final class AiClarifyConstants {

    private AiClarifyConstants() {
    }

    /** Markdown fence 语言标记 */
    public static final String FENCE_LANG = "ai-clarify";

    /** 用户提交补充说明时的消息前缀 */
    public static final String REPLY_PREFIX = "【补充说明】";

    public static final String FIELD_TEXT = "text";
    public static final String FIELD_TEXTAREA = "textarea";
    public static final String FIELD_NUMBER = "number";
    public static final String FIELD_SELECT = "select";
    public static final String FIELD_SINGLE = "single";
    public static final String FIELD_MULTI = "multi";

    public static final int MAX_FIELDS = 8;
}
