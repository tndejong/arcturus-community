package com.eu.habbo.messages.outgoing.users;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class AiModalSettingsComposer extends MessageComposer {
    private final String provider;
    private final String apiKey;
    private final boolean verified;
    private final String elevenlabsKey;
    private final String elevenlabsVoiceId;

    public AiModalSettingsComposer(String provider, String apiKey, boolean verified,
                                    String elevenlabsKey, String elevenlabsVoiceId) {
        this.provider = (provider == null) ? "" : provider;
        this.apiKey = (apiKey == null) ? "" : apiKey;
        this.verified = verified;
        this.elevenlabsKey = (elevenlabsKey == null) ? "" : elevenlabsKey;
        this.elevenlabsVoiceId = (elevenlabsVoiceId == null) ? "" : elevenlabsVoiceId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.AiModalSettingsComposer);
        this.response.appendString(this.provider);
        this.response.appendString(this.apiKey);
        this.response.appendBoolean(this.verified);
        this.response.appendString(this.elevenlabsKey);
        this.response.appendString(this.elevenlabsVoiceId);
        return this.response;
    }
}
