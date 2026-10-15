package cn.ysmp.ai.spi;

/**
 * 宿主数据范围：按能力码复用 RBAC/数据权限服务，算出强制条件。
 * <p>默认 DenyAll；宿主实现应调用与 Web 拦截器同源的 DataPermission 逻辑。
 */
public interface AiDataScopeProvider {

    /**
     * @param capabilityCode 查询能力码（非表名），如 order.amount.sum
     */
    AiDataScope resolve(String capabilityCode);
}
