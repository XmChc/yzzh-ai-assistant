package cn.ysmp.ai.config;

import cn.ysmp.ai.chat.AiChatOrchestrator;
import cn.ysmp.ai.chat.AiConversationCompressor;
import cn.ysmp.ai.llm.OpenAiCompatibleClient;
import cn.ysmp.ai.tool.openapi.OpenApiToolExecutor;
import cn.ysmp.ai.spi.AiChatMemory;
import cn.ysmp.ai.spi.AiDataScopeProvider;
import cn.ysmp.ai.spi.AiGovernedQueryExecutor;
import cn.ysmp.ai.spi.AiLlmConfigProvider;
import cn.ysmp.ai.spi.AiQueryCapabilityCatalog;
import cn.ysmp.ai.spi.AiSecurityAdapter;
import cn.ysmp.ai.spi.NoOpAiDataScopeProvider;
import cn.ysmp.ai.spi.NoOpAiGovernedQueryExecutor;
import cn.ysmp.ai.spi.NoOpAiQueryCapabilityCatalog;
import cn.ysmp.ai.tool.AiTool;
import cn.ysmp.ai.tool.AiToolCatalog;
import cn.ysmp.ai.tool.result.AiToolResultCompactor;
import cn.ysmp.ai.tool.result.AiToolResultStore;
import cn.ysmp.ai.tool.result.InMemoryAiToolResultStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@AutoConfiguration
@EnableConfigurationProperties(AiProperties.class)
@ConditionalOnProperty(prefix = "ysmp.ai", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public WebClient.Builder aiWebClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    @ConditionalOnMissingBean
    public OpenAiCompatibleClient openAiCompatibleClient(ObjectMapper objectMapper,
                                                         WebClient.Builder aiWebClientBuilder) {
        return new OpenAiCompatibleClient(objectMapper, aiWebClientBuilder);
    }

    @Bean
    @ConditionalOnMissingBean
    public OpenApiToolExecutor openApiToolExecutor(AiSecurityAdapter securityAdapter,
                                                   WebClient.Builder aiWebClientBuilder,
                                                   ObjectMapper objectMapper,
                                                   AiProperties properties) {
        int storeLimit = Math.max(properties.getMaxToolStoreChars(), properties.getMaxToolResponseChars());
        return new OpenApiToolExecutor(securityAdapter, aiWebClientBuilder, objectMapper,
                properties.getInternalBaseUrl(), storeLimit);
    }

    @Bean
    @ConditionalOnMissingBean(AiToolResultStore.class)
    public AiToolResultStore aiToolResultStore(AiProperties properties, ObjectMapper objectMapper) {
        return new InMemoryAiToolResultStore(properties.getToolResultTtlMs(), objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public AiToolResultCompactor aiToolResultCompactor(ObjectMapper objectMapper,
                                                       AiToolResultStore toolResultStore,
                                                       AiProperties properties) {
        return new AiToolResultCompactor(objectMapper, toolResultStore, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public AiConversationCompressor aiConversationCompressor(OpenAiCompatibleClient openAiCompatibleClient) {
        return new AiConversationCompressor(openAiCompatibleClient);
    }

    @Bean
    @ConditionalOnMissingBean(AiQueryCapabilityCatalog.class)
    public AiQueryCapabilityCatalog aiQueryCapabilityCatalog() {
        return NoOpAiQueryCapabilityCatalog.INSTANCE;
    }

    @Bean
    @ConditionalOnMissingBean(AiDataScopeProvider.class)
    public AiDataScopeProvider aiDataScopeProvider() {
        return NoOpAiDataScopeProvider.INSTANCE;
    }

    @Bean
    @ConditionalOnMissingBean(AiGovernedQueryExecutor.class)
    public AiGovernedQueryExecutor aiGovernedQueryExecutor() {
        return NoOpAiGovernedQueryExecutor.INSTANCE;
    }

    @Bean
    @ConditionalOnMissingBean
    public AiChatOrchestrator aiChatOrchestrator(AiLlmConfigProvider llmConfigProvider,
                                                 AiToolCatalog toolCatalog,
                                                 AiSecurityAdapter securityAdapter,
                                                 OpenAiCompatibleClient openAiCompatibleClient,
                                                 OpenApiToolExecutor openApiToolExecutor,
                                                 ObjectProvider<List<AiTool>> builtinTools,
                                                 ObjectMapper objectMapper,
                                                 ObjectProvider<AiChatMemory> chatMemoryProvider,
                                                 AiProperties properties,
                                                 AiConversationCompressor conversationCompressor,
                                                 AiToolResultCompactor toolResultCompactor,
                                                 AiQueryCapabilityCatalog queryCapabilityCatalog,
                                                 AiDataScopeProvider dataScopeProvider,
                                                 AiGovernedQueryExecutor governedQueryExecutor) {
        List<AiTool> tools = builtinTools.getIfAvailable();
        if (tools == null) {
            tools = List.of();
        }
        return new AiChatOrchestrator(llmConfigProvider, toolCatalog, securityAdapter,
                openAiCompatibleClient, openApiToolExecutor, tools, objectMapper,
                chatMemoryProvider, properties, conversationCompressor, toolResultCompactor,
                queryCapabilityCatalog, dataScopeProvider, governedQueryExecutor);
    }
}
