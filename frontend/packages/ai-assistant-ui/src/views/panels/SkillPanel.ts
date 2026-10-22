import {
  createSkill,
  getSkill,
  listSkills,
  listToolApis,
  updateSkill,
} from '@ysmp/ai-assistant-core';
import { defineComponent, onMounted, reactive, ref, h } from 'vue';

import { asBool, errMsg } from '../configTabs';

interface SkillRow {
  id: number;
  skill_code: string;
  name: string;
  enabled?: boolean | number;
  remark?: string;
}

interface ToolOpt {
  tool_code: string;
  name: string;
  enabled?: boolean | number;
}

interface SkillDetail {
  id?: number;
  skill_code?: string;
  name?: string;
  enabled?: boolean | number;
  system_prompt?: string;
  remark?: string;
  toolCodes?: string[];
}

/**
 * 技能配置：列表 + 新建/编辑（提示词与绑定 Tool）。
 */
export const SkillPanel = defineComponent({
  name: 'AiSkillPanel',
  setup() {
    /** 能进入「助手配置」页即可维护；细粒度菜单已收敛删除 */
    const canUpdate = () => true;
    const skills = ref<SkillRow[]>([]);
    const tools = ref<ToolOpt[]>([]);
    const tip = ref('');
    const tipOk = ref(false);
    const loading = ref(true);
    const saving = ref(false);
    const editingId = ref<number | null>(null);
    const formOpen = ref(false);
    const form = reactive({
      skillCode: '',
      name: '',
      enabled: true,
      systemPrompt: '',
      remark: '',
      toolCodes: [] as string[],
    });

    function setTip(msg: string, ok = false) {
      tip.value = msg;
      tipOk.value = ok;
    }

    function resetForm() {
      editingId.value = null;
      form.skillCode = '';
      form.name = '';
      form.enabled = true;
      form.systemPrompt = '';
      form.remark = '';
      form.toolCodes = [];
    }

    async function refresh() {
      skills.value = (await listSkills()) as SkillRow[];
      tools.value = (await listToolApis()) as ToolOpt[];
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
      if (!canUpdate()) {
        setTip('无技能配置更新权限', false);
        return;
      }
      resetForm();
      formOpen.value = true;
    }

    async function openEdit(row: SkillRow) {
      if (!canUpdate()) {
        setTip('无技能配置更新权限', false);
        return;
      }
      try {
        const detail = (await getSkill(row.id)) as SkillDetail;
        editingId.value = row.id;
        form.skillCode = String(detail.skill_code || '');
        form.name = String(detail.name || '');
        form.enabled = asBool(detail.enabled);
        form.systemPrompt = String(detail.system_prompt || '');
        form.remark = String(detail.remark || '');
        form.toolCodes = Array.isArray(detail.toolCodes) ? [...detail.toolCodes] : [];
        formOpen.value = true;
      } catch (e: unknown) {
        setTip(errMsg(e), false);
      }
    }

    function closeForm() {
      formOpen.value = false;
      resetForm();
    }

    function toggleTool(code: string) {
      const i = form.toolCodes.indexOf(code);
      if (i >= 0) form.toolCodes.splice(i, 1);
      else form.toolCodes.push(code);
    }

    async function save() {
      if (!canUpdate()) {
        setTip('无技能配置更新权限', false);
        return;
      }
      if (!form.skillCode.trim() || !form.name.trim()) {
        setTip('请填写编码与名称', false);
        return;
      }
      saving.value = true;
      try {
        const payload = {
          skillCode: form.skillCode.trim(),
          name: form.name.trim(),
          enabled: form.enabled,
          systemPrompt: form.systemPrompt,
          remark: form.remark,
          toolCodes: [...form.toolCodes],
        };
        if (editingId.value != null) {
          await updateSkill({ id: editingId.value, ...payload });
          setTip('已更新技能', true);
        } else {
          await createSkill(payload);
          setTip('已创建技能', true);
        }
        closeForm();
        await refresh();
      } catch (e: unknown) {
        setTip(errMsg(e), false);
      } finally {
        saving.value = false;
      }
    }

    return () =>
      h('div', { class: 'ai-config-panel' }, [
        h('div', { class: 'ai-config-panel-head' }, [
          h('div', { class: 'ai-config-panel-head-row' }, [
            h('h3', { class: 'ai-config-panel-title' }, '技能'),
            h(
              'button',
              {
                class: 'ai-config-btn ai-config-btn--primary',
                disabled: !canUpdate(),
                onClick: openCreate,
              },
              '新建技能',
            ),
          ]),
          h(
            'p',
            { class: 'ai-config-panel-desc' },
            '技能 = 系统提示词 + 绑定的 Tool。激活条件：绑定 Tool 对当前用户全部 available。',
          ),
        ]),
        tip.value
          ? h('div', { class: tipOk.value ? 'ai-config-banner is-ok' : 'ai-config-banner is-err' }, tip.value)
          : null,
        loading.value
          ? h('div', { class: 'ai-config-empty' }, '加载中…')
          : skills.value.length === 0
            ? h('div', { class: 'ai-config-empty' }, '暂无技能，请先新建并绑定已启用的接口。')
            : h(
                'div',
                { class: 'ai-config-card-list' },
                skills.value.map((s) =>
                  h('div', { key: s.id, class: 'ai-config-card' }, [
                    h('div', { class: 'ai-config-card-main' }, [
                      h('div', { class: 'ai-config-card-title-row' }, [
                        h('span', { class: 'ai-config-card-title' }, s.name),
                        h(
                          'span',
                          {
                            class: asBool(s.enabled) ? 'ai-config-tag is-on' : 'ai-config-tag is-off',
                          },
                          asBool(s.enabled) ? '启用' : '停用',
                        ),
                      ]),
                      h('div', { class: 'ai-config-card-meta' }, s.skill_code),
                      s.remark
                        ? h('div', { class: 'ai-config-card-sub' }, s.remark)
                        : null,
                    ]),
                    h('div', { class: 'ai-config-card-actions' }, [
                      h(
                        'button',
                        {
                          class: 'ai-config-btn',
                          disabled: !canUpdate(),
                          onClick: () => openEdit(s),
                        },
                        '编辑',
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
                    h('h3', editingId.value != null ? '编辑技能' : '新建技能'),
                    h('button', { class: 'ai-config-btn ai-config-btn--ghost', onClick: closeForm }, '关闭'),
                  ]),
                  h('div', { class: 'ai-config-form' }, [
                    h('div', { class: 'ai-config-field' }, [
                      h('label', { class: 'ai-config-label' }, '编码'),
                      h('input', {
                        class: 'ai-config-input',
                        value: form.skillCode,
                        disabled: editingId.value != null,
                        onInput: (e: Event) => {
                          form.skillCode = (e.target as HTMLInputElement).value;
                        },
                      }),
                    ]),
                    h('div', { class: 'ai-config-field' }, [
                      h('label', { class: 'ai-config-label' }, '名称'),
                      h('input', {
                        class: 'ai-config-input',
                        value: form.name,
                        onInput: (e: Event) => {
                          form.name = (e.target as HTMLInputElement).value;
                        },
                      }),
                    ]),
                    h('div', { class: 'ai-config-field' }, [
                      h('label', { class: 'ai-config-label' }, '启用'),
                      h('label', { class: 'ai-config-switch' }, [
                        h('input', {
                          type: 'checkbox',
                          checked: form.enabled,
                          onChange: (e: Event) => {
                            form.enabled = (e.target as HTMLInputElement).checked;
                          },
                        }),
                        h('span', form.enabled ? '已启用' : '已停用'),
                      ]),
                    ]),
                    h('div', { class: 'ai-config-field' }, [
                      h('label', { class: 'ai-config-label' }, '系统提示词'),
                      h('textarea', {
                        class: 'ai-config-input',
                        rows: 5,
                        value: form.systemPrompt,
                        onInput: (e: Event) => {
                          form.systemPrompt = (e.target as HTMLTextAreaElement).value;
                        },
                      }),
                    ]),
                    h('div', { class: 'ai-config-field' }, [
                      h('label', { class: 'ai-config-label' }, '备注'),
                      h('input', {
                        class: 'ai-config-input',
                        value: form.remark,
                        onInput: (e: Event) => {
                          form.remark = (e.target as HTMLInputElement).value;
                        },
                      }),
                    ]),
                    h('div', { class: 'ai-config-field' }, [
                      h('label', { class: 'ai-config-label' }, `绑定 Tool（已选 ${form.toolCodes.length}）`),
                      tools.value.length === 0
                        ? h('div', { class: 'ai-config-muted' }, '暂无已注册接口，请先到「接口」Tab 注册。')
                        : h(
                            'div',
                            { class: 'ai-config-check-grid' },
                            tools.value.map((t) =>
                              h('label', { class: 'ai-config-check-chip', key: t.tool_code }, [
                                h('input', {
                                  type: 'checkbox',
                                  checked: form.toolCodes.includes(t.tool_code),
                                  onChange: () => toggleTool(t.tool_code),
                                }),
                                h('span', [
                                  t.name || t.tool_code,
                                  asBool(t.enabled)
                                    ? null
                                    : h('em', { class: 'ai-config-muted' }, ' · 停用'),
                                ]),
                              ]),
                            ),
                          ),
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
