package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.habbohotel.gameclients.GameClient;

/**
 * :ai
 *
 * Shows all available AI agent commands.
 * Admin-only commands are only shown to players with the required permission.
 */
public class AiHelpCommand extends Command {

    public AiHelpCommand() {
        super("cmd_setup_agent", new String[]{"ai"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        boolean isAdmin = gameClient.getHabbo().hasPermission("cmd_setup_agent");

        StringBuilder sb = new StringBuilder();
        sb.append("=== AI Agent Commands ===\n\n");

        sb.append(":ai\n");
        sb.append("  Show this help message.\n\n");

        if (isAdmin) {
            sb.append(":set_ai_key <api_key>\n");
            sb.append("  Link your Anthropic API key for chat completion.\n");
            sb.append("  Example: :set_ai_key sk-ant-...\n\n");

            sb.append(":setup_agent <name> [type:<figure_type>] [x:<tile_x> y:<tile_y>] <persona...>\n");
            sb.append("  Spawn an AI bot in your room with the given name and persona.\n");
            sb.append("  Figure types: default, citizen, agent, bouncer, m-employee\n");
            sb.append("  Example: :setup_agent Aria type:agent A friendly assistant who loves Habbo\n\n");

            sb.append(":remove_agent <name|all>\n");
            sb.append("  Remove one of your AI bots in the current room, or remove all of them.\n");
            sb.append("  Example: :remove_agent Aria\n\n");

            sb.append(":ai_duet <bot_a> <bot_b> [turns:<2-20>] <topic...>\n");
            sb.append("  Start a turn-based conversation between two of your AI bots.\n");
            sb.append("  Example: :ai_duet Bob Bas turns:8 Have a conversation about mice\n\n");

            sb.append(":ai_stop\n");
            sb.append("  Stop your active bot-to-bot conversation in this room.\n\n");
        }

        sb.append("=== Talking to an agent ===\n");
        sb.append("Address the bot by name at the start of your message:\n");
        sb.append("  Aria what can you do?\n");
        sb.append("  Aria, tell me something fun!\n");

        gameClient.getHabbo().alert(sb.toString());
        return true;
    }
}
