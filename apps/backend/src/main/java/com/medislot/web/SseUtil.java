package com.medislot.web;

import java.io.PrintWriter;

/**
 * SSE 输出工具（text/event-stream）。
 */
final class SseUtil {

    private SseUtil() {
    }

    static void write(PrintWriter writer, String event, String data) {
        writer.print("event: " + event + "\n");
        writer.print("data: " + data + "\n\n");
        writer.flush();
    }

    /** 最小 JSON 字符串转义（避免额外依赖）。 */
    static String jsonString(String s) {
        if (s == null) {
            return "\"\"";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
