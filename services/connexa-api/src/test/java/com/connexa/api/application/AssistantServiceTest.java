package com.connexa.api.application;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.connexa.api.domain.assistant.AssistantRequest;
import com.connexa.api.domain.assistant.AssistantUnavailableException;
import com.connexa.api.domain.identity.IdentityKey;
import com.connexa.api.domain.identity.IdentityRole;
import com.connexa.api.domain.identity.VerifiedIdentity;
import com.connexa.api.infrastructure.assistant.UnavailableAssistantGateway;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AssistantServiceTest {

    @Test
    void unavailableGatewayDoesNotAttemptAClientSideOrUnconfiguredModelCall() {
        AssistantService service = new AssistantService(new UnavailableAssistantGateway());
        VerifiedIdentity identity = new VerifiedIdentity(
                new IdentityKey("test-issuer", "attendee-1"),
                "Attendee",
                null,
                Set.of(IdentityRole.ATTENDEE));

        assertThrows(AssistantUnavailableException.class, () -> service.answer(
                identity,
                new AssistantRequest("Show events this week", null, null)));
    }
}
