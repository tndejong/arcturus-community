package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.permissions.Permission;

/**
 * :ai
 *
 * Shows all available AI agent commands.
 * Admin-only commands are only shown to players with the required permission.
 */
public class AiHelpCommand extends Command {

    public AiHelpCommand() {
        super("cmd_ai_help", new String[]{"ai"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        boolean isAdmin = gameClient.getHabbo().hasPermission("cmd_setup_agent");

        StringBuilder sb = new StringBuilder();
        sb.append("=== AI Agent Commands ===\n\n");

        sb.append(":ai\n");
        sb.append("  Show this help message.\n\n");

        if (isAdmin) {
            sb.append(":set_ai_key <api_key> [provider]\n");
            sb.append("  Link your AI provider API key. Provider defaults to 'openai'.\n");
            sb.append("  Example: :set_ai_key sk-... openai\n\n");

            sb.append(":setup_agent <name> <persona...>\n");
            sb.append("  Spawn an AI bot in your room with the given name and persona.\n");
            sb.append("  Example: :setup_agent Aria A friendly assistant who loves Habbo\n\n");
        }

        sb.append("=== Talking to an agent ===\n");
        sb.append("Address the bot by name at the start of your message:\n");
        sb.append("  Aria what can you do?\n");
        sb.append("  Aria, tell me something fun!\n");

        gameClient.getHabbo().alert(sb.toString());
        return true;
    }
}
