package cn.ysmp.module.ai.config;

import cn.ysmp.ai.spi.AiEndpointPermissionResolver;
import cn.ysmp.module.ai.service.PreAuthorizeEndpointPermissionResolver;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 仅在存在 Spring Security {@link PreAuthorize} 时装配默认权限解析器。
 */
@AutoConfiguration
@ConditionalOnClass(PreAuthorize.class)
@ConditionalOnProperty(prefix = "ysmp.ai", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AiPreAuthorizePermissionAutoConfiguration {

    /**
     * 宿主常有多个 {@link RequestMappingHandlerMapping}
     * （MVC / Spring Boot Admin / Actuator），必须限定主 MVC Bean。
     */
    @Bean
    @ConditionalOnMissingBean(AiEndpointPermissionResolver.class)
    public AiEndpointPermissionResolver preAuthorizeEndpointPermissionResolver(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        return new PreAuthorizeEndpointPermissionResolver(handlerMapping);
    }
}
