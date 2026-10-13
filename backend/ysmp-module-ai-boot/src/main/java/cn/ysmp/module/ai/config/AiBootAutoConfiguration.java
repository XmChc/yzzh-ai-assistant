package cn.ysmp.module.ai.config;

import cn.ysmp.ai.config.AiAutoConfiguration;
import cn.ysmp.ai.config.AiProperties;
import cn.ysmp.ai.spi.AiChatMemory;
import cn.ysmp.ai.spi.AiEndpointPermissionResolver;
import cn.ysmp.ai.spi.AiLlmConfigProvider;
import cn.ysmp.ai.spi.AiSecurityAdapter;
import cn.ysmp.ai.spi.NoOpAiEndpointPermissionResolver;
import cn.ysmp.ai.tool.AiToolCatalog;
import cn.ysmp.module.ai.service.DbAiChatMemory;
import cn.ysmp.module.ai.controller.admin.AiAgentController;
import cn.ysmp.module.ai.controller.admin.AiAssistantController;
import cn.ysmp.module.ai.controller.admin.AiModelConfigController;
import cn.ysmp.module.ai.controller.admin.AiSkillController;
import cn.ysmp.module.ai.controller.admin.AiToolApiController;
import cn.ysmp.module.ai.service.AiToolPermissionBackfillRunner;
import cn.ysmp.module.ai.service.AiToolPermissionSyncService;
import cn.ysmp.module.ai.service.DbAiLlmConfigProvider;
import cn.ysmp.module.ai.service.DbAiToolCatalog;
import cn.ysmp.module.ai.service.DenyAllAiSecurityAdapter;
import cn.ysmp.module.ai.service.OpenApiCatalogService;
import cn.ysmp.module.ai.service.PermitAllAiSecurityAdapter;
import cn.ysmp.module.ai.service.agent.AiAgentAdminService;
import cn.ysmp.module.ai.service.catalog.AiCatalogCache;
import cn.ysmp.module.ai.service.openapi.OpenApiToolSchemaFactory;
import cn.ysmp.module.ai.service.skill.AiSkillAdminService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 开箱装配：在引擎 {@link AiAutoConfiguration} 之后注册表访问与管理端 API。
 */
@AutoConfiguration
@AutoConfigureAfter({AiAutoConfiguration.class, AiPreAuthorizePermissionAutoConfiguration.class})
@ConditionalOnProperty(prefix = "ysmp.ai", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import({
        AiAssistantController.class,
        AiModelConfigController.class,
        AiToolApiController.class,
        AiSkillController.class,
        AiAgentController.class
})
public class AiBootAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AiSecurityAdapter.class)
    public AiSecurityAdapter aiSecurityAdapter(AiProperties properties) {
        if (properties.getSecurity() != null && properties.getSecurity().isPermitAllWhenMissing()) {
            return new PermitAllAiSecurityAdapter();
        }
        return new DenyAllAiSecurityAdapter();
    }

    @Bean
    @ConditionalOnMissingBean
    public AiCatalogCache aiCatalogCache(JdbcTemplate jdbcTemplate,
                                         ObjectMapper objectMapper,
                                         AiProperties properties) {
        return new AiCatalogCache(jdbcTemplate, objectMapper, properties);
    }

    @Bean
    @ConditionalOnMissingBean(AiChatMemory.class)
    public AiChatMemory aiChatMemory(JdbcTemplate jdbcTemplate) {
        return new DbAiChatMemory(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(AiLlmConfigProvider.class)
    public DbAiLlmConfigProvider aiLlmConfigProvider(JdbcTemplate jdbcTemplate, AiProperties properties) {
        return new DbAiLlmConfigProvider(jdbcTemplate, properties);
    }

    @Bean
    @ConditionalOnMissingBean(AiToolCatalog.class)
    public AiToolCatalog aiToolCatalog(AiCatalogCache catalogCache,
                                       AiSecurityAdapter securityAdapter) {
        return new DbAiToolCatalog(catalogCache, securityAdapter);
    }

    @Bean
    @ConditionalOnMissingBean(AiEndpointPermissionResolver.class)
    public AiEndpointPermissionResolver noOpAiEndpointPermissionResolver() {
        return new NoOpAiEndpointPermissionResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    public AiToolPermissionSyncService aiToolPermissionSyncService(
            JdbcTemplate jdbcTemplate,
            AiEndpointPermissionResolver permissionResolver,
            ObjectMapper objectMapper,
            AiCatalogCache catalogCache) {
        return new AiToolPermissionSyncService(jdbcTemplate, permissionResolver, objectMapper, catalogCache);
    }

    @Bean
    @ConditionalOnMissingBean
    public OpenApiToolSchemaFactory openApiToolSchemaFactory(ObjectMapper objectMapper) {
        return new OpenApiToolSchemaFactory(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public OpenApiCatalogService openApiCatalogService(ObjectProvider<OpenAPI> openAPIProvider,
                                                       JdbcTemplate jdbcTemplate,
                                                       AiToolPermissionSyncService permissionSyncService,
                                                       AiCatalogCache catalogCache,
                                                       OpenApiToolSchemaFactory schemaFactory) {
        return new OpenApiCatalogService(
                openAPIProvider, jdbcTemplate, permissionSyncService, catalogCache, schemaFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    public AiToolPermissionBackfillRunner aiToolPermissionBackfillRunner(
            AiToolPermissionSyncService syncService) {
        return new AiToolPermissionBackfillRunner(syncService);
    }

    @Bean
    @ConditionalOnMissingBean
    public AiSkillAdminService aiSkillAdminService(JdbcTemplate jdbcTemplate,
                                                   AiCatalogCache catalogCache) {
        return new AiSkillAdminService(jdbcTemplate, catalogCache);
    }

    @Bean
    @ConditionalOnMissingBean
    public AiAgentAdminService aiAgentAdminService(JdbcTemplate jdbcTemplate,
                                                   AiSecurityAdapter securityAdapter,
                                                   AiToolCatalog toolCatalog,
                                                   AiCatalogCache catalogCache) {
        return new AiAgentAdminService(jdbcTemplate, securityAdapter, toolCatalog, catalogCache);
    }
}
