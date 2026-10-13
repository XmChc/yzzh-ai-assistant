package cn.ysmp.ai.spi;

import java.util.List;
import java.util.Map;

/**
 * 会话消息持久化：按 session 存历史，供多轮上下文与历史查询。
 * <p>对话热路径只读写本会话最近消息；列表查询走索引。
 */
public interface AiChatMemory {

    /**
     * 确保会话存在；已存在则刷新 agent / 时间。
     */
    void ensureSession(String sessionId, Long userId, String agentCode, String titleHint);

    /**
     * 加载最近若干条 user/assistant 消息（按时间升序），用于拼上下文。
     */
    List<StoredMessage> loadRecentMessages(String sessionId, int maxMessages);

    /**
     * 读取会话摘要状态（无则空摘要 + untilId=0）。
     */
    SummaryState loadSummaryState(String sessionId);

    /**
     * 加载 {@code id > afterId} 的 user/assistant 消息（升序，最多 maxMessages）。
     */
    List<StoredMessage> loadMessagesAfter(String sessionId, long afterId, int maxMessages);

    /**
     * 持久化滚动摘要：覆盖 {@code untilMessageId} 及之前已被压缩的内容。
     */
    void saveSummary(String sessionId, String summary, long untilMessageId);

    /**
     * 追加一条可见消息（user / assistant）。
     */
    void appendMessage(String sessionId, String role, String content, String toolSummary);

    /**
     * 当前用户的会话列表（新→旧）。
     */
    List<Map<String, Object>> listSessions(Long userId, int limit);

    /**
     * 某会话全部可见消息（升序）；校验归属。
     */
    List<Map<String, Object>> listMessages(String sessionId, Long userId);

    /**
     * 软删会话。
     */
    void deleteSession(String sessionId, Long userId);

    record StoredMessage(long id, String role, String content) {
        public StoredMessage(String role, String content) {
            this(0L, role, content);
        }
    }

    record SummaryState(String summary, long untilMessageId) {
        public static SummaryState empty() {
            return new SummaryState(null, 0L);
        }

        public boolean hasSummary() {
            return summary != null && !summary.isBlank();
        }
    }
}
