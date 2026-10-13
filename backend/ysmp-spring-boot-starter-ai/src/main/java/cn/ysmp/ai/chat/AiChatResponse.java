package cn.ysmp.ai.chat;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiChatResponse {

    private String sessionId;
    private String content;
    private String error;
    /** 本轮上下文用量（估算 + 可选真实 usage） */
    private AiContextUsage usage;
    /** 需要用户补充的表单（可空） */
    private AiClarifyForm clarify;
}
