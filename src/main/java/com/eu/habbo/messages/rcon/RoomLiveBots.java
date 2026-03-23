package com.eu.habbo.messages.rcon;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.rooms.Room;
import com.google.gson.Gson;
import gnu.trove.iterator.TIntObjectIterator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Returns bot IDs currently spawned in the emulator for a loaded room.
 * Used to distinguish real in-room bots from stale {@code bots.room_id} rows in MySQL.
 */
public class RoomLiveBots extends RCONMessage<RoomLiveBots.JSON> {

    public RoomLiveBots() {
        super(JSON.class);
    }

    @Override
    public void handle(Gson gson, JSON json) {
        try {
            if (json.room_id <= 0) {
                this.status = STATUS_ERROR;
                this.message = "{\"loaded\":false,\"reason\":\"bad_room_id\"}";
                return;
            }

            Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(json.room_id);
            if (room == null || !room.isLoaded()) {
                this.message = "{\"loaded\":false,\"bot_ids\":[]}";
                return;
            }

            List<Integer> ids = new ArrayList<>();
            synchronized (room.getCurrentBots()) {
                TIntObjectIterator<Bot> it = room.getCurrentBots().iterator();
                for (int i = room.getCurrentBots().size(); i-- > 0; ) {
                    try {
                        it.advance();
                        ids.add(it.value().getId());
                    } catch (Exception ignored) {
                        break;
                    }
                }
            }
            Collections.sort(ids);

            LivePayload p = new LivePayload();
            p.loaded = true;
            p.bot_ids = ids;
            this.message = gson.toJson(p);
        } catch (Exception e) {
            this.status = STATUS_ERROR;
            this.message = "{\"loaded\":false,\"error\":true}";
        }
    }

    static class JSON {
        public int room_id;
    }

    static class LivePayload {
        public boolean loaded;
        public List<Integer> bot_ids;
    }
}
