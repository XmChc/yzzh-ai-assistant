package cn.ysmp.ai.constants;

import java.util.Locale;
import java.util.Set;

/**
 * Tool 查询参数名约定：分页/排序键不计入「业务约束」。
 */
public final class AiToolQueryParamNames {

    private AiToolQueryParamNames() {
    }

    /** 分页相关（出现即视为分页/列表类 Tool） */
    public static final Set<String> PAGING = Set.of(
            "pageno", "pagenum", "page", "pagesize", "page_size",
            "current", "size", "limit", "offset", "from", "skip"
    );

    /** 排序 / 展示类，不计入筛选约束 */
    public static final Set<String> NON_FILTER = Set.of(
            "pageno", "pagenum", "page", "pagesize", "page_size",
            "current", "size", "limit", "offset", "from", "skip",
            "sort", "sortfield", "sort_field", "order", "orderby", "order_by",
            "asc", "desc", "sortingfields", "orders"
    );

    public static String normalize(String name) {
        if (name == null) {
            return "";
        }
        return name.trim().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
    }

    public static boolean isPaging(String name) {
        return PAGING.contains(normalize(name));
    }

    public static boolean isNonFilter(String name) {
        return NON_FILTER.contains(normalize(name));
    }
}
