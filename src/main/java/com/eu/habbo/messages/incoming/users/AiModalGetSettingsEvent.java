package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.users.AiModalSettingsComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AiModalGetSettingsEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AiModalGetSettingsEvent.class);

    @Override
    public void handle() {
        if (this.client == null || this.client.getHabbo() == null) return;

        int userId = this.client.getHabbo().getHabboInfo().getId();

        String provider = "anthropic";
        String apiKey = "";
        boolean verified = false;
        String elevenlabsKey = "";
        String elevenlabsVoiceId = "";

        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
            // Load Anthropic key
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT api_key, verified FROM ai_api_keys " +
                            "WHERE user_id = ? AND provider = 'anthropic' LIMIT 1")) {
                stmt.setInt(1, userId);
                try (ResultSet set = stmt.executeQuery()) {
                    if (set.next()) {
                        apiKey = set.getString("api_key");
                        verified = set.getInt("verified") == 1;
                    }
                }
            }

            // Load ElevenLabs key + voice ID
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT api_key, voice_id FROM ai_api_keys " +
                            "WHERE user_id = ? AND provider = 'elevenlabs' LIMIT 1")) {
                stmt.setInt(1, userId);
                try (ResultSet set = stmt.executeQuery()) {
                    if (set.next()) {
                        elevenlabsKey = set.getString("api_key");
                        elevenlabsVoiceId = set.getString("voice_id");
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to load AI modal settings for user {}", userId, e);
        }

        this.client.sendResponse(new AiModalSettingsComposer(provider, apiKey, verified, elevenlabsKey, elevenlabsVoiceId));
    }
}
