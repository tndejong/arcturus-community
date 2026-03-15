package com.eu.habbo.messages.rcon;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.rooms.Room;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class DeleteBot extends RCONMessage<DeleteBot.JSON> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeleteBot.class);

    public DeleteBot() {
        super(JSON.class);
    }

    @Override
    public void handle(Gson gson, JSON json) {
        try {
            if (json.bot_id <= 0) {
                this.status = STATUS_ERROR;
                this.message = "Invalid bot_id";
                return;
            }

            Bot activeBot = null;
            Room activeRoom = null;
            for (Room room : Emulator.getGameEnvironment().getRoomManager().getActiveRooms()) {
                Bot candidate = room.getBot(json.bot_id);
                if (candidate != null) {
                    activeBot = candidate;
                    activeRoom = room;
                    break;
                }
            }

            if (activeBot != null && activeRoom != null) {
                activeRoom.removeBot(activeBot);
            }

            int affectedRows;
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement("DELETE FROM bots WHERE id = ? LIMIT 1")) {
                statement.setInt(1, json.bot_id);
                affectedRows = statement.executeUpdate();
            }

            if (affectedRows == 0 && activeBot == null) {
                this.status = HABBO_NOT_FOUND;
                this.message = "Bot " + json.bot_id + " not found";
                return;
            }

            this.message = "deleted";
        } catch (Exception e) {
            this.status = STATUS_ERROR;
            LOGGER.error("Caught exception in DeleteBot RCON", e);
        }
    }

    static class JSON {
        public int bot_id;
    }
}
