package cn.ysmp.ai.constants;

/**
 * 对话输入框 {@code @} 引用关键词（与前端 mention 对齐，忽略大小写前缀匹配）。
 */
public final class AiMentionKeywords {

    private AiMentionKeywords() {
    }

    public static final String AGENT = "agent";
    public static final String SKILL = "skill";
    public static final String TOOL = "tool";

    /**
     * @return token 是否为 keyword 的前缀（双方忽略大小写）
     */
    public static boolean likes(String keyword, String token) {
        if (keyword == null || token == null || token.isEmpty()) {
            return false;
        }
        return keyword.toLowerCase().startsWith(token.toLowerCase());
    }
}
