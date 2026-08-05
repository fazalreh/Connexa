package com.connexa.api.application;

import com.connexa.api.domain.assistant.AssistantRequest;
import com.connexa.api.domain.assistant.AssistantReply;
import com.connexa.api.domain.identity.VerifiedIdentity;
import com.connexa.api.infrastructure.assistant.AssistantGateway;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Application service that keeps assistant requests behind verified identity and provider boundaries.
 */
@Service
public class AssistantService {

    private final AssistantGateway assistantGateway;

    public AssistantService(AssistantGateway assistantGateway) {
        this.assistantGateway = Objects.requireNonNull(assistantGateway, "assistantGateway is required");
    }

    public AssistantReply answer(VerifiedIdentity identity, AssistantRequest request) {
        return assistantGateway.answer(
                Objects.requireNonNull(identity, "identity is required"),
                Objects.requireNonNull(request, "request is required"));
    }
}
