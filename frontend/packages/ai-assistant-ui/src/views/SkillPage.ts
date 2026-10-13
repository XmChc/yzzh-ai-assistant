import { createSkill, listSkills, listToolApis } from '@ysmp/ai-assistant-core';
import { defineComponent, onMounted, reactive, ref, h } from 'vue';

/**
 * 技能配置默认页：创建技能并勾选 Tool。
 */
export const SkillPage = defineComponent({
  name: 'AiSkillPage',
  setup() {
    const skills = ref<Array<Record<string, unknown>>>([]);
    const tools = ref<Array<{ tool_code: string; name: string }>>([]);
    const tip = ref('');
    const form = reactive({
      skillCode: '',
      name: '',
      systemPrompt: '',
      toolCodes: [] as string[],
    });

    async function refresh() {
      skills.value = (await listSkills()) as Array<Record<string, unknown>>;
      tools.value = (await listToolApis()) as Array<{ tool_code: string; name: string }>;
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
        await createSkill({
          skillCode: form.skillCode,
          name: form.name,
          enabled: true,
          systemPrompt: form.systemPrompt,
          toolCodes: form.toolCodes,
        });
        tip.value = '已创建';
        form.skillCode = '';
        form.name = '';
        form.systemPrompt = '';
        form.toolCodes = [];
        await refresh();
      } catch (e: unknown) {
        tip.value = e instanceof Error ? e.message : String(e);
      }
    }

    function toggleTool(code: string) {
      const i = form.toolCodes.indexOf(code);
      if (i >= 0) form.toolCodes.splice(i, 1);
      else form.toolCodes.push(code);
    }

    return () =>
      h('div', { class: 'ai-page' }, [
        h('h2', 'AI 技能配置'),
        tip.value ? h('p', tip.value) : null,
        h('h3', '已有技能'),
        h(
          'ul',
          skills.value.map((s) => h('li', `${s.skill_code} — ${s.name}`)),
        ),
        h('h3', '新建技能'),
        h('label', '编码'),
        h('input', {
          value: form.skillCode,
          onInput: (e: Event) => {
            form.skillCode = (e.target as HTMLInputElement).value;
          },
        }),
        h('label', '名称'),
        h('input', {
          value: form.name,
          onInput: (e: Event) => {
            form.name = (e.target as HTMLInputElement).value;
          },
        }),
        h('label', '系统提示词'),
        h('textarea', {
          value: form.systemPrompt,
          rows: 4,
          onInput: (e: Event) => {
            form.systemPrompt = (e.target as HTMLTextAreaElement).value;
          },
        }),
        h('label', '绑定 Tool'),
        h(
          'div',
          tools.value.map((t) =>
            h('label', { style: 'display:block;margin:4px 0' }, [
              h('input', {
                type: 'checkbox',
                checked: form.toolCodes.includes(t.tool_code),
                onChange: () => toggleTool(t.tool_code),
              }),
              ` ${t.tool_code} (${t.name})`,
            ]),
          ),
        ),
        h('div', { class: 'actions' }, [h('button', { onClick: save }, '创建')]),
      ]);
  },
});
