package cn.ysmp.module.ai.service.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 解析宿主「带 paths 的完整 OpenAPI 文档」。
 * <p>
 * springdoc 注入的 {@link OpenAPI} Bean 通常只有 info/security，不含 paths；
 * 真正的接口列表由 {@code AbstractOpenApiResource#getOpenApi} 扫描 Controller 后生成。
 * 本类通过反射调用 springdoc（可选依赖），优先取分组 {@code all}，否则合并各 GroupedOpenApi。
 */
@Slf4j
@RequiredArgsConstructor
public class SpringdocOpenApiDocumentResolver {

    private static final String GROUP_ALL = "all";
    private static final String CLASS_MULTIPLE = "org.springdoc.webmvc.api.MultipleOpenApiWebMvcResource";
    private static final String CLASS_SINGLE = "org.springdoc.webmvc.api.OpenApiWebMvcResource";
    private static final String CLASS_GROUPED = "org.springdoc.core.models.GroupedOpenApi";
    private static final String CLASS_ABSTRACT_MULTIPLE = "org.springdoc.api.AbstractMultipleOpenApiResource";
    private static final String CLASS_ABSTRACT_RESOURCE = "org.springdoc.api.AbstractOpenApiResource";
    private static final String CLASS_OPENAPI_SERVICE = "org.springdoc.core.service.OpenAPIService";

    private final ApplicationContext applicationContext;
    private final ObjectProvider<OpenAPI> openAPIProvider;

    /**
     * @return 含 paths 的 OpenAPI；无法解析时返回 null
     */
    public OpenAPI resolve() {
        try {
            OpenAPI fromSpringdoc = resolveFromSpringdocResources();
            if (hasPaths(fromSpringdoc)) {
                return fromSpringdoc;
            }
        } catch (Throwable t) {
            log.warn("[ai] resolve OpenAPI via springdoc resource failed: {}", t.toString());
        }

        try {
            OpenAPI cached = resolveFromOpenApiServiceCache();
            if (hasPaths(cached)) {
                return cached;
            }
        } catch (Throwable t) {
            log.debug("[ai] OpenAPIService cache unavailable: {}", t.toString());
        }

        OpenAPI bean = openAPIProvider.getIfAvailable();
        if (hasPaths(bean)) {
            return bean;
        }
        if (bean != null) {
            log.warn("[ai] OpenAPI bean present but paths empty; ensure springdoc has scanned controllers "
                    + "(visit /v3/api-docs/all once or check GroupedOpenApi)");
        } else {
            log.warn("[ai] OpenAPI document not found; ensure springdoc is on classpath");
        }
        return null;
    }

    private OpenAPI resolveFromSpringdocResources() throws Exception {
        Object multiple = findBeanByClassName(CLASS_MULTIPLE);
        if (multiple != null) {
            List<String> groups = listGroupNames();
            if (groups.contains(GROUP_ALL)) {
                OpenAPI all = invokeGetOpenApi(getGroupedResource(multiple, GROUP_ALL));
                if (hasPaths(all)) {
                    return all;
                }
            }
            OpenAPI merged = null;
            for (String group : groups) {
                if (GROUP_ALL.equals(group)) {
                    continue;
                }
                OpenAPI part = invokeGetOpenApi(getGroupedResource(multiple, group));
                merged = mergeOpenApi(merged, part);
            }
            if (hasPaths(merged)) {
                return merged;
            }
            // 分组名未知时仍尝试 all
            OpenAPI all = invokeGetOpenApi(getGroupedResource(multiple, GROUP_ALL));
            if (hasPaths(all)) {
                return all;
            }
        }

        Object single = findBeanByClassName(CLASS_SINGLE);
        if (single != null) {
            return invokeGetOpenApi(single);
        }
        return null;
    }

    private OpenAPI resolveFromOpenApiServiceCache() throws Exception {
        Object service = findBeanByClassName(CLASS_OPENAPI_SERVICE);
        if (service == null) {
            return null;
        }
        Method getCached = service.getClass().getMethod("getCachedOpenAPI", Locale.class);
        Object cached = getCached.invoke(service, Locale.getDefault());
        return cached instanceof OpenAPI ? (OpenAPI) cached : null;
    }

    private List<String> listGroupNames() throws Exception {
        List<String> names = new ArrayList<>();
        Class<?> groupedClass = Class.forName(CLASS_GROUPED);
        Collection<?> beans = applicationContext.getBeansOfType(groupedClass).values();
        Method getGroup = groupedClass.getMethod("getGroup");
        for (Object bean : beans) {
            Object name = getGroup.invoke(bean);
            if (name != null && !String.valueOf(name).isBlank()) {
                names.add(String.valueOf(name));
            }
        }
        return names;
    }

    private Object getGroupedResource(Object multipleResource, String group) throws Exception {
        Class<?> abstractMultiple = Class.forName(CLASS_ABSTRACT_MULTIPLE);
        Method m = abstractMultiple.getDeclaredMethod("getOpenApiResourceOrThrow", String.class);
        m.setAccessible(true);
        return m.invoke(multipleResource, group);
    }

    private OpenAPI invokeGetOpenApi(Object openApiResource) throws Exception {
        if (openApiResource == null) {
            return null;
        }
        Class<?> abstractResource = Class.forName(CLASS_ABSTRACT_RESOURCE);
        Method m = abstractResource.getDeclaredMethod("getOpenApi", Locale.class);
        m.setAccessible(true);
        Object result = m.invoke(openApiResource, Locale.getDefault());
        return result instanceof OpenAPI ? (OpenAPI) result : null;
    }

    private Object findBeanByClassName(String className) {
        try {
            Class<?> type = Class.forName(className);
            String[] names = applicationContext.getBeanNamesForType(type);
            if (names.length == 0) {
                return null;
            }
            return applicationContext.getBean(names[0]);
        } catch (ClassNotFoundException e) {
            return null;
        } catch (Exception e) {
            log.debug("[ai] bean {} not available: {}", className, e.toString());
            return null;
        }
    }

    static boolean hasPaths(OpenAPI openAPI) {
        return openAPI != null && openAPI.getPaths() != null && !openAPI.getPaths().isEmpty();
    }

    /**
     * 合并多分组文档的 paths / components / tags（后写覆盖同 path）。
     */
    @SuppressWarnings("rawtypes")
    static OpenAPI mergeOpenApi(OpenAPI base, OpenAPI extra) {
        if (extra == null || (!hasPaths(extra) && extra.getComponents() == null)) {
            return base;
        }
        if (base == null) {
            OpenAPI copy = new OpenAPI();
            if (extra.getInfo() != null) {
                copy.setInfo(extra.getInfo());
            }
            copy.setPaths(copyPaths(extra.getPaths()));
            copy.setComponents(copyComponents(extra.getComponents()));
            if (extra.getTags() != null) {
                copy.setTags(new ArrayList<>(extra.getTags()));
            }
            return copy;
        }
        if (extra.getPaths() != null) {
            if (base.getPaths() == null) {
                base.setPaths(new Paths());
            }
            for (Map.Entry<String, PathItem> e : extra.getPaths().entrySet()) {
                base.getPaths().addPathItem(e.getKey(), e.getValue());
            }
        }
        if (extra.getComponents() != null) {
            if (base.getComponents() == null) {
                base.setComponents(new Components());
            }
            Map<String, Schema> schemas = extra.getComponents().getSchemas();
            if (schemas != null) {
                if (base.getComponents().getSchemas() == null) {
                    base.getComponents().setSchemas(new LinkedHashMap<>());
                }
                base.getComponents().getSchemas().putAll(schemas);
            }
        }
        if (extra.getTags() != null && !extra.getTags().isEmpty()) {
            if (base.getTags() == null) {
                base.setTags(new ArrayList<>());
            }
            for (Tag tag : extra.getTags()) {
                boolean exists = base.getTags().stream()
                        .anyMatch(t -> t.getName() != null && t.getName().equals(tag.getName()));
                if (!exists) {
                    base.getTags().add(tag);
                }
            }
        }
        return base;
    }

    private static Paths copyPaths(Paths src) {
        if (src == null) {
            return null;
        }
        Paths paths = new Paths();
        paths.putAll(src);
        return paths;
    }

    @SuppressWarnings("rawtypes")
    private static Components copyComponents(Components src) {
        if (src == null) {
            return null;
        }
        Components c = new Components();
        if (src.getSchemas() != null) {
            c.setSchemas(new LinkedHashMap<>(src.getSchemas()));
        }
        return c;
    }
}
