package com.eu.habbo.habbohotel.bots;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import gnu.trove.iterator.TIntObjectIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coordinates short, turn-based AI bot conversations in a room.
 * One active conversation per (room, owner) to keep behavior predictable.
 */
public class AiConversationManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(AiConversationManager.class);
    private static final int DEFAULT_MAX_TURNS = 8;
    private static final int MIN_TURNS = 2;
    private static final int MAX_TURNS = 20;
    private static final long TURN_DELAY_MS = 2000;

    private static final Map<String, ConversationSession> ACTIVE = new ConcurrentHashMap<>();

    private AiConversationManager() {
    }

    public static synchronized String startConversation(Room room, int ownerId, String ownerName, String botAName, String botBName, String topic, int requestedTurns) {
        if (room == null) return "You must be in a room.";
        if (botAName == null || botAName.trim().isEmpty() || botBName == null || botBName.trim().isEmpty()) {
            return "Please provide two bot names.";
        }
        if (botAName.equalsIgnoreCase(botBName)) {
            return "Please provide two different bots.";
        }
        if (topic == null || topic.trim().isEmpty()) {
            return "Please provide a topic for the conversation.";
        }

        String key = key(room.getId(), ownerId);
        if (ACTIVE.containsKey(key)) {
            return "You already have an active bot-to-bot conversation in this room. Use :ai_stop first.";
        }

        AiBot botA = findOwnedAiBotInRoom(room, ownerId, botAName);
        AiBot botB = findOwnedAiBotInRoom(room, ownerId, botBName);

        if (botA == null) return "Bot '" + botAName + "' was not found in this room or is not your AI bot.";
        if (botB == null) return "Bot '" + botBName + "' was not found in this room or is not your AI bot.";

        int maxTurns = Math.min(MAX_TURNS, Math.max(MIN_TURNS, requestedTurns > 0 ? requestedTurns : DEFAULT_MAX_TURNS));

        ConversationSession session = new ConversationSession(room.getId(), ownerId, ownerName, botA, botB, topic.trim(), maxTurns);
        ACTIVE.put(key, session);

        // Kick off first turn asynchronously.
        Emulator.getThreading().run(() -> runTurn(session), 500);

        return "Started conversation between " + botA.getName() + " and " + botB.getName() + " about '" + session.topic + "' (" + maxTurns + " turns max).";
    }

    public static synchronized String stopConversation(Room room, int ownerId) {
        if (room == null) return "You must be in a room.";

        ConversationSession removed = ACTIVE.remove(key(room.getId(), ownerId));
        if (removed == null) return "No active bot-to-bot conversation found in this room.";

        removed.active = false;
        return "Stopped the active bot-to-bot conversation.";
    }

    private static void runTurn(ConversationSession session) {
        if (!session.active) return;

        String key = key(session.roomId, session.ownerId);
        if (ACTIVE.get(key) != session) return;

        if (session.turnCount >= session.maxTurns) {
            finish(session, "Conversation ended after " + session.maxTurns + " turns.");
            return;
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(session.roomId);
        if (room == null || !room.isLoaded()) {
            finish(session, "Conversation stopped because the room is no longer active.");
            return;
        }

        AiBot speaker = session.nextSpeakerIsA ? resolveBot(room, session.botAId) : resolveBot(room, session.botBId);
        AiBot opponent = session.nextSpeakerIsA ? resolveBot(room, session.botBId) : resolveBot(room, session.botAId);

        if (speaker == null || opponent == null) {
            finish(session, "Conversation stopped because one of the bots is no longer in the room.");
            return;
        }

        String instruction = buildTurnInstruction(session, speaker, opponent);
        String response;

        try {
            response = AgentServiceClient.chat(speaker.getId(), opponent.getName(), instruction);
        } catch (Exception e) {
            LOGGER.error("Failed bot-to-bot turn for speaker {}", speaker.getId(), e);
            response = null;
        }

        if (response == null || response.trim().isEmpty()) {
            finish(session, "Conversation stopped because AI service did not return a response.");
            return;
        }

        String spoken = ensureOpponentPrefix(response.trim(), opponent.getName());
        speaker.talk(spoken);

        session.lastSpokenMessage = spoken;
        session.turnCount++;
        session.nextSpeakerIsA = !session.nextSpeakerIsA;

        Emulator.getThreading().run(() -> runTurn(session), TURN_DELAY_MS);
    }

    private static String buildTurnInstruction(ConversationSession session, AiBot speaker, AiBot opponent) {
        if (session.turnCount == 0) {
            return "Start a short conversation with " + opponent.getName() + " about this topic: " + session.topic + ". "
                    + "Ask one clear question. Start your sentence with '" + opponent.getName() + ",'. Keep it concise.";
        }

        return "You are in a turn-based conversation with " + opponent.getName() + " about '" + session.topic + "'. "
                + "Reply to this latest message: \"" + session.lastSpokenMessage + "\". "
                + "Ask exactly one follow-up question and start with '" + opponent.getName() + ",'. Keep it concise.";
    }

    private static String ensureOpponentPrefix(String text, String opponentName) {
        String prefix = opponentName + ",";
        if (text.regionMatches(true, 0, opponentName, 0, opponentName.length())) {
            return text;
        }
        return prefix + " " + text;
    }

    private static synchronized void finish(ConversationSession session, String reason) {
        String key = key(session.roomId, session.ownerId);
        ConversationSession active = ACTIVE.get(key);
        if (active == session) {
            ACTIVE.remove(key);
        }
        session.active = false;

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(session.roomId);
        if (room != null) {
            if (session.ownerId > 0 && room.getHabbo(session.ownerId) != null) {
                room.getHabbo(session.ownerId).whisper(reason);
            }
        }
    }

    private static AiBot resolveBot(Room room, int botId) {
        if (room == null || botId <= 0) return null;
        Bot bot = room.getBot(botId);
        if (!(bot instanceof AiBot)) return null;
        return (AiBot) bot;
    }

    private static AiBot findOwnedAiBotInRoom(Room room, int ownerId, String name) {
        if (room == null || name == null) return null;
        TIntObjectIterator<Bot> iterator = room.getCurrentBots().iterator();

        for (int i = room.getCurrentBots().size(); i-- > 0; ) {
            iterator.advance();
            Bot bot = iterator.value();
            if (bot == null) continue;
            if (!(bot instanceof AiBot)) continue;
            if (bot.getOwnerId() != ownerId) continue;
            if (!bot.getName().equalsIgnoreCase(name)) continue;

            return (AiBot) bot;
        }

        return null;
    }

    private static String key(int roomId, int ownerId) {
        return roomId + ":" + ownerId;
    }

    private static class ConversationSession {
        final int roomId;
        final int ownerId;
        final String ownerName;
        final int botAId;
        final int botBId;
        final String topic;
        final int maxTurns;
        volatile boolean active = true;
        volatile int turnCount = 0;
        volatile boolean nextSpeakerIsA = true;
        volatile String lastSpokenMessage = "";

        ConversationSession(int roomId, int ownerId, String ownerName, AiBot botA, AiBot botB, String topic, int maxTurns) {
            this.roomId = roomId;
            this.ownerId = ownerId;
            this.ownerName = ownerName;
            this.botAId = botA.getId();
            this.botBId = botB.getId();
            this.topic = topic;
            this.maxTurns = maxTurns;
        }
    }
}
