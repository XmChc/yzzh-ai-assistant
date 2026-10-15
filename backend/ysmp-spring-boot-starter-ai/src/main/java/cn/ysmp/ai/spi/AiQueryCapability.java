package cn.ysmp.ai.spi;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 受控查询能力（与表解耦；权限绑业务权限码）。
 */
@Data
@Builder
public class AiQueryCapability {

    private String code;
    private String name;
    private String description;
    private List<String> requiredPermissions;
    private Map<String, Object> parametersSchema;
}
