import {
  AiAgentModes,
  createAgent,
  getAgent,
  listAgents,
  listSkills,
  updateAgent,
} from '@ysmp/ai-assistant-core';
import { defineComponent, onMounted, reactive, ref, h } from 'vue';

import { asBool, errMsg } from '../configTabs';

interface AgentRow {
  id: number;
  agent_code: string;
  name: string;
  enabled?: boolean | number;
  access_permission?: string;
  agent_mode?: string;
  default_flag?: boolean | number;
  remark?: string;
}

interface SkillOpt {
  id: number;
  skill_code: string;
  name: string;
  enabled?: boolean | number;
}

interface AgentDetail {
  id?: number;
  agent_code?: string;
  name?: string;
  enabled?: boolean | number;
  system_prompt?: string;
  access_permission?: string;
  agent_mode?: string;
  default_flag?: boolean | number;
  remark?: string;
  skillIds?: number[];
}

/**
 * Agent 配置：列表 + 新建/编辑（模式、默认、权限码、绑定 Skill）。
 */
export const AgentPanel = defineComponent({
  name: 'AiAgentPanel',
  setup() {
    /** 能进入「助手配置」页即可维护；细粒度菜单已收敛删除 */
    const canUpdate = () => true;
    const agents = ref<AgentRow[]>([]);
    const skills = ref<SkillOpt[]>([]);
    const tip = ref('');
    const tipOk = ref(false);
    const loading = ref(true);
    const saving = ref(false);
    const editingId = ref<number | null>(null);
    const formOpen = ref(false);
    const form = reactive({
      agentCode: '',
      name: '',
      enabled: true,
      accessPermission: '',
      agentMode: AiAgentModes.GENERAL as string,
      systemPrompt: '',
      defaultFlag: false,
      remark: '',
      skillIds: [] as number[],
    });

    function setTip(msg: string, ok = false) {
      tip.value = msg;
      tipOk.value = ok;
    }

    function resetForm() {
      editingId.value = null;
      form.agentCode = '';
      form.name = '';
      form.enabled = true;
      form.accessPermission = '';
      form.agentMode = AiAgentModes.GENERAL;
      form.systemPrompt = '';
      form.defaultFlag = false;
      form.remark = '';
      form.skillIds = [];
    }

    async function refresh() {
      agents.value = (await listAgents()) as AgentRow[];
      skills.value = (await listSkills()) as SkillOpt[];
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
        setTip('无智能体配置更新权限', false);
        return;
      }
      resetForm();
      formOpen.value = true;
    }

    async function openEdit(row: AgentRow) {
      if (!canUpdate()) {
        setTip('无智能体配置更新权限', false);
        return;
      }
      try {
        const detail = (await getAgent(row.id)) as AgentDetail;
        editingId.value = row.id;
        form.agentCode = String(detail.agent_code || '');
        form.name = String(detail.name || '');
        form.enabled = asBool(detail.enabled);
        form.accessPermission = String(detail.access_permission || '');
        form.agentMode = String(detail.agent_mode || AiAgentModes.GENERAL);
        form.systemPrompt = String(detail.system_prompt || '');
        form.defaultFlag = asBool(detail.default_flag);
        form.remark = String(detail.remark || '');
        form.skillIds = Array.isArray(detail.skillIds)
          ? detail.skillIds.map((x) => Number(x))
          : [];
        formOpen.value = true;
      } catch (e: unknown) {
        setTip(errMsg(e), false);
      }
    }

    function closeForm() {
      formOpen.value = false;
      resetForm();
    }

    function toggleSkill(id: number) {
      const i = form.skillIds.indexOf(id);
      if (i >= 0) form.skillIds.splice(i, 1);
      else form.skillIds.push(id);
    }

    async function save() {
      if (!canUpdate()) {
        setTip('无智能体配置更新权限', false);
        return;
      }
      if (!form.agentCode.trim() || !form.name.trim()) {
        setTip('请填写编码与名称', false);
        return;
      }
      saving.value = true;
      try {
        const payload = {
          agentCode: form.agentCode.trim(),
          name: form.name.trim(),
          enabled: form.enabled,
          accessPermission: form.accessPermission.trim() || undefined,
          agentMode: form.agentMode,
          systemPrompt: form.systemPrompt,
          defaultFlag: form.defaultFlag,
          remark: form.remark,
          skillIds: [...form.skillIds],
        };
        if (editingId.value != null) {
          await updateAgent({ id: editingId.value, ...payload });
          setTip('已更新智能体', true);
        } else {
          await createAgent(payload);
          setTip('已创建智能体', true);
        }
        closeForm();
        await refresh();
      } catch (e: unknown) {
        setTip(errMsg(e), false);
      } finally {
        saving.value = false;
      }
    }

    function modeLabel(mode?: string) {
      return mode === AiAgentModes.SCENARIO ? '场景' : '通用';
    }

    return () =>
      h('div', { class: 'ai-config-panel' }, [
        h('div', { class: 'ai-config-panel-head' }, [
          h('div', { class: 'ai-config-panel-head-row' }, [
            h('h3', { class: 'ai-config-panel-title' }, '智能体'),
            h(
              'button',
              {
                class: 'ai-config-btn ai-config-btn--primary',
                disabled: !canUpdate(),
                onClick: openCreate,
              },
              '新建智能体',
            ),
          ]),
          h(
            'p',
            { class: 'ai-config-panel-desc' },
            '智能体绑定多个技能，并控制访问权限与模式（通用可调用全部可用 Tool；场景仅用已激活技能内 Tool）。',
          ),
        ]),
        tip.value
          ? h('div', { class: tipOk.value ? 'ai-config-banner is-ok' : 'ai-config-banner is-err' }, tip.value)
          : null,
        loading.value
          ? h('div', { class: 'ai-config-empty' }, '加载中…')
          : agents.value.length === 0
            ? h('div', { class: 'ai-config-empty' }, '暂无智能体，请先准备技能后再创建。')
            : h(
                'div',
                { class: 'ai-config-card-list' },
                agents.value.map((a) =>
                  h('div', { key: a.id, class: 'ai-config-card' }, [
                    h('div', { class: 'ai-config-card-main' }, [
                      h('div', { class: 'ai-config-card-title-row' }, [
                        h('span', { class: 'ai-config-card-title' }, a.name),
                        h(
                          'span',
                          {
                            class: asBool(a.enabled) ? 'ai-config-tag is-on' : 'ai-config-tag is-off',
                          },
                          asBool(a.enabled) ? '启用' : '停用',
                        ),
                        asBool(a.default_flag)
                          ? h('span', { class: 'ai-config-tag is-primary' }, '默认')
                          : null,
                        h('span', { class: 'ai-config-tag' }, modeLabel(a.agent_mode)),
                      ]),
                      h(
                        'div',
                        { class: 'ai-config-card-meta' },
                        `${a.agent_code} · ${a.access_permission || '—'}`,
                      ),
                    ]),
                    h('div', { class: 'ai-config-card-actions' }, [
                      h(
                        'button',
                        {
                          class: 'ai-config-btn',
                          disabled: !canUpdate(),
                          onClick: () => openEdit(a),
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
                    h('h3', editingId.value != null ? '编辑智能体' : '新建智能体'),
                    h('button', { class: 'ai-config-btn ai-config-btn--ghost', onClick: closeForm }, '关闭'),
                  ]),
                  h('div', { class: 'ai-config-form' }, [
                    h('div', { class: 'ai-config-field' }, [
                      h('label', { class: 'ai-config-label' }, '编码'),
                      h('input', {
                        class: 'ai-config-input',
                        value: form.agentCode,
                        disabled: editingId.value != null,
                        onInput: (e: Event) => {
                          form.agentCode = (e.target as HTMLInputElement).value;
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
                      h('label', { class: 'ai-config-label' }, '访问权限码'),
                      h('input', {
                        class: 'ai-config-input',
                        placeholder: '空则 ai:agent:{编码}',
                        value: form.accessPermission,
                        onInput: (e: Event) => {
                          form.accessPermission = (e.target as HTMLInputElement).value;
                        },
                      }),
                      h('div', { class: 'ai-config-hint' }, '用户需具备该权限码才能选用此智能体。'),
                    ]),
                    h('div', { class: 'ai-config-field' }, [
                      h('label', { class: 'ai-config-label' }, '模式'),
                      h(
                        'select',
                        {
                          class: 'ai-config-input',
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
                      h('label', { class: 'ai-config-label' }, '系统提示词'),
                      h('textarea', {
                        class: 'ai-config-input',
                        rows: 4,
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
                      h('label', { class: 'ai-config-label' }, `绑定 Skill（已选 ${form.skillIds.length}）`),
                      skills.value.length === 0
                        ? h('div', { class: 'ai-config-muted' }, '暂无技能，请先到「技能」Tab 创建。')
                        : h(
                            'div',
                            { class: 'ai-config-check-grid' },
                            skills.value.map((s) =>
                              h('label', { class: 'ai-config-check-chip', key: s.id }, [
                                h('input', {
                                  type: 'checkbox',
                                  checked: form.skillIds.includes(s.id),
                                  onChange: () => toggleSkill(s.id),
                                }),
                                h('span', [
                                  `${s.name}（${s.skill_code}）`,
                                  asBool(s.enabled)
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
