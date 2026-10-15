package cn.ysmp.ai.spi;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 宿主数据范围快照（由 {@link AiDataScopeProvider} 算出，执行前强制合并）。
 */
@Data
@Builder
public class AiDataScope {

    private boolean allowed;
    private String reason;
    /** ALL | DEPT | DEPT_AND_CHILD | SELF | CUSTOM | DENY */
    private String scopeType;
    private Map<String, Object> forcedEquals;
    private Map<String, List<Object>> forcedIn;
    private List<String> deniedColumns;
    private int maxRows;
    private long timeoutMs;

    public static AiDataScope deny(String reason) {
        return AiDataScope.builder()
                .allowed(false)
                .reason(reason == null ? "数据权限不足" : reason)
                .scopeType("DENY")
                .maxRows(0)
                .timeoutMs(0)
                .build();
    }

    public static AiDataScope allowAll() {
        return AiDataScope.builder()
                .allowed(true)
                .scopeType("ALL")
                .maxRows(10_000)
                .timeoutMs(30_000)
                .build();
    }
}
