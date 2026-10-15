import {
  AiPermissions,
  AiWriteConfirm,
  chatStream,
  deleteChatSession,
  getAssistantCapabilities,
  listAvailableAgents,
  listChatMessages,
  listChatSessions,
  useAiRuntime,
  type AiCapabilityBundle,
  type AiChatSessionSummary,
  type AiClarifyForm,
  type AiContextUsage,
  type AiToolDataRef,
} from '@ysmp/ai-assistant-core';
import { defineComponent, ref, h, watch, onMounted, computed, nextTick } from 'vue';
import { detectMention, type MentionKind } from './mention';
import { renderMarkdown } from './markdownRender';
import { initClarifyValues, renderClarifyForm } from './ClarifyFormPanel';
import { splitContentWithCharts } from './chartParse';
import { renderChart } from './ChartPanel';
import { ToolResultPanel } from './ToolResultPanel';
import { renderWriteConfirmBar } from './WriteConfirmBar';

interface Msg {
  role: 'user' | 'assistant' | 'system';
  content: string;
  clarify?: AiClarifyForm;
  clarifyValues?: Record<string, string | string[]>;
  clarifyAnswered?: boolean;
  clarifyError?: string;
}

interface PickItem {
  code: string;
  label: string;
}

function formatTokens(n: number | undefined): string {
  if (n == null || Number.isNaN(n)) return '0';
  if (n >= 1000) return `${(n / 1000).toFixed(1)}K`;
  return String(n);
}

/**
 * 浮动对话入口：会话历史 + 上下文用量 + @agent / @skill / @tool。
 */
export const AiAssistantFloat = defineComponent({
  name: 'AiAssistantFloat',
  setup() {
    const open = ref(false);
    const input = ref('');
    const loading = ref(false);
    const messages = ref<Msg[]>([]);
    const sessionId = ref<string | undefined>();
    const sessions = ref<AiChatSessionSummary[]>([]);
    const usage = ref<AiContextUsage | null>(null);
    const showUsage = ref(false);
    const toolDataList = ref<AiToolDataRef[]>([]);
    const pendingWriteConfirms = ref<Array<{ id: string; name: string; arguments?: string }>>([]);
    const lastUserText = ref('');
    const chatBodyEl = ref<HTMLElement | null>(null);

    const agentCode = ref<string | undefined>();
    const skillCode = ref<string | undefined>();
    const toolCode = ref<string | undefined>();
    const agentLabel = ref('');
    const skillLabel = ref('');
    const toolLabel = ref('');

    const agents = ref<PickItem[]>([]);
    const capabilities = ref<AiCapabilityBundle | null>(null);

    const mentionKind = ref<MentionKind | null>(null);
    const mentionStart = ref(-1);
    const pickerFilter = ref('');
    const showPicker = ref(false);

    let canUse = true;
    try {
      canUse = useAiRuntime().hasPermission(AiPermissions.ASSISTANT_QUERY);
    } catch {
      canUse = false;
    }

    async function loadAgents() {
      try {
        const list = (await listAvailableAgents()) as Array<Record<string, unknown>>;
        agents.value = list.map((a) => ({
          code: String(a.agent_code ?? a.agentCode ?? ''),
          label: String(a.name ?? a.agent_code ?? ''),
        }));
        if (!agentCode.value && agents.value.length) {
          agentCode.value = agents.value[0].code;
          agentLabel.value = agents.value[0].label;
        }
      } catch {
        agents.value = [];
      }
    }

    async function loadCapabilities() {
      if (!agentCode.value) {
        capabilities.value = null;
        return;
      }
      try {
        capabilities.value = (await getAssistantCapabilities(
          agentCode.value,
        )) as AiCapabilityBundle;
      } catch {
        capabilities.value = null;
      }
    }

    async function loadSessions() {
      try {
        sessions.value = await listChatSessions();
      } catch {
        sessions.value = [];
      }
    }

    async function openSession(sid: string) {
      if (!sid || loading.value) return;
      sessionId.value = sid;
      usage.value = null;
      toolDataList.value = [];
      pendingWriteConfirms.value = [];
      lastUserText.value = '';
      try {
        const rows = await listChatMessages(sid);
        messages.value = rows
          .filter((r) => r.role === 'user' || r.role === 'assistant')
          .map((r) => ({
            role: r.role as 'user' | 'assistant',
            content: r.content || '',
          }));
      } catch {
        messages.value = [];
      }
    }

    function newSession() {
      sessionId.value = undefined;
      messages.value = [];
      usage.value = null;
      toolDataList.value = [];
      pendingWriteConfirms.value = [];
      lastUserText.value = '';
    }

    async function removeSession(sid: string, e: Event) {
      e.stopPropagation();
      try {
        await deleteChatSession(sid);
        if (sessionId.value === sid) {
          newSession();
        }
        await loadSessions();
      } catch {
        // ignore
      }
    }

    onMounted(() => {
      if (canUse) {
        void loadAgents().then(loadCapabilities);
      }
    });

    watch(agentCode, () => {
      skillCode.value = undefined;
      skillLabel.value = '';
      toolCode.value = undefined;
      toolLabel.value = '';
      void loadCapabilities();
    });

    const pickerItems = computed((): PickItem[] => {
      const q = pickerFilter.value.toLowerCase();
      let items: PickItem[] = [];
      if (mentionKind.value === 'agent') {
        items = agents.value;
      } else if (mentionKind.value === 'skill') {
        items = (capabilities.value?.activatedSkills ?? []).map((s) => ({
          code: s.skillCode,
          label: s.name || s.skillCode,
        }));
      } else if (mentionKind.value === 'tool') {
        items = (capabilities.value?.callableTools ?? []).map((t) => ({
          code: t.toolCode,
          label: t.name || t.toolCode,
        }));
      }
      if (!q) return items;
      return items.filter(
        (i) => i.code.toLowerCase().includes(q) || i.label.toLowerCase().includes(q),
      );
    });

    const usageSegments = computed(() => {
      const u = usage.value;
      if (!u) return [];
      return [
        { key: 'system', label: 'System prompt', value: u.systemPrompt, color: '#8b8b8b' },
        { key: 'skills', label: 'Skills', value: u.skillPrompts, color: '#a67c52' },
        { key: 'tools', label: 'Tool definitions', value: u.toolDefinitions, color: '#7c5cbf' },
        {
          key: 'summarized',
          label: 'Summarized conversation',
          value: u.summarizedConversation || 0,
          color: '#d4818a',
        },
        { key: 'conversation', label: 'Conversation', value: u.conversation, color: '#c44d4d' },
        { key: 'current', label: 'Current message', value: u.currentMessage, color: '#3d8bfd' },
      ].filter((s) => s.value > 0);
    });

    function onInputChange(e: Event) {
      const el = e.target as HTMLInputElement;
      input.value = el.value;
      const cursor = el.selectionStart ?? el.value.length;
      const before = el.value.slice(0, cursor);
      const hit = detectMention(before);
      if (!hit) {
        showPicker.value = false;
        mentionKind.value = null;
        return;
      }
      mentionKind.value = hit.kind;
      mentionStart.value = hit.start;
      pickerFilter.value = '';
      showPicker.value = true;
    }

    function pickItem(item: PickItem) {
      if (mentionKind.value === 'agent') {
        agentCode.value = item.code;
        agentLabel.value = item.label;
      } else if (mentionKind.value === 'skill') {
        skillCode.value = item.code;
        skillLabel.value = item.label;
      } else if (mentionKind.value === 'tool') {
        toolCode.value = item.code;
        toolLabel.value = item.label;
      }
      if (mentionStart.value >= 0) {
        const before = input.value.slice(0, mentionStart.value);
        const afterCursor = input.value.slice(mentionStart.value);
        const rest = afterCursor.replace(/^@[^\s]*/, '');
        input.value = (before + rest).replace(/\s{2,}/g, ' ').trimStart();
      }
      showPicker.value = false;
      mentionKind.value = null;
    }

    function clearChip(kind: MentionKind) {
      if (kind === 'agent') {
        agentCode.value = undefined;
        agentLabel.value = '';
      } else if (kind === 'skill') {
        skillCode.value = undefined;
        skillLabel.value = '';
      } else {
        toolCode.value = undefined;
        toolLabel.value = '';
      }
    }

    function scrollChatToBottom() {
      void nextTick(() => {
        const el = chatBodyEl.value;
        if (el) el.scrollTop = el.scrollHeight;
      });
    }

    async function sendMessage(
      text: string,
      opts?: { confirmWriteTools?: boolean; hideUserBubble?: boolean },
    ) {
      const body = text.trim();
      if (!body || loading.value) return;
      if (!opts?.confirmWriteTools) {
        lastUserText.value = body;
        pendingWriteConfirms.value = [];
      }
      if (opts?.hideUserBubble) {
        messages.value.push({ role: 'system', content: '已确认，正在执行写操作…' });
      } else {
        messages.value.push({ role: 'user', content: body });
      }
      loading.value = true;
      scrollChatToBottom();

      let assistantIdx = messages.value.length;
      messages.value.push({ role: 'assistant', content: '' });
      const startedTools = new Map<string, string>();

      const insertBeforeAssistant = (msg: Msg) => {
        messages.value.splice(assistantIdx, 0, msg);
        assistantIdx += 1;
        messages.value = [...messages.value];
        scrollChatToBottom();
      };

      try {
        await chatStream(
          {
            message: body,
            sessionId: sessionId.value,
            agentCode: agentCode.value,
            skillCode: skillCode.value,
            toolCode: toolCode.value,
            confirmWriteTools: opts?.confirmWriteTools === true ? true : undefined,
          },
          {
            onMeta: (sid) => {
              if (sid) sessionId.value = sid;
            },
            onUsage: (u) => {
              usage.value = u;
            },
            onClarify: (form) => {
              const cur = messages.value[assistantIdx];
              if (cur && cur.role === 'assistant' && form?.fields?.length) {
                cur.clarify = form;
                cur.clarifyValues = initClarifyValues(form);
                cur.clarifyAnswered = false;
                messages.value = [...messages.value];
                scrollChatToBottom();
              }
            },
            onToolCall: (p) => {
              startedTools.set(p.id, p.name);
              insertBeforeAssistant({
                role: 'system',
                content: `调用工具 ${p.name}…`,
              });
            },
            onToolConfirm: (p) => {
              const next = pendingWriteConfirms.value.filter((x) => x.id !== p.id);
              next.push({ id: p.id, name: p.name, arguments: p.arguments });
              pendingWriteConfirms.value = next;
              const callLabel = `调用工具 ${startedTools.get(p.id) || p.name}…`;
              const cleaned = messages.value.filter(
                (m, i) => !(i < assistantIdx && m.role === 'system' && m.content === callLabel),
              );
              const removed = messages.value.length - cleaned.length;
              if (removed > 0) {
                assistantIdx -= removed;
                messages.value = cleaned;
              }
              insertBeforeAssistant({
                role: 'system',
                content: `写操作待确认：${p.name}`,
              });
            },
            onToolResult: (p) => {
              const preview = p.preview || '';
              if (preview.includes(AiWriteConfirm.STATUS_NEED_CONFIRM)) {
                return;
              }
              const callLabel = `调用工具 ${startedTools.get(p.id) || p.name}…`;
              const cleaned = messages.value.filter(
                (m, i) => !(i < assistantIdx && m.role === 'system' && m.content === callLabel),
              );
              const removed = messages.value.length - cleaned.length;
              if (removed > 0) {
                assistantIdx -= removed;
                messages.value = cleaned;
              }
              insertBeforeAssistant({
                role: 'system',
                content: `工具 ${p.name} 完成：${preview.slice(0, 160)}`,
              });
            },
            onToolData: (data) => {
              if (!data?.refId) return;
              const next = toolDataList.value.filter((d) => d.refId !== data.refId);
              next.push(data);
              toolDataList.value = next;
              scrollChatToBottom();
            },
            onDelta: (chunk) => {
              const cur = messages.value[assistantIdx];
              if (cur && cur.role === 'assistant') {
                cur.content += chunk;
                messages.value = [...messages.value];
                scrollChatToBottom();
              }
            },
            onDone: (full) => {
              const cur = messages.value[assistantIdx];
              if (cur && cur.role === 'assistant' && !cur.content && full) {
                cur.content = full;
                messages.value = [...messages.value];
              }
              scrollChatToBottom();
              void loadSessions();
            },
            onError: (msg) => {
              const cur = messages.value[assistantIdx];
              if (cur && cur.role === 'assistant') {
                cur.content = cur.content || msg;
                messages.value = [...messages.value];
              } else {
                messages.value.push({ role: 'assistant', content: msg });
              }
              scrollChatToBottom();
            },
          },
        );
      } catch (e: unknown) {
        const cur = messages.value[assistantIdx];
        const err = e instanceof Error ? e.message : String(e);
        if (cur && cur.role === 'assistant') {
          cur.content = cur.content || err;
          messages.value = [...messages.value];
        } else {
          messages.value.push({ role: 'assistant', content: err });
        }
        scrollChatToBottom();
      } finally {
        loading.value = false;
      }
    }

    async function confirmWrite() {
      const text = lastUserText.value.trim();
      if (!text || loading.value) return;
      pendingWriteConfirms.value = [];
      await sendMessage(text, { confirmWriteTools: true, hideUserBubble: true });
    }

    function cancelWrite() {
      pendingWriteConfirms.value = [];
      messages.value.push({
        role: 'system',
        content: '已取消写操作，未执行任何变更。',
      });
      scrollChatToBottom();
    }

    async function send() {
      const text = input.value.trim();
      if (!text || loading.value) return;
      input.value = '';
      showPicker.value = false;
      await sendMessage(text);
    }

    function submitClarify(msgIdx: number, reply: string) {
      const cur = messages.value[msgIdx];
      if (!cur || cur.clarifyAnswered || loading.value) return;
      cur.clarifyAnswered = true;
      cur.clarifyError = undefined;
      messages.value = [...messages.value];
      void sendMessage(reply);
    }

    return () => {
      if (!canUse) return null;
      const u = usage.value;
      const totalSeg = usageSegments.value.reduce((s, x) => s + x.value, 0) || 1;

      return h('div', { class: 'ai-assistant-root' }, [
        open.value
          ? h('div', { class: 'ai-panel ai-panel-wide' }, [
              h('div', { class: 'ai-panel-header' }, [
                h('span', 'AI 助手'),
                h('div', { class: 'ai-panel-header-actions' }, [
                  u
                    ? h(
                        'button',
                        {
                          class: 'ai-usage-btn',
                          title: '上下文用量',
                          onClick: () => {
                            showUsage.value = !showUsage.value;
                          },
                        },
                        `${u.percentFull}%`,
                      )
                    : null,
                  h(
                    'button',
                    {
                      style: 'border:none;background:transparent;cursor:pointer',
                      onClick: () => {
                        open.value = false;
                      },
                    },
                    '关闭',
                  ),
                ]),
              ]),
              showUsage.value && u
                ? h('div', { class: 'ai-usage-panel' }, [
                    h('div', { class: 'ai-usage-title' }, [
                      h('span', 'Context Usage'),
                      h('span', { class: 'ai-usage-meta' }, [
                        `${u.percentFull}% Full · ~${formatTokens(u.estimatedTotal)} / ${formatTokens(u.contextWindow)}`,
                      ]),
                    ]),
                    h(
                      'div',
                      { class: 'ai-usage-bar' },
                      usageSegments.value.map((s) =>
                        h('div', {
                          class: 'ai-usage-seg',
                          style: {
                            width: `${Math.max(2, (s.value / totalSeg) * 100)}%`,
                            background: s.color,
                          },
                          title: `${s.label}: ${s.value}`,
                        }),
                      ),
                    ),
                    h(
                      'ul',
                      { class: 'ai-usage-list' },
                      usageSegments.value.map((s) =>
                        h('li', [
                          h('i', { style: { background: s.color } }),
                          h('span', s.label),
                          h('b', formatTokens(s.value)),
                        ]),
                      ),
                    ),
                    u.promptTokens != null
                      ? h(
                          'div',
                          { class: 'ai-usage-api' },
                          `模型返回：prompt ${u.promptTokens} · completion ${u.completionTokens ?? 0} · total ${u.totalTokens ?? 0}`,
                        )
                      : null,
                  ])
                : null,
              h('div', { class: 'ai-panel-main' }, [
                h('div', { class: 'ai-session-side' }, [
                  h(
                    'button',
                    { class: 'ai-session-new', onClick: newSession },
                    '新对话',
                  ),
                  h(
                    'ul',
                    { class: 'ai-session-list' },
                    sessions.value.map((s) =>
                      h(
                        'li',
                        {
                          class: [
                            'ai-session-item',
                            s.sessionId === sessionId.value ? 'active' : '',
                          ],
                          onClick: () => void openSession(s.sessionId),
                        },
                        [
                          h('span', { class: 'ai-session-title' }, s.title || '未命名会话'),
                          h(
                            'button',
                            {
                              class: 'ai-session-del',
                              onClick: (e: Event) => void removeSession(s.sessionId, e),
                            },
                            '×',
                          ),
                        ],
                      ),
                    ),
                  ),
                ]),
                h('div', { class: 'ai-chat-col' }, [
                  h('div', { class: 'ai-chips' }, [
                    agentCode.value
                      ? h('span', { class: 'ai-chip' }, [
                          `Agent:${agentLabel.value || agentCode.value}`,
                          h(
                            'button',
                            {
                              class: 'ai-chip-x',
                              onClick: () => clearChip('agent'),
                            },
                            '×',
                          ),
                        ])
                      : null,
                    skillCode.value
                      ? h('span', { class: 'ai-chip' }, [
                          `Skill:${skillLabel.value || skillCode.value}`,
                          h(
                            'button',
                            {
                              class: 'ai-chip-x',
                              onClick: () => clearChip('skill'),
                            },
                            '×',
                          ),
                        ])
                      : null,
                    toolCode.value
                      ? h('span', { class: 'ai-chip' }, [
                          `Tool:${toolLabel.value || toolCode.value}`,
                          h(
                            'button',
                            {
                              class: 'ai-chip-x',
                              onClick: () => clearChip('tool'),
                            },
                            '×',
                          ),
                        ])
                      : null,
                  ]),
                  h(
                    'div',
                    {
                      class: 'ai-panel-body',
                      ref: chatBodyEl,
                    },
                    messages.value.map((m, idx) => {
                      const isUser = m.role === 'user';
                      const isSystem = m.role === 'system';
                      const label = isUser ? '我' : isSystem ? '系统' : '助手';
                      return h(
                        'div',
                        {
                          class: [
                            'ai-msg',
                            isUser ? 'ai-msg-user' : '',
                            isSystem ? 'ai-msg-system' : '',
                            !isUser && !isSystem ? 'ai-msg-assistant' : '',
                          ],
                        },
                        [
                          h('div', { class: 'ai-msg-role' }, label),
                          isSystem || isUser
                            ? h('div', { class: 'ai-msg-plain' }, m.content)
                            : h(
                                'div',
                                { class: 'ai-msg-md' },
                                splitContentWithCharts(m.content || '').map((seg) =>
                                  seg.kind === 'chart'
                                    ? renderChart(seg.chart)
                                    : h('div', {
                                        class: 'ai-md',
                                        innerHTML: renderMarkdown(seg.text),
                                      }),
                                ),
                              ),
                          m.clarify && m.clarifyValues
                            ? renderClarifyForm({
                                form: m.clarify,
                                values: m.clarifyValues,
                                answered: !!m.clarifyAnswered,
                                error: m.clarifyError,
                                onError: (err) => {
                                  m.clarifyError = err;
                                  messages.value = [...messages.value];
                                },
                                onChange: () => {
                                  messages.value = [...messages.value];
                                },
                                onSubmit: (reply) => submitClarify(idx, reply),
                              })
                            : null,
                        ],
                      );
                    }).concat(
                      toolDataList.value.map((d) =>
                        h(ToolResultPanel, { key: d.refId, data: d }),
                      ),
                    ),
                  ),
                  renderWriteConfirmBar({
                    items: pendingWriteConfirms.value,
                    loading: loading.value,
                    onConfirm: () => void confirmWrite(),
                    onCancel: cancelWrite,
                  }),
                  h('div', { class: 'ai-panel-footer ai-panel-footer-col' }, [
                    showPicker.value
                      ? h('div', { class: 'ai-mention-picker' }, [
                          h(
                            'div',
                            { class: 'ai-mention-title' },
                            mentionKind.value === 'agent'
                              ? '选择 Agent'
                              : mentionKind.value === 'skill'
                                ? '选择 Skill'
                                : '选择 Tool',
                          ),
                          h('input', {
                            class: 'ai-mention-filter',
                            placeholder: '过滤…',
                            value: pickerFilter.value,
                            onInput: (e: Event) => {
                              pickerFilter.value = (e.target as HTMLInputElement).value;
                            },
                          }),
                          h(
                            'ul',
                            pickerItems.value.length
                              ? pickerItems.value.map((it) =>
                                  h(
                                    'li',
                                    {
                                      onClick: () => pickItem(it),
                                    },
                                    `${it.label} (${it.code})`,
                                  ),
                                )
                              : [h('li', { class: 'ai-mention-empty' }, '无可用项')],
                          ),
                        ])
                      : null,
                    h('div', { class: 'ai-panel-footer-row' }, [
                      h('input', {
                        value: input.value,
                        placeholder: loading.value
                          ? '思考中…'
                          : '输入问题，或 @agent / @skill / @tool',
                        disabled: loading.value,
                        onInput: onInputChange,
                        onKeydown: (e: KeyboardEvent) => {
                          if (e.key === 'Escape') {
                            showPicker.value = false;
                            return;
                          }
                          if (e.key === 'Enter' && !showPicker.value) send();
                        },
                      }),
                      h('button', { disabled: loading.value, onClick: send }, '发送'),
                    ]),
                  ]),
                ]),
              ]),
            ])
          : null,
        h(
          'button',
          {
            class: 'ai-float-btn',
            onClick: () => {
              open.value = !open.value;
              if (open.value) {
                void loadAgents().then(loadCapabilities);
                void loadSessions();
              }
            },
          },
          'AI',
        ),
      ]);
    };
  },
});
