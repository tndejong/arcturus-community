package com.eu.habbo.habbohotel.ai;

import com.eu.habbo.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Service-to-service HTTP client for the portal's internal API. Authenticates
 * with the shared service secret (X-Internal-Secret). The portal is the single
 * source of truth for API keys, so the hotel resolves credentials and mints
 * user tokens here instead of storing keys locally.
 *
 * All methods are synchronous and must be called from a background thread.
 */
public class PortalClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(PortalClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();

    private static String baseUrl() {
        return Emulator.getConfig().getValue("portal.internal.url", "http://agent-portal:3000");
    }

    public static String secret() {
        return Emulator.getConfig().getValue("portal.internal.secret", "");
    }

    /**
     * Mints a short-lived portal bearer token for the in-hotel Nitro client.
     * The user has already been authenticated by the emulator (SSO), so the
     * portal trusts the service secret to mint on their behalf.
     *
     * @return the JWT string, or null on failure.
     */
    public static String mintHotelToken(int habboUserId) {
        String body = String.format("{\"habbo_user_id\":%d}", habboUserId);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl() + "/api/internal/hotel-token"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("X-Internal-Secret", secret())
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return JsonUtil.extractField(response.body(), "token");
            }
            LOGGER.warn("Portal /api/internal/hotel-token returned status {} body {}", response.statusCode(), response.body());
        } catch (Exception e) {
            LOGGER.error("Failed to mint hotel token for user {}", habboUserId, e);
        }
        return null;
    }

    /**
     * Resolves a portal-stored API key for the given habbo user.
     *
     * @return the plaintext key, or null when absent / on failure.
     */
    public static String fetchApiKey(int habboUserId, String provider) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl() + "/api/internal/hotel-user/" + habboUserId + "/api-key/" + provider))
                    .timeout(TIMEOUT)
                    .header("X-Internal-Secret", secret())
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return JsonUtil.extractField(response.body(), "api_key");
            }
            LOGGER.warn("Portal api-key lookup returned status {} for user {}", response.statusCode(), habboUserId);
        } catch (Exception e) {
            LOGGER.error("Failed to fetch {} key for user {}", provider, habboUserId, e);
        }
        return null;
    }
}
