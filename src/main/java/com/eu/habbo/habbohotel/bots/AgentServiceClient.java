package com.eu.habbo.habbohotel.bots;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.ai.JsonUtil;
import com.eu.habbo.habbohotel.ai.PortalClient;
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
 *
 * The AI service no longer receives API keys — it resolves them from the portal
 * by habbo user id. These calls only carry the shared service secret.
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
     * Initialises an in-memory agent session in habbo-ai-service. The service
     * resolves the Anthropic key from the portal using the user id.
     * @return null on success, or an error message string on failure.
     */
    public static String initSession(int botId, int userId, String persona, String provider) {
        String body = String.format(
                "{\"bot_id\":%d,\"user_id\":%d,\"persona\":\"%s\",\"provider\":\"%s\"}",
                botId, userId, JsonUtil.escape(persona), JsonUtil.escape(provider)
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
                botId, JsonUtil.escape(username), JsonUtil.escape(message)
        );
        long t0 = System.currentTimeMillis();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl() + "/api/chat"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("X-Internal-Secret", PortalClient.secret())
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

            long elapsed = System.currentTimeMillis() - t0;
            if (response.statusCode() == 200) {
                String reply = JsonUtil.extractField(response.body(), "response");
                LOGGER.info("[TIMING] AgentServiceClient.chat bot={} ms={} replyLen={}", botId, elapsed, reply != null ? reply.length() : 0);
                return reply;
            } else {
                LOGGER.warn("[TIMING] habbo-ai-service /api/chat bot={} ms={} status={} body={}", botId, elapsed, response.statusCode(), response.body());
                return null;
            }
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - t0;
            LOGGER.error("[TIMING] Failed to call habbo-ai-service /api/chat for bot={} ms={}", botId, elapsed, e);
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
                    .header("X-Internal-Secret", PortalClient.secret())
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String okField = JsonUtil.extractField(response.body(), "ok");
                if ("true".equals(okField)) return null;
            }

            String errorField = JsonUtil.extractField(response.body(), "error");
            return errorField != null ? errorField : "Unknown error (HTTP " + response.statusCode() + ")";
        } catch (Exception e) {
            LOGGER.error("Failed to call habbo-ai-service {}", path, e);
            return "Service unavailable: " + e.getMessage();
        }
    }
}
