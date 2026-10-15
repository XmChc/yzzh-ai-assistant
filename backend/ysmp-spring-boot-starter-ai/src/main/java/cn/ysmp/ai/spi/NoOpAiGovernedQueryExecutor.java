package cn.ysmp.ai.spi;

public final class NoOpAiGovernedQueryExecutor implements AiGovernedQueryExecutor {

    public static final NoOpAiGovernedQueryExecutor INSTANCE = new NoOpAiGovernedQueryExecutor();

    private NoOpAiGovernedQueryExecutor() {
    }

    @Override
    public boolean supports(String capabilityCode) {
        return false;
    }

    @Override
    public AiGovernedQueryResult execute(AiGovernedQueryRequest request) {
        return AiGovernedQueryResult.fail("未配置 AiGovernedQueryExecutor");
    }
}
