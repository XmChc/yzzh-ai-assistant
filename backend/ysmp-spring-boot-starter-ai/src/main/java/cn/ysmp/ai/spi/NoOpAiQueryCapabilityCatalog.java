package cn.ysmp.ai.spi;

import java.util.List;

public final class NoOpAiQueryCapabilityCatalog implements AiQueryCapabilityCatalog {

    public static final NoOpAiQueryCapabilityCatalog INSTANCE = new NoOpAiQueryCapabilityCatalog();

    private NoOpAiQueryCapabilityCatalog() {
    }

    @Override
    public List<AiQueryCapability> listAvailable() {
        return List.of();
    }
}
