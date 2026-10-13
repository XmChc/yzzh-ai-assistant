package cn.ysmp.ai.tool.result;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 进程内暂存：分页仅转换当前页行；TTL 按间隔清理。
 */
@Slf4j
public class InMemoryAiToolResultStore implements AiToolResultStore {

    private static final int NESTED_FOLD_THRESHOLD = 5;
    private static final long PURGE_INTERVAL_MS = 60_000L;

    private final ConcurrentHashMap<String, StoredToolResult> map = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final AtomicLong lastPurgeAt = new AtomicLong(0);

    public InMemoryAiToolResultStore(long ttlMs, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        // ttlMs 由写入方写到 expireAtMs；此处仅保留构造兼容
    }

    /** 兼容旧构造 */
    public InMemoryAiToolResultStore(long ttlMs) {
        this(ttlMs, new ObjectMapper());
    }

    @Override
    public void put(StoredToolResult result) {
        if (result == null || result.refId() == null) {
            return;
        }
        maybePurge();
        map.put(result.refId(), result);
    }

    @Override
    public Optional<StoredToolResult> get(String refId) {
        if (refId == null || refId.isBlank()) {
            return Optional.empty();
        }
        StoredToolResult r = map.get(refId);
        if (r == null) {
            return Optional.empty();
        }
        if (r.expireAtMs() < System.currentTimeMillis()) {
            map.remove(refId, r);
            return Optional.empty();
        }
        return Optional.of(r);
    }

    @Override
    public Optional<ToolResultPage> page(String refId, int page, int pageSize) {
        return get(refId).map(r -> sliceArray(r, r.rowsArray(), r.fields(), page, pageSize, null));
    }

    @Override
    public Optional<ToolResultPage> pageField(String refId, int rowIndex, String field, int page, int pageSize) {
        Optional<StoredToolResult> opt = get(refId);
        if (opt.isEmpty() || field == null || field.isBlank()) {
            return Optional.empty();
        }
        StoredToolResult r = opt.get();
        JsonNode arr = r.rowsArray();
        if (arr == null || !arr.isArray() || rowIndex < 0 || rowIndex >= arr.size()) {
            return Optional.empty();
        }
        JsonNode rowNode = arr.get(rowIndex);
        if (rowNode == null || !rowNode.isObject()) {
            return Optional.empty();
        }
        JsonNode val = rowNode.get(field);
        if (val == null || val.isNull()) {
            return Optional.empty();
        }
        if (val.isArray()) {
            List<String> fields = inferFields(val);
            return Optional.of(sliceArray(r, val, fields, page, pageSize, field));
        }
        if (val.isObject()) {
            Map<String, Object> one = toMap(val);
            List<String> fields = new ArrayList<>(one.keySet());
            return Optional.of(new ToolResultPage(
                    r.refId(), r.toolCode(), r.shape(), fields, List.of(one),
                    1, 1, 1, r.reportedTotal(), field));
        }
        Map<String, Object> one = new LinkedHashMap<>();
        one.put(field, asJava(val));
        return Optional.of(new ToolResultPage(
                r.refId(), r.toolCode(), r.shape(), List.of(field), List.of(one),
                1, 1, 1, r.reportedTotal(), field));
    }

    private ToolResultPage sliceArray(StoredToolResult r,
                                      JsonNode arr,
                                      List<String> fields,
                                      int page,
                                      int pageSize,
                                      String parentField) {
        int ps = Math.max(1, Math.min(pageSize, 100));
        int p = Math.max(1, page);
        int total = arr != null && arr.isArray() ? arr.size() : 0;
        int from = Math.min((p - 1) * ps, total);
        int to = Math.min(from + ps, total);
        List<Map<String, Object>> view = new ArrayList<>(Math.max(0, to - from));
        for (int i = from; i < to; i++) {
            JsonNode n = arr.get(i);
            if (n != null && n.isObject()) {
                view.add(toMapFolded(n));
            } else {
                Map<String, Object> row = new LinkedHashMap<>(2);
                row.put("value", asJava(n));
                view.add(row);
            }
        }
        List<String> cols = fields;
        if ((cols == null || cols.isEmpty()) && !view.isEmpty()) {
            cols = new ArrayList<>(view.get(0).keySet());
        }
        return new ToolResultPage(
                r.refId(), r.toolCode(), r.shape(),
                cols == null ? List.of() : cols,
                view, p, ps, total, r.reportedTotal(), parentField);
    }

    private List<String> inferFields(JsonNode arr) {
        if (arr == null || !arr.isArray() || arr.isEmpty()) {
            return List.of("value");
        }
        JsonNode first = arr.get(0);
        if (first != null && first.isObject()) {
            List<String> fields = new ArrayList<>();
            Iterator<String> it = first.fieldNames();
            while (it.hasNext()) {
                fields.add(it.next());
            }
            return fields;
        }
        return List.of("value");
    }

    /** 行转 Map：大嵌套数组在 JsonNode 层折叠，避免整表 convertValue。 */
    private Map<String, Object> toMapFolded(JsonNode obj) {
        Map<String, Object> row = new LinkedHashMap<>();
        if (obj == null || !obj.isObject()) {
            return row;
        }
        Iterator<Map.Entry<String, JsonNode>> it = obj.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            JsonNode v = e.getValue();
            if (v != null && v.isArray() && v.size() > NESTED_FOLD_THRESHOLD) {
                Map<String, Object> mark = new LinkedHashMap<>(4);
                mark.put("_nested", true);
                mark.put("_count", v.size());
                List<Object> preview = new ArrayList<>(3);
                int lim = Math.min(3, v.size());
                for (int i = 0; i < lim; i++) {
                    preview.add(asJava(v.get(i)));
                }
                mark.put("_preview", preview);
                row.put(e.getKey(), mark);
            } else {
                row.put(e.getKey(), asJava(v));
            }
        }
        return row;
    }

    private Map<String, Object> toMap(JsonNode obj) {
        Map<String, Object> row = new LinkedHashMap<>();
        if (obj == null || !obj.isObject()) {
            return row;
        }
        Iterator<Map.Entry<String, JsonNode>> it = obj.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            row.put(e.getKey(), asJava(e.getValue()));
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
        if (n.isDouble() || n.isFloat() || n.isBigDecimal()) {
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

    private void maybePurge() {
        long now = System.currentTimeMillis();
        long last = lastPurgeAt.get();
        if (now - last < PURGE_INTERVAL_MS && map.size() < 256) {
            return;
        }
        if (!lastPurgeAt.compareAndSet(last, now)) {
            return;
        }
        map.entrySet().removeIf(e -> e.getValue().expireAtMs() < now);
    }
}
