package com.connexa.api.application;

import com.connexa.api.config.CheckInProperties;
import com.connexa.api.domain.checkin.CheckInPass;
import com.connexa.api.domain.checkin.InvalidCheckInPassException;
import com.connexa.api.domain.identity.ActorNotAuthorizedException;
import com.connexa.api.domain.identity.IdentityKey;
import com.connexa.api.domain.identity.IdentityRole;
import com.connexa.api.domain.identity.VerifiedIdentity;
import com.connexa.api.infrastructure.checkin.CheckInResult;
import com.connexa.api.infrastructure.checkin.CheckInStore;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Issues attendee passes and honours them at the door.
 */
@Service
public class CheckInService {

    private final CheckInStore checkInStore;
    private final EventQueryService eventQueryService;
    private final CheckInProperties properties;
    private final Clock clock;

    /**
     * With a second constructor present for clock injection, the container cannot pick
     * one on its own, so the injection point is stated rather than left to chance.
     */
    @Autowired
    public CheckInService(
            CheckInStore checkInStore,
            EventQueryService eventQueryService,
            CheckInProperties properties) {
        this(checkInStore, eventQueryService, properties, Clock.systemUTC());
    }

    CheckInService(
            CheckInStore checkInStore,
            EventQueryService eventQueryService,
            CheckInProperties properties,
            Clock clock) {
        this.checkInStore = Objects.requireNonNull(checkInStore, "checkInStore");
        this.eventQueryService = Objects.requireNonNull(eventQueryService, "eventQueryService");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Issues the calling attendee a pass for one event.
     *
     * <p>Issued on demand and short-lived rather than stored, so a screenshot taken earlier
     * stops working. The caller can only ever obtain a pass for themselves: the identity is
     * taken from the verified token, never from the request.
     */
    public IssuedPass issuePass(VerifiedIdentity identity, UUID eventId) {
        requireEnabled();
        Objects.requireNonNull(identity, "identity is required");
        eventQueryService.findEvent(eventId);

        Instant expiresAt = clock.instant().plus(properties.passValidity());
        return new IssuedPass(
                CheckInPass.issue(identity.key(), eventId, expiresAt, properties.secretBytes()),
                expiresAt);
    }

    /**
     * Honours a scanned pass.
     *
     * <p>Only an organizer may check anyone in. Without that rule any attendee could scan a
     * neighbour's screen and mark them arrived.
     */
    public CheckInResult redeem(VerifiedIdentity scanner, UUID eventId, String pass) {
        requireEnabled();
        Objects.requireNonNull(scanner, "scanner is required");
        if (!scanner.hasRole(IdentityRole.ORGANIZER)) {
            throw new ActorNotAuthorizedException("check attendees in");
        }
        eventQueryService.findEvent(eventId);

        IdentityKey attendee = CheckInPass.verify(
                pass, eventId, clock.instant(), properties.secretBytes());
        return checkInStore.checkIn(attendee, eventId, clock.instant());
    }

    public long countCheckedIn(VerifiedIdentity scanner, UUID eventId) {
        requireEnabled();
        if (!scanner.hasRole(IdentityRole.ORGANIZER)) {
            throw new ActorNotAuthorizedException("view door attendance");
        }
        return checkInStore.countCheckedIn(eventId);
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new InvalidCheckInPassException("Check-in is not configured for this service.");
        }
    }

    /** @param expiresAt returned so a client can refresh the code before it lapses */
    public record IssuedPass(String pass, Instant expiresAt) {
    }
}
