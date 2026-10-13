package cn.ysmp.ai.chat;

import cn.ysmp.ai.constants.AiLimits;
import cn.ysmp.ai.llm.LlmCompletionResult;
import cn.ysmp.ai.llm.OpenAiCompatibleClient;
import cn.ysmp.ai.spi.AiChatMemory;
import cn.ysmp.ai.spi.AiLlmRuntimeConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 将溢出的多轮对话压缩为滚动摘要（无 tools，单次补全）。
 */
@Slf4j
@RequiredArgsConstructor
public class AiConversationCompressor {

    private final OpenAiCompatibleClient llmClient;

    /**
     * @param existingSummary 已有摘要，可空
     * @param overflow        待并入摘要的旧消息（升序）
     * @return 新摘要；失败时尽力拼本地短摘要
     */
    public String compress(AiLlmRuntimeConfig cfg,
                           String existingSummary,
                           List<AiChatMemory.StoredMessage> overflow,
                           int maxSummaryChars) {
        if (overflow == null || overflow.isEmpty()) {
            return existingSummary;
        }
        int limit = maxSummaryChars > 0 ? maxSummaryChars : AiLimits.MAX_SUMMARY_CHARS;
        String dialogue = formatDialogue(overflow);
        String prompt = buildPrompt(existingSummary, dialogue, limit);

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content",
                "你是对话摘要器。只输出摘要正文，不要前言或标题。"));
        messages.add(Map.of("role", "user", "content", prompt));

        try {
            LlmCompletionResult result = llmClient.complete(cfg, messages, List.of());
            String text = result.getContent();
            if (text != null && !text.isBlank()) {
                return truncate(text.trim(), limit);
            }
        } catch (Exception e) {
            log.warn("[ai] conversation compress failed, fallback extract: {}", e.getMessage());
        }
        return truncate(fallbackMerge(existingSummary, dialogue), limit);
    }

    private static String buildPrompt(String existingSummary, String dialogue, int maxChars) {
        StringBuilder sb = new StringBuilder();
        sb.append("将「新增对话」并入滚动摘要。保留：用户目标、关键事实/数据/编号、已做决定、未决问题。")
                .append("删除寒暄与重复。使用简洁中文条目或短段落。总长不超过 ")
                .append(maxChars)
                .append(" 字。不要编造。\n");
        if (existingSummary != null && !existingSummary.isBlank()) {
            sb.append("\n## 已有摘要\n").append(existingSummary.trim()).append('\n');
        }
        sb.append("\n## 新增对话\n").append(dialogue);
        return sb.toString();
    }

    private static String formatDialogue(List<AiChatMemory.StoredMessage> overflow) {
        StringBuilder sb = new StringBuilder();
        for (AiChatMemory.StoredMessage m : overflow) {
            String role = "assistant".equalsIgnoreCase(m.role()) ? "助手" : "用户";
            String content = m.content() == null ? "" : m.content().trim();
            if (content.length() > 1200) {
                content = content.substring(0, 1200) + "…";
            }
            sb.append(role).append("：").append(content).append('\n');
        }
        return sb.toString();
    }

    private static String fallbackMerge(String existing, String dialogue) {
        StringBuilder sb = new StringBuilder();
        if (existing != null && !existing.isBlank()) {
            sb.append(existing.trim()).append('\n');
        }
        sb.append(dialogue);
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
