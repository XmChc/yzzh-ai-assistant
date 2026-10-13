package cn.ysmp.module.ai.service;

import cn.ysmp.ai.spi.AiEndpointPermissionResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ysmp / Spring Security 默认实现：从 HandlerMethod 的 {@link PreAuthorize} 解析字面量权限码。
 */
@Slf4j
@RequiredArgsConstructor
public class PreAuthorizeEndpointPermissionResolver implements AiEndpointPermissionResolver {

    private static final Pattern HAS_PERMISSION = Pattern.compile(
            "has(?:Any)?Permissions?\\s*\\(\\s*'([^']+)'(?:\\s*,\\s*'([^']+)')*\\s*\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern QUOTED = Pattern.compile("'([^']+)'");

    private final RequestMappingHandlerMapping handlerMapping;

    @Override
    public List<String> resolve(String httpMethod, String path) {
        if (httpMethod == null || path == null) {
            return List.of();
        }
        HandlerMethod hm = findHandler(httpMethod.toUpperCase(Locale.ROOT), normalizePath(path));
        if (hm == null) {
            log.debug("[ai] no handler for {} {}", httpMethod, path);
            return List.of();
        }
        Set<String> perms = new LinkedHashSet<>();
        collectFromMethod(hm.getMethod(), perms);
        collectFromMethod(hm.getBeanType(), perms);
        return new ArrayList<>(perms);
    }

    private void collectFromMethod(Method method, Set<String> out) {
        PreAuthorize pa = method.getAnnotation(PreAuthorize.class);
        if (pa != null) {
            out.addAll(parseExpression(pa.value()));
        }
    }

    private void collectFromMethod(Class<?> type, Set<String> out) {
        PreAuthorize pa = type.getAnnotation(PreAuthorize.class);
        if (pa != null) {
            out.addAll(parseExpression(pa.value()));
        }
    }

    static List<String> parseExpression(String expr) {
        if (expr == null || expr.isBlank()) {
            return List.of();
        }
        // 优先匹配 hasPermission / hasAnyPermissions 内引号串
        if (HAS_PERMISSION.matcher(expr).find() || expr.contains("hasPermission") || expr.contains("hasAnyPermissions")) {
            List<String> list = new ArrayList<>();
            Matcher m = QUOTED.matcher(expr);
            while (m.find()) {
                String v = m.group(1);
                if (v != null && !v.isBlank() && !v.startsWith("@")) {
                    list.add(v.trim());
                }
            }
            return list;
        }
        return List.of();
    }

    private HandlerMethod findHandler(String method, String path) {
        Map<RequestMappingInfo, HandlerMethod> map = handlerMapping.getHandlerMethods();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : map.entrySet()) {
            RequestMappingInfo info = e.getKey();
            if (info.getMethodsCondition().getMethods() != null
                    && !info.getMethodsCondition().getMethods().isEmpty()
                    && info.getMethodsCondition().getMethods().stream()
                    .noneMatch(m -> m.name().equalsIgnoreCase(method))) {
                continue;
            }
            Set<String> patterns = info.getPatternValues();
            if (patterns == null || patterns.isEmpty()) {
                continue;
            }
            for (String pattern : patterns) {
                if (pathsMatch(normalizePath(pattern), path)) {
                    return e.getValue();
                }
            }
        }
        return null;
    }

    private static boolean pathsMatch(String pattern, String path) {
        if (pattern.equals(path)) {
            return true;
        }
        // 简单：去掉 path 变量 {id} 后按段比较
        String[] pp = pattern.split("/");
        String[] ap = path.split("/");
        if (pp.length != ap.length) {
            return false;
        }
        for (int i = 0; i < pp.length; i++) {
            String a = pp[i];
            String b = ap[i];
            if (a.startsWith("{") && a.endsWith("}")) {
                continue;
            }
            if (!a.equals(b)) {
                return false;
            }
        }
        return true;
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String p = path.trim();
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        if (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }
}
