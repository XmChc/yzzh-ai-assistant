package cn.ysmp.module.ai.service;

import cn.ysmp.ai.constants.AiAgentModes;
import cn.ysmp.ai.constants.AiToolTypes;
import cn.ysmp.ai.guard.AiAccessGuard;
import cn.ysmp.ai.spi.AiSecurityAdapter;
import cn.ysmp.ai.tool.AiCapabilityBundle;
import cn.ysmp.ai.tool.AiSkillHint;
import cn.ysmp.ai.tool.AiToolCatalog;
import cn.ysmp.ai.tool.AiToolDefinition;
import cn.ysmp.module.ai.service.catalog.AiCatalogCache;
import cn.ysmp.module.ai.service.catalog.AiCatalogCache.AgentSnap;
import cn.ysmp.module.ai.service.catalog.AiCatalogCache.SkillSnap;
import cn.ysmp.module.ai.service.catalog.AiCatalogCache.Snapshot;
import cn.ysmp.module.ai.service.catalog.AiCatalogCache.ToolSnap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Agent 双模式 + Skill 激活 + Tool available。
 * <p>配置数据来自 {@link AiCatalogCache} 内存快照；每请求只做权限判定（带 memo），不打库。
 */
@Slf4j
@RequiredArgsConstructor
public class DbAiToolCatalog implements AiToolCatalog {

    private final AiCatalogCache catalogCache;
    private final AiSecurityAdapter securityAdapter;

    @Override
    public List<AiToolDefinition> listAvailableTools(String agentCode) {
        AiCapabilityBundle bundle = resolveCapabilities(agentCode, null, null);
        if (bundle.getCallableTools() == null) {
            return List.of();
        }
        return bundle.getCallableTools();
    }

    /**
     * 短路：找到第一个可用能力即返回，不构建完整 Skill/Tool 列表。
     */
    @Override
    public boolean hasUsableCapability(String agentCode) {
        Snapshot snap = catalogCache.get();
        String resolved = resolveAgentCode(agentCode, snap);
        if (resolved == null) {
            return false;
        }
        AgentSnap agent = snap.getAgentsByCode().get(resolved);
        if (agent == null) {
            return false;
        }
        if (!AiAccessGuard.canAccessAgent(securityAdapter, agent.getAccessPermission())) {
            return false;
        }
        Map<String, Boolean> memo = AiAccessGuard.newPermissionMemo();
        if (AiAgentModes.isScenario(agent.getAgentMode())) {
            for (Long skillId : agent.getSkillIds()) {
                SkillSnap skill = snap.getSkillsById().get(skillId);
                if (skill != null && isSkillActivated(skill, snap, memo)) {
                    return true;
                }
            }
            return false;
        }
        for (ToolSnap tool : snap.getToolsByCode().values()) {
            if (isToolAvailable(tool, memo)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public AiCapabilityBundle resolveCapabilities(String agentCode, String skillCodeHint, String toolCodeHint) {
        Snapshot snap = catalogCache.get();
        String resolvedAgent = resolveAgentCode(agentCode, snap);
        if (resolvedAgent == null) {
            return AiCapabilityBundle.builder()
                    .agentAccessible(false)
                    .error("未配置可用 Agent")
                    .callableTools(List.of())
                    .activatedSkills(List.of())
                    .inactiveSkills(List.of())
                    .build();
        }

        AgentSnap agent = snap.getAgentsByCode().get(resolvedAgent);
        if (agent == null) {
            return AiCapabilityBundle.builder()
                    .agentCode(resolvedAgent)
                    .agentAccessible(false)
                    .error("Agent 不存在或未启用: " + resolvedAgent)
                    .callableTools(List.of())
                    .activatedSkills(List.of())
                    .inactiveSkills(List.of())
                    .build();
        }

        if (!AiAccessGuard.canAccessAgent(securityAdapter, agent.getAccessPermission())) {
            return AiCapabilityBundle.builder()
                    .agentCode(resolvedAgent)
                    .agentMode(agent.getAgentMode())
                    .agentAccessible(false)
                    .error("无权限使用该 Agent")
                    .callableTools(List.of())
                    .activatedSkills(List.of())
                    .inactiveSkills(List.of())
                    .build();
        }

        Map<String, Boolean> memo = AiAccessGuard.newPermissionMemo();
        Map<String, AiToolDefinition> allTools = materializeTools(snap, memo);

        String mode = agent.getAgentMode();
        List<SkillSnap> candidateSkills = AiAgentModes.isScenario(mode)
                ? skillsForAgent(agent, snap)
                : skillsAllEnabled(snap);

        List<AiSkillHint> activated = new ArrayList<>();
        List<AiSkillHint> inactive = new ArrayList<>();
        for (SkillSnap skill : candidateSkills) {
            AiSkillHint hint = evaluateSkill(skill, allTools);
            if (hint.isActivated()) {
                activated.add(hint);
            } else {
                inactive.add(hint);
            }
        }

        Set<String> callableCodes = new LinkedHashSet<>();
        if (AiAgentModes.isScenario(mode)) {
            for (AiSkillHint s : activated) {
                if (s.getToolCodes() != null) {
                    callableCodes.addAll(s.getToolCodes());
                }
            }
        } else {
            for (AiToolDefinition t : allTools.values()) {
                if (t.isAvailable()) {
                    callableCodes.add(t.getToolCode());
                }
            }
        }

        String err = null;
        if (skillCodeHint != null && !skillCodeHint.isBlank()) {
            String sc = skillCodeHint.trim();
            AiSkillHint hit = activated.stream()
                    .filter(s -> sc.equals(s.getSkillCode()))
                    .findFirst()
                    .orElse(null);
            if (hit == null) {
                AiSkillHint miss = inactive.stream()
                        .filter(s -> sc.equals(s.getSkillCode()))
                        .findFirst()
                        .orElse(null);
                if (miss != null && AiAgentModes.isScenario(mode)) {
                    err = "Skill 未激活: " + sc
                            + (miss.getMissingHints() == null || miss.getMissingHints().isEmpty()
                            ? ""
                            : "（" + String.join("；", miss.getMissingHints()) + "）");
                } else {
                    err = "Skill 不可用或不存在: " + sc;
                }
                callableCodes = Set.of();
            } else {
                callableCodes = new LinkedHashSet<>(
                        hit.getToolCodes() == null ? List.of() : hit.getToolCodes());
            }
        }

        if (err == null && toolCodeHint != null && !toolCodeHint.isBlank()) {
            String tc = toolCodeHint.trim();
            AiToolDefinition def = allTools.get(tc);
            if (def == null || !def.isAvailable()) {
                err = "Tool 不可用或不存在: " + tc
                        + (def != null && def.getUnavailableReason() != null
                        ? "（" + def.getUnavailableReason() + "）"
                        : "");
                callableCodes = Set.of();
            } else if (!callableCodes.contains(tc) && AiAgentModes.isScenario(mode)) {
                err = "Tool 不在当前 Agent 已激活 Skill 范围内: " + tc;
                callableCodes = Set.of();
            } else {
                callableCodes = Set.of(tc);
            }
        }

        List<AiToolDefinition> callable = new ArrayList<>();
        for (String code : callableCodes) {
            AiToolDefinition t = allTools.get(code);
            if (t != null && t.isAvailable()) {
                callable.add(t);
            }
        }

        return AiCapabilityBundle.builder()
                .agentCode(resolvedAgent)
                .agentMode(mode)
                .agentSystemPrompt(agent.getSystemPrompt())
                .agentAccessible(true)
                .error(err)
                .callableTools(callable)
                .activatedSkills(activated)
                .inactiveSkills(AiAgentModes.isScenario(mode) ? inactive : List.of())
                .build();
    }

    private String resolveAgentCode(String agentCode, Snapshot snap) {
        if (agentCode != null && !agentCode.isBlank()) {
            return agentCode.trim();
        }
        return snap.getDefaultAgentCode();
    }

    private List<SkillSnap> skillsForAgent(AgentSnap agent, Snapshot snap) {
        List<SkillSnap> list = new ArrayList<>();
        for (Long id : agent.getSkillIds()) {
            SkillSnap s = snap.getSkillsById().get(id);
            if (s != null) {
                list.add(s);
            }
        }
        return list;
    }

    private List<SkillSnap> skillsAllEnabled(Snapshot snap) {
        List<SkillSnap> list = new ArrayList<>(snap.getAllEnabledSkillIds().size());
        for (Long id : snap.getAllEnabledSkillIds()) {
            SkillSnap s = snap.getSkillsById().get(id);
            if (s != null) {
                list.add(s);
            }
        }
        return list;
    }

    private Map<String, AiToolDefinition> materializeTools(Snapshot snap, Map<String, Boolean> memo) {
        Map<String, AiToolDefinition> map = new java.util.LinkedHashMap<>();
        for (ToolSnap tool : snap.getToolsByCode().values()) {
            map.put(tool.getToolCode(), toDefinition(tool, memo));
        }
        return map;
    }

    private AiToolDefinition toDefinition(ToolSnap tool, Map<String, Boolean> memo) {
        List<String> perms = tool.getRequiredPermissions() == null ? List.of() : tool.getRequiredPermissions();
        // OPENAPI：空权限码 fail-closed；其它类型仍按「空=通过」兼容
        boolean openApi = AiToolTypes.OPENAPI.equalsIgnoreCase(tool.getToolType());
        boolean available = openApi
                ? AiAccessGuard.isOpenApiToolAvailable(securityAdapter, perms, memo)
                : AiAccessGuard.hasAllPermissions(securityAdapter, perms, memo);
        String reason = null;
        if (!available) {
            if (openApi && !AiAccessGuard.hasBoundPermissionCodes(perms)) {
                reason = "未绑定权限码（须宿主 @PreAuthorize 同步）";
            } else {
                List<String> miss = perms.stream()
                        .filter(p -> {
                            Boolean hit = memo.get(p);
                            if (hit != null) {
                                return !hit;
                            }
                            boolean ok = securityAdapter.hasAnyPermissions(p);
                            memo.put(p, ok);
                            return !ok;
                        })
                        .collect(Collectors.toList());
                reason = miss.isEmpty() ? "权限不足" : ("缺少 " + String.join(", ", miss));
            }
        }
        return AiToolDefinition.builder()
                .toolCode(tool.getToolCode())
                .toolType(tool.getToolType())
                .name(tool.getName())
                .description(tool.getDescription())
                .httpMethod(tool.getHttpMethod())
                .path(tool.getPath())
                .module(tool.getModule())
                .requiredPermissions(perms)
                .parametersSchema(tool.getParametersSchema())
                .responseSchema(tool.getResponseSchema())
                .available(available)
                .unavailableReason(reason)
                .build();
    }

    private boolean isToolAvailable(ToolSnap tool, Map<String, Boolean> memo) {
        List<String> perms = tool.getRequiredPermissions() == null ? List.of() : tool.getRequiredPermissions();
        if (AiToolTypes.OPENAPI.equalsIgnoreCase(tool.getToolType())) {
            return AiAccessGuard.isOpenApiToolAvailable(securityAdapter, perms, memo);
        }
        return AiAccessGuard.hasAllPermissions(securityAdapter, perms, memo);
    }

    private boolean isSkillActivated(SkillSnap skill, Snapshot snap, Map<String, Boolean> memo) {
        List<String> codes = skill.getToolCodes();
        if (codes == null || codes.isEmpty()) {
            return false;
        }
        for (String code : codes) {
            ToolSnap tool = snap.getToolsByCode().get(code);
            if (tool == null || !isToolAvailable(tool, memo)) {
                return false;
            }
        }
        return true;
    }

    private AiSkillHint evaluateSkill(SkillSnap skill, Map<String, AiToolDefinition> allTools) {
        List<String> missing = new ArrayList<>();
        boolean allOk = true;
        List<String> codes = skill.getToolCodes() == null ? List.of() : skill.getToolCodes();
        if (codes.isEmpty()) {
            allOk = false;
            missing.add("未绑定任何 Tool");
        }
        for (String code : codes) {
            AiToolDefinition t = allTools.get(code);
            if (t == null) {
                allOk = false;
                missing.add(code + "：未注册或未启用");
            } else if (!t.isAvailable()) {
                allOk = false;
                missing.add(code + "："
                        + (t.getUnavailableReason() == null ? "权限不足" : t.getUnavailableReason()));
            }
        }
        return AiSkillHint.builder()
                .skillCode(skill.getSkillCode())
                .name(skill.getName())
                .systemPrompt(skill.getSystemPrompt())
                .activated(allOk)
                .missingHints(missing)
                .toolCodes(codes)
                .build();
    }
}
