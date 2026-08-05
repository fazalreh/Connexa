package com.connexa.api.infrastructure.assistant;

import com.connexa.api.domain.assistant.AssistantRequest;
import com.connexa.api.domain.assistant.AssistantReply;
import com.connexa.api.domain.assistant.AssistantUnavailableException;
import com.connexa.api.domain.identity.VerifiedIdentity;

/**
 * Safe default that keeps inference disabled until a provider is configured server-side.
 */
public final class UnavailableAssistantGateway implements AssistantGateway {

    @Override
    public AssistantReply answer(VerifiedIdentity identity, AssistantRequest request) {
        throw new AssistantUnavailableException();
    }
}
