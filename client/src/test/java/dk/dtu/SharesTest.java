package dk.dtu;

import dk.dtu.methods.Shares;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link Shares}'s pure static helpers. No JavaFX is
 * instantiated: these functions carry no JavaFX types in their signatures on
 * purpose, so they can be pinned here without a running application.
 *
 * <p>Their behaviour is duplicated verbatim in the web edition at
 * {@code website/src/lib/todo/share.ts} in the {@code patr7257/PatrickRobelWeb}
 * repo; any change to a branch tested here needs the matching change there.
 */
class SharesTest {

    // -- labelOrDefault ------------------------------------------------------

    @Test
    void labelOrDefaultFallsBackOnNull() {
        assertEquals("Untitled link", Shares.labelOrDefault(null));
    }

    @Test
    void labelOrDefaultFallsBackOnBlank() {
        assertEquals("Untitled link", Shares.labelOrDefault("   "));
    }

    @Test
    void labelOrDefaultTrimsAGivenLabel() {
        assertEquals("sent to mum", Shares.labelOrDefault("  sent to mum  "));
    }

    // -- shareUrl --------------------------------------------------------------

    @Test
    void shareUrlJoinsBaseAndToken() {
        assertEquals("https://patrickrobel.dk/s/Kf3xQ9mZ", Shares.shareUrl("https://patrickrobel.dk", "Kf3xQ9mZ"));
    }

    @Test
    void shareUrlStripsATrailingSlashSoItNeverDoubleSlashes() {
        assertEquals("https://patrickrobel.dk/s/Kf3xQ9mZ", Shares.shareUrl("https://patrickrobel.dk/", "Kf3xQ9mZ"));
    }

    @Test
    void shareUrlStripsMultipleTrailingSlashes() {
        assertEquals("https://patrickrobel.dk/s/Kf3xQ9mZ", Shares.shareUrl("https://patrickrobel.dk///", "Kf3xQ9mZ"));
    }

    // -- describeLastViewed ------------------------------------------------------

    @Test
    void describeLastViewedNeverOpenedWhenViewCountIsZero() {
        Instant now = Instant.parse("2026-08-09T12:00:00Z");
        assertEquals("Never opened", Shares.describeLastViewed(now.minusSeconds(10), 0, now));
    }

    @Test
    void describeLastViewedNeverOpenedWhenViewCountIsNegative() {
        Instant now = Instant.parse("2026-08-09T12:00:00Z");
        assertEquals("Never opened", Shares.describeLastViewed(now.minusSeconds(10), -1, now));
    }

    @Test
    void describeLastViewedNeverOpenedWhenLastViewedIsNull() {
        Instant now = Instant.parse("2026-08-09T12:00:00Z");
        assertEquals("Never opened", Shares.describeLastViewed(null, 3, now));
    }

    @Test
    void describeLastViewedOnceUsesSingular() {
        Instant now = Instant.parse("2026-08-09T12:00:00Z");
        Instant lastViewed = now.minusSeconds(30);
        assertEquals("Opened once, just now", Shares.describeLastViewed(lastViewed, 1, now));
    }

    @Test
    void describeLastViewedManyUsesCountAndPlural() {
        Instant now = Instant.parse("2026-08-09T12:00:00Z");
        Instant lastViewed = now.minusSeconds(120);
        assertEquals("Opened 3 times, last 2 minutes ago", Shares.describeLastViewed(lastViewed, 3, now));
    }

    // -- relative: the four bands ------------------------------------------------

    @Test
    void relativeJustNowUnderAMinute() {
        Instant now = Instant.parse("2026-08-09T12:00:00Z");
        assertEquals("just now", Shares.relative(now.minusSeconds(59), now));
    }

    @Test
    void relativeClampsAFutureInstantToJustNow() {
        Instant now = Instant.parse("2026-08-09T12:00:00Z");
        assertEquals("just now", Shares.relative(now.plusSeconds(30), now));
    }

    @Test
    void relativeMinutesPlural() {
        Instant now = Instant.parse("2026-08-09T12:00:00Z");
        assertEquals("5 minutes ago", Shares.relative(now.minusSeconds(300), now));
    }

    @Test
    void relativeHoursPlural() {
        Instant now = Instant.parse("2026-08-09T12:00:00Z");
        assertEquals("2 hours ago", Shares.relative(now.minusSeconds(7200), now));
    }

    @Test
    void relativeDaysPlural() {
        Instant now = Instant.parse("2026-08-09T12:00:00Z");
        assertEquals("3 days ago", Shares.relative(now.minusSeconds(3 * 86400), now));
    }

    @Test
    void relativeBoundaryAtExactlyOneMinuteIsSingular() {
        Instant now = Instant.parse("2026-08-09T12:00:00Z");
        assertEquals("1 minute ago", Shares.relative(now.minusSeconds(60), now));
    }

    @Test
    void relativeBoundaryAtExactlyOneHourIsSingular() {
        Instant now = Instant.parse("2026-08-09T12:00:00Z");
        assertEquals("1 hour ago", Shares.relative(now.minusSeconds(3600), now));
    }

    @Test
    void relativeBoundaryAtExactlyOneDayIsSingular() {
        Instant now = Instant.parse("2026-08-09T12:00:00Z");
        assertEquals("1 day ago", Shares.relative(now.minusSeconds(86400), now));
    }

    @Test
    void relativeJustBelowTheMinuteBoundaryStaysJustNow() {
        Instant now = Instant.parse("2026-08-09T12:00:00Z");
        assertEquals("just now", Shares.relative(now.minusSeconds(59), now));
    }

    @Test
    void relativeJustBelowTheHourBoundaryStaysMinutes() {
        Instant now = Instant.parse("2026-08-09T12:00:00Z");
        assertEquals("59 minutes ago", Shares.relative(now.minusSeconds(3599), now));
    }

    @Test
    void relativeJustBelowTheDayBoundaryStaysHours() {
        Instant now = Instant.parse("2026-08-09T12:00:00Z");
        assertEquals("23 hours ago", Shares.relative(now.minusSeconds(86399), now));
    }

    // -- messageForManagementFailure / isAlreadyGone: the per-route 404 mapping ---
    //
    // A 404 means something different on each of the three share routes (a
    // valid list id always has a shares collection, so load's 404 means an
    // old server; create's 404 means the list itself is gone; revoke's 404
    // means the link is already gone, which is the desired outcome).

    @Test
    void loadFailureOn404MeansServerNeedsUpdating() {
        assertEquals("Sharing isn't available yet - the server needs updating.",
                Shares.messageForManagementFailure(Shares.OP_LOAD, 404));
    }

    @Test
    void loadFailureOnNon404HasNoSpecialMessage() {
        assertNull(Shares.messageForManagementFailure(Shares.OP_LOAD, 500));
        assertNull(Shares.messageForManagementFailure(Shares.OP_LOAD, 401));
    }

    @Test
    void createFailureOn404MeansTheListIsGone() {
        assertEquals("That list no longer exists.",
                Shares.messageForManagementFailure(Shares.OP_CREATE, 404));
    }

    @Test
    void createFailureOnNon404HasNoSpecialMessage() {
        assertNull(Shares.messageForManagementFailure(Shares.OP_CREATE, 500));
    }

    @Test
    void revokeFailureOn404HasNoMessageBecauseItIsASilentSuccess() {
        assertNull(Shares.messageForManagementFailure(Shares.OP_REVOKE, 404));
    }

    @Test
    void revokeFailureOnNon404HasNoSpecialMessageEither() {
        assertNull(Shares.messageForManagementFailure(Shares.OP_REVOKE, 500));
    }

    @Test
    void isAlreadyGoneTrueOnlyForRevoke404() {
        assertTrue(Shares.isAlreadyGone(Shares.OP_REVOKE, 404));
    }

    @Test
    void isAlreadyGoneFalseForRevokeOnOtherStatuses() {
        assertFalse(Shares.isAlreadyGone(Shares.OP_REVOKE, 500));
        assertFalse(Shares.isAlreadyGone(Shares.OP_REVOKE, 401));
    }

    @Test
    void isAlreadyGoneFalseFor404OnLoadOrCreate() {
        assertFalse(Shares.isAlreadyGone(Shares.OP_LOAD, 404));
        assertFalse(Shares.isAlreadyGone(Shares.OP_CREATE, 404));
    }
}
