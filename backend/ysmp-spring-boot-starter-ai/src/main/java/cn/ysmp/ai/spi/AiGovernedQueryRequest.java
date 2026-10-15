package cn.ysmp.ai.spi;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class AiGovernedQueryRequest {

    private String capabilityCode;
    private Map<String, Object> params;
    private AiDataScope scope;
    private Long userId;
    private String sessionId;
}
