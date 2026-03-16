package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.habbohotel.bots.AiConversationManager;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;

/**
 * :ai_stop
 */
public class AiStopCommand extends Command {
    public AiStopCommand() {
        super("cmd_setup_agent", new String[]{"ai_stop", "stop_ai_chat"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        Room room = gameClient.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null) {
            gameClient.getHabbo().alert("You must be in a room to stop a bot-to-bot conversation.");
            return false;
        }

        String result = AiConversationManager.stopConversation(room, gameClient.getHabbo().getHabboInfo().getId());
        gameClient.getHabbo().alert(result);
        return true;
    }
}
