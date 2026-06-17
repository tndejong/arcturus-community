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
 *   "<BotName> ask <OtherBot> how to ..."
 *   "<BotName> tell <OtherBot> about <topic>"
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

    // Matches "ask/tell <BotName> [about] <topic>" — e.g. "ask Henk how to check in".
    private static final Pattern ASK_TRIGGER = Pattern.compile(
        "^(?:ask|tell)\\s+(\\S+?)(?:\\s+about)?\\s+(.+)$",
        Pattern.CASE_INSENSITIVE
    );

    // Optional greeting/filler allowed before the bot's name, so "Hey Henk ...",
    // "Hi Henk ...", "Hallo Henk ..." are still recognised as addressing the bot.
    private static final Pattern GREETING_PREFIX = Pattern.compile(
        "^(?:hey|hi|hello|hellow|yo|hoi|hai|hallo|hej|hola|ok|oke|okay|so|well)\\b[,!\\s]+",
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

        // Allow an optional greeting before the name: "Hey Henk ...", "Hi Henk ..."
        String addressed = GREETING_PREFIX.matcher(raw).replaceFirst("");

        // Must be addressed by name: "<BotName> <message>" or "<BotName>, <message>".
        // Require a word boundary after the name so "Henkie" doesn't trigger "Henk".
        String prefix = this.getName();
        if (!startsWithName(addressed, prefix)) return;

        // Strip the name prefix and optional punctuation/space
        String text = addressed.substring(prefix.length()).replaceFirst("^[,:\\s]+", "").trim();
        if (text.isEmpty()) return;

        Room room = message.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null) return;

        // --- Natural-language duet triggers ---
        // "Pieter, chat with Henk about sports" / "Pieter ask Henk how to check in" → starts a duet
        String targetName = null;
        String rawTopic = null;

        Matcher duetMatch = DUET_TRIGGER.matcher(text);
        if (duetMatch.find()) {
            targetName = duetMatch.group(1);
            rawTopic = duetMatch.group(2);
        } else {
            Matcher askMatch = ASK_TRIGGER.matcher(text);
            if (askMatch.find()) {
                targetName = askMatch.group(1);
                rawTopic = askMatch.group(2);
            }
        }

        if (targetName != null) {
            // Trim trailing punctuation from the name (e.g. "Henk,")
            targetName = targetName.replaceAll("[,:;.]+$", "").trim();

            String topic = rawTopic != null
                ? rawTopic.trim().replaceAll("[?.!]+$", "").trim()
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

    /**
     * True when {@code message} begins with {@code name} as a whole word —
     * i.e. the name is either the entire message or is followed by a
     * non-letter/digit character (space, comma, "?", etc.). This prevents
     * names like "Henk" from matching unrelated words such as "Henkie".
     */
    private static boolean startsWithName(String message, String name) {
        if (message == null || name == null || name.isEmpty()) return false;
        if (message.length() < name.length()) return false;
        if (!message.regionMatches(true, 0, name, 0, name.length())) return false;
        if (message.length() == name.length()) return true;
        return !Character.isLetterOrDigit(message.charAt(name.length()));
    }
}
