package cn.ysmp.ai.llm;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LlmToolCall {

    private String id;
    private String name;
    /** 原始 JSON 参数字符串 */
    private String argumentsJson;
}
