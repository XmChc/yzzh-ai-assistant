package cn.ysmp.ai.llm;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class LlmCompletionResult {

    private String content;
    private List<LlmToolCall> toolCalls;
    private String finishReason;
    /** 原始 assistant message（可回写到 messages） */
    private Object assistantMessage;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
