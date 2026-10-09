package com.medislot.service.external;

import tools.jackson.databind.JsonNode;

import java.util.Map;

/**
 * 极简 JSON 路径解析与占位符替换（避免引入额外的 JSONPath 依赖）。
 *
 * <p>路径语法：以 {@code .} 分隔，数组下标写 {@code name[0]} 或 {@code [0]}；
 * {@code "."} 或空串表示当前节点。
 */
public final class JsonPaths {

    private JsonPaths() {
    }

    public static JsonNode at(JsonNode node, String path) {
        if (node == null) {
            return null;
        }
        if (path == null || path.isBlank() || ".".equals(path)) {
            return node;
        }
        JsonNode cur = node;
        for (String seg : path.split("\\.")) {
            if (cur == null || cur.isMissingNode() || cur.isNull()) {
                return null;
            }
            int br = seg.indexOf('[');
            if (br >= 0 && seg.endsWith("]")) {
                String name = seg.substring(0, br);
                if (!name.isEmpty()) {
                    cur = cur.get(name);
                }
                int idx;
                try {
                    idx = Integer.parseInt(seg.substring(br + 1, seg.length() - 1));
                } catch (NumberFormatException e) {
                    return null;
                }
                if (cur == null || !cur.isArray() || idx < 0 || idx >= cur.size()) {
                    return null;
                }
                cur = cur.get(idx);
            } else {
                cur = cur.get(seg);
            }
        }
        return cur;
    }

    public static String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        return node.asText();
    }

    /** 用 vars 替换模板中的 {{key}}；未提供的占位符一律清空。 */
    public static String subst(String template, Map<String, String> vars) {
        if (template == null) {
            return "";
        }
        String out = template;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            String v = e.getValue() == null ? "" : e.getValue();
            out = out.replace("{{" + e.getKey() + "}}", v);
        }
        // 清掉未识别的占位符，避免残留 {{xxx}}
        return out.replaceAll("\\{\\{[^{}]+\\}\\}", "");
    }
}
