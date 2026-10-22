import {
  deleteToolApi,
  listOpenApiCatalog,
  listToolApis,
  registerOpenApiTool,
  resyncToolPermissions,
  resyncToolSchemas,
  updateToolEnabled,
} from '@ysmp/ai-assistant-core';
import { computed, defineComponent, h, onMounted, ref, watch } from 'vue';

import { asBool, errMsg } from '../configTabs';

/** OpenAPI 列表筛选：全部 / 建议 / 未注册 / 已注册 */
type CatalogFilter = 'all' | 'suggested' | 'unregistered' | 'registered';

interface ToolRow {
  id: number;
  tool_code: string;
  name: string;
  enabled: boolean | number;
  http_method?: string;
  path?: string;
  module?: string;
  channel?: string;
  param_schema_json?: string | null;
  response_schema_json?: string | null;
  required_permissions?: string | null;
}

/** 是否已绑定非空权限码（同步后的 [] 不算） */
function hasBoundPerms(raw: string | null | undefined): boolean {
  if (raw == null || raw === '' || raw === 'null' || raw === '[]') {
    return false;
  }
  try {
    const arr = JSON.parse(raw) as unknown;
    return Array.isArray(arr) && arr.some((p) => String(p || '').trim().length > 0);
  } catch {
    return String(raw).trim().length > 0 && raw !== '[]';
  }
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

const TOOL_PAGE_SIZE = 8;

/**
 * Tool / OpenAPI 白名单：注册、启停、注销、同步权限码与 Schema（禁止手填）。
 * OpenAPI 勾选在侧边抽屉中完成，主区只展示已注册列表。
 */
export const ToolPanel = defineComponent({
  name: 'AiToolPanel',
  setup() {
    /** 能进入「助手配置」页即可维护；细粒度菜单已收敛删除 */
    const canUpdate = () => true;
    const tools = ref<ToolRow[]>([]);
    const catalog = ref<CatalogModule[]>([]);
    const tip = ref('');
    const tipOk = ref(false);
    const catalogOpen = ref(false);
    const catalogFilter = ref<CatalogFilter>('suggested');
    const catalogQuery = ref('');
    const toolQuery = ref('');
    const toolPage = ref(1);
    const expanded = ref<Record<string, boolean>>({});
    const schemaOpen = ref<Record<number, boolean>>({});
    const loading = ref(true);
    const busyId = ref<number | null>(null);
    const registeringKey = ref<string | null>(null);
    const batchBusy = ref(false);

    function setTip(msg: string, ok = false) {
      tip.value = msg;
      tipOk.value = ok;
    }

    function itemKey(c: CatalogItem) {
      return `${c.method}:${c.path}`;
    }

    function matchTokens(hayParts: Array<string | null | undefined>, q: string) {
      if (!q) return true;
      const hay = hayParts.filter(Boolean).join(' ').toLowerCase();
      return q
        .toLowerCase()
        .split(/\s+/)
        .filter(Boolean)
        .every((token) => hay.includes(token));
    }

    function matchCatalogQuery(c: CatalogItem, q: string) {
      return matchTokens(
        [c.method, c.path, c.summary, c.operationId, c.module, c.moduleLabel],
        q,
      );
    }

    function passFilter(c: CatalogItem) {
      switch (catalogFilter.value) {
        case 'suggested':
          return !!c.suggested;
        case 'unregistered':
          return !c.registered;
        case 'registered':
          return !!c.registered;
        default:
          return true;
      }
    }

    function visibleCatalogItems(items: CatalogItem[]) {
      const q = catalogQuery.value.trim();
      return (items || []).filter((c) => passFilter(c) && matchCatalogQuery(c, q));
    }

    const filteredCatalog = computed(() =>
      catalog.value
        .map((mod) => ({
          ...mod,
          items: visibleCatalogItems(mod.items || []),
        }))
        .filter((mod) => mod.items.length > 0),
    );

    const catalogStats = computed(() => {
      let total = 0;
      let suggested = 0;
      for (const mod of catalog.value) {
        for (const c of mod.items || []) {
          total += 1;
          if (c.suggested) suggested += 1;
        }
      }
      const shown = filteredCatalog.value.reduce((n, m) => n + m.items.length, 0);
      const canBatch = filteredCatalog.value.reduce(
        (n, m) => n + m.items.filter((c) => !c.registered).length,
        0,
      );
      return { total, suggested, shown, canBatch };
    });

    const filteredTools = computed(() => {
      const q = toolQuery.value.trim();
      return tools.value.filter((t) =>
        matchTokens(
          [t.name, t.tool_code, t.module, t.http_method, t.path, t.required_permissions],
          q,
        ),
      );
    });

    const toolPageCount = computed(() =>
      Math.max(1, Math.ceil(filteredTools.value.length / TOOL_PAGE_SIZE)),
    );

    const pagedTools = computed(() => {
      const page = Math.min(toolPage.value, toolPageCount.value);
      const start = (page - 1) * TOOL_PAGE_SIZE;
      return filteredTools.value.slice(start, start + TOOL_PAGE_SIZE);
    });

    watch(toolQuery, () => {
      toolPage.value = 1;
    });

    watch(filteredTools, () => {
      if (toolPage.value > toolPageCount.value) {
        toolPage.value = toolPageCount.value;
      }
    });

    function expandAllFiltered() {
      const next = { ...expanded.value };
      for (const mod of filteredCatalog.value) {
        next[mod.module] = true;
      }
      expanded.value = next;
    }

    function collapseAll() {
      const next = { ...expanded.value };
      for (const key of Object.keys(next)) {
        next[key] = false;
      }
      expanded.value = next;
    }

    watch([catalogQuery, catalogFilter], () => {
      if (!catalogQuery.value.trim() && catalogStats.value.shown > 40) return;
      expandAllFiltered();
    });

    function setFilter(next: CatalogFilter) {
      catalogFilter.value = next;
    }

    /** 仅刷新已注册列表（主区首屏，避免每次拉全量 OpenAPI） */
    async function refreshTools() {
      tools.value = (await listToolApis()) as ToolRow[];
    }

    /** 拉取 OpenAPI 候选目录（抽屉打开时再请求） */
    async function refreshCatalog() {
      const raw = await listOpenApiCatalog();
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

    async function refresh() {
      await refreshTools();
      if (catalogOpen.value) {
        await refreshCatalog();
      }
    }

    onMounted(async () => {
      try {
        await refreshTools();
      } catch (e: unknown) {
        setTip(errMsg(e), false);
      } finally {
        loading.value = false;
      }
    });

    async function openCatalog() {
      catalogOpen.value = true;
      try {
        await Promise.all([refreshTools(), refreshCatalog()]);
      } catch (e: unknown) {
        setTip(errMsg(e), false);
      }
    }

    function closeCatalog() {
      catalogOpen.value = false;
    }

    async function register(item: CatalogItem) {
      if (!canUpdate()) {
        setTip('无接口配置更新权限', false);
        return;
      }
      const key = itemKey(item);
      registeringKey.value = key;
      try {
        await registerOpenApiTool({
          method: item.method,
          path: item.path,
          summary: item.summary,
          operationId: item.operationId,
          module: item.module,
        });
        setTip(`已注册 ${item.method} ${item.path}`, true);
        await refresh();
      } catch (e: unknown) {
        setTip(errMsg(e), false);
      } finally {
        registeringKey.value = null;
      }
    }

    async function registerMany(
      targets: CatalogItem[],
      okTip: (ok: number, fail: number) => string,
    ) {
      if (!canUpdate()) {
        setTip('无接口配置更新权限', false);
        return;
      }
      if (targets.length === 0) {
        setTip('当前没有可注册接口', false);
        return;
      }
      batchBusy.value = true;
      let ok = 0;
      let fail = 0;
      try {
        for (const item of targets) {
          try {
            await registerOpenApiTool({
              method: item.method,
              path: item.path,
              summary: item.summary,
              operationId: item.operationId,
              module: item.module,
            });
            ok += 1;
          } catch {
            fail += 1;
          }
        }
        setTip(okTip(ok, fail), fail === 0);
        await refresh();
      } finally {
        batchBusy.value = false;
      }
    }

    async function registerVisibleBatch() {
      const targets = filteredCatalog.value.flatMap((m) =>
        m.items.filter((c) => !c.registered),
      );
      await registerMany(targets, (ok, fail) =>
        fail === 0 ? `已批量注册 ${ok} 个接口` : `批量注册完成：成功 ${ok}，失败 ${fail}`,
      );
    }

    async function registerModuleUnregistered(mod: CatalogModule) {
      const targets = (mod.items || []).filter((c) => !c.registered);
      const label = mod.moduleLabel || mod.module;
      await registerMany(targets, (ok, fail) =>
        fail === 0
          ? `模块 ${label} 已注册 ${ok} 个`
          : `模块 ${label}：成功 ${ok}，失败 ${fail}`,
      );
    }

    async function toggleEnabled(t: ToolRow) {
      if (!canUpdate()) {
        setTip('无接口配置更新权限', false);
        return;
      }
      const next = !asBool(t.enabled);
      busyId.value = t.id;
      try {
        await updateToolEnabled(t.id, next);
        t.enabled = next;
        setTip(next ? `已启用 ${t.tool_code}` : `已停用 ${t.tool_code}`, true);
      } catch (e: unknown) {
        setTip(errMsg(e), false);
      } finally {
        busyId.value = null;
      }
    }

    async function unregister(t: ToolRow) {
      if (!canUpdate()) {
        setTip('无接口配置更新权限', false);
        return;
      }
      const label = t.name || t.tool_code;
      if (
        typeof window !== 'undefined' &&
        !window.confirm(`确认注销「${label}」？将移出白名单并清理技能绑定。`)
      ) {
        return;
      }
      busyId.value = t.id;
      try {
        await deleteToolApi(t.id);
        setTip(`已注销 ${t.tool_code}`, true);
        await refresh();
      } catch (e: unknown) {
        setTip(errMsg(e), false);
      } finally {
        busyId.value = null;
      }
    }

    async function syncPermissions() {
      if (!canUpdate()) {
        setTip('无接口配置更新权限', false);
        return;
      }
      try {
        const n = await resyncToolPermissions();
        await refresh();
        const withPerm = tools.value.filter((t) => hasBoundPerms(t.required_permissions)).length;
        const empty = tools.value.length - withPerm;
        setTip(
          empty === 0
            ? `已同步 ${n} 个 Tool，均已绑定权限码`
            : `已同步 ${n} 个 Tool：${withPerm} 个有权限码，${empty} 个为空已强制停用（须宿主补 @PreAuthorize 后再启用；ADMIN 渠道不含 app-api）`,
          true,
        );
      } catch (e: unknown) {
        setTip(errMsg(e), false);
      }
    }

    async function syncSchemas() {
      if (!canUpdate()) {
        setTip('无接口配置更新权限', false);
        return;
      }
      try {
        const n = await resyncToolSchemas();
        setTip(`已从 OpenAPI 同步 ${n} 个 Tool 的 Schema`, true);
        await refresh();
      } catch (e: unknown) {
        setTip(errMsg(e), false);
      }
    }

    /**
     * 展示固化权限码。
     * 注意：同步成功但宿主未声明 @PreAuthorize 时会写成 "[]"（阻断态，不可启用）。
     */
    function formatPerms(raw: string | null | undefined) {
      if (raw == null || raw === '' || raw === 'null') {
        return '尚未同步（不可启用）';
      }
      try {
        const arr = JSON.parse(raw) as unknown;
        if (Array.isArray(arr) && arr.length) {
          return arr.map(String).join('、');
        }
        if (Array.isArray(arr)) {
          return '无权限码·阻断（须宿主补 @PreAuthorize 后同步；不可启用）';
        }
      } catch {
        // plain
      }
      if (raw === '[]') {
        return '无权限码·阻断（须宿主补 @PreAuthorize 后同步；不可启用）';
      }
      return String(raw);
    }

    function prettyJson(raw: string | null | undefined) {
      if (raw == null || raw === '') return '(空)';
      try {
        return JSON.stringify(JSON.parse(raw), null, 2);
      } catch {
        return raw;
      }
    }

    function hasSchema(t: ToolRow) {
      return !!(t.param_schema_json || t.response_schema_json);
    }

    function renderPager() {
      const total = filteredTools.value.length;
      if (total <= TOOL_PAGE_SIZE) return null;
      const page = Math.min(toolPage.value, toolPageCount.value);
      return h('div', { class: 'ai-config-pager' }, [
        h(
          'span',
          { class: 'ai-config-muted' },
          `第 ${page}/${toolPageCount.value} 页 · 共 ${total} 条`,
        ),
        h(
          'button',
          {
            type: 'button',
            class: 'ai-config-btn ai-config-btn--sm',
            disabled: page <= 1,
            onClick: () => {
              toolPage.value = Math.max(1, page - 1);
            },
          },
          '上一页',
        ),
        h(
          'button',
          {
            type: 'button',
            class: 'ai-config-btn ai-config-btn--sm',
            disabled: page >= toolPageCount.value,
            onClick: () => {
              toolPage.value = Math.min(toolPageCount.value, page + 1);
            },
          },
          '下一页',
        ),
      ]);
    }

    function renderCatalogDrawer() {
      if (!catalogOpen.value) return null;
      return h('div', { class: 'ai-config-drawer-mask', onClick: closeCatalog }, [
        h(
          'div',
          {
            class: 'ai-config-drawer ai-config-drawer--wide',
            onClick: (e: Event) => e.stopPropagation(),
          },
          [
            h('div', { class: 'ai-config-drawer-head' }, [
              h('div', [
                h('h3', '从 OpenAPI 注册'),
                h(
                  'p',
                  { class: 'ai-config-hint', style: 'margin:4px 0 0' },
                  '勾选注册后进入白名单；权限码与 Schema 仅允许从宿主同步。',
                ),
              ]),
              h(
                'button',
                { type: 'button', class: 'ai-config-btn ai-config-btn--ghost', onClick: closeCatalog },
                '关闭',
              ),
            ]),
            h('div', { class: 'ai-config-drawer-body' }, [
              h('div', { class: 'ai-config-catalog-bar ai-config-catalog-bar--drawer' }, [
                h('div', { class: 'ai-config-catalog-search-row' }, [
                  h('input', {
                    class: 'ai-config-input ai-config-catalog-search',
                    type: 'search',
                    placeholder: '搜索 path / 方法 / 摘要 / 模块，空格分词',
                    value: catalogQuery.value,
                    onInput: (e: Event) => {
                      catalogQuery.value = (e.target as HTMLInputElement).value;
                    },
                  }),
                  catalogQuery.value
                    ? h(
                        'button',
                        {
                          type: 'button',
                          class: 'ai-config-btn ai-config-btn--ghost ai-config-btn--sm',
                          onClick: () => {
                            catalogQuery.value = '';
                          },
                        },
                        '清空',
                      )
                    : null,
                ]),
                h('div', { class: 'ai-config-catalog-filters' }, [
                  (
                    [
                      ['suggested', '建议'],
                      ['unregistered', '未注册'],
                      ['registered', '已注册'],
                      ['all', '全部'],
                    ] as [CatalogFilter, string][]
                  ).map(([key, label]) =>
                    h(
                      'button',
                      {
                        type: 'button',
                        class:
                          catalogFilter.value === key
                            ? 'ai-config-filter-chip is-active'
                            : 'ai-config-filter-chip',
                        onClick: () => setFilter(key),
                      },
                      label,
                    ),
                  ),
                ]),
                h('div', { class: 'ai-config-catalog-actions' }, [
                  h(
                    'span',
                    { class: 'ai-config-muted' },
                    `显示 ${catalogStats.value.shown} / 共 ${catalogStats.value.total}（建议 ${catalogStats.value.suggested}）`,
                  ),
                  h(
                    'button',
                    {
                      type: 'button',
                      class: 'ai-config-btn ai-config-btn--sm',
                      onClick: expandAllFiltered,
                    },
                    '展开命中',
                  ),
                  h(
                    'button',
                    {
                      type: 'button',
                      class: 'ai-config-btn ai-config-btn--sm',
                      onClick: collapseAll,
                    },
                    '全部收起',
                  ),
                  h(
                    'button',
                    {
                      type: 'button',
                      class: 'ai-config-btn ai-config-btn--primary ai-config-btn--sm',
                      disabled:
                        !canUpdate() ||
                        batchBusy.value ||
                        catalogStats.value.canBatch === 0,
                      onClick: registerVisibleBatch,
                    },
                    batchBusy.value
                      ? '注册中…'
                      : `注册当前筛选未注册（${catalogStats.value.canBatch}）`,
                  ),
                ]),
              ]),
              catalog.value.length === 0
                ? h(
                    'div',
                    { class: 'ai-config-empty' },
                    '暂无可勾选接口：宿主 springdoc 未扫到 paths。请确认后端已加载并重启，或先打开 /v3/api-docs/all 再刷新。',
                  )
                : filteredCatalog.value.length === 0
                  ? h(
                      'div',
                      { class: 'ai-config-empty' },
                      '当前筛选无结果，试试换关键词或切到「全部」。',
                    )
                  : h(
                      'div',
                      { class: 'ai-config-catalog-scroll ai-config-catalog-scroll--drawer' },
                      filteredCatalog.value.map((mod) => {
                        const items = mod.items;
                        const open = !!expanded.value[mod.module];
                        const unreg = items.filter((c) => !c.registered).length;
                        return h('div', { key: mod.module, class: 'ai-config-catalog-mod' }, [
                          h('div', { class: 'ai-config-catalog-mod-head' }, [
                            h(
                              'button',
                              {
                                type: 'button',
                                class: 'ai-config-catalog-toggle',
                                onClick: () => {
                                  expanded.value = {
                                    ...expanded.value,
                                    [mod.module]: !expanded.value[mod.module],
                                  };
                                },
                              },
                              `${open ? '▼' : '▶'} ${mod.moduleLabel || mod.module}（${items.length}${
                                unreg ? ` · 未注册 ${unreg}` : ''
                              }）`,
                            ),
                            unreg > 0
                              ? h(
                                  'button',
                                  {
                                    type: 'button',
                                    class: 'ai-config-btn ai-config-btn--sm',
                                    disabled: !canUpdate() || batchBusy.value,
                                    onClick: () => registerModuleUnregistered(mod),
                                  },
                                  `本模块注册（${unreg}）`,
                                )
                              : null,
                          ]),
                          open
                            ? h(
                                'ul',
                                { class: 'ai-config-catalog-list' },
                                items.map((c) => {
                                  const key = itemKey(c);
                                  return h('li', { key, class: 'ai-config-catalog-item' }, [
                                    h(
                                      'span',
                                      {
                                        class: c.suggested
                                          ? 'ai-config-tag is-suggest'
                                          : 'ai-config-tag is-warn',
                                      },
                                      c.suggested ? '建议' : '确认',
                                    ),
                                    h(
                                      'span',
                                      {
                                        class: 'ai-config-catalog-path',
                                        title: `${c.method} ${c.path}`,
                                      },
                                      `${c.method} ${c.path}`,
                                    ),
                                    h(
                                      'span',
                                      { class: 'ai-config-muted ai-config-catalog-summary' },
                                      c.summary || '',
                                    ),
                                    c.registered
                                      ? h('span', { class: 'ai-config-tag is-on' }, '已注册')
                                      : h(
                                          'button',
                                          {
                                            type: 'button',
                                            class:
                                              'ai-config-btn ai-config-btn--sm ai-config-btn--primary',
                                            disabled:
                                              !canUpdate() ||
                                              batchBusy.value ||
                                              registeringKey.value === key,
                                            onClick: () => register(c),
                                          },
                                          registeringKey.value === key ? '…' : '注册',
                                        ),
                                  ]);
                                }),
                              )
                            : null,
                        ]);
                      }),
                    ),
            ]),
          ],
        ),
      ]);
    }

    return () =>
      h('div', { class: 'ai-config-panel' }, [
        h('div', { class: 'ai-config-panel-head' }, [
          h('h3', { class: 'ai-config-panel-title' }, '接口白名单'),
          h(
            'p',
            { class: 'ai-config-panel-desc' },
            '从 OpenAPI 勾选注册并启用后进入编排白名单。权限码与 Schema 仅允许从宿主同步，禁止手填。',
          ),
        ]),
        tip.value
          ? h('div', { class: tipOk.value ? 'ai-config-banner is-ok' : 'ai-config-banner is-err' }, tip.value)
          : null,
        h('div', { class: 'ai-config-toolbar' }, [
          h(
            'button',
            {
              type: 'button',
              class: 'ai-config-btn ai-config-btn--primary',
              disabled: !canUpdate(),
              onClick: openCatalog,
            },
            '从 OpenAPI 注册',
          ),
          h(
            'button',
            {
              type: 'button',
              class: 'ai-config-btn',
              disabled: !canUpdate(),
              onClick: syncPermissions,
            },
            '同步权限码',
          ),
          h(
            'button',
            {
              type: 'button',
              class: 'ai-config-btn',
              disabled: !canUpdate(),
              onClick: syncSchemas,
            },
            '同步 Schema',
          ),
        ]),
        loading.value
          ? h('div', { class: 'ai-config-empty' }, '加载中…')
          : [
              h('div', { class: 'ai-config-section-head' }, [
                h(
                  'h4',
                  { class: 'ai-config-section-title' },
                  `已注册（${tools.value.length}）`,
                ),
                h('div', { class: 'ai-config-catalog-search-row', style: 'flex:1;max-width:420px' }, [
                  h('input', {
                    class: 'ai-config-input ai-config-catalog-search',
                    type: 'search',
                    placeholder: '筛选已注册：名称 / path / 模块…',
                    value: toolQuery.value,
                    onInput: (e: Event) => {
                      toolQuery.value = (e.target as HTMLInputElement).value;
                    },
                  }),
                ]),
              ]),
              tools.value.length === 0
                ? h(
                    'div',
                    { class: 'ai-config-empty' },
                    '尚未注册接口，点击上方「从 OpenAPI 注册」打开侧边目录勾选。',
                  )
                : filteredTools.value.length === 0
                  ? h('div', { class: 'ai-config-empty' }, '无匹配的已注册接口。')
                  : [
                      h(
                        'div',
                        { class: 'ai-config-card-list' },
                        pagedTools.value.map((t) => {
                          const on = asBool(t.enabled);
                          const bound = hasBoundPerms(t.required_permissions);
                          return h('div', { key: t.id, class: 'ai-config-card' }, [
                            h('div', { class: 'ai-config-card-main' }, [
                              h('div', { class: 'ai-config-card-title-row' }, [
                                h('span', { class: 'ai-config-card-title' }, t.name || t.tool_code),
                                h(
                                  'span',
                                  { class: on ? 'ai-config-tag is-on' : 'ai-config-tag is-off' },
                                  on ? '启用' : '停用',
                                ),
                                bound
                                  ? null
                                  : h('span', { class: 'ai-config-tag is-warn' }, '无权限码'),
                              ]),
                              h(
                                'div',
                                { class: 'ai-config-card-meta' },
                                [
                                  t.module || '未分类',
                                  t.channel || 'ADMIN',
                                  t.tool_code,
                                  `${t.http_method || ''} ${t.path || ''}`.trim(),
                                ]
                                  .filter(Boolean)
                                  .join(' · '),
                              ),
                              h(
                                'div',
                                {
                                  class: bound
                                    ? 'ai-config-card-sub'
                                    : 'ai-config-card-sub ai-config-card-sub--warn',
                                  title: '只读：由同步写入；空权限不可启用',
                                },
                                `权限：${formatPerms(t.required_permissions)}`,
                              ),
                            ]),
                            h('div', { class: 'ai-config-card-actions' }, [
                              h(
                                'button',
                                {
                                  type: 'button',
                                  class: on
                                    ? 'ai-config-btn ai-config-btn--ghost'
                                    : 'ai-config-btn ai-config-btn--primary',
                                  disabled:
                                    !canUpdate() ||
                                    busyId.value === t.id ||
                                    (!on && !bound),
                                  title: !on && !bound ? '无权限码，无法启用' : undefined,
                                  onClick: () => toggleEnabled(t),
                                },
                                busyId.value === t.id ? '…' : on ? '停用' : '启用',
                              ),
                              h(
                                'button',
                                {
                                  type: 'button',
                                  class: 'ai-config-btn ai-config-btn--sm ai-config-btn--danger',
                                  disabled: !canUpdate() || busyId.value === t.id,
                                  onClick: () => unregister(t),
                                },
                                '注销',
                              ),
                              hasSchema(t)
                                ? h(
                                    'button',
                                    {
                                      type: 'button',
                                      class: 'ai-config-btn ai-config-btn--link',
                                      onClick: () => {
                                        schemaOpen.value = {
                                          ...schemaOpen.value,
                                          [t.id]: !schemaOpen.value[t.id],
                                        };
                                      },
                                    },
                                    schemaOpen.value[t.id] ? '收起 Schema' : '查看 Schema',
                                  )
                                : h('span', { class: 'ai-config-muted' }, '无 Schema'),
                            ]),
                            schemaOpen.value[t.id]
                              ? h('div', { class: 'ai-config-schema-grid' }, [
                                  h(
                                    'pre',
                                    { class: 'ai-config-pre' },
                                    `入参:\n${prettyJson(t.param_schema_json)}`,
                                  ),
                                  h(
                                    'pre',
                                    { class: 'ai-config-pre' },
                                    `出参:\n${prettyJson(t.response_schema_json)}`,
                                  ),
                                ])
                              : null,
                          ]);
                        }),
                      ),
                      renderPager(),
                    ],
            ],
        renderCatalogDrawer(),
      ]);
  },
});
