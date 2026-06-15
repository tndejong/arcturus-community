package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.habbohotel.bots.AiConversationManager;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * :ai_next — signals the active bot duet to advance to the next turn
 * after the client's TTS audio has finished playing.
 */
public class AiNextCommand extends Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(AiNextCommand.class);

    public AiNextCommand() {
        super(null, new String[]{"ai_next"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        long t0 = System.currentTimeMillis();
        Room room = gameClient.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null) return false;

        boolean signaled = AiConversationManager.signalNextTurn(room, gameClient.getHabbo().getHabboInfo().getId());
        LOGGER.info("[TIMING] AiNextCommand.handle room={} signaled={} ms={}", room.getId(), signaled, System.currentTimeMillis() - t0);
        return true;
    }
}
