package cn.ysmp.ai.spi;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiGovernedQueryResult {

    private boolean success;
    private String error;
    /** 给 LLM 的 JSON/文本 */
    private String content;
    private boolean complete;
    private Integer coveredRows;
    private Integer reportedTotal;

    public static AiGovernedQueryResult fail(String error) {
        return AiGovernedQueryResult.builder()
                .success(false)
                .error(error == null ? "governed query failed" : error)
                .content("{\"error\":\"" + escape(error) + "\"}")
                .complete(false)
                .build();
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
