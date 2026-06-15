package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.AiBot;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.bots.AgentServiceClient;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUsersComposer;
import gnu.trove.map.hash.THashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * :setup_agent <name> [type:<figure_type>] [x:<tile_x> y:<tile_y>] <persona...>
 *
 * Creates an AiBot in the player's current room, adjacent to the player.
 * The bot persona is the joined remaining params after the name.
 * Persists the configuration in ai_agent_configs so it auto-restores on restart.
 */
public class SetupAgentCommand extends Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(SetupAgentCommand.class);

    private static final String DEFAULT_FIGURE = "hd-180-1.ch-210-66.lg-270-110.sh-300-91";
    private static final String DEFAULT_FIGURE_TYPE = "default";
    private static final Map<String, String> BUILTIN_FIGURE_TYPES = new HashMap<>();

    static {
        BUILTIN_FIGURE_TYPES.put("default", DEFAULT_FIGURE);
        BUILTIN_FIGURE_TYPES.put("citizen", "hd-180-1.ch-210-66.lg-270-110.sh-300-91.ha-1012-110.hr-828-61");
        BUILTIN_FIGURE_TYPES.put("agent", "hd-3095-12.ch-255-64.lg-3235-96.sh-295-91.ha-3426-110.hr-3531-61.he-1601-0.ea-3169-0.fa-1211-1408.cp-3310-0.cc-3007-0.ca-1809-0.wa-2007-0");
        BUILTIN_FIGURE_TYPES.put("bouncer", "ca-1809.cc-3007-82.ch-255-82.cp-3119-82.ea-3169-62.fa-1211-62.ha-1012-110.hd-3095-1.he-1601-62.hr-828-35.lg-3202-110.sh-290-91.wa-2007");
        BUILTIN_FIGURE_TYPES.put("m-employee", "cc-3007-62.ch-265-82.ea-1403-62.hd-3095-8.hr-155-61.lg-285-90.sh-300-91.wa-2007");
    }

    public SetupAgentCommand() {
        super("cmd_setup_agent", new String[]{"setup_agent"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length < 3) {
            gameClient.getHabbo().alert("Usage: :setup_agent <name> [type:<figure_type>] [x:<tile_x> y:<tile_y>] <persona description...>\n" +
                    "Examples:\n" +
                    "  :setup_agent Aria A helpful and friendly assistant\n" +
                    "  :setup_agent Aria type:agent A helpful and friendly assistant\n" +
                    "  :setup_agent Aria type:agent x:12 y:8 A helpful and friendly assistant\n" +
                    "Available figure types: " + listFigureTypes());
            return false;
        }

        Room room = gameClient.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null) {
            gameClient.getHabbo().alert("You must be in a room to create an AI agent.");
            return false;
        }

        String botName = params[1];
        String figureType = DEFAULT_FIGURE_TYPE;
        String figure = DEFAULT_FIGURE;
        Integer spawnX = null;
        Integer spawnY = null;

        int personaStartIndex = 2;

        while (personaStartIndex < params.length) {
            String token = params[personaStartIndex];

            if (isFigureTypeToken(token)) {
                figureType = extractFigureType(token);
                String resolved = resolveFigureByType(figureType);
                if (resolved == null) {
                    gameClient.getHabbo().alert("Unknown figure_type '" + figureType + "'. Available: " + listFigureTypes());
                    return false;
                }
                figure = resolved;
                personaStartIndex++;
                continue;
            }

            if (isSpawnXToken(token)) {
                Integer value = parseSpawnCoordinate(extractSpawnValue(token));
                if (value == null) {
                    gameClient.getHabbo().alert("Invalid x coordinate. Use x:<0-" + Short.MAX_VALUE + ">.");
                    return false;
                }
                spawnX = value;
                personaStartIndex++;
                continue;
            }

            if (isSpawnYToken(token)) {
                Integer value = parseSpawnCoordinate(extractSpawnValue(token));
                if (value == null) {
                    gameClient.getHabbo().alert("Invalid y coordinate. Use y:<0-" + Short.MAX_VALUE + ">.");
                    return false;
                }
                spawnY = value;
                personaStartIndex++;
                continue;
            }

            break;
        }

        if ((spawnX == null) != (spawnY == null)) {
            gameClient.getHabbo().alert("When setting custom spawn, provide both x and y.\n" +
                    "Example: :setup_agent Aria x:12 y:8 Helpful assistant");
            return false;
        }

        if (params.length <= personaStartIndex) {
            gameClient.getHabbo().alert("You must provide a persona.\n" +
                    "Usage: :setup_agent <name> [type:<figure_type>] [x:<tile_x> y:<tile_y>] <persona description...>");
            return false;
        }

        StringBuilder personaBuilder = new StringBuilder();
        for (int i = personaStartIndex; i < params.length; i++) {
            if (i > personaStartIndex) personaBuilder.append(" ");
            personaBuilder.append(params[i]);
        }
        String persona = personaBuilder.toString();
        int userId = gameClient.getHabbo().getHabboInfo().getId();

        // Look up the verified API key for this user
        ApiKeyRow keyRow = loadApiKey(userId);
        if (keyRow == null) {
            gameClient.getHabbo().alert("No verified AI API key found. Run :set_ai_key <key> first.");
            return false;
        }

        RoomTile spawnTile;
        if (spawnX != null) {
            RoomTile requestedTile = room.getLayout().getTile(spawnX.shortValue(), spawnY.shortValue());
            if (!isValidSpawnTile(room, requestedTile)) {
                gameClient.getHabbo().alert("Custom spawn tile (" + spawnX + ", " + spawnY + ") is invalid or occupied.");
                return false;
            }
            spawnTile = requestedTile;
        } else {
            // Prefer spawning on the tile the user is facing (useful for chairs), then fallback to adjacent tiles.
            spawnTile = findPreferredSpawnTile(room, gameClient.getHabbo().getRoomUnit());
        }

        if (spawnTile == null) {
            gameClient.getHabbo().alert("No free tile found to place the agent bot. Clear some space first.");
            return false;
        }

        // Deploy the AiBot using the DeployBot pattern (direct INSERT + loadBot + inject)
        int botId = insertBot(userId, room.getId(), botName, figure, spawnTile);
        if (botId < 0) {
            gameClient.getHabbo().alert("Failed to create the agent bot. Please try again.");
            return false;
        }

        Bot bot = loadAndInjectBot(botId, room, spawnTile);
        if (bot == null) {
            gameClient.getHabbo().alert("Failed to initialise the agent bot. Please try again.");
            return false;
        }

        // Persist configuration so it auto-restores on server restart
        int configId = insertAgentConfig(userId, room.getId(), botName, persona, figure, spawnTile);

        // Initialise the AI session in habbo-ai-service
        String error = AgentServiceClient.initSession(botId, userId, persona, keyRow.apiKey, keyRow.provider);
        if (error != null) {
            LOGGER.warn("Failed to init AI session for bot {}: {}", botId, error);
            gameClient.getHabbo().alert("Agent '" + botName + "' placed but AI service is unavailable: " + error);
            return false;
        }

        gameClient.getHabbo().alert("Agent '" + botName + "' is ready! (figure_type: " + figureType + ") Talk to it and it will respond.");
        return true;
    }

    private boolean isFigureTypeToken(String token) {
        return token != null && token.toLowerCase().startsWith("type:");
    }

    private String extractFigureType(String token) {
        return token.substring("type:".length()).trim().toLowerCase();
    }

    private boolean isSpawnXToken(String token) {
        return token != null && token.toLowerCase().startsWith("x:");
    }

    private boolean isSpawnYToken(String token) {
        return token != null && token.toLowerCase().startsWith("y:");
    }

    private String extractSpawnValue(String token) {
        return token.substring(2).trim();
    }

    private Integer parseSpawnCoordinate(String value) {
        if (value == null || value.isEmpty()) return null;

        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0 || parsed > Short.MAX_VALUE) return null;
            return parsed;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String resolveFigureByType(String type) {
        return BUILTIN_FIGURE_TYPES.get(type.toLowerCase());
    }

    private String listFigureTypes() {
        return String.join(", ", BUILTIN_FIGURE_TYPES.keySet());
    }

    /** Prefer the tile in front of the user; fallback to nearby valid tiles. */
    private RoomTile findPreferredSpawnTile(Room room, RoomUnit userRoomUnit) {
        RoomTile facingTile = getTileInFront(room, userRoomUnit);
        if (isValidSpawnTile(room, facingTile)) {
            return facingTile;
        }

        return findAdjacentTile(room, userRoomUnit.getCurrentLocation());
    }

    /** Find the first valid, unoccupied tile adjacent (N/E/S/W) to the given tile. */
    private RoomTile findAdjacentTile(Room room, RoomTile origin) {
        int[][] offsets = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int[] off : offsets) {
            RoomTile candidate = room.getLayout().getTile((short) (origin.x + off[0]), (short) (origin.y + off[1]));
            if (isValidSpawnTile(room, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /** Tile in front based on current avatar body rotation. */
    private RoomTile getTileInFront(Room room, RoomUnit userRoomUnit) {
        RoomTile origin = userRoomUnit.getCurrentLocation();
        if (origin == null) return null;

        int offsetX = 0;
        int offsetY = 0;
        switch (userRoomUnit.getBodyRotation()) {
            case NORTH: offsetY = -1; break;
            case NORTH_EAST: offsetX = 1; offsetY = -1; break;
            case EAST: offsetX = 1; break;
            case SOUTH_EAST: offsetX = 1; offsetY = 1; break;
            case SOUTH: offsetY = 1; break;
            case SOUTH_WEST: offsetX = -1; offsetY = 1; break;
            case WEST: offsetX = -1; break;
            case NORTH_WEST: offsetX = -1; offsetY = -1; break;
            default: break;
        }

        return room.getLayout().getTile((short) (origin.x + offsetX), (short) (origin.y + offsetY));
    }

    /**
     * Spawn is valid on a walkable tile or a chair/sit tile,
     * as long as the tile is not already occupied by a user or bot.
     */
    private boolean isValidSpawnTile(Room room, RoomTile tile) {
        if (tile == null) return false;
        boolean walkableOrSeat = tile.isWalkable() || room.canSitAt(tile.x, tile.y);
        return walkableOrSeat
                && !room.hasBotsAt(tile.x, tile.y)
                && !room.hasHabbosAt(tile.x, tile.y);
    }

    private int insertBot(int userId, int roomId, String name, String figure, RoomTile tile) {
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO bots (user_id, room_id, name, motto, figure, gender, x, y, z, rot, type, freeroam, chat_auto, chat_random, chat_delay) " +
                     "VALUES (?, ?, ?, '[AI] Agent', ?, 'M', ?, ?, 0.0, 2, 'ai_agent', '0', '0', '0', 10)",
                     Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, roomId);
            stmt.setString(3, name);
            stmt.setString(4, figure);
            stmt.setInt(5, tile.x);
            stmt.setInt(6, tile.y);
            stmt.execute();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to insert AiBot row", e);
        }
        return -1;
    }

    private Bot loadAndInjectBot(int botId, Room room, RoomTile tile) {
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT b.*, COALESCE(u.username, '') AS owner_name FROM bots b " +
                     "LEFT JOIN users u ON b.user_id = u.id WHERE b.id = ? LIMIT 1")) {
            stmt.setInt(1, botId);
            try (ResultSet set = stmt.executeQuery()) {
                if (!set.next()) return null;
                Bot bot = Emulator.getGameEnvironment().getBotManager().loadBot(set);
                if (bot == null) return null;

                RoomUnit roomUnit = new RoomUnit();
                roomUnit.setRotation(RoomUserRotation.SOUTH);
                roomUnit.setLocation(tile);
                roomUnit.setPreviousLocationZ(tile.getStackHeight());
                roomUnit.setZ(tile.getStackHeight());
                roomUnit.setPathFinderRoom(room);
                roomUnit.setRoomUnitType(RoomUnitType.BOT);
                roomUnit.setCanWalk(false);

                bot.setRoomUnit(roomUnit);
                bot.setRoom(room);
                bot.needsUpdate(false);

                room.addBot(bot);
                Emulator.getThreading().run(bot);
                room.sendComposer(new RoomUsersComposer(bot).compose());
                room.sendComposer(new RoomUserStatusComposer(bot.getRoomUnit()).compose());

                return bot;
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to load/inject AiBot {}", botId, e);
        }
        return null;
    }

    private int insertAgentConfig(int userId, int roomId, String name, String persona, String figure, RoomTile tile) {
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO ai_agent_configs (user_id, room_id, name, persona, figure, gender, spawn_x, spawn_y) VALUES (?, ?, ?, ?, ?, 'M', ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, roomId);
            stmt.setString(3, name);
            stmt.setString(4, persona);
            stmt.setString(5, figure);
            stmt.setInt(6, tile.x);
            stmt.setInt(7, tile.y);
            stmt.execute();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to insert ai_agent_configs row", e);
        }
        return -1;
    }

    private ApiKeyRow loadApiKey(int userId) {
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT api_key, provider FROM ai_api_keys WHERE user_id = ? AND provider = 'anthropic' AND verified = 1 LIMIT 1")) {
            stmt.setInt(1, userId);
            try (ResultSet set = stmt.executeQuery()) {
                if (set.next()) {
                    return new ApiKeyRow(set.getString("api_key"), set.getString("provider"));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to load API key for user {}", userId, e);
        }
        return null;
    }

    private static class ApiKeyRow {
        final String apiKey;
        final String provider;
        ApiKeyRow(String apiKey, String provider) {
            this.apiKey = apiKey;
            this.provider = provider;
        }
    }
}
