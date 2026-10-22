import { defineComponent, ref, watch, h, type PropType } from 'vue';

import { AI_CONFIG_TABS, isAiConfigTab, type AiConfigTab } from './configTabs';
import { AgentPanel } from './panels/AgentPanel';
import { ModelPanel } from './panels/ModelPanel';
import { SkillPanel } from './panels/SkillPanel';
import { ToolPanel } from './panels/ToolPanel';

/**
 * 助手配置中心：模型 / 接口 / 技能 / 智能体 统一 Tab。
 * 菜单仅保留「助手配置」入口；能进入本页即可配置全链路。
 * 可通过 defaultTab 深链到对应面板（兼容旧路由）。
 */
export const ConfigCenterPage = defineComponent({
  name: 'AiConfigCenterPage',
  props: {
    defaultTab: {
      type: String as PropType<AiConfigTab>,
      default: 'model',
    },
  },
  setup(props) {
    const active = ref<AiConfigTab>(
      isAiConfigTab(props.defaultTab) ? props.defaultTab : 'model',
    );

    watch(
      () => props.defaultTab,
      (v) => {
        if (isAiConfigTab(v)) active.value = v;
      },
    );

    function panel() {
      switch (active.value) {
        case 'tool':
          return h(ToolPanel);
        case 'skill':
          return h(SkillPanel);
        case 'agent':
          return h(AgentPanel);
        case 'model':
        default:
          return h(ModelPanel);
      }
    }

    return () =>
      h('div', { class: 'ai-config-page' }, [
        h('header', { class: 'ai-config-hero' }, [
          h('div', { class: 'ai-config-hero-text' }, [
            h('h2', { class: 'ai-config-hero-title' }, '助手配置'),
            h(
              'p',
              { class: 'ai-config-hero-desc' },
              '按链路配置：模型 → 接口 → 技能 → 智能体。先注册并启用接口，再组装技能与智能体供对话使用。',
            ),
          ]),
          h('ol', { class: 'ai-config-pipeline' }, [
            h('li', { class: active.value === 'model' ? 'is-active' : undefined }, '模型'),
            h('li', { class: 'ai-config-pipeline-sep' }, '→'),
            h('li', { class: active.value === 'tool' ? 'is-active' : undefined }, '接口'),
            h('li', { class: 'ai-config-pipeline-sep' }, '→'),
            h('li', { class: active.value === 'skill' ? 'is-active' : undefined }, '技能'),
            h('li', { class: 'ai-config-pipeline-sep' }, '→'),
            h('li', { class: active.value === 'agent' ? 'is-active' : undefined }, '智能体'),
          ]),
        ]),
        h(
          'div',
          { class: 'ai-config-tabs', role: 'tablist' },
          AI_CONFIG_TABS.map((t) =>
            h(
              'button',
              {
                key: t.key,
                role: 'tab',
                type: 'button',
                class: active.value === t.key ? 'ai-config-tab is-active' : 'ai-config-tab',
                'aria-selected': active.value === t.key,
                onClick: () => {
                  active.value = t.key;
                },
              },
              t.label,
            ),
          ),
        ),
        h('div', { class: 'ai-config-body', role: 'tabpanel' }, [panel()]),
      ]);
  },
});
