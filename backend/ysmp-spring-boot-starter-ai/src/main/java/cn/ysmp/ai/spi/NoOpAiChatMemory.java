package cn.ysmp.ai.spi;

import java.util.List;
import java.util.Map;

/**
 * 无表环境占位：不持久化，无多轮历史。
 */
public class NoOpAiChatMemory implements AiChatMemory {

    public static final AiChatMemory INSTANCE = new NoOpAiChatMemory();

    @Override
    public void ensureSession(String sessionId, Long userId, String agentCode, String titleHint) {
        // no-op
    }

    @Override
    public List<StoredMessage> loadRecentMessages(String sessionId, int maxMessages) {
        return List.of();
    }

    @Override
    public SummaryState loadSummaryState(String sessionId) {
        return SummaryState.empty();
    }

    @Override
    public List<StoredMessage> loadMessagesAfter(String sessionId, long afterId, int maxMessages) {
        return List.of();
    }

    @Override
    public void saveSummary(String sessionId, String summary, long untilMessageId) {
        // no-op
    }

    @Override
    public void appendMessage(String sessionId, String role, String content, String toolSummary) {
        // no-op
    }

    @Override
    public List<Map<String, Object>> listSessions(Long userId, int limit) {
        return List.of();
    }

    @Override
    public List<Map<String, Object>> listMessages(String sessionId, Long userId) {
        return List.of();
    }

    @Override
    public void deleteSession(String sessionId, Long userId) {
        // no-op
    }
}
