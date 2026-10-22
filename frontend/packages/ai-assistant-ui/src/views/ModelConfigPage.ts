import { defineComponent, h } from 'vue';

import { ConfigCenterPage } from './ConfigCenterPage';

/** @deprecated 请使用 ConfigCenterPage；保留以兼容旧菜单 component */
export const ModelConfigPage = defineComponent({
  name: 'AiModelConfigPage',
  setup() {
    return () => h(ConfigCenterPage, { defaultTab: 'model' });
  },
});
