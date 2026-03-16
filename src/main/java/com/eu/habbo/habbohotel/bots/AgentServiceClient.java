package com.eu.habbo.habbohotel.bots;

import com.eu.habbo.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Thin HTTP client for talking to habbo-ai-service.
 * All methods are synchronous and must be called from a background thread.
 */
public class AgentServiceClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentServiceClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();

    private static String baseUrl() {
        return Emulator.getConfig().getValue("agent.service.url", "http://127.0.0.1:3002");
    }

    /**
     * Validates the given API key with habbo-ai-service and stores it in the DB.
     * @return null on success, or an error message string on failure.
     */
    public static String setApiKey(int userId, String apiKey, String provider) {
        String body = String.format(
                "{\"user_id\":%d,\"api_key\":\"%s\",\"provider\":\"%s\"}",
                userId, escapeJson(apiKey), escapeJson(provider)
        );
        return post("/api/set-api-key", body);
    }

    /**
     * Initialises an in-memory agent session in habbo-ai-service.
     * @return null on success, or an error message string on failure.
     */
    public static String initSession(int botId, int userId, String persona, String apiKey, String provider) {
        String body = String.format(
                "{\"bot_id\":%d,\"user_id\":%d,\"persona\":\"%s\",\"api_key\":\"%s\",\"provider\":\"%s\"}",
                botId, userId, escapeJson(persona), escapeJson(apiKey), escapeJson(provider)
        );
        return post("/api/init-session", body);
    }

    /**
     * Sends a chat message to the agent session and returns the AI reply.
     * @return the reply text, or null if the call failed.
     */
    public static String chat(int botId, String username, String message) {
        String body = String.format(
                "{\"bot_id\":%d,\"username\":\"%s\",\"message\":\"%s\"}",
                botId, escapeJson(username), escapeJson(message)
        );
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl() + "/api/chat"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return extractJsonField(response.body(), "response");
            } else {
                LOGGER.warn("habbo-ai-service /api/chat returned {}: {}", response.statusCode(), response.body());
                return null;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to call habbo-ai-service /api/chat for bot {}", botId, e);
            return null;
        }
    }

    /** Posts JSON and returns null on ok=true, or the error message on failure. */
    private static String post(String path, String jsonBody) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl() + path))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String okField = extractJsonField(response.body(), "ok");
                if ("true".equals(okField)) return null;
            }

            String errorField = extractJsonField(response.body(), "error");
            return errorField != null ? errorField : "Unknown error (HTTP " + response.statusCode() + ")";
        } catch (Exception e) {
            LOGGER.error("Failed to call habbo-ai-service {}", path, e);
            return "Service unavailable: " + e.getMessage();
        }
    }

    /** Minimal JSON field extractor — avoids adding a JSON library dependency. */
    private static String extractJsonField(String json, String field) {
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

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
