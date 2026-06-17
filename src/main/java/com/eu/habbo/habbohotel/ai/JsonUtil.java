package com.eu.habbo.habbohotel.ai;

/**
 * Minimal JSON helpers shared by the hotel's HTTP clients (AgentServiceClient,
 * PortalClient) so we avoid pulling in a JSON library for a handful of fields.
 */
public final class JsonUtil {

    private JsonUtil() {
    }

    /** Extracts a top-level string/scalar field value from a flat JSON object. */
    public static String extractField(String json, String field) {
        if (json == null) return null;
        String search = "\"" + field + "\":";
        int idx = json.indexOf(search);
        if (idx == -1) return null;
        int start = idx + search.length();
        if (start >= json.length()) return null;

        char first = json.charAt(start);
        if (first == '"') {
            int end = json.indexOf('"', start + 1);
            return end == -1 ? null : json.substring(start + 1, end);
        } else {
            int end = json.indexOf(',', start);
            if (end == -1) end = json.indexOf('}', start);
            return end == -1 ? json.substring(start) : json.substring(start, end).trim();
        }
    }

    /** Escapes a string for safe inclusion inside a JSON string literal. */
    public static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
