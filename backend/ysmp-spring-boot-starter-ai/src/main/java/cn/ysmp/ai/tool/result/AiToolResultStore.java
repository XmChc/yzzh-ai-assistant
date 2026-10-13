package cn.ysmp.ai.tool.result;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tool 全量结果暂存：LLM 只看压缩摘要，UI 按 refId 分页拉取。
 * <p>行数据以 {@link JsonNode} 数组保留，分页时再按页转换，避免全量 Map 化。
 */
public interface AiToolResultStore {

    void put(StoredToolResult result);

    Optional<StoredToolResult> get(String refId);

    Optional<ToolResultPage> page(String refId, int page, int pageSize);

    Optional<ToolResultPage> pageField(String refId, int rowIndex, String field, int page, int pageSize);

    /**
     * @param rowsArray 行数组节点（ArrayNode）；不可变引用，勿再修改
     */
    record StoredToolResult(
            String refId,
            String sessionId,
            Long userId,
            String toolCode,
            String shape,
            List<String> fields,
            JsonNode rowsArray,
            Integer reportedTotal,
            long expireAtMs
    ) {
        public int rowCount() {
            return rowsArray != null && rowsArray.isArray() ? rowsArray.size() : 0;
        }
    }

    record ToolResultPage(
            String refId,
            String toolCode,
            String shape,
            List<String> fields,
            List<Map<String, Object>> rows,
            int page,
            int pageSize,
            int total,
            Integer reportedTotal,
            String parentField
    ) {
    }
}
