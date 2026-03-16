package com.eu.habbo.habbohotel.bots;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.RoomChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * An AI-powered hotel bot. Each instance is linked to an owner (user_id).
 * When the owner says something in the room, the message is forwarded
 * asynchronously to habbo-ai-service, and the reply is spoken by this bot.
 *
 * Conversation memory lives in habbo-ai-service (in-memory, resets on service
 * restart). Agent configuration persists in ai_agent_configs (DB).
 */
public class AiBot extends Bot {
    private static final Logger LOGGER = LoggerFactory.getLogger(AiBot.class);

    public AiBot(ResultSet set) throws SQLException {
        super(set);
    }

    public static void initialise() {
        // nothing to preload
    }

    public static void dispose() {
        // nothing to clean up
    }

    @Override
    public void onUserSay(final RoomChatMessage message) {
        if (message.getHabbo() == null) return;

        // Only respond to the owner
        if (message.getHabbo().getHabboInfo().getId() != this.getOwnerId()) return;

        String raw = message.getUnfilteredMessage().trim();

        // Must be addressed by name: "<BotName> <message>" or "<BotName>, <message>"
        String prefix = this.getName();
        if (!raw.toLowerCase().startsWith(prefix.toLowerCase())) return;

        // Strip the name prefix and optional punctuation/space
        String text = raw.substring(prefix.length()).replaceFirst("^[,:\\s]+", "").trim();
        if (text.isEmpty()) return;

        final AiBot self = this;
        final int botId = this.getId();
        final String username = message.getHabbo().getHabboInfo().getUsername();

        Emulator.getThreading().run(() -> {
            try {
                String reply = AgentServiceClient.chat(botId, username, text);
                if (reply != null && !reply.isEmpty()) {
                    self.talk(reply);
                }
            } catch (Exception e) {
                LOGGER.error("AiBot {} failed to get AI reply", botId, e);
            }
        });
    }
}
