import { getModelConfig, updateModelConfig } from '@ysmp/ai-assistant-core';
import { defineComponent, onMounted, reactive, ref, h } from 'vue';

/**
 * 模型配置默认页（宿主菜单可指向本组件）。
 */
export const ModelConfigPage = defineComponent({
  name: 'AiModelConfigPage',
  setup() {
    const form = reactive({
      enabled: false,
      baseUrl: '',
      apiKey: '',
      model: '',
      timeoutMs: 60000,
      remark: '',
    });
    const saving = ref(false);
    const tip = ref('');

    onMounted(async () => {
      try {
        const data = (await getModelConfig()) as typeof form;
        Object.assign(form, data);
      } catch (e: unknown) {
        tip.value = e instanceof Error ? e.message : String(e);
      }
    });

    async function save() {
      saving.value = true;
      tip.value = '';
      try {
        await updateModelConfig({ ...form });
        tip.value = '已保存';
      } catch (e: unknown) {
        tip.value = e instanceof Error ? e.message : String(e);
      } finally {
        saving.value = false;
      }
    }

    const field = (label: string, key: keyof typeof form, type = 'text') =>
      h('div', [
        h('label', label),
        key === 'enabled'
          ? h('input', {
              type: 'checkbox',
              checked: form.enabled,
              onChange: (e: Event) => {
                form.enabled = (e.target as HTMLInputElement).checked;
              },
            })
          : h('input', {
              type,
              value: String(form[key] ?? ''),
              onInput: (e: Event) => {
                const v = (e.target as HTMLInputElement).value;
                if (key === 'timeoutMs') form.timeoutMs = Number(v) || 60000;
                else (form as Record<string, unknown>)[key] = v;
              },
            }),
      ]);

    return () =>
      h('div', { class: 'ai-page' }, [
        h('h2', 'AI 模型配置'),
        field('启用', 'enabled'),
        field('Base URL', 'baseUrl'),
        field('API Key', 'apiKey', 'password'),
        field('模型', 'model'),
        field('超时(ms)', 'timeoutMs'),
        h('div', { class: 'actions' }, [
          h('button', { disabled: saving.value, onClick: save }, saving.value ? '保存中…' : '保存'),
          tip.value ? h('span', { style: 'margin-left:12px' }, tip.value) : null,
        ]),
      ]);
  },
});
