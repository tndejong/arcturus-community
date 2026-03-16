package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.habbohotel.bots.AiConversationManager;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;

/**
 * :ai_duet <bot_a> <bot_b> [turns:<2-20>] <topic...>
 */
public class AiDuetCommand extends Command {
    public AiDuetCommand() {
        super("cmd_setup_agent", new String[]{"ai_duet", "start_ai_chat"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length < 4) {
            gameClient.getHabbo().alert("Usage: :ai_duet <bot_a> <bot_b> [turns:<2-20>] <topic...>\n" +
                    "Examples:\n" +
                    "  :ai_duet Bob Bas Gesprek over muizen\n" +
                    "  :ai_duet Bob Bas turns:10 Bespreek de voor- en nadelen van draadloze muizen");
            return false;
        }

        Room room = gameClient.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null) {
            gameClient.getHabbo().alert("You must be in a room to start a bot-to-bot conversation.");
            return false;
        }

        String botA = params[1];
        String botB = params[2];
        int startTopicIndex = 3;
        int maxTurns = 8;

        if (params.length >= 5 && params[3].toLowerCase().startsWith("turns:")) {
            try {
                maxTurns = Integer.parseInt(params[3].substring("turns:".length()));
            } catch (NumberFormatException ignored) {
                gameClient.getHabbo().alert("Invalid turns value. Use turns:<number> (2-20).");
                return false;
            }
            startTopicIndex = 4;
        }

        if (params.length <= startTopicIndex) {
            gameClient.getHabbo().alert("Please provide a topic.\nUsage: :ai_duet <bot_a> <bot_b> [turns:<2-20>] <topic...>");
            return false;
        }

        StringBuilder topicBuilder = new StringBuilder();
        for (int i = startTopicIndex; i < params.length; i++) {
            if (i > startTopicIndex) topicBuilder.append(" ");
            topicBuilder.append(params[i]);
        }

        String result = AiConversationManager.startConversation(
                room,
                gameClient.getHabbo().getHabboInfo().getId(),
                gameClient.getHabbo().getHabboInfo().getUsername(),
                botA,
                botB,
                topicBuilder.toString(),
                maxTurns
        );

        gameClient.getHabbo().alert(result);
        return true;
    }
}
