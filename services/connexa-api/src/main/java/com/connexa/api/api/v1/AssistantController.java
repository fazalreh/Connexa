package com.connexa.api.api.v1;

import com.connexa.api.application.AssistantService;
import com.connexa.api.application.IdentityAccessService;
import com.connexa.api.domain.assistant.AssistantReply;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Assistant endpoint. Inference remains server-only and disabled until a provider is approved.
 */
@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {

    private final AssistantService assistantService;
    private final IdentityAccessService identityAccessService;

    public AssistantController(AssistantService assistantService, IdentityAccessService identityAccessService) {
        this.assistantService = assistantService;
        this.identityAccessService = identityAccessService;
    }

    @PostMapping("/messages")
    public AssistantReplyResponse answer(
            HttpServletRequest request,
            @Valid @RequestBody AssistantMessageRequest messageRequest) {
        AssistantReply reply = assistantService.answer(
                identityAccessService.requireVerifiedIdentity(request),
                messageRequest.toAssistantRequest());
        return AssistantReplyResponse.from(reply);
    }
}
