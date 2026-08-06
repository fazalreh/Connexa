package com.connexa.api.infrastructure.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AssistantRateLimiterTest {

    /** A clock the test advances by hand, so the window is exercised without sleeping. */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-06-01T10:00:00Z");

        void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    @Test
    @DisplayName("calls within the limit are allowed")
    void allowsUpToTheLimit() {
        AssistantRateLimiter limiter = new AssistantRateLimiter(3, new MovableClock());

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
    }

    @Test
    @DisplayName("the call past the limit is refused")
    void refusesBeyondTheLimit() {
        AssistantRateLimiter limiter = new AssistantRateLimiter(2, new MovableClock());
        limiter.tryAcquire();
        limiter.tryAcquire();

        assertThat(limiter.tryAcquire()).isFalse();
    }

    @Test
    @DisplayName("capacity returns as calls age out of the window")
    void recoversAsTheWindowSlides() {
        MovableClock clock = new MovableClock();
        AssistantRateLimiter limiter = new AssistantRateLimiter(2, clock);
        limiter.tryAcquire();
        limiter.tryAcquire();
        assertThat(limiter.tryAcquire()).isFalse();

        clock.advance(Duration.ofSeconds(61));

        assertThat(limiter.tryAcquire()).isTrue();
    }

    @Test
    @DisplayName("a burst spanning a minute boundary cannot exceed the rate")
    void slidingWindowPreventsBoundaryBurst() {
        MovableClock clock = new MovableClock();
        AssistantRateLimiter limiter = new AssistantRateLimiter(2, clock);
        clock.advance(Duration.ofSeconds(59));
        limiter.tryAcquire();
        limiter.tryAcquire();

        // A fixed-bucket limiter would reset here and wrongly allow two more.
        clock.advance(Duration.ofSeconds(2));

        assertThat(limiter.tryAcquire()).isFalse();
    }

    @Test
    @DisplayName("only calls older than the window are released")
    void releasesOnlyExpiredCalls() {
        MovableClock clock = new MovableClock();
        AssistantRateLimiter limiter = new AssistantRateLimiter(2, clock);
        limiter.tryAcquire();
        clock.advance(Duration.ofSeconds(30));
        limiter.tryAcquire();

        clock.advance(Duration.ofSeconds(31));

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
    }
}
