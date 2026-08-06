package com.connexa.mobile.core.checkin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.zxing.common.BitMatrix;
import java.time.Duration;
import java.time.Instant;
import org.junit.Test;

/**
 * The parts of check-in that can be decided without a door: when a pass is renewed, and
 * whether a scan should be acted on.
 */
public class CheckInLogicTest {

    private static final Instant NOW = Instant.parse("2026-10-15T09:00:00Z");

    // ---- renewal ----

    @Test
    public void renewsAheadOfExpiryRatherThanAtIt() {
        // A pass that lapses while its holder is queuing gets them turned away at the front
        // of the line, and the failure looks like theirs.
        long delay = PassRefreshSchedule.millisUntilRefresh(NOW.plus(Duration.ofMinutes(5)), NOW);

        assertEquals(Duration.ofMinutes(5).minusSeconds(45).toMillis(), delay);
    }

    @Test
    public void renewsImmediatelyWhenThePassIsNearlySpent() {
        long delay = PassRefreshSchedule.millisUntilRefresh(NOW.plusSeconds(20), NOW);

        assertEquals(1_000L, delay);
    }

    @Test
    public void neverBusyLoopsOnAnExpiryAlreadyPast() {
        assertEquals(1_000L, PassRefreshSchedule.millisUntilRefresh(NOW.minusSeconds(90), NOW));
    }

    @Test
    public void survivesAMissingExpiry() {
        assertEquals(1_000L, PassRefreshSchedule.millisUntilRefresh(null, NOW));
    }

    @Test
    public void countsDownInWholeSeconds() {
        assertEquals(90L, PassRefreshSchedule.secondsRemaining(NOW.plusSeconds(90), NOW));
    }

    @Test
    public void theCountdownStopsAtZeroRatherThanGoingNegative() {
        assertEquals(0L, PassRefreshSchedule.secondsRemaining(NOW.minusSeconds(30), NOW));
    }

    // ---- repeat scans ----

    @Test
    public void actsOnAScan() {
        assertTrue(new RepeatScanGuard().shouldHandle("pass-a", NOW));
    }

    @Test
    public void ignoresTheSameCodeStillInFrontOfTheLens() {
        // A continuous decoder reports the same square many times a second; without this the
        // steward would see a burst of "already checked in" for someone who just arrived.
        RepeatScanGuard guard = new RepeatScanGuard();
        guard.shouldHandle("pass-a", NOW);

        assertFalse(guard.shouldHandle("pass-a", NOW.plusMillis(200)));
        assertFalse(guard.shouldHandle("pass-a", NOW.plusSeconds(4)));
    }

    @Test
    public void acceptsTheNextGuestImmediately() {
        // Queues move fast. Making the next person wait out a timer meant for the previous
        // one would be the wrong trade.
        RepeatScanGuard guard = new RepeatScanGuard();
        guard.shouldHandle("pass-a", NOW);

        assertTrue(guard.shouldHandle("pass-b", NOW.plusMillis(200)));
    }

    @Test
    public void honoursTheSameCodeOnceTheQuietPeriodPasses() {
        RepeatScanGuard guard = new RepeatScanGuard();
        guard.shouldHandle("pass-a", NOW);

        assertTrue(guard.shouldHandle("pass-a", NOW.plusSeconds(6)));
    }

    @Test
    public void aFailedScanCanBeRetriedAtOnce() {
        // The guest fixes a lapsed pass and presents again; making them wait would be its
        // own small insult.
        RepeatScanGuard guard = new RepeatScanGuard();
        guard.shouldHandle("pass-a", NOW);
        guard.reset();

        assertTrue(guard.shouldHandle("pass-a", NOW.plusMillis(200)));
    }

    @Test
    public void ignoresAnEmptyRead() {
        RepeatScanGuard guard = new RepeatScanGuard();

        assertFalse(guard.shouldHandle("", NOW));
        assertFalse(guard.shouldHandle(null, NOW));
    }

    // ---- encoding ----

    @Test
    public void encodesAPassAsASquare() {
        BitMatrix matrix = PassQrCode.encode("connexa-pass-value", 240);

        assertEquals(matrix.getWidth(), matrix.getHeight());
        assertTrue(matrix.getWidth() > 0);
    }

    @Test
    public void differentPassesProduceDifferentCodes() {
        BitMatrix one = PassQrCode.encode("pass-one", 240);
        BitMatrix other = PassQrCode.encode("pass-two", 240);

        assertFalse(one.equals(other));
    }

    @Test
    public void refusesAnEmptyPass() {
        // An empty code would scan as a valid-looking nothing rather than failing visibly.
        assertThrows(IllegalArgumentException.class, () -> PassQrCode.encode("", 240));
        assertThrows(IllegalArgumentException.class, () -> PassQrCode.encode(null, 240));
    }

    @Test
    public void refusesAnImpossibleSize() {
        assertThrows(IllegalArgumentException.class, () -> PassQrCode.encode("pass", 0));
    }
}
