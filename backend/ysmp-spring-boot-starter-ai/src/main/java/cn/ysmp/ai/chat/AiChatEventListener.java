package cn.ysmp.ai.chat;

/**
 * 对话过程事件（同步 chat 可空实现；SSE 推送）。
 */
public interface AiChatEventListener {

    default void onMeta(String sessionId) {
    }

    /** 本轮上下文用量拆分（请求前估算；结束后可再带模型真实 usage） */
    default void onUsage(AiContextUsage usage) {
    }

    /** 需要用户填写/选择的补充说明表单 */
    default void onClarify(AiClarifyForm form) {
    }

    default void onToolCall(String callId, String toolName, String argumentsJson) {
    }

    default void onToolResult(String callId, String toolName, String resultPreview) {
    }

    /** 工具大结果已暂存，前端可按 refId 分页 */
    default void onToolData(java.util.Map<String, Object> dataRef) {
    }

    default void onDelta(String textChunk) {
    }

    default void onDone(String fullContent) {
    }

    default void onError(String message) {
    }
}
