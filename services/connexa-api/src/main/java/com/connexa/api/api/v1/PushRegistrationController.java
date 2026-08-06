package com.connexa.api.api.v1;

import com.connexa.api.application.IdentityAccessService;
import com.connexa.api.infrastructure.push.PushTokenStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Device registration for notifications.
 *
 * <p>A device is always bound to the identity in the verified token, never to one named in
 * the request, so nobody can register a device against someone else's account and receive
 * their notifications.
 */
@Validated
@RestController
@RequestMapping("/api/v1/me/devices")
public class PushRegistrationController {

    private final PushTokenStore pushTokenStore;
    private final IdentityAccessService identityAccessService;

    public PushRegistrationController(
            PushTokenStore pushTokenStore, IdentityAccessService identityAccessService) {
        this.pushTokenStore = pushTokenStore;
        this.identityAccessService = identityAccessService;
    }

    @PostMapping
    public ResponseEntity<Void> register(
            HttpServletRequest request,
            @Valid @RequestBody PushRegistrationRequest registration) {
        pushTokenStore.register(
                identityAccessService.requireVerifiedIdentity(request).key(),
                registration.token(),
                registration.platform(),
                Instant.now());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> unregister(
            HttpServletRequest request,
            @Valid @RequestBody PushRegistrationRequest registration) {
        // Sign-out path. Idempotent: an already-removed device is the state the caller wanted.
        identityAccessService.requireVerifiedIdentity(request);
        pushTokenStore.unregister(registration.token());
        return ResponseEntity.noContent().build();
    }
}
