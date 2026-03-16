package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import gnu.trove.iterator.TIntObjectIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * :remove_agent <name|all>
 *
 * Removes one or all AI agents owned by the caller in the current room.
 */
public class RemoveAgentCommand extends Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(RemoveAgentCommand.class);

    public RemoveAgentCommand() {
        super("cmd_setup_agent", new String[]{"remove_agent", "delete_agent"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length < 2) {
            gameClient.getHabbo().alert("Usage: :remove_agent <name|all>\n" +
                    "Examples:\n" +
                    "  :remove_agent Aria\n" +
                    "  :remove_agent all");
            return false;
        }

        Room room = gameClient.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null) {
            gameClient.getHabbo().alert("You must be in a room to remove an AI agent.");
            return false;
        }

        int userId = gameClient.getHabbo().getHabboInfo().getId();
        String target = params[1];
        boolean removeAll = "all".equalsIgnoreCase(target);

        List<Bot> targets = findTargets(room, userId, target, removeAll);
        if (targets.isEmpty()) {
            if (removeAll) {
                gameClient.getHabbo().alert("No AI agents found in this room for your account.");
            } else {
                gameClient.getHabbo().alert("No AI agent named '" + target + "' found in this room.");
            }
            return false;
        }

        int removed = 0;
        for (Bot bot : targets) {
            try {
                room.removeBot(bot);
                Emulator.getGameEnvironment().getBotManager().deleteBot(bot);
                deactivateAgentConfig(userId, room.getId(), bot.getName());
                removed++;
            } catch (Exception e) {
                LOGGER.error("Failed to remove AI agent {}", bot.getId(), e);
            }
        }

        if (removed == 0) {
            gameClient.getHabbo().alert("Failed to remove the AI agent(s). Please try again.");
            return false;
        }

        if (removeAll) {
            gameClient.getHabbo().alert("Removed " + removed + " AI agent(s) from this room.");
        } else {
            gameClient.getHabbo().alert("Agent '" + targets.get(0).getName() + "' removed.");
        }

        return true;
    }

    private List<Bot> findTargets(Room room, int userId, String name, boolean removeAll) {
        List<Bot> targets = new ArrayList<>();
        TIntObjectIterator<Bot> iterator = room.getCurrentBots().iterator();
        for (int i = room.getCurrentBots().size(); i-- > 0; ) {
            iterator.advance();
            Bot bot = iterator.value();
            if (bot == null) continue;
            if (!"ai_agent".equalsIgnoreCase(bot.getType())) continue;
            if (bot.getOwnerId() != userId) continue;

            if (removeAll || bot.getName().equalsIgnoreCase(name)) {
                targets.add(bot);
            }
        }
        return targets;
    }

    private void deactivateAgentConfig(int userId, int roomId, String name) {
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE ai_agent_configs SET active = 0 WHERE user_id = ? AND room_id = ? AND name = ? AND active = 1")) {
            stmt.setInt(1, userId);
            stmt.setInt(2, roomId);
            stmt.setString(3, name);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.warn("Failed to deactivate ai_agent_configs for user {} room {} name {}", userId, roomId, name, e);
        }
    }
}
