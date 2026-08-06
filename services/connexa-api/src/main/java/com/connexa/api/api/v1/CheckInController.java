package com.connexa.api.api.v1;

import com.connexa.api.application.CheckInService;
import com.connexa.api.application.IdentityAccessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Door check-in.
 *
 * <p>An attendee can only ever obtain a pass for themselves, and only an organizer can
 * redeem one. Both identities come from the verified token rather than the request.
 */
@Validated
@RestController
@RequestMapping("/api/v1/events/{eventId}")
public class CheckInController {

    private final CheckInService checkInService;
    private final IdentityAccessService identityAccessService;

    public CheckInController(
            CheckInService checkInService, IdentityAccessService identityAccessService) {
        this.checkInService = checkInService;
        this.identityAccessService = identityAccessService;
    }

    @GetMapping("/check-in-pass")
    public CheckInPassResponse issuePass(HttpServletRequest request, @PathVariable UUID eventId) {
        return CheckInPassResponse.from(checkInService.issuePass(
                identityAccessService.requireVerifiedIdentity(request), eventId));
    }

    @PostMapping("/check-in")
    public CheckInResultResponse redeem(
            HttpServletRequest request,
            @PathVariable UUID eventId,
            @Valid @RequestBody CheckInRedeemRequest redeemRequest) {
        return CheckInResultResponse.from(checkInService.redeem(
                identityAccessService.requireVerifiedIdentity(request),
                eventId,
                redeemRequest.pass()));
    }

    @GetMapping("/check-in-count")
    public long countCheckedIn(HttpServletRequest request, @PathVariable UUID eventId) {
        return checkInService.countCheckedIn(
                identityAccessService.requireVerifiedIdentity(request), eventId);
    }
}
