package cn.ysmp.ai.tool.result;

import cn.ysmp.ai.config.AiProperties;
import cn.ysmp.ai.constants.AiLimits;
import cn.ysmp.ai.tool.result.AiToolResultStore.StoredToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 解析 Tool JSON → 大结果暂存（保留数组节点）→ 仅样例行转 Map 进 LLM。
 */
@Slf4j
@RequiredArgsConstructor
public class AiToolResultCompactor {

    private static final int NESTED_FOLD_THRESHOLD = 5;
    private static final String[] LIST_KEYS = {"list", "records", "rows", "items", "content", "data"};
    private static final String[] TOTAL_KEYS = {"total", "totalCount", "count"};

    private final ObjectMapper objectMapper;
    private final AiToolResultStore store;
    private final AiProperties properties;

    public record CompactOutcome(String llmContent, ToolDataRef dataRef) {
    }

    public record ToolDataRef(
            String refId,
            String toolCode,
            String shape,
            List<String> fields,
            int total,
            Integer reportedTotal,
            int pageSize,
            List<Map<String, Object>> sampleRows,
            Map<String, Object> stats,
            Map<String, Object> coverage,
            boolean compacted
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>(14);
            m.put("refId", refId);
            m.put("toolCode", toolCode);
            m.put("shape", shape);
            m.put("fields", fields);
            m.put("total", total);
            if (reportedTotal != null) {
                m.put("reportedTotal", reportedTotal);
            }
            m.put("pageSize", pageSize);
            m.put("sampleRows", sampleRows);
            if (stats != null && !stats.isEmpty()) {
                m.put("stats", stats);
            }
            if (coverage != null && !coverage.isEmpty()) {
                m.put("coverage", coverage);
            }
            m.put("compacted", compacted);
            return m;
        }
    }

    public CompactOutcome compact(String rawToolJson,
                                  String toolCode,
                                  String sessionId,
                                  Long userId) {
        return compact(rawToolJson, toolCode, sessionId, userId, null);
    }

    public CompactOutcome compact(String rawToolJson,
                                  String toolCode,
                                  String sessionId,
                                  Long userId,
                                  Map<String, Object> responseSchema) {
        if (rawToolJson == null || rawToolJson.isBlank()) {
            return new CompactOutcome("{}", null);
        }

        int threshold = positive(properties.getToolResultCompactThresholdRows(), 20);
        int sampleN = positive(properties.getToolResultSampleRows(), 5);
        int pageSize = positive(properties.getToolResultPageSizeDefault(), 10);
        int softLen = Math.min(Math.max(properties.getMaxToolResponseChars(), 1), 8000);

        // 过短且不像大数组：尽量原样返回，避免无意义解析成本
        if (rawToolJson.length() < softLen && !looksLikeLargeArray(rawToolJson, threshold)) {
            return new CompactOutcome(rawToolJson, null);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawToolJson);
        } catch (Exception e) {
            String text = rawToolJson.length() > 4000
                    ? rawToolJson.substring(0, 4000) + "...(truncated)"
                    : rawToolJson;
            return new CompactOutcome(text, null);
        }

        Located located = locateRows(root, responseSchema);
        int rowCount = located.rowsArray != null && located.rowsArray.isArray()
                ? located.rowsArray.size()
                : 0;

        boolean needCompact = rowCount >= threshold || rawToolJson.length() > softLen;
        if (!needCompact) {
            return new CompactOutcome(rawToolJson, null);
        }

        // 无行可分页（大对象）：仍压缩为摘要，但暂存单元素数组便于面板
        JsonNode storeArray = located.rowsArray;
        if (storeArray == null || !storeArray.isArray()) {
            ArrayNode one = objectMapper.createArrayNode();
            if (root != null && root.isObject()) {
                one.add(root);
                rowCount = 1;
            } else if (root != null) {
                one.add(root);
                rowCount = 1;
            }
            storeArray = one;
            located = new Located(
                    located.shape == null ? "object" : located.shape,
                    fieldsOf(storeArray),
                    storeArray,
                    located.reportedTotal);
        }

        List<String> fields = located.fields;
        if (fields == null || fields.isEmpty()) {
            fields = fieldsOf(storeArray);
        }

        List<Map<String, Object>> sampleView = sampleRows(storeArray, sampleN);
        Map<String, Object> statsView = columnStats(storeArray, fields, rowCount, located.reportedTotal);

        String refId = UUID.randomUUID().toString().replace("-", "");
        long ttl = properties.getToolResultTtlMs() > 0 ? properties.getToolResultTtlMs() : 1_800_000L;
        store.put(new StoredToolResult(
                refId, sessionId, userId, toolCode, located.shape,
                List.copyOf(fields), storeArray, located.reportedTotal,
                System.currentTimeMillis() + ttl));

        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("refId", refId);
        summary.put("toolCode", toolCode == null ? "" : toolCode);
        summary.put("shape", located.shape);
        summary.put("rowCount", rowCount);
        if (located.reportedTotal != null) {
            summary.put("reportedTotal", located.reportedTotal);
        }
        ArrayNode fieldsNode = summary.putArray("fields");
        for (String f : fields) {
            fieldsNode.add(f);
        }
        boolean statsComplete = AiToolResultCoverage.isComplete(rowCount, located.reportedTotal);
        Map<String, Object> coverage = AiToolResultCoverage.toMap(rowCount, located.reportedTotal);
        statsView.put("complete", statsComplete);
        if (located.reportedTotal != null) {
            statsView.put("reportedTotal", located.reportedTotal);
        }
        summary.set("stats", objectMapper.valueToTree(statsView));
        summary.set("sample", objectMapper.valueToTree(sampleView));
        summary.set("coverage", objectMapper.valueToTree(coverage));
        summary.put("statsComplete", statsComplete);
        summary.put("note", AiToolResultCoverage.note(refId, rowCount, located.reportedTotal, statsComplete));

        ToolDataRef ref = new ToolDataRef(
                refId, toolCode, located.shape, fields,
                rowCount, located.reportedTotal, pageSize, sampleView, statsView, coverage, true);
        try {
            return new CompactOutcome(objectMapper.writeValueAsString(summary), ref);
        } catch (Exception e) {
            return new CompactOutcome(summary.toString(), ref);
        }
    }

    /** 粗判：避免对短文本做完整 JSON 树解析 */
    private static boolean looksLikeLargeArray(String raw, int threshold) {
        // 统计顶层常见 list 片段中的 `{` 粗估行数；失败则交由完整解析
        int approx = 0;
        int idx = indexOfAny(raw, "\"list\"", "\"records\"", "\"rows\"", "\"items\"");
        int from = idx >= 0 ? idx : 0;
        int limit = Math.min(raw.length(), from + 200_000);
        for (int i = from; i < limit; i++) {
            if (raw.charAt(i) == '{') {
                approx++;
                if (approx >= threshold) {
                    return true;
                }
            }
        }
        return raw.length() > 50_000;
    }

    private static int indexOfAny(String s, String... keys) {
        int best = -1;
        for (String k : keys) {
            int i = s.indexOf(k);
            if (i >= 0 && (best < 0 || i < best)) {
                best = i;
            }
        }
        return best;
    }

    private Located locateRows(JsonNode root, Map<String, Object> responseSchema) {
        Located fromSchema = locateRowsBySchema(root, responseSchema);
        if (fromSchema != null) {
            return fromSchema;
        }
        return locateRowsHeuristic(root);
    }

    /**
     * 按出参 Schema 定位数组路径与行字段；找不到则返回 null 走启发式。
     */
    @SuppressWarnings("unchecked")
    private Located locateRowsBySchema(JsonNode root, Map<String, Object> responseSchema) {
        if (root == null || responseSchema == null || responseSchema.isEmpty()) {
            return null;
        }
        List<List<String>> arrayPaths = new ArrayList<>();
        collectArrayPaths(responseSchema, new ArrayList<>(), arrayPaths, 0);
        if (arrayPaths.isEmpty()) {
            return null;
        }
        // 优先更深路径，且偏好含 list/records/rows/items/data 的路径
        arrayPaths.sort(Comparator
                .comparingInt((List<String> p) -> -pathScore(p))
                .thenComparingInt(p -> -p.size()));
        Integer reportedTotal = root.isObject() ? firstInt(root, TOTAL_KEYS) : null;
        for (List<String> path : arrayPaths) {
            JsonNode node = navigate(root, path);
            if (node != null && node.isArray()) {
                List<String> schemaFields = fieldsFromSchemaPath(responseSchema, path);
                List<String> fields = schemaFields.isEmpty() ? fieldsOf(node) : schemaFields;
                Integer total = reportedTotal;
                if (path.size() >= 1) {
                    JsonNode parent = path.size() == 1 ? root : navigate(root, path.subList(0, path.size() - 1));
                    if (parent != null && parent.isObject()) {
                        Integer t2 = firstInt(parent, TOTAL_KEYS);
                        if (t2 != null) {
                            total = t2;
                        }
                    }
                }
                String shape = total != null ? "page" : "list";
                return new Located(shape, fields, node, total);
            }
        }
        return null;
    }

    private static int pathScore(List<String> path) {
        int score = path.size() * 10;
        for (String seg : path) {
            String s = seg == null ? "" : seg.toLowerCase();
            for (String k : LIST_KEYS) {
                if (k.equals(s)) {
                    score += 5;
                }
            }
        }
        return score;
    }

    private static JsonNode navigate(JsonNode root, List<String> path) {
        JsonNode cur = root;
        for (String seg : path) {
            if (cur == null || !cur.isObject()) {
                return null;
            }
            cur = cur.get(seg);
        }
        return cur;
    }

    @SuppressWarnings("unchecked")
    private static void collectArrayPaths(Map<String, Object> schema,
                                          List<String> prefix,
                                          List<List<String>> out,
                                          int depth) {
        if (schema == null || depth > 6) {
            return;
        }
        Object type = schema.get("type");
        if ("array".equals(type) && !prefix.isEmpty()) {
            out.add(List.copyOf(prefix));
        }
        Object props = schema.get("properties");
        if (props instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!(e.getValue() instanceof Map<?, ?> child)) {
                    continue;
                }
                List<String> next = new ArrayList<>(prefix);
                next.add(String.valueOf(e.getKey()));
                collectArrayPaths((Map<String, Object>) child, next, out, depth + 1);
            }
        }
        // items 自身是数组元素定义，不当作路径段
        Object items = schema.get("items");
        if (items instanceof Map<?, ?> itemMap && prefix.isEmpty() && "array".equals(type)) {
            // 根即为 array：路径空，由调用方直接用 root
            out.add(List.of());
            collectArrayPaths((Map<String, Object>) itemMap, prefix, out, depth + 1);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> fieldsFromSchemaPath(Map<String, Object> schema, List<String> path) {
        Map<String, Object> cur = schema;
        for (String seg : path) {
            Object props = cur.get("properties");
            if (!(props instanceof Map<?, ?> map) || !map.containsKey(seg)) {
                return List.of();
            }
            Object next = map.get(seg);
            if (!(next instanceof Map<?, ?> m)) {
                return List.of();
            }
            cur = (Map<String, Object>) m;
        }
        if (!"array".equals(cur.get("type"))) {
            return List.of();
        }
        Object items = cur.get("items");
        if (!(items instanceof Map<?, ?> itemMap)) {
            return List.of();
        }
        Object itemProps = itemMap.get("properties");
        if (!(itemProps instanceof Map<?, ?> ip)) {
            return List.of();
        }
        List<String> fields = new ArrayList<>();
        for (Object k : ip.keySet()) {
            if (k != null) {
                fields.add(String.valueOf(k));
            }
        }
        return fields;
    }

    private Located locateRowsHeuristic(JsonNode root) {
        if (root == null || root.isNull()) {
            return new Located("empty", List.of(), null, null);
        }
        if (root.isArray()) {
            return new Located("list", fieldsOf(root), root, null);
        }
        if (!root.isObject()) {
            return new Located("scalar", List.of("value"), null, null);
        }
        Integer reportedTotal = firstInt(root, TOTAL_KEYS);
        for (String key : LIST_KEYS) {
            JsonNode n = root.get(key);
            if (n != null && n.isArray()) {
                return new Located(reportedTotal != null ? "page" : "list", fieldsOf(n), n, reportedTotal);
            }
            if ("data".equals(key) && n != null && n.isObject()) {
                Integer t2 = firstInt(n, TOTAL_KEYS);
                for (String k2 : LIST_KEYS) {
                    if ("data".equals(k2)) {
                        continue;
                    }
                    JsonNode inner = n.get(k2);
                    if (inner != null && inner.isArray()) {
                        return new Located("page", fieldsOf(inner), inner, t2 != null ? t2 : reportedTotal);
                    }
                }
            }
        }
        return new Located("object", fieldNames(root), null, reportedTotal);
    }

    private List<Map<String, Object>> sampleRows(JsonNode arr, int sampleN) {
        if (arr == null || !arr.isArray() || arr.isEmpty() || sampleN <= 0) {
            return List.of();
        }
        int n = Math.min(sampleN, arr.size());
        List<Map<String, Object>> sample = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            JsonNode node = arr.get(i);
            if (node != null && node.isObject()) {
                sample.add(toMapFolded(node));
            } else {
                Map<String, Object> row = new LinkedHashMap<>(2);
                row.put("value", asJava(node));
                sample.add(row);
            }
        }
        return sample;
    }

    private static List<String> fieldsOf(JsonNode arr) {
        if (arr == null || !arr.isArray() || arr.isEmpty()) {
            return List.of();
        }
        // 只扫前几行推断字段，避免全表扫描
        int scan = Math.min(arr.size(), 8);
        LinkedHashMap<String, Boolean> set = new LinkedHashMap<>();
        for (int i = 0; i < scan; i++) {
            JsonNode n = arr.get(i);
            if (n != null && n.isObject()) {
                Iterator<String> it = n.fieldNames();
                while (it.hasNext()) {
                    set.putIfAbsent(it.next(), Boolean.TRUE);
                }
            } else {
                set.putIfAbsent("value", Boolean.TRUE);
            }
        }
        return new ArrayList<>(set.keySet());
    }

    private static List<String> fieldNames(JsonNode obj) {
        List<String> fields = new ArrayList<>();
        if (obj != null && obj.isObject()) {
            Iterator<String> it = obj.fieldNames();
            while (it.hasNext()) {
                fields.add(it.next());
            }
        }
        return fields;
    }

    /** 样例行：大嵌套数组在 JsonNode 层折叠，避免整表 convertValue。 */
    private Map<String, Object> toMapFolded(JsonNode obj) {
        Map<String, Object> row = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> it = obj.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            JsonNode v = e.getValue();
            if (v != null && v.isArray() && v.size() > NESTED_FOLD_THRESHOLD) {
                Map<String, Object> mark = new LinkedHashMap<>(3);
                mark.put("_nested", true);
                mark.put("_count", v.size());
                row.put(e.getKey(), mark);
            } else {
                row.put(e.getKey(), asJava(v));
            }
        }
        return row;
    }

    private Object asJava(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isTextual()) {
            return n.asText();
        }
        if (n.isInt()) {
            return n.intValue();
        }
        if (n.isLong()) {
            return n.longValue();
        }
        if (n.isFloatingPointNumber()) {
            return n.numberValue();
        }
        if (n.isBoolean()) {
            return n.booleanValue();
        }
        try {
            return objectMapper.convertValue(n, Object.class);
        } catch (Exception e) {
            return n.asText();
        }
    }

    private static Integer firstInt(JsonNode obj, String... keys) {
        for (String k : keys) {
            JsonNode n = obj.get(k);
            if (n != null && n.isNumber()) {
                return n.intValue();
            }
        }
        return null;
    }

    private static int positive(int v, int dft) {
        return v > 0 ? v : dft;
    }

    /**
     * 单遍扫描行数组，生成列级汇总供 LLM 回答「合计/均值/分布」类问题。
     * 不转 Map，直接读 JsonNode；行数/字段数有上限。
     */
    private Map<String, Object> columnStats(JsonNode arr,
                                            List<String> fields,
                                            int rowCount,
                                            Integer reportedTotal) {
        Map<String, Object> out = new LinkedHashMap<>(8);
        out.put("rowCount", rowCount);
        if (reportedTotal != null) {
            out.put("reportedTotal", reportedTotal);
        }
        if (arr == null || !arr.isArray() || arr.isEmpty() || fields == null || fields.isEmpty()) {
            out.put("columns", Map.of());
            return out;
        }

        int maxFields = Math.min(fields.size(), AiLimits.TOOL_RESULT_STATS_MAX_FIELDS);
        List<String> cols = fields.subList(0, maxFields);
        FieldAcc[] acc = new FieldAcc[cols.size()];
        for (int i = 0; i < cols.size(); i++) {
            acc[i] = new FieldAcc(cols.get(i));
        }

        int scan = Math.min(arr.size(), AiLimits.TOOL_RESULT_STATS_MAX_SCAN_ROWS);
        out.put("scannedRows", scan);
        if (scan < arr.size()) {
            out.put("sampled", true);
        }

        for (int r = 0; r < scan; r++) {
            JsonNode row = arr.get(r);
            if (row == null || !row.isObject()) {
                continue;
            }
            for (FieldAcc a : acc) {
                a.accept(row.get(a.name));
            }
        }

        Map<String, Object> columns = new LinkedHashMap<>(acc.length);
        for (FieldAcc a : acc) {
            Map<String, Object> col = a.toMap();
            if (!col.isEmpty()) {
                columns.put(a.name, col);
            }
        }
        out.put("columns", columns);
        return out;
    }

    /** 单列累加器：数值 min/max/sum；分类 top-K。 */
    private static final class FieldAcc {
        final String name;
        int nulls;
        int nums;
        int cats;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        double sum;
        boolean overflowDistinct;
        final Map<String, int[]> freq = new HashMap<>();

        FieldAcc(String name) {
            this.name = name;
        }

        void accept(JsonNode v) {
            if (v == null || v.isNull()) {
                nulls++;
                return;
            }
            if (v.isNumber()) {
                double d = v.asDouble();
                nums++;
                if (d < min) {
                    min = d;
                }
                if (d > max) {
                    max = d;
                }
                sum += d;
                return;
            }
            if (v.isBoolean()) {
                bump(v.booleanValue() ? "true" : "false");
                return;
            }
            if (v.isTextual()) {
                String t = v.asText();
                if (t.length() > 64) {
                    t = t.substring(0, 64) + "…";
                }
                bump(t);
                return;
            }
            // 对象/数组：只记类型占位，避免膨胀
            bump(v.isArray() ? "[array]" : "[object]");
        }

        private void bump(String key) {
            cats++;
            if (overflowDistinct) {
                return;
            }
            int[] c = freq.get(key);
            if (c == null) {
                if (freq.size() >= AiLimits.TOOL_RESULT_STATS_MAX_DISTINCT) {
                    overflowDistinct = true;
                    return;
                }
                freq.put(key, new int[]{1});
            } else {
                c[0]++;
            }
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>(8);
            if (nulls > 0) {
                m.put("nulls", nulls);
            }
            if (nums > 0) {
                m.put("type", cats > 0 ? "mixed" : "number");
                m.put("count", nums);
                m.put("min", min);
                m.put("max", max);
                m.put("sum", sum);
                m.put("avg", sum / nums);
            } else if (cats > 0) {
                m.put("type", "category");
                m.put("count", cats);
            } else if (nulls > 0) {
                m.put("type", "null");
            } else {
                return Map.of();
            }
            if (!freq.isEmpty()) {
                List<Map.Entry<String, int[]>> ranked = new ArrayList<>(freq.entrySet());
                ranked.sort(Comparator.comparingInt((Map.Entry<String, int[]> e) -> e.getValue()[0]).reversed());
                int k = Math.min(AiLimits.TOOL_RESULT_STATS_TOP_K, ranked.size());
                List<Map<String, Object>> top = new ArrayList<>(k);
                for (int i = 0; i < k; i++) {
                    Map.Entry<String, int[]> e = ranked.get(i);
                    Map<String, Object> item = new LinkedHashMap<>(2);
                    item.put("value", e.getKey());
                    item.put("count", e.getValue()[0]);
                    top.add(item);
                }
                m.put("top", top);
                if (overflowDistinct) {
                    m.put("distinctCapped", true);
                } else {
                    m.put("distinct", freq.size());
                }
            }
            return m;
        }
    }

    private record Located(String shape, List<String> fields, JsonNode rowsArray, Integer reportedTotal) {
    }
}
