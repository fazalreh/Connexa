package com.connexa.api.api.v1;

import com.connexa.api.application.IdentityAccessService;
import com.connexa.api.application.OrganizerEventDraftService;
import com.connexa.api.domain.event.PageResponse;
import com.connexa.api.domain.organizer.OrganizerEventDraft;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Private organizer draft workflow. Publishing is intentionally a separate future workflow.
 */
@Validated
@RestController
@RequestMapping("/api/v1/organizer/events")
public class OrganizerEventController {

    private final IdentityAccessService identityAccessService;
    private final OrganizerEventDraftService organizerEventDraftService;

    public OrganizerEventController(
            IdentityAccessService identityAccessService,
            OrganizerEventDraftService organizerEventDraftService) {
        this.identityAccessService = identityAccessService;
        this.organizerEventDraftService = organizerEventDraftService;
    }

    @PostMapping
    public ResponseEntity<OrganizerEventDraftResponse> create(
            HttpServletRequest request,
            @Valid @RequestBody OrganizerEventCreateRequest createRequest) {
        OrganizerEventDraft draft = organizerEventDraftService.create(
                identityAccessService.requireVerifiedIdentity(request),
                createRequest.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(OrganizerEventDraftResponse.from(draft));
    }

    @GetMapping
    public PageResponse<OrganizerEventDraftResponse> list(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResponse<OrganizerEventDraft> drafts = organizerEventDraftService.findAll(
                identityAccessService.requireVerifiedIdentity(request), page, size);
        List<OrganizerEventDraftResponse> items = drafts.items().stream()
                .map(OrganizerEventDraftResponse::from)
                .toList();
        return new PageResponse<>(items, drafts.page(), drafts.size(), drafts.total());
    }
}
