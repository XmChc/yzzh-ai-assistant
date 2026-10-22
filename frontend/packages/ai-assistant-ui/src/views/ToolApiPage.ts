import { defineComponent, h } from 'vue';

import { ConfigCenterPage } from './ConfigCenterPage';

/** @deprecated 请使用 ConfigCenterPage；保留以兼容旧菜单 component */
export const ToolApiPage = defineComponent({
  name: 'AiToolApiPage',
  setup() {
    return () => h(ConfigCenterPage, { defaultTab: 'tool' });
  },
});
