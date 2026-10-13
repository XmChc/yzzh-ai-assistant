package cn.ysmp.ai.chat;

import cn.ysmp.ai.constants.AiClarifyConstants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从助手正文中剥离 {@code ```ai-clarify} 块并解析为表单。
 */
@Slf4j
public final class AiClarifyParser {

    private static final Pattern FENCE = Pattern.compile(
            "```\\s*" + AiClarifyConstants.FENCE_LANG + "\\s*\\n([\\s\\S]*?)```",
            Pattern.CASE_INSENSITIVE);

    private AiClarifyParser() {
    }

    public record Result(String displayContent, AiClarifyForm form) {
        public boolean hasForm() {
            return form != null && form.getFields() != null && !form.getFields().isEmpty();
        }
    }

    public static Result parse(String content, ObjectMapper objectMapper) {
        if (content == null || content.isBlank()) {
            return new Result(content == null ? "" : content, null);
        }
        Matcher m = FENCE.matcher(content);
        AiClarifyForm form = null;
        StringBuffer cleaned = new StringBuffer();
        while (m.find()) {
            String json = m.group(1) == null ? "" : m.group(1).trim();
            if (form == null) {
                form = parseForm(json, objectMapper);
            }
            m.appendReplacement(cleaned, "");
        }
        m.appendTail(cleaned);
        String display = cleaned.toString().replaceAll("\\n{3,}", "\n\n").trim();
        if (form != null && form.getFields() != null && !form.getFields().isEmpty()) {
            if (display.isBlank()) {
                display = form.getTitle() != null && !form.getTitle().isBlank()
                        ? form.getTitle()
                        : "请补充以下信息后继续。";
            }
            return new Result(display, form);
        }
        return new Result(content, null);
    }

    private static AiClarifyForm parseForm(String json, ObjectMapper om) {
        try {
            JsonNode root = om.readTree(json);
            if (root == null || !root.isObject()) {
                return null;
            }
            List<AiClarifyForm.Field> fields = new ArrayList<>();
            JsonNode fieldsNode = root.path("fields");
            if (fieldsNode.isArray()) {
                int n = 0;
                for (JsonNode f : fieldsNode) {
                    if (n >= AiClarifyConstants.MAX_FIELDS) {
                        break;
                    }
                    AiClarifyForm.Field field = toField(f);
                    if (field != null) {
                        fields.add(field);
                        n++;
                    }
                }
            }
            if (fields.isEmpty()) {
                return null;
            }
            String id = text(root, "id");
            if (id == null || id.isBlank()) {
                id = "clarify_" + System.currentTimeMillis();
            }
            return AiClarifyForm.builder()
                    .id(id)
                    .title(text(root, "title"))
                    .description(text(root, "description"))
                    .fields(fields)
                    .build();
        } catch (Exception e) {
            log.debug("[ai] clarify parse failed: {}", e.getMessage());
            return null;
        }
    }

    private static AiClarifyForm.Field toField(JsonNode f) {
        String id = text(f, "id");
        String label = text(f, "label");
        if (id == null || id.isBlank() || label == null || label.isBlank()) {
            return null;
        }
        String type = normalizeType(text(f, "type"));
        boolean required = f.path("required").asBoolean(false);
        List<AiClarifyForm.Option> options = new ArrayList<>();
        JsonNode opts = f.path("options");
        if (opts.isArray()) {
            for (JsonNode o : opts) {
                if (o.isTextual()) {
                    String v = o.asText();
                    options.add(AiClarifyForm.Option.builder().value(v).label(v).build());
                } else if (o.isObject()) {
                    String v = text(o, "value");
                    if (v == null || v.isBlank()) {
                        continue;
                    }
                    String lb = text(o, "label");
                    options.add(AiClarifyForm.Option.builder()
                            .value(v)
                            .label(lb == null || lb.isBlank() ? v : lb)
                            .build());
                }
            }
        }
        if ((AiClarifyConstants.FIELD_SELECT.equals(type)
                || AiClarifyConstants.FIELD_SINGLE.equals(type)
                || AiClarifyConstants.FIELD_MULTI.equals(type))
                && options.isEmpty()) {
            return null;
        }
        return AiClarifyForm.Field.builder()
                .id(id.trim())
                .label(label.trim())
                .type(type)
                .required(required)
                .placeholder(text(f, "placeholder"))
                .defaultValue(text(f, "defaultValue"))
                .options(options.isEmpty() ? null : options)
                .build();
    }

    private static String normalizeType(String raw) {
        if (raw == null || raw.isBlank()) {
            return AiClarifyConstants.FIELD_TEXT;
        }
        String t = raw.trim().toLowerCase(Locale.ROOT);
        return switch (t) {
            case "textarea", "text_area" -> AiClarifyConstants.FIELD_TEXTAREA;
            case "number", "int", "integer" -> AiClarifyConstants.FIELD_NUMBER;
            case "select", "dropdown" -> AiClarifyConstants.FIELD_SELECT;
            case "single", "radio", "choice" -> AiClarifyConstants.FIELD_SINGLE;
            case "multi", "checkbox", "multiple" -> AiClarifyConstants.FIELD_MULTI;
            default -> AiClarifyConstants.FIELD_TEXT;
        };
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asText();
        return s == null || s.isBlank() ? null : s;
    }
}
