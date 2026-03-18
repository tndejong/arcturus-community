package com.eu.habbo.messages.rcon;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboGender;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUsersComposer;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Updates any combination of a bot's name, motto, figure, and gender live
 * in the hotel without removing or re-adding it. Finds the bot in active
 * rooms, applies changes in memory, and broadcasts RoomUsersComposer.
 */
public class UpdateBotVisuals extends RCONMessage<UpdateBotVisuals.JSON> {
    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateBotVisuals.class);

    public UpdateBotVisuals() {
        super(JSON.class);
    }

    @Override
    public void handle(Gson gson, JSON json) {
        if (json.bot_id <= 0) {
            this.status = STATUS_ERROR;
            this.message = "Invalid bot_id";
            return;
        }

        for (Room room : Emulator.getGameEnvironment().getRoomManager().getActiveRooms()) {
            Bot bot = room.getBot(json.bot_id);
            if (bot != null) {
                boolean needsBroadcast = false;

                if (json.name != null && !json.name.equals(bot.getName())) {
                    bot.setName(json.name);
                    needsBroadcast = true;
                }
                if (json.motto != null && !json.motto.equals(bot.getMotto())) {
                    bot.setMotto(json.motto);
                    needsBroadcast = true;
                }
                if (json.figure != null && !json.figure.equals(bot.getFigure())) {
                    bot.setFigure(json.figure); // broadcasts internally
                    needsBroadcast = false; // already sent by setFigure
                }
                if (json.gender != null) {
                    HabboGender g = HabboGender.valueOf(json.gender.toUpperCase());
                    if (g != bot.getGender()) {
                        bot.setGender(g); // broadcasts internally
                        needsBroadcast = false; // already sent by setGender
                    }
                }

                if (needsBroadcast) {
                    room.sendComposer(new RoomUsersComposer(bot).compose());
                }

                this.message = "updated live";
                return;
            }
        }

        this.message = "bot not in active room; changes apply on next room load";
    }

    static class JSON {
        public int bot_id;
        public String name;
        public String motto;
        public String figure;
        public String gender;
    }
}
