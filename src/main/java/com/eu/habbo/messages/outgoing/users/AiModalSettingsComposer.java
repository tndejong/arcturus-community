package com.eu.habbo.messages.outgoing.users;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class AiModalSettingsComposer extends MessageComposer {
    private final String provider;
    private final String apiKey;
    private final boolean verified;

    public AiModalSettingsComposer(String provider, String apiKey, boolean verified) {
        this.provider = (provider == null) ? "" : provider;
        this.apiKey = (apiKey == null) ? "" : apiKey;
        this.verified = verified;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.AiModalSettingsComposer);
        this.response.appendString(this.provider);
        this.response.appendString(this.apiKey);
        this.response.appendBoolean(this.verified);
        return this.response;
    }
}
