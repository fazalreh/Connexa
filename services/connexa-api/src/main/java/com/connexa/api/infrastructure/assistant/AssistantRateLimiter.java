package com.connexa.api.infrastructure.assistant;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Caps how many provider calls the service makes per minute.
 *
 * <p>The limit protects the account's budget, so it is applied service-wide rather than per
 * caller: a per-caller limit would still let a hundred callers exhaust the quota between
 * them. A sliding window is used instead of fixed buckets so a burst spanning a bucket
 * boundary cannot briefly double the rate.
 */
public final class AssistantRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final Deque<Instant> calls = new ArrayDeque<>();
    private final int maxPerWindow;
    private final Clock clock;

    public AssistantRateLimiter(int maxPerWindow, Clock clock) {
        if (maxPerWindow < 1) {
            throw new IllegalArgumentException("maxPerWindow must be at least 1");
        }
        this.maxPerWindow = maxPerWindow;
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    public synchronized boolean tryAcquire() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(WINDOW);
        while (!calls.isEmpty() && !calls.peekFirst().isAfter(cutoff)) {
            calls.pollFirst();
        }
        if (calls.size() >= maxPerWindow) {
            return false;
        }
        calls.addLast(now);
        return true;
    }
}
