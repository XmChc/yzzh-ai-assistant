package cn.ysmp.ai.constants;

import java.util.Locale;
import java.util.Set;

/**
 * 写操作类 Tool 识别标记；命中后须用户确认才执行。
 */
public final class AiWriteToolMarkers {

    private AiWriteToolMarkers() {
    }

    public static final Set<String> KEYWORDS = Set.of(
            "approve", "reject", "cancel", "submit", "delete", "remove", "create", "update", "save",
            "enable", "disable", "void", "close", "open", "modify",
            "审批", "驳回", "作废", "取消", "提交", "删除", "创建", "修改", "更新", "保存",
            "启用", "停用", "关闭", "开启"
    );

    public static boolean matchesText(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String k : KEYWORDS) {
            if (lower.contains(k.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
