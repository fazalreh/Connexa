package com.connexa.api.api.v1;

import com.connexa.api.application.IdentityAccessService;
import com.connexa.api.application.WaitlistService;
import com.connexa.api.domain.identity.VerifiedIdentity;
import com.connexa.api.domain.waitlist.WaitlistEntry;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Queueing for events that are full.
 *
 * <p>An attendee can only ever act on their own place: the identity comes from the verified
 * token, so nobody can join or leave a queue on someone else's behalf.
 */
@Validated
@RestController
@RequestMapping("/api/v1/events/{eventId}/waitlist")
public class WaitlistController {

    private final WaitlistService waitlistService;
    private final IdentityAccessService identityAccessService;

    public WaitlistController(
            WaitlistService waitlistService, IdentityAccessService identityAccessService) {
        this.waitlistService = waitlistService;
        this.identityAccessService = identityAccessService;
    }

    @PostMapping
    public ResponseEntity<WaitlistStatusResponse> join(
            HttpServletRequest request, @PathVariable UUID eventId) {
        VerifiedIdentity identity = identityAccessService.requireVerifiedIdentity(request);
        WaitlistEntry entry = waitlistService.join(identity, eventId);
        return ResponseEntity.status(HttpStatus.CREATED).body(WaitlistStatusResponse.from(
                entry, waitlistService.placeInQueue(identity, eventId).orElse(null)));
    }

    /** Reports the caller's own place, or 404 when they are not queued. */
    @GetMapping
    public ResponseEntity<WaitlistStatusResponse> status(
            HttpServletRequest request, @PathVariable UUID eventId) {
        VerifiedIdentity identity = identityAccessService.requireVerifiedIdentity(request);
        return waitlistService.placeInQueue(identity, eventId)
                .map(place -> ResponseEntity.ok(
                        new WaitlistStatusResponse(eventId, place, false, null)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping
    public ResponseEntity<Void> leave(HttpServletRequest request, @PathVariable UUID eventId) {
        waitlistService.leave(identityAccessService.requireVerifiedIdentity(request), eventId);
        // Idempotent: leaving a queue you were not in is the state the caller wanted.
        return ResponseEntity.noContent().build();
    }
}
