package cn.ysmp.ai.constants;

/**
 * 允许注册为 OPENAPI Tool 的 HTTP 方法。
 */
public final class AiHttpMethods {

    private AiHttpMethods() {
    }

    public static final String GET = "GET";
    public static final String POST = "POST";

    public static boolean isAllowedForOpenApiTool(String method) {
        if (method == null) {
            return false;
        }
        return GET.equalsIgnoreCase(method) || POST.equalsIgnoreCase(method);
    }
}
