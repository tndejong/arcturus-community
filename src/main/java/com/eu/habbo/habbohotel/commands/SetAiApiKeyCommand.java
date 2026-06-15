package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.AgentServiceClient;
import com.eu.habbo.habbohotel.gameclients.GameClient;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * :set_ai_key <api_key> [provider]
 *
 * Stores the player's AI provider API key and verifies it against habbo-ai-service.
 * Provider defaults to "anthropic" if omitted.
 */
public class SetAiApiKeyCommand extends Command {

    public SetAiApiKeyCommand() {
        super("cmd_set_ai_key", new String[]{"set_ai_key"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length < 2) {
            gameClient.getHabbo().alert("Usage: :set_ai_key <api_key>\nExample: :set_ai_key sk-ant-...");
            return false;
        }

        String apiKey = params[1];
        String provider = (params.length >= 3 && !params[2].isEmpty()) ? params[2] : "anthropic";
        int userId = gameClient.getHabbo().getHabboInfo().getId();

        // Persist key to DB first (unverified) so the service can also read it
        upsertApiKey(userId, apiKey, provider, false);

        // Validate with habbo-ai-service (blocking, but runs in command thread which is fine)
        // Skip validation for ElevenLabs — just store the key
        if (!provider.equals("elevenlabs")) {
            String error = AgentServiceClient.setApiKey(userId, apiKey, provider);

            if (error != null) {
                gameClient.getHabbo().alert("API key verification failed: " + error);
                return false;
            }
        }

        // Mark as verified in DB
        upsertApiKey(userId, apiKey, provider, provider.equals("elevenlabs") || true);

        String responseMsg = provider.equals("elevenlabs")
                ? "ElevenLabs API key saved! Bot messages will now be spoken aloud in the hotel."
                : "API key verified! Use :setup_agent <name> <persona> to create an AI agent bot.";

        gameClient.getHabbo().alert(responseMsg);
        return true;
    }

    private void upsertApiKey(int userId, String apiKey, String provider, boolean verified) {
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO ai_api_keys (user_id, provider, api_key, verified) VALUES (?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE provider = VALUES(provider), api_key = VALUES(api_key), " +
                     "verified = VALUES(verified), updated_at = CURRENT_TIMESTAMP")) {
            stmt.setInt(1, userId);
            stmt.setString(2, provider);
            stmt.setString(3, apiKey);
            stmt.setInt(4, verified ? 1 : 0);
            stmt.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to store API key", e);
        }
    }
}
