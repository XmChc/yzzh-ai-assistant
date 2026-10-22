import { AgentPage } from './views/AgentPage';
import { ConfigCenterPage } from './views/ConfigCenterPage';
import { ModelConfigPage } from './views/ModelConfigPage';
import { SkillPage } from './views/SkillPage';
import { ToolApiPage } from './views/ToolApiPage';

export { AiAssistantFloat } from './AiAssistantFloat';
export { renderMarkdown } from './markdownRender';
export { splitContentWithCharts } from './chartParse';
export { renderChart } from './ChartPanel';
export { ToolResultPanel } from './ToolResultPanel';
export {
  ConfigCenterPage,
  ModelConfigPage,
  ToolApiPage,
  SkillPage,
  AgentPage,
};

/** 宿主动态路由 component 映射（配置入口仅 ai/config） */
export const aiRouteViews = {
  'ai/config': ConfigCenterPage,
};
