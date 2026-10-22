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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ysmp / Spring Security 默认实现：从 HandlerMethod 的 {@link PreAuthorize} 解析字面量权限码。
 * <p>内部按 method+path 建索引，避免 Catalog 扫描时对每个接口 O(N) 遍历 Handler。
 */
@Slf4j
@RequiredArgsConstructor
public class PreAuthorizeEndpointPermissionResolver implements AiEndpointPermissionResolver {

    private static final Pattern HAS_PERMISSION = Pattern.compile(
            "has(?:Any)?Permissions?\\s*\\(\\s*'([^']+)'(?:\\s*,\\s*'([^']+)')*\\s*\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern QUOTED = Pattern.compile("'([^']+)'");

    private final RequestMappingHandlerMapping handlerMapping;

    /** key: GET /admin-api/trade/sfmp-order/page */
    private final Map<String, List<String>> exactIndex = new ConcurrentHashMap<>();
    /** 含 path 变量的模式，数量通常远小于全量 Handler */
    private final List<PatternEntry> patternIndex = new ArrayList<>();
    private volatile boolean indexed;

    @Override
    public List<String> resolve(String httpMethod, String path) {
        if (httpMethod == null || path == null) {
            return List.of();
        }
        ensureIndex();
        String method = httpMethod.toUpperCase(Locale.ROOT);
        String normPath = normalizePath(path);
        // OpenAPI 可能带 /admin-api 前缀，Handler 也可能是模块相对路径，两侧都试
        List<String> hit = lookup(method, normPath);
        if (hit != null) {
            return hit;
        }
        if (normPath.startsWith("/admin-api/")) {
            hit = lookup(method, normPath.substring("/admin-api".length()));
            if (hit != null) {
                return hit;
            }
        } else {
            hit = lookup(method, "/admin-api" + normPath);
            if (hit != null) {
                return hit;
            }
        }
        log.debug("[ai] no handler for {} {}", httpMethod, path);
        return List.of();
    }

    private List<String> lookup(String method, String path) {
        List<String> exact = exactIndex.get(method + " " + path);
        if (exact != null) {
            return exact;
        }
        for (PatternEntry pe : patternIndex) {
            if (!pe.method.equals(method)) {
                continue;
            }
            if (pathsMatch(pe.pattern, path)) {
                return pe.permissions;
            }
        }
        return null;
    }

    private void ensureIndex() {
        if (indexed) {
            return;
        }
        synchronized (this) {
            if (indexed) {
                return;
            }
            Map<String, List<String>> exact = new LinkedHashMap<>();
            List<PatternEntry> patterns = new ArrayList<>();
            Map<RequestMappingInfo, HandlerMethod> map = handlerMapping.getHandlerMethods();
            for (Map.Entry<RequestMappingInfo, HandlerMethod> e : map.entrySet()) {
                RequestMappingInfo info = e.getKey();
                HandlerMethod hm = e.getValue();
                Set<String> perms = new LinkedHashSet<>();
                collectFromMethod(hm.getMethod(), perms);
                collectFromMethod(hm.getBeanType(), perms);
                List<String> permList = List.copyOf(perms);

                Set<org.springframework.web.bind.annotation.RequestMethod> methods =
                        info.getMethodsCondition().getMethods();
                List<String> httpMethods = new ArrayList<>();
                if (methods == null || methods.isEmpty()) {
                    httpMethods.add("GET");
                    httpMethods.add("POST");
                } else {
                    for (org.springframework.web.bind.annotation.RequestMethod m : methods) {
                        httpMethods.add(m.name());
                    }
                }
                Set<String> pathPatterns = info.getPatternValues();
                if (pathPatterns == null) {
                    continue;
                }
                for (String rawPattern : pathPatterns) {
                    String pattern = normalizePath(rawPattern);
                    boolean templated = pattern.contains("{");
                    for (String m : httpMethods) {
                        if (templated) {
                            patterns.add(new PatternEntry(m, pattern, permList));
                        } else {
                            exact.put(m + " " + pattern, permList);
                        }
                    }
                }
            }
            exactIndex.clear();
            exactIndex.putAll(exact);
            patternIndex.clear();
            patternIndex.addAll(patterns);
            indexed = true;
            log.info("[ai] PreAuthorize index built: exact={}, patterns={}",
                    exactIndex.size(), patternIndex.size());
        }
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

    private static boolean pathsMatch(String pattern, String path) {
        if (pattern.equals(path)) {
            return true;
        }
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

    private record PatternEntry(String method, String pattern, List<String> permissions) {
    }
}
