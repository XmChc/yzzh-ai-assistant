import {
  listOpenApiCatalog,
  listToolApis,
  registerOpenApiTool,
  resyncToolPermissions,
  resyncToolSchemas,
} from '@ysmp/ai-assistant-core';
import { defineComponent, onMounted, ref, h } from 'vue';

interface ToolRow {
  id: number;
  tool_code: string;
  name: string;
  enabled: boolean | number;
  http_method?: string;
  path?: string;
  module?: string;
  param_schema_json?: string | null;
  response_schema_json?: string | null;
  /** 只读：由宿主 @PreAuthorize / SPI 同步，禁止手填 */
  required_permissions?: string | null;
}

interface CatalogItem {
  method: string;
  path: string;
  summary?: string;
  operationId?: string;
  module?: string;
  moduleLabel?: string;
  suggested?: boolean;
  registered?: boolean;
}

interface CatalogModule {
  module: string;
  moduleLabel?: string;
  items: CatalogItem[];
}

/**
 * Tool / OpenAPI 勾选默认页：按功能模块分组，注册后启用即进入白名单。
 */
export const ToolApiPage = defineComponent({
  name: 'AiToolApiPage',
  setup() {
    const tools = ref<ToolRow[]>([]);
    const catalog = ref<CatalogModule[]>([]);
    const tip = ref('');
    const onlySuggested = ref(true);
    const expanded = ref<Record<string, boolean>>({});
    const schemaOpen = ref<Record<number, boolean>>({});

    async function refresh() {
      tools.value = (await listToolApis()) as ToolRow[];
      const raw = await listOpenApiCatalog();
      // 兼容旧版扁平列表
      if (Array.isArray(raw) && raw.length > 0 && !('items' in (raw[0] as object))) {
        const flat = raw as CatalogItem[];
        const map = new Map<string, CatalogModule>();
        for (const c of flat) {
          const key = c.module || '未分类';
          let m = map.get(key);
          if (!m) {
            m = { module: key, moduleLabel: c.moduleLabel || key, items: [] };
            map.set(key, m);
          }
          m.items.push(c);
        }
        catalog.value = [...map.values()];
      } else {
        catalog.value = (raw as CatalogModule[]) || [];
      }
      for (const m of catalog.value) {
        if (expanded.value[m.module] === undefined) {
          expanded.value[m.module] = false;
        }
      }
    }

    onMounted(async () => {
      try {
        await refresh();
      } catch (e: unknown) {
        tip.value = e instanceof Error ? e.message : String(e);
      }
    });

    async function register(item: CatalogItem) {
      tip.value = '';
      try {
        await registerOpenApiTool({
          method: item.method,
          path: item.path,
          summary: item.summary,
          operationId: item.operationId,
          module: item.module,
        });
        tip.value = `已注册 ${item.method} ${item.path}`;
        await refresh();
      } catch (e: unknown) {
        tip.value = e instanceof Error ? e.message : String(e);
      }
    }

    async function syncPermissions() {
      tip.value = '';
      try {
        const n = await resyncToolPermissions();
        tip.value = `已从宿主同步 ${n} 个 Tool 的权限码（非手填）`;
        await refresh();
      } catch (e: unknown) {
        tip.value = e instanceof Error ? e.message : String(e);
      }
    }

    async function syncSchemas() {
      tip.value = '';
      try {
        const n = await resyncToolSchemas();
        tip.value = `已从 OpenAPI 同步 ${n} 个 Tool 的入参/出参 Schema（非手填）`;
        await refresh();
      } catch (e: unknown) {
        tip.value = e instanceof Error ? e.message : String(e);
      }
    }

    function formatPerms(raw: string | null | undefined) {
      if (raw == null || raw === '' || raw === '[]' || raw === 'null') {
        return '（尚未同步到权限码，请点「同步权限码」）';
      }
      try {
        const arr = JSON.parse(raw) as unknown;
        if (Array.isArray(arr) && arr.length) {
          return arr.map(String).join('、');
        }
        if (Array.isArray(arr)) {
          return '（空：宿主接口可能未声明 @PreAuthorize）';
        }
      } catch {
        // plain string
      }
      return String(raw);
    }

    function visibleItems(items: CatalogItem[]) {
      return onlySuggested.value ? items.filter((i) => i.suggested) : items;
    }

    function enabledLabel(v: boolean | number | undefined) {
      return v === true || v === 1 ? '启用' : '停用';
    }

    function prettyJson(raw: string | null | undefined) {
      if (raw == null || raw === '') {
        return '(空)';
      }
      try {
        return JSON.stringify(JSON.parse(raw), null, 2);
      } catch {
        return raw;
      }
    }

    function hasSchema(t: ToolRow) {
      return !!(t.param_schema_json || t.response_schema_json);
    }

    return () =>
      h('div', { class: 'ai-page' }, [
        h('h2', 'AI 接口（Tool）配置'),
        tip.value ? h('p', tip.value) : null,
        h('p', { style: 'color:#666;font-size:13px;line-height:1.55' }, [
          '从 OpenAPI 勾选接口注册并启用后进入白名单。',
          '权限码与入参/出参 Schema ',
          h('strong', '仅允许从宿主同步'),
          '（注册时自动写入；宿主改注解/OpenAPI 后点下方同步）。',
          h('strong', '禁止在本页手填或改写权限码 / Schema'),
          '。',
        ]),
        h('div', { class: 'actions', style: 'margin-bottom:12px;display:flex;gap:8px;flex-wrap:wrap' }, [
          h('button', { onClick: syncPermissions }, '同步权限码（从宿主 @PreAuthorize）'),
          h('button', { onClick: syncSchemas }, '同步 Schema（从 OpenAPI）'),
        ]),
        h('h3', '已注册（启用即生效 · 只读展示同步结果）'),
        h(
          'ul',
          tools.value.map((t) =>
            h('li', { key: t.id, style: 'margin-bottom:12px' }, [
              h(
                'div',
                `${t.module || '-'} | ${t.tool_code} | ${t.name} | ${enabledLabel(t.enabled)} | ${t.http_method || ''} ${t.path || ''}`,
              ),
              h(
                'div',
                {
                  style: 'margin-top:4px;font-size:12px;color:#555',
                  title: '只读：由同步写入，不可手填',
                },
                `权限码：${formatPerms(t.required_permissions)}`,
              ),
              hasSchema(t)
                ? h(
                    'button',
                    {
                      style: 'margin-top:4px;font-size:12px',
                      onClick: () => {
                        schemaOpen.value = {
                          ...schemaOpen.value,
                          [t.id]: !schemaOpen.value[t.id],
                        };
                      },
                    },
                    schemaOpen.value[t.id] ? '收起 Schema（只读）' : '查看 Schema（只读）',
                  )
                : h(
                    'span',
                    { style: 'margin-left:8px;color:#888;font-size:12px' },
                    '（无 Schema，请点「同步 Schema」）',
                  ),
              schemaOpen.value[t.id]
                ? h('div', { style: 'display:flex;gap:12px;flex-wrap:wrap;margin-top:6px' }, [
                    h('pre', {
                      style:
                        'flex:1;min-width:240px;max-height:220px;overflow:auto;background:#f7f7f7;padding:8px;font-size:11px',
                    }, `入参 parameters（只读）:\n${prettyJson(t.param_schema_json)}`),
                    h('pre', {
                      style:
                        'flex:1;min-width:240px;max-height:220px;overflow:auto;background:#f7f7f7;padding:8px;font-size:11px',
                    }, `出参 response（只读）:\n${prettyJson(t.response_schema_json)}`),
                  ])
                : null,
            ]),
          ),
        ),
        h('h3', 'OpenAPI 可勾选（按模块）'),
        h('label', { style: 'display:block;margin-bottom:12px' }, [
          h('input', {
            type: 'checkbox',
            checked: onlySuggested.value,
            onChange: (e: Event) => {
              onlySuggested.value = (e.target as HTMLInputElement).checked;
            },
          }),
          ' 仅显示建议勾选（GET，或 path 含 page/list/query 等的 POST）',
        ]),
        ...catalog.value.map((mod) => {
          const items = visibleItems(mod.items || []);
          if (items.length === 0) {
            return null;
          }
          const open = expanded.value[mod.module];
          return h('div', { key: mod.module, style: 'margin-bottom:16px;border-bottom:1px solid #eee;padding-bottom:8px' }, [
            h(
              'button',
              {
                style: 'background:none;border:none;cursor:pointer;font-weight:600;padding:0;font-size:14px',
                onClick: () => {
                  expanded.value = { ...expanded.value, [mod.module]: !open };
                },
              },
              `${open ? '▼' : '▶'} ${mod.moduleLabel || mod.module}（${items.length}）`,
            ),
            open
              ? h(
                  'ul',
                  { style: 'margin-top:8px' },
                  items.map((c) =>
                    h('li', { style: 'margin-bottom:8px' }, [
                      h(
                        'span',
                        {
                          style: c.suggested
                            ? 'color:#0a7'
                            : 'color:#a60',
                        },
                        c.suggested ? '[建议勾选]' : '[需确认]',
                      ),
                      h('span', ` ${c.method} ${c.path} — ${c.summary || ''}`),
                      c.registered
                        ? h('span', { style: 'margin-left:8px;color:#888' }, '已注册')
                        : h(
                            'button',
                            {
                              style: 'margin-left:8px',
                              onClick: () => register(c),
                            },
                            '注册',
                          ),
                    ]),
                  ),
                )
              : null,
          ]);
        }),
      ]);
  },
});
