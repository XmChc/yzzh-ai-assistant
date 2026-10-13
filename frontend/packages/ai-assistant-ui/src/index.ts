import { AgentPage } from './views/AgentPage';
import { ModelConfigPage } from './views/ModelConfigPage';
import { SkillPage } from './views/SkillPage';
import { ToolApiPage } from './views/ToolApiPage';

export { AiAssistantFloat } from './AiAssistantFloat';
export { renderMarkdown } from './markdownRender';
export { splitContentWithCharts } from './chartParse';
export { renderChart } from './ChartPanel';
export { ToolResultPanel } from './ToolResultPanel';
export { ModelConfigPage, ToolApiPage, SkillPage, AgentPage };

/** 宿主动态路由 component 映射 */
export const aiRouteViews = {
  'ai/model-config': ModelConfigPage,
  'ai/tool-api': ToolApiPage,
  'ai/skill': SkillPage,
  'ai/agent': AgentPage,
};
