package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * :set_ai_voice_id <voice_id>
 *
 * Sets the default ElevenLabs voice ID for the current user.
 * Requires an existing elevenlabs API key row in ai_api_keys.
 */
public class SetAiVoiceIdCommand extends Command {

    public SetAiVoiceIdCommand() {
        super(null, new String[]{"set_ai_voice_id"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length < 2) {
            gameClient.getHabbo().alert("Usage: :set_ai_voice_id <voice_id>\nExample: :set_ai_voice_id EXAVITQu4vr4xnSDxMaL");
            return false;
        }

        String voiceId = params[1];
        int userId = gameClient.getHabbo().getHabboInfo().getId();

        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE ai_api_keys SET voice_id = ?, updated_at = CURRENT_TIMESTAMP " +
                     "WHERE user_id = ? AND provider = 'elevenlabs'")) {
            stmt.setString(1, voiceId);
            stmt.setInt(2, userId);
            int rows = stmt.executeUpdate();

            if (rows == 0) {
                gameClient.getHabbo().alert("No ElevenLabs API key found. Save an ElevenLabs key first using :set_ai_key <key> elevenlabs.");
                return false;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to store voice ID", e);
        }

        gameClient.getHabbo().alert("ElevenLabs voice ID saved!");
        return true;
    }
}
