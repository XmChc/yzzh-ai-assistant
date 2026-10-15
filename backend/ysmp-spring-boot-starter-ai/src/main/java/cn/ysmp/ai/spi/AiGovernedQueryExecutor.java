package cn.ysmp.ai.spi;

/**
 * 受控查询唯一执行出口：在宿主进程内校验权限码、合并 {@link AiDataScope}、跑官方查询。
 * <p>starter 不连业务库；默认 NoOp（不支持任何能力）。
 */
public interface AiGovernedQueryExecutor {

    boolean supports(String capabilityCode);

    AiGovernedQueryResult execute(AiGovernedQueryRequest request);
}
