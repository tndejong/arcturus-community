package com.eu.habbo.messages.rcon;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUsersComposer;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

public class DeployBot extends RCONMessage<DeployBot.JSON> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeployBot.class);

    public DeployBot() {
        super(JSON.class);
    }

    @Override
    public void handle(Gson gson, JSON json) {
        try {
            Room room = Emulator.getGameEnvironment().getRoomManager().loadRoom(json.room_id);
            if (room == null) {
                this.status = ROOM_NOT_FOUND;
                this.message = "Room " + json.room_id + " not found";
                return;
            }

            boolean canWalk = json.freeroam;
            String freeroamVal = canWalk ? "1" : "0";

            int botId;
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
                // Reuse existing bot record by name to avoid duplicates across sessions
                int existingId = 0;
                try (PreparedStatement check = connection.prepareStatement(
                        "SELECT id FROM bots WHERE name = ? LIMIT 1")) {
                    check.setString(1, json.name);
                    try (ResultSet rs = check.executeQuery()) {
                        if (rs.next()) existingId = rs.getInt(1);
                    }
                }

                if (existingId > 0) {
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE bots SET room_id=?, motto=?, figure=?, gender=?, x=?, y=?, z=0.0, rot=2, freeroam=?, chat_auto='0', chat_random='0', chat_delay=10 WHERE id=?")) {
                        update.setInt(1, json.room_id);
                        update.setString(2, json.motto != null ? json.motto : "");
                        update.setString(3, json.figure != null ? json.figure : "hd-180-1.ch-210-66.lg-270-110.sh-300-91");
                        update.setString(4, json.gender != null ? json.gender.toUpperCase() : "M");
                        update.setInt(5, json.x);
                        update.setInt(6, json.y);
                        update.setString(7, freeroamVal);
                        update.setInt(8, existingId);
                        update.execute();
                    }
                    botId = existingId;
                } else {
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO bots (user_id, room_id, name, motto, figure, gender, x, y, z, rot, type, freeroam, chat_auto, chat_random, chat_delay) " +
                            "VALUES (0, ?, ?, ?, ?, ?, ?, ?, 0.0, 2, 'generic', ?, '0', '0', 10)",
                            Statement.RETURN_GENERATED_KEYS)) {
                        insert.setInt(1, json.room_id);
                        insert.setString(2, json.name);
                        insert.setString(3, json.motto != null ? json.motto : "");
                        insert.setString(4, json.figure != null ? json.figure : "hd-180-1.ch-210-66.lg-270-110.sh-300-91");
                        insert.setString(5, json.gender != null ? json.gender.toUpperCase() : "M");
                        insert.setInt(6, json.x);
                        insert.setInt(7, json.y);
                        insert.setString(8, freeroamVal);
                        insert.execute();
                        try (ResultSet keys = insert.getGeneratedKeys()) {
                            if (!keys.next()) {
                                this.status = STATUS_ERROR;
                                this.message = "Failed to insert bot into database";
                                return;
                            }
                            botId = keys.getInt(1);
                        }
                    }
                }
            }

            // Load bot from DB and inject into loaded room
            Bot bot;
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                 PreparedStatement stmt = connection.prepareStatement(
                         "SELECT b.*, COALESCE(u.username, 'agent') AS owner_name FROM bots b " +
                         "LEFT JOIN users u ON b.user_id = u.id WHERE b.id = ? LIMIT 1")) {

                stmt.setInt(1, botId);
                try (ResultSet set = stmt.executeQuery()) {
                    if (!set.next()) {
                        this.status = STATUS_ERROR;
                        this.message = "Could not reload bot from database";
                        return;
                    }
                    bot = Emulator.getGameEnvironment().getBotManager().loadBot(set);
                }
            }

            if (bot == null) {
                this.status = STATUS_ERROR;
                this.message = "Failed to load bot instance";
                return;
            }

            // Only inject into the live room if the bot is not already present there
            if (room.getBot(botId) == null) {
                RoomTile tile = room.getLayout().getTile((short) json.x, (short) json.y);
                if (tile == null) tile = room.getLayout().getDoorTile();

                RoomUnit roomUnit = new RoomUnit();
                roomUnit.setRotation(RoomUserRotation.SOUTH);
                roomUnit.setLocation(tile);
                double stackHeight = tile.getStackHeight();
                roomUnit.setPreviousLocationZ(stackHeight);
                roomUnit.setZ(stackHeight);
                roomUnit.setPathFinderRoom(room);
                roomUnit.setRoomUnitType(RoomUnitType.BOT);
                roomUnit.setCanWalk(canWalk);

                bot.setRoomUnit(roomUnit);
                bot.setRoom(room);
                bot.setCanWalk(canWalk);
                bot.needsUpdate(false);

                room.addBot(bot);
                Emulator.getThreading().run(bot);
                room.sendComposer(new RoomUsersComposer(bot).compose());
                room.sendComposer(new RoomUserStatusComposer(bot.getRoomUnit()).compose());
            }

            this.message = String.valueOf(botId);
        } catch (Exception e) {
            this.status = STATUS_ERROR;
            LOGGER.error("Caught exception in DeployBot RCON", e);
        }
    }

    static class JSON {
        public int room_id;
        public String name;
        public String figure;
        public String gender = "M";
        public String motto = "";
        public int x = 0;
        public int y = 0;
        public boolean freeroam = false;
    }
}
