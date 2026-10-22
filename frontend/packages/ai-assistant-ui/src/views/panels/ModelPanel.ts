import {
  createModelConfig,
  getModelConfig,
  listModelConfigs,
  setDefaultModelConfig,
  updateModelConfig,
} from '@ysmp/ai-assistant-core';
import { defineComponent, onMounted, reactive, ref, h } from 'vue';

import { asBool, errMsg } from '../configTabs';

interface ModelRow {
  id: number;
  name?: string;
  enabled?: boolean | number;
  defaultFlag?: boolean | number;
  default_flag?: boolean | number;
  baseUrl?: string;
  base_url?: string;
  apiKey?: string;
  api_key?: string;
  model?: string;
  timeoutMs?: number;
  timeout_ms?: number;
  remark?: string;
}

/**
 * 模型连接：多条配置列表 + 新建/编辑 + 设为默认（对话使用默认且已启用的配置）。
 */
export const ModelPanel = defineComponent({
  name: 'AiModelPanel',
  setup() {
    const models = ref<ModelRow[]>([]);
    const tip = ref('');
    const tipOk = ref(false);
    const loading = ref(true);
    const saving = ref(false);
    const editingId = ref<number | null>(null);
    const formOpen = ref(false);
    const busyId = ref<number | null>(null);
    const form = reactive({
      name: '',
      enabled: true,
      defaultFlag: false,
      baseUrl: '',
      apiKey: '',
      model: '',
      timeoutMs: 60000,
      remark: '',
    });

    function setTip(msg: string, ok = false) {
      tip.value = msg;
      tipOk.value = ok;
    }

    function isDefault(row: ModelRow) {
      return asBool(row.defaultFlag ?? row.default_flag);
    }

    function displayName(row: ModelRow) {
      return row.name || row.model || `配置 #${row.id}`;
    }

    function resetForm() {
      editingId.value = null;
      form.name = '';
      form.enabled = true;
      form.defaultFlag = models.value.length === 0;
      form.baseUrl = '';
      form.apiKey = '';
      form.model = '';
      form.timeoutMs = 60000;
      form.remark = '';
    }

    async function refresh() {
      models.value = (await listModelConfigs()) as ModelRow[];
    }

    onMounted(async () => {
      try {
        await refresh();
      } catch (e: unknown) {
        setTip(errMsg(e), false);
      } finally {
        loading.value = false;
      }
    });

    function openCreate() {
      resetForm();
      formOpen.value = true;
    }

    async function openEdit(row: ModelRow) {
      try {
        const detail = (await getModelConfig(row.id)) as ModelRow;
        editingId.value = row.id;
        form.name = String(detail.name || '');
        form.enabled = asBool(detail.enabled);
        form.defaultFlag = asBool(detail.defaultFlag ?? detail.default_flag);
        form.baseUrl = String(detail.baseUrl ?? detail.base_url ?? '');
        form.apiKey = String(detail.apiKey ?? detail.api_key ?? '');
        form.model = String(detail.model || '');
        form.timeoutMs = Number(detail.timeoutMs ?? detail.timeout_ms ?? 60000) || 60000;
        form.remark = String(detail.remark || '');
        formOpen.value = true;
      } catch (e: unknown) {
        setTip(errMsg(e), false);
      }
    }

    function closeForm() {
      formOpen.value = false;
      resetForm();
    }

    async function save() {
      if (!form.baseUrl.trim() || !form.model.trim()) {
        setTip('请填写 Base URL 与模型名', false);
        return;
      }
      saving.value = true;
      try {
        const payload = {
          name: form.name.trim() || undefined,
          enabled: form.enabled,
          defaultFlag: form.defaultFlag,
          baseUrl: form.baseUrl.trim(),
          apiKey: form.apiKey,
          model: form.model.trim(),
          timeoutMs: form.timeoutMs,
          remark: form.remark,
        };
        if (editingId.value != null) {
          await updateModelConfig({ id: editingId.value, ...payload });
          setTip('已更新模型配置', true);
        } else {
          await createModelConfig(payload);
          setTip('已创建模型配置', true);
        }
        closeForm();
        await refresh();
      } catch (e: unknown) {
        setTip(errMsg(e), false);
      } finally {
        saving.value = false;
      }
    }

    async function setDefault(row: ModelRow) {
      if (isDefault(row)) return;
      busyId.value = row.id;
      try {
        await setDefaultModelConfig(row.id);
        setTip(`已将「${displayName(row)}」设为默认`, true);
        await refresh();
      } catch (e: unknown) {
        setTip(errMsg(e), false);
      } finally {
        busyId.value = null;
      }
    }

    return () =>
      h('div', { class: 'ai-config-panel' }, [
        h('div', { class: 'ai-config-panel-head' }, [
          h('div', { class: 'ai-config-panel-head-row' }, [
            h('h3', { class: 'ai-config-panel-title' }, '模型连接'),
            h(
              'button',
              {
                class: 'ai-config-btn ai-config-btn--primary',
                onClick: openCreate,
              },
              '新建模型',
            ),
          ]),
          h(
            'p',
            { class: 'ai-config-panel-desc' },
            '可维护多条 OpenAI 兼容连接；对话使用「默认」且已启用的配置。API Key 含 **** 时保存不会覆盖原值。',
          ),
        ]),
        tip.value
          ? h('div', { class: tipOk.value ? 'ai-config-banner is-ok' : 'ai-config-banner is-err' }, tip.value)
          : null,
        loading.value
          ? h('div', { class: 'ai-config-empty' }, '加载中…')
          : models.value.length === 0
            ? h('div', { class: 'ai-config-empty' }, '暂无模型配置，请点击「新建模型」添加。')
            : h(
                'div',
                { class: 'ai-config-card-list' },
                models.value.map((m) =>
                  h('div', { key: m.id, class: 'ai-config-card' }, [
                    h('div', { class: 'ai-config-card-main' }, [
                      h('div', { class: 'ai-config-card-title-row' }, [
                        h('span', { class: 'ai-config-card-title' }, displayName(m)),
                        asBool(m.enabled)
                          ? h('span', { class: 'ai-config-tag is-on' }, '启用')
                          : h('span', { class: 'ai-config-tag is-off' }, '停用'),
                        isDefault(m)
                          ? h('span', { class: 'ai-config-tag is-primary' }, '默认')
                          : null,
                      ]),
                      h(
                        'div',
                        { class: 'ai-config-card-meta' },
                        [
                          m.model,
                          m.baseUrl || m.base_url,
                        ]
                          .filter(Boolean)
                          .join(' · '),
                      ),
                      m.remark
                        ? h('div', { class: 'ai-config-card-sub' }, m.remark)
                        : null,
                    ]),
                    h('div', { class: 'ai-config-card-actions' }, [
                      h(
                        'button',
                        {
                          class: 'ai-config-btn',
                          onClick: () => openEdit(m),
                        },
                        '编辑',
                      ),
                      h(
                        'button',
                        {
                          class: isDefault(m)
                            ? 'ai-config-btn ai-config-btn--ghost'
                            : 'ai-config-btn ai-config-btn--primary',
                          disabled: isDefault(m) || busyId.value === m.id,
                          onClick: () => setDefault(m),
                        },
                        busyId.value === m.id ? '…' : isDefault(m) ? '当前默认' : '设为默认',
                      ),
                    ]),
                  ]),
                ),
              ),
        formOpen.value
          ? h('div', { class: 'ai-config-drawer-mask', onClick: closeForm }, [
              h(
                'div',
                {
                  class: 'ai-config-drawer',
                  onClick: (e: Event) => e.stopPropagation(),
                },
                [
                  h('div', { class: 'ai-config-drawer-head' }, [
                    h('h3', editingId.value != null ? '编辑模型' : '新建模型'),
                    h(
                      'button',
                      { class: 'ai-config-btn ai-config-btn--ghost', onClick: closeForm },
                      '关闭',
                    ),
                  ]),
                  h('div', { class: 'ai-config-form' }, [
                    h('div', { class: 'ai-config-field' }, [
                      h('label', { class: 'ai-config-label' }, '名称'),
                      h('input', {
                        class: 'ai-config-input',
                        placeholder: '如：生产 GPT / 本地 Ollama',
                        value: form.name,
                        onInput: (e: Event) => {
                          form.name = (e.target as HTMLInputElement).value;
                        },
                      }),
                    ]),
                    h('div', { class: 'ai-config-field' }, [
                      h('label', { class: 'ai-config-label' }, 'Base URL'),
                      h('input', {
                        class: 'ai-config-input',
                        placeholder: 'https://api.openai.com/v1',
                        value: form.baseUrl,
                        onInput: (e: Event) => {
                          form.baseUrl = (e.target as HTMLInputElement).value;
                        },
                      }),
                      h(
                        'div',
                        { class: 'ai-config-hint' },
                        'OpenAI 兼容根地址；按供应商文档填写。',
                      ),
                    ]),
                    h('div', { class: 'ai-config-field' }, [
                      h('label', { class: 'ai-config-label' }, 'API Key'),
                      h('input', {
                        class: 'ai-config-input',
                        type: 'password',
                        placeholder: 'sk-…',
                        value: form.apiKey,
                        onInput: (e: Event) => {
                          form.apiKey = (e.target as HTMLInputElement).value;
                        },
                      }),
                    ]),
                    h('div', { class: 'ai-config-field' }, [
                      h('label', { class: 'ai-config-label' }, '模型名'),
                      h('input', {
                        class: 'ai-config-input',
                        placeholder: 'gpt-4o-mini',
                        value: form.model,
                        onInput: (e: Event) => {
                          form.model = (e.target as HTMLInputElement).value;
                        },
                      }),
                    ]),
                    h('div', { class: 'ai-config-field' }, [
                      h('label', { class: 'ai-config-label' }, '超时 (ms)'),
                      h('input', {
                        class: 'ai-config-input',
                        type: 'number',
                        value: String(form.timeoutMs),
                        onInput: (e: Event) => {
                          form.timeoutMs = Number((e.target as HTMLInputElement).value) || 60000;
                        },
                      }),
                    ]),
                    h('div', { class: 'ai-config-field ai-config-field--inline' }, [
                      h('label', { class: 'ai-config-switch' }, [
                        h('input', {
                          type: 'checkbox',
                          checked: form.enabled,
                          onChange: (e: Event) => {
                            form.enabled = (e.target as HTMLInputElement).checked;
                          },
                        }),
                        h('span', '启用'),
                      ]),
                      h('label', { class: 'ai-config-switch' }, [
                        h('input', {
                          type: 'checkbox',
                          checked: form.defaultFlag,
                          onChange: (e: Event) => {
                            form.defaultFlag = (e.target as HTMLInputElement).checked;
                          },
                        }),
                        h('span', '设为默认'),
                      ]),
                    ]),
                    h('div', { class: 'ai-config-field' }, [
                      h('label', { class: 'ai-config-label' }, '备注'),
                      h('textarea', {
                        class: 'ai-config-input',
                        rows: 2,
                        value: form.remark,
                        onInput: (e: Event) => {
                          form.remark = (e.target as HTMLTextAreaElement).value;
                        },
                      }),
                    ]),
                    h('div', { class: 'ai-config-actions' }, [
                      h(
                        'button',
                        {
                          class: 'ai-config-btn ai-config-btn--primary',
                          disabled: saving.value,
                          onClick: save,
                        },
                        saving.value ? '保存中…' : '保存',
                      ),
                      h(
                        'button',
                        { class: 'ai-config-btn ai-config-btn--ghost', onClick: closeForm },
                        '取消',
                      ),
                    ]),
                  ]),
                ],
              ),
            ])
          : null,
      ]);
  },
});
