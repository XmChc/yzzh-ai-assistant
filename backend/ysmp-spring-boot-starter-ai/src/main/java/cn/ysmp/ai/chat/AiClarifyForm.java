package cn.ysmp.ai.chat;

import lombok.Builder;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@Builder
public class AiClarifyForm {

    private String id;
    private String title;
    private String description;
    private List<Field> fields;

    @Data
    @Builder
    public static class Field {
        private String id;
        private String label;
        private String type;
        private boolean required;
        private String placeholder;
        private String defaultValue;
        private List<Option> options;

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("label", label);
            m.put("type", type);
            m.put("required", required);
            if (placeholder != null) {
                m.put("placeholder", placeholder);
            }
            if (defaultValue != null) {
                m.put("defaultValue", defaultValue);
            }
            if (options != null && !options.isEmpty()) {
                m.put("options", options.stream().map(Option::toMap).collect(Collectors.toList()));
            }
            return m;
        }
    }

    @Data
    @Builder
    public static class Option {
        private String value;
        private String label;

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("value", value);
            m.put("label", label != null ? label : value);
            return m;
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        if (title != null) {
            m.put("title", title);
        }
        if (description != null) {
            m.put("description", description);
        }
        if (fields != null) {
            m.put("fields", fields.stream().map(Field::toMap).collect(Collectors.toList()));
        }
        return m;
    }
}
