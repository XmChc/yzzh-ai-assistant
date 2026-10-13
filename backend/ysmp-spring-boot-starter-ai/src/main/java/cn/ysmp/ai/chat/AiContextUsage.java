package cn.ysmp.ai.chat;

import lombok.Builder;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 本轮请求的上下文用量拆分（近似 token，便于 UI 展示；非计费账单）。
 */
@Data
@Builder
public class AiContextUsage {

    /** 估计总 token（各分段之和） */
    private int estimatedTotal;
    /** 上下文窗口上限（配置） */
    private int contextWindow;
    /** 占用百分比 0–100 */
    private int percentFull;

    /** system（Agent 基础指令，不含 Skill 段） */
    private int systemPrompt;
    /** 已激活 Skill 指令 */
    private int skillPrompts;
    /** tools JSON Schema */
    private int toolDefinitions;
    /** 滚动摘要（Summarized conversation） */
    private int summarizedConversation;
    /** 最近原文多轮（不含本轮 user） */
    private int conversation;
    /** 本轮用户消息 */
    private int currentMessage;

    /** 模型返回的 prompt_tokens（多轮 tool_call 累加，可能为空） */
    private Integer promptTokens;
    /** 模型返回的 completion_tokens */
    private Integer completionTokens;
    /** 模型返回的 total_tokens */
    private Integer totalTokens;

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("estimatedTotal", estimatedTotal);
        m.put("contextWindow", contextWindow);
        m.put("percentFull", percentFull);
        m.put("systemPrompt", systemPrompt);
        m.put("skillPrompts", skillPrompts);
        m.put("toolDefinitions", toolDefinitions);
        m.put("summarizedConversation", summarizedConversation);
        m.put("conversation", conversation);
        m.put("currentMessage", currentMessage);
        if (promptTokens != null) {
            m.put("promptTokens", promptTokens);
        }
        if (completionTokens != null) {
            m.put("completionTokens", completionTokens);
        }
        if (totalTokens != null) {
            m.put("totalTokens", totalTokens);
        }
        return m;
    }
}
