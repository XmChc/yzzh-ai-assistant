package cn.ysmp.ai.spi;

/**
 * 模型运行时配置读取（由 boot 模块从 ai_model_config 表实现）。
 */
public interface AiLlmConfigProvider {

    /**
     * @return 当前可用配置；未启用或缺必填项时返回 null 或 enabled=false
     */
    AiLlmRuntimeConfig getRuntimeConfig();
}
