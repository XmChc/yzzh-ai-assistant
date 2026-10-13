package cn.ysmp.ai.tool;

import cn.ysmp.ai.constants.AiLimits;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将出参 Schema 拼入 Tool description（OpenAI function 无标准 returns 字段）。
 */
public final class AiToolSchemaPrompt {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AiToolSchemaPrompt() {
    }

    public static String withReturns(String description, Map<String, Object> responseSchema) {
        if (responseSchema == null || responseSchema.isEmpty()) {
            return description == null ? "" : description;
        }
        String base = description == null ? "" : description.trim();
        String returns = compactSchema(responseSchema, AiLimits.MAX_TOOL_RETURNS_SCHEMA_CHARS);
        if (returns.isBlank()) {
            return base;
        }
        if (base.isBlank()) {
            return "Returns: " + returns;
        }
        if (base.contains("Returns:")) {
            return base;
        }
        return base + "\nReturns: " + returns;
    }

    /**
     * 精简 Schema：保留 type / title / description / properties(一层) / items / required。
     */
    @SuppressWarnings("unchecked")
    static String compactSchema(Map<String, Object> schema, int maxChars) {
        Map<String, Object> slim = slim(schema, 0, 2);
        try {
            String json = MAPPER.writeValueAsString(slim);
            if (json.length() <= maxChars) {
                return json;
            }
            // 再压一层：只保留 properties 的 type + 字段名
            Map<String, Object> outline = outline(schema);
            String outlineJson = MAPPER.writeValueAsString(outline);
            if (outlineJson.length() <= maxChars) {
                return outlineJson;
            }
            return outlineJson.substring(0, Math.max(0, maxChars - 3)) + "...";
        } catch (Exception e) {
            return outline(schema).toString();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> slim(Map<String, Object> schema, int depth, int maxDepth) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (schema == null) {
            return out;
        }
        copyIfPresent(schema, out, "type");
        copyIfPresent(schema, out, "title");
        copyIfPresent(schema, out, "format");
        copyIfPresent(schema, out, "description");
        copyIfPresent(schema, out, "enum");
        copyIfPresent(schema, out, "required");
        if (depth >= maxDepth) {
            if (schema.get("properties") instanceof Map<?, ?> props) {
                out.put("properties", props.keySet());
            }
            if (schema.containsKey("items")) {
                out.put("items", Map.of("type", "object"));
            }
            return out;
        }
        Object props = schema.get("properties");
        if (props instanceof Map<?, ?> map) {
            Map<String, Object> child = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getValue() instanceof Map<?, ?> m) {
                    child.put(String.valueOf(e.getKey()), slim((Map<String, Object>) m, depth + 1, maxDepth));
                } else {
                    child.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
            out.put("properties", child);
        }
        Object items = schema.get("items");
        if (items instanceof Map<?, ?> im) {
            out.put("items", slim((Map<String, Object>) im, depth + 1, maxDepth));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> outline(Map<String, Object> schema) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (schema == null) {
            return out;
        }
        copyIfPresent(schema, out, "type");
        copyIfPresent(schema, out, "title");
        copyIfPresent(schema, out, "description");
        Object props = schema.get("properties");
        if (props instanceof Map<?, ?> map) {
            Map<String, Object> fields = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String key = String.valueOf(e.getKey());
                if (e.getValue() instanceof Map<?, ?> m) {
                    Object type = m.get("type");
                    if ("array".equals(type) && m.get("items") instanceof Map<?, ?> items) {
                        Object itemProps = items.get("properties");
                        if (itemProps instanceof Map<?, ?> ip) {
                            fields.put(key, Map.of("type", "array", "itemFields", new ArrayList<>(ip.keySet())));
                        } else {
                            fields.put(key, Map.of("type", "array"));
                        }
                    } else if ("object".equals(type) && m.get("properties") instanceof Map<?, ?> nested) {
                        fields.put(key, Map.of("type", "object", "fields", new ArrayList<>(nested.keySet())));
                    } else {
                        fields.put(key, type == null ? "any" : type);
                    }
                } else {
                    fields.put(key, "any");
                }
            }
            out.put("properties", fields);
        }
        if (schema.get("items") instanceof Map<?, ?> items) {
            Object itemProps = items.get("properties");
            if (itemProps instanceof Map<?, ?> ip) {
                out.put("items", Map.of("fields", new ArrayList<>(ip.keySet())));
            } else {
                out.put("items", Map.of("type", items.get("type") == null ? "object" : items.get("type")));
            }
        }
        return out;
    }

    private static void copyIfPresent(Map<String, Object> from, Map<String, Object> to, String key) {
        Object v = from.get(key);
        if (v != null) {
            to.put(key, v);
        }
    }
}
