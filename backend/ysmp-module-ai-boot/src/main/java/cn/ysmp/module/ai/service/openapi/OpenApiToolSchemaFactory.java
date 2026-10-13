package cn.ysmp.module.ai.service.openapi;

import cn.ysmp.ai.constants.AiLimits;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 从 OpenAPI Operation 抽取入参 / 出参 JSON Schema（$ref 本地展开）。
 * <p>入参扁平合并：path/query + requestBody.properties，与执行器 arguments→query/body 一致。
 */
@Slf4j
@RequiredArgsConstructor
public class OpenApiToolSchemaFactory {

    private final ObjectMapper objectMapper;

    public record ToolSchemas(Map<String, Object> parametersSchema, Map<String, Object> responseSchema) {
    }

    public ToolSchemas extract(OpenAPI openAPI, Operation operation) {
        Map<String, Object> params = buildParametersSchema(openAPI, operation);
        Map<String, Object> response = buildResponseSchema(openAPI, operation);
        return new ToolSchemas(params, response);
    }

    public String toJson(Map<String, Object> schema) {
        if (schema == null || schema.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(schema);
        } catch (Exception e) {
            log.warn("[ai] schema toJson failed: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> buildParametersSchema(OpenAPI openAPI, Operation operation) {
        Map<String, Object> properties = new LinkedHashMap<>();
        Set<String> required = new LinkedHashSet<>();

        if (operation != null && operation.getParameters() != null) {
            for (Parameter p : operation.getParameters()) {
                if (p == null || p.getName() == null || p.getName().isBlank()) {
                    continue;
                }
                String in = p.getIn() == null ? "" : p.getIn().toLowerCase(Locale.ROOT);
                if ("header".equals(in) || "cookie".equals(in)) {
                    continue;
                }
                Map<String, Object> prop = schemaToMap(openAPI, p.getSchema(), new HashSet<>(), 0);
                if (prop.isEmpty()) {
                    prop = Map.of("type", "string");
                }
                if (p.getDescription() != null && !p.getDescription().isBlank()) {
                    prop = new LinkedHashMap<>(prop);
                    prop.putIfAbsent("description", p.getDescription());
                }
                properties.put(p.getName(), prop);
                if (Boolean.TRUE.equals(p.getRequired())) {
                    required.add(p.getName());
                }
            }
        }

        Schema<?> bodySchema = jsonBodySchema(operation == null ? null : operation.getRequestBody());
        if (bodySchema != null) {
            Map<String, Object> bodyMap = schemaToMap(openAPI, bodySchema, new HashSet<>(), 0);
            mergeBodyIntoProperties(bodyMap, properties, required);
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", "object");
        root.put("properties", properties);
        if (!required.isEmpty()) {
            root.put("required", new ArrayList<>(required));
        }
        return root;
    }

    @SuppressWarnings("unchecked")
    private void mergeBodyIntoProperties(Map<String, Object> bodyMap,
                                         Map<String, Object> properties,
                                         Set<String> required) {
        if (bodyMap == null || bodyMap.isEmpty()) {
            return;
        }
        Object type = bodyMap.get("type");
        Object propsObj = bodyMap.get("properties");
        if (("object".equals(type) || type == null) && propsObj instanceof Map<?, ?> props) {
            for (Map.Entry<?, ?> e : props.entrySet()) {
                if (e.getKey() == null) {
                    continue;
                }
                String name = String.valueOf(e.getKey());
                if (e.getValue() instanceof Map<?, ?> m) {
                    properties.put(name, new LinkedHashMap<>((Map<String, Object>) m));
                } else {
                    properties.put(name, e.getValue());
                }
            }
            Object req = bodyMap.get("required");
            if (req instanceof List<?> list) {
                for (Object r : list) {
                    if (r != null) {
                        required.add(String.valueOf(r));
                    }
                }
            }
            return;
        }
        // 非 object body：整包放在 body 字段，避免丢失
        properties.put("body", bodyMap);
    }

    private Map<String, Object> buildResponseSchema(OpenAPI openAPI, Operation operation) {
        if (operation == null) {
            return Map.of();
        }
        ApiResponses responses = operation.getResponses();
        if (responses == null || responses.isEmpty()) {
            return Map.of();
        }
        ApiResponse chosen = null;
        for (String code : List.of("200", "201", "202", "204")) {
            ApiResponse r = responses.get(code);
            if (r != null && hasJsonContent(r)) {
                chosen = r;
                break;
            }
        }
        if (chosen == null) {
            for (Map.Entry<String, ApiResponse> e : responses.entrySet()) {
                String code = e.getKey();
                if (code != null && code.startsWith("2") && hasJsonContent(e.getValue())) {
                    chosen = e.getValue();
                    break;
                }
            }
        }
        if (chosen == null) {
            return Map.of();
        }
        Schema<?> schema = jsonContentSchema(chosen.getContent());
        if (schema == null) {
            return Map.of();
        }
        Map<String, Object> map = schemaToMap(openAPI, schema, new HashSet<>(), 0);
        if (map.isEmpty()) {
            return Map.of();
        }
        // 宿主 CommonResult 等包装：提示业务数据常在 data
        if (map.get("properties") instanceof Map<?, ?> props && props.containsKey("data")) {
            Map<String, Object> annotated = new LinkedHashMap<>(map);
            annotated.putIfAbsent("description", "业务数据通常在 data 字段");
            return annotated;
        }
        return map;
    }

    private static boolean hasJsonContent(ApiResponse response) {
        return jsonContentSchema(response == null ? null : response.getContent()) != null;
    }

    private static Schema<?> jsonBodySchema(RequestBody body) {
        if (body == null) {
            return null;
        }
        return jsonContentSchema(body.getContent());
    }

    private static Schema<?> jsonContentSchema(Content content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        MediaType mt = content.get("application/json");
        if (mt == null) {
            mt = content.get("application/*+json");
        }
        if (mt == null) {
            for (Map.Entry<String, MediaType> e : content.entrySet()) {
                if (e.getKey() != null && e.getKey().toLowerCase(Locale.ROOT).contains("json")) {
                    mt = e.getValue();
                    break;
                }
            }
        }
        return mt == null ? null : mt.getSchema();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Map<String, Object> schemaToMap(OpenAPI openAPI, Schema schema, Set<String> visiting, int depth) {
        if (schema == null) {
            return Map.of();
        }
        if (depth > AiLimits.MAX_OPENAPI_SCHEMA_DEPTH) {
            return Map.of("type", "object", "description", "schema depth truncated");
        }

        String ref = schema.get$ref();
        if (ref != null && !ref.isBlank()) {
            String name = refName(ref);
            if (name == null) {
                return Map.of("$ref", ref);
            }
            if (visiting.contains(name)) {
                return Map.of("type", "object", "description", "circular ref " + name);
            }
            Schema<?> resolved = resolveComponent(openAPI, name);
            if (resolved == null) {
                return Map.of("$ref", ref);
            }
            visiting.add(name);
            Map<String, Object> mapped = schemaToMap(openAPI, resolved, visiting, depth + 1);
            visiting.remove(name);
            if (!mapped.isEmpty()) {
                Map<String, Object> withTitle = new LinkedHashMap<>(mapped);
                withTitle.putIfAbsent("title", name);
                return withTitle;
            }
            return mapped;
        }

        Map<String, Object> out = new LinkedHashMap<>();
        Object type = schema.getType();
        if (type != null) {
            out.put("type", type);
        }
        if (schema.getFormat() != null) {
            out.put("format", schema.getFormat());
        }
        if (schema.getDescription() != null && !schema.getDescription().isBlank()) {
            out.put("description", schema.getDescription());
        }
        if (schema.getEnum() != null && !schema.getEnum().isEmpty()) {
            out.put("enum", new ArrayList<>(schema.getEnum()));
        }
        if (schema.getExample() != null) {
            out.put("example", schema.getExample());
        }

        Map<?, ?> props = schema.getProperties();
        if (props != null && !props.isEmpty()) {
            Map<String, Object> propMaps = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : props.entrySet()) {
                if (e.getKey() == null || !(e.getValue() instanceof Schema)) {
                    continue;
                }
                propMaps.put(String.valueOf(e.getKey()),
                        schemaToMap(openAPI, (Schema) e.getValue(), visiting, depth + 1));
            }
            out.put("properties", propMaps);
            if (type == null) {
                out.putIfAbsent("type", "object");
            }
        }

        if (schema.getItems() != null) {
            out.put("items", schemaToMap(openAPI, schema.getItems(), visiting, depth + 1));
            if (type == null) {
                out.putIfAbsent("type", "array");
            }
        }

        if (schema.getRequired() != null && !schema.getRequired().isEmpty()) {
            out.put("required", new ArrayList<>(schema.getRequired()));
        }

        // allOf：浅合并 properties
        List<?> allOf = schema.getAllOf();
        if (allOf != null && !allOf.isEmpty()) {
            Map<String, Object> mergedProps = new LinkedHashMap<>();
            List<String> mergedReq = new ArrayList<>();
            for (Object partObj : allOf) {
                if (!(partObj instanceof Schema)) {
                    continue;
                }
                Map<String, Object> partMap = schemaToMap(openAPI, (Schema) partObj, visiting, depth + 1);
                Object pp = partMap.get("properties");
                if (pp instanceof Map<?, ?> m) {
                    for (Map.Entry<?, ?> pe : m.entrySet()) {
                        mergedProps.put(String.valueOf(pe.getKey()), pe.getValue());
                    }
                }
                Object rq = partMap.get("required");
                if (rq instanceof List<?> list) {
                    for (Object r : list) {
                        if (r != null) {
                            mergedReq.add(String.valueOf(r));
                        }
                    }
                }
                if (partMap.get("type") != null && !out.containsKey("type")) {
                    out.put("type", partMap.get("type"));
                }
            }
            if (!mergedProps.isEmpty()) {
                Object existing = out.get("properties");
                if (existing instanceof Map<?, ?> em) {
                    Map<String, Object> combined = new LinkedHashMap<>();
                    em.forEach((k, v) -> combined.put(String.valueOf(k), v));
                    combined.putAll(mergedProps);
                    out.put("properties", combined);
                } else {
                    out.put("properties", mergedProps);
                }
                out.putIfAbsent("type", "object");
            }
            if (!mergedReq.isEmpty()) {
                out.put("required", mergedReq);
            }
        }

        return out;
    }

    private static String refName(String ref) {
        // #/components/schemas/Foo
        int idx = ref.lastIndexOf('/');
        if (idx < 0 || idx >= ref.length() - 1) {
            return null;
        }
        return ref.substring(idx + 1);
    }

    private static Schema<?> resolveComponent(OpenAPI openAPI, String name) {
        if (openAPI == null || openAPI.getComponents() == null || openAPI.getComponents().getSchemas() == null) {
            return null;
        }
        return openAPI.getComponents().getSchemas().get(name);
    }
}
