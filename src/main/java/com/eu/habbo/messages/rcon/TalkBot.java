package com.eu.habbo.messages.rcon;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.rooms.Room;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TalkBot extends RCONMessage<TalkBot.JSON> {
    private static final Logger LOGGER = LoggerFactory.getLogger(TalkBot.class);

    public TalkBot() {
        super(JSON.class);
    }

    @Override
    public void handle(Gson gson, JSON json) {
        try {
            Bot bot = null;

            for (Room room : Emulator.getGameEnvironment().getRoomManager().getActiveRooms()) {
                bot = room.getBot(json.bot_id);
                if (bot != null) break;
            }

            if (bot == null) {
                this.status = HABBO_NOT_FOUND;
                this.message = "Bot " + json.bot_id + " not found in any loaded room";
                return;
            }

            switch (json.type.toLowerCase()) {
                case "shout":
                    bot.shout(json.message);
                    break;
                case "talk":
                default:
                    bot.talk(json.message);
                    break;
            }
        } catch (Exception e) {
            this.status = STATUS_ERROR;
            LOGGER.error("Caught exception in TalkBot RCON", e);
        }
    }

    static class JSON {
        public int bot_id;
        public String message;
        public String type = "talk";
    }
}
