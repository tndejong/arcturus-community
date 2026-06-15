package com.eu.habbo.habbohotel.bots;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An AI-powered hotel bot. Each instance is linked to an owner (user_id).
 * When the owner says something in the room, the message is forwarded
 * asynchronously to habbo-ai-service, and the reply is spoken by this bot.
 *
 * Also supports natural-language duet triggers:
 *   "<BotName>, chat with <OtherBot> about <topic>"
 * and automatically stops any active duet when the owner talks to a bot directly.
 *
 * Conversation memory lives in habbo-ai-service (in-memory, resets on service
 * restart). Agent configuration persists in ai_agent_configs (DB).
 */
public class AiBot extends Bot {
    private static final Logger LOGGER = LoggerFactory.getLogger(AiBot.class);

    // Matches "chat/talk/conversation/discuss [anything] (with|to) <BotName> [about <topic>]"
    private static final Pattern DUET_TRIGGER = Pattern.compile(
        "(?:chat|talk|conversation|discuss)\\b.*?\\b(?:with|to)\\b\\s+(\\S+?)(?:\\s+about\\b\\s+(.+))?$",
        Pattern.CASE_INSENSITIVE
    );

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
        int ownerId = this.getOwnerId();
        if (message.getHabbo().getHabboInfo().getId() != ownerId) return;

        String raw = message.getUnfilteredMessage().trim();

        // Must be addressed by name: "<BotName> <message>" or "<BotName>, <message>"
        String prefix = this.getName();
        if (!raw.toLowerCase().startsWith(prefix.toLowerCase())) return;

        // Strip the name prefix and optional punctuation/space
        String text = raw.substring(prefix.length()).replaceFirst("^[,:\\s]+", "").trim();
        if (text.isEmpty()) return;

        Room room = message.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null) return;

        // --- Natural-language duet trigger ---
        // "Pieter, chat with Henk about sports" → starts a duet
        Matcher duetMatch = DUET_TRIGGER.matcher(text);
        if (duetMatch.find()) {
            String targetName = duetMatch.group(1);
            String topic = duetMatch.group(2) != null
                ? duetMatch.group(2).trim().replaceAll("[?.!]+$", "").trim()
                : "general chat";
            if (topic.isEmpty()) topic = "general chat";
            String result = AiConversationManager.startConversation(room, ownerId,
                message.getHabbo().getHabboInfo().getUsername(),
                this.getName(), targetName, topic, 8);
            if (result != null && (result.startsWith("Started") || result.startsWith("You already"))) {
                return; // duet started (or was already running) — don't also send a chat reply
            }
            // fall through: failed to start duet (bot not found etc.) → let AI respond naturally
        }

        // --- Interrupt any active duet so the owner can talk to this bot directly ---
        AiConversationManager.stopConversation(room, ownerId);

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
