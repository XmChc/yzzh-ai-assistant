package cn.ysmp.ai.constants;

/**
 * AI Tool / Agent 渠道。
 * <p>ADMIN：管理端助手，仅注册 admin-api；APP：小程序等会员端（默认关闭，宿主 opt-in）。
 */
public final class AiChannels {

    private AiChannels() {
    }

    /** 管理端渠道（默认） */
    public static final String ADMIN = "ADMIN";

    /** 会员 / 小程序渠道（预留；本阶段不交付运行时） */
    public static final String APP = "APP";

    /** 是否为管理端渠道（空 / 未知按 ADMIN 兼容旧数据） */
    public static boolean isAdmin(String channel) {
        return channel == null || channel.isBlank() || ADMIN.equalsIgnoreCase(channel.trim());
    }
}
