package com.eu.habbo.messages.outgoing.users;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * Relays a short-lived portal bearer token to the Nitro client. The client uses
 * it as an Authorization: Bearer credential for portal calls; no API keys are
 * ever sent to the browser.
 */
public class AiModalSettingsComposer extends MessageComposer {
    private final String hotelToken;

    public AiModalSettingsComposer(String hotelToken) {
        this.hotelToken = (hotelToken == null) ? "" : hotelToken;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.AiModalSettingsComposer);
        this.response.appendString(this.hotelToken);
        return this.response;
    }
}
