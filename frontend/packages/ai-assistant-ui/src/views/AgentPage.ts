import {
  AiAgentModes,
  createAgent,
  listAgents,
  listSkills,
} from '@ysmp/ai-assistant-core';
import { defineComponent, onMounted, reactive, ref, h } from 'vue';

/**
 * Agent 配置默认页：绑定多个 Skill。
 */
export const AgentPage = defineComponent({
  name: 'AiAgentPage',
  setup() {
    const agents = ref<Array<Record<string, unknown>>>([]);
    const skills = ref<Array<{ id: number; skill_code: string; name: string }>>([]);
    const tip = ref('');
    const form = reactive({
      agentCode: '',
      name: '',
      accessPermission: '',
      agentMode: AiAgentModes.GENERAL,
      systemPrompt: '',
      defaultFlag: false,
      skillIds: [] as number[],
    });

    async function refresh() {
      agents.value = (await listAgents()) as Array<Record<string, unknown>>;
      skills.value = (await listSkills()) as Array<{ id: number; skill_code: string; name: string }>;
    }

    onMounted(async () => {
      try {
        await refresh();
      } catch (e: unknown) {
        tip.value = e instanceof Error ? e.message : String(e);
      }
    });

    async function save() {
      tip.value = '';
      try {
        await createAgent({
          agentCode: form.agentCode,
          name: form.name,
          enabled: true,
          accessPermission: form.accessPermission || undefined,
          agentMode: form.agentMode,
          systemPrompt: form.systemPrompt,
          defaultFlag: form.defaultFlag,
          skillIds: form.skillIds,
        });
        tip.value = '已创建';
        await refresh();
      } catch (e: unknown) {
        tip.value = e instanceof Error ? e.message : String(e);
      }
    }

    function toggleSkill(id: number) {
      const i = form.skillIds.indexOf(id);
      if (i >= 0) form.skillIds.splice(i, 1);
      else form.skillIds.push(id);
    }

    return () =>
      h('div', { class: 'ai-page' }, [
        h('h2', 'AI Agent 配置'),
        tip.value ? h('p', tip.value) : null,
        h('h3', '已有 Agent'),
        h(
          'ul',
          agents.value.map((a) =>
            h(
              'li',
              `${a.agent_code} — ${a.name} — ${a.agent_mode ?? AiAgentModes.GENERAL} — ${a.access_permission}`,
            ),
          ),
        ),
        h('h3', '新建 Agent'),
        h('label', '编码'),
        h('input', {
          value: form.agentCode,
          onInput: (e: Event) => {
            form.agentCode = (e.target as HTMLInputElement).value;
          },
        }),
        h('label', '名称'),
        h('input', {
          value: form.name,
          onInput: (e: Event) => {
            form.name = (e.target as HTMLInputElement).value;
          },
        }),
        h('label', '访问权限码（空则 ai:agent:{编码}）'),
        h('input', {
          value: form.accessPermission,
          onInput: (e: Event) => {
            form.accessPermission = (e.target as HTMLInputElement).value;
          },
        }),
        h('label', '模式'),
        h(
          'select',
          {
            value: form.agentMode,
            onChange: (e: Event) => {
              form.agentMode = (e.target as HTMLSelectElement).value;
            },
          },
          [
            h('option', { value: AiAgentModes.GENERAL }, 'GENERAL（通用）'),
            h('option', { value: AiAgentModes.SCENARIO }, 'SCENARIO（场景）'),
          ],
        ),
        h('label', [
          h('input', {
            type: 'checkbox',
            checked: form.defaultFlag,
            onChange: (e: Event) => {
              form.defaultFlag = (e.target as HTMLInputElement).checked;
            },
          }),
          ' 设为默认',
        ]),
        h('label', '绑定 Skill'),
        h(
          'div',
          skills.value.map((s) =>
            h('label', { style: 'display:block;margin:4px 0' }, [
              h('input', {
                type: 'checkbox',
                checked: form.skillIds.includes(s.id),
                onChange: () => toggleSkill(s.id),
              }),
              ` ${s.skill_code} (${s.name})`,
            ]),
          ),
        ),
        h('div', { class: 'actions' }, [h('button', { onClick: save }, '创建')]),
      ]);
  },
});
