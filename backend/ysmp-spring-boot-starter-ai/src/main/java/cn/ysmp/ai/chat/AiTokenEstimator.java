package cn.ysmp.ai.chat;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 粗估 token：中英混合按「约 2 字符 ≈ 1 token」；JSON 结构同理。
 * <p>仅用于 UI 与截断，不替代模型账单。
 */
public final class AiTokenEstimator {

    private AiTokenEstimator() {
    }

    public static int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, (text.length() + 1) / 2);
    }

    public static int estimateObject(ObjectMapper om, Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return estimate(om.writeValueAsString(value));
        } catch (Exception e) {
            return estimate(String.valueOf(value));
        }
    }
}
