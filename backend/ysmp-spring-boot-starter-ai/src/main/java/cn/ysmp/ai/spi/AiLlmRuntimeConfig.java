package cn.ysmp.ai.spi;

import lombok.Builder;
import lombok.Data;

/**
 * LLM 运行时快照（来自管理端模型配置表，非 yaml）。
 */
@Data
@Builder
public class AiLlmRuntimeConfig {

    private boolean enabled;
    private String baseUrl;
    private String apiKey;
    private String model;
    /** 超时毫秒 */
    private int timeoutMs;

    public boolean isReady() {
        return enabled
                && baseUrl != null && !baseUrl.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && model != null && !model.isBlank();
    }
}
