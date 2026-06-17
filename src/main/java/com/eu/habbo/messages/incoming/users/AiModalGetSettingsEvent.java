package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.ai.PortalClient;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.users.AiModalSettingsComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Nitro client opens the AI modal and requests its settings. We mint a
 * short-lived portal bearer token (the user is already authenticated via SSO)
 * and relay it so the client can call portal endpoints (e.g. TTS) without ever
 * handling raw API keys.
 */
public class AiModalGetSettingsEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AiModalGetSettingsEvent.class);

    @Override
    public void handle() {
        if (this.client == null || this.client.getHabbo() == null) return;

        final int userId = this.client.getHabbo().getHabboInfo().getId();

        // Network call to the portal — run off the client thread.
        Emulator.getThreading().run(() -> {
            String token = PortalClient.mintHotelToken(userId);
            if (token == null || token.isEmpty()) {
                LOGGER.warn("[AI] Failed to mint hotel token for user {} — TTS will fall back to browser speech. " +
                        "Check portal.internal.secret matches the portal and that a portal user exists for this habbo id.", userId);
            } else {
                LOGGER.info("[AI] Minted hotel token for user {} (len={})", userId, token.length());
            }
            this.client.sendResponse(new AiModalSettingsComposer(token == null ? "" : token));
        });
    }
}
