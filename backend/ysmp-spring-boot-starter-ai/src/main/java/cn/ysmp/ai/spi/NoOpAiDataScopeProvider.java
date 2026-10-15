package cn.ysmp.ai.spi;

/**
 * 默认：拒绝一切受控查询数据范围（宿主未提供实现时不可走 GOVERNED 路径）。
 */
public final class NoOpAiDataScopeProvider implements AiDataScopeProvider {

    public static final NoOpAiDataScopeProvider INSTANCE = new NoOpAiDataScopeProvider();

    private NoOpAiDataScopeProvider() {
    }

    @Override
    public AiDataScope resolve(String capabilityCode) {
        return AiDataScope.deny("未配置 AiDataScopeProvider，受控查询不可用");
    }
}
