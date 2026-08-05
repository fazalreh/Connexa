package com.connexa.api.infrastructure.assistant;

import com.connexa.api.domain.assistant.AssistantRequest;
import com.connexa.api.domain.assistant.AssistantReply;
import com.connexa.api.domain.identity.VerifiedIdentity;

/**
 * Server-side provider boundary for assistant inference and retrieval.
 */
public interface AssistantGateway {

    AssistantReply answer(VerifiedIdentity identity, AssistantRequest request);
}
