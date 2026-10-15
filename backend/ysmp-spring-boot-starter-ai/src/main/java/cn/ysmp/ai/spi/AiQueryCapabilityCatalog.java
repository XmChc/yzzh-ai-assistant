package cn.ysmp.ai.spi;

import java.util.List;

/**
 * 当前用户可见的受控查询能力目录（已按权限码过滤）。
 * <p>默认空实现；宿主按 RBAC 暴露汇总/分析能力，避免助手直连表。
 */
public interface AiQueryCapabilityCatalog {

    List<AiQueryCapability> listAvailable();
}
