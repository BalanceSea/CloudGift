package cn.cloudgift.gift;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class GiftDefinitionTest {

    @Test
    void calculatesNextClaimFromPlayersLastClaimTime() {
        GiftDefinition gift = new GiftDefinition(
                "monthly", "Monthly", "", 86_400_000L, false, 0, List.of());

        assertEquals(1_086_400_000L, gift.nextClaimAt(1_000_000_000L, ZoneId.of("UTC")));
        assertEquals(913_600_000L, gift.claimableLastClaimAt(1_000_000_000L, ZoneId.of("UTC")));
    }

    @Test
    void refreshesAtNextMidnightInConfiguredTimeZone() {
        ZoneId zoneId = ZoneId.of("Asia/Shanghai");
        long lastClaim = epochMillis(zoneId, 2026, 8, 5, 23, 59);
        long startOfClaimDay = epochMillis(zoneId, 2026, 8, 5, 0, 0);
        long nextMidnight = epochMillis(zoneId, 2026, 8, 6, 0, 0);
        GiftDefinition gift = new GiftDefinition(
                "daily", "Daily", "", 86_400_000L, true, 0, List.of());

        assertEquals(nextMidnight, gift.nextClaimAt(lastClaim, zoneId));
        assertEquals(startOfClaimDay - 1L, gift.claimableLastClaimAt(lastClaim, zoneId));
        assertEquals(nextMidnight - 1L, gift.claimableLastClaimAt(nextMidnight, zoneId));
        assertEquals(nextMidnight - 1L,
                gift.claimableLastClaimAt(epochMillis(zoneId, 2026, 8, 6, 12, 0), zoneId));
    }

    @Test
    void saturatesWhenTimestampWouldOverflow() {
        GiftDefinition gift = new GiftDefinition("monthly", "Monthly", "", 10L, false, 0, List.of());

        assertEquals(Long.MAX_VALUE, gift.nextClaimAt(Long.MAX_VALUE - 5L, ZoneId.of("UTC")));
    }

    @Test
    void treatsNonPositiveMaxClaimsAsUnlimited() {
        GiftDefinition unlimited = new GiftDefinition("monthly", "Monthly", "", 10L, false, 0, List.of());

        assertFalse(unlimited.hasClaimLimit());
        assertFalse(unlimited.limitReached(9_999));
    }

    @Test
    void reportsLimitReachedOnceCountMeetsMax() {
        GiftDefinition limited = new GiftDefinition("monthly", "Monthly", "", 10L, false, 3, List.of());

        assertTrue(limited.hasClaimLimit());
        assertFalse(limited.limitReached(2));
        assertTrue(limited.limitReached(3));
        assertTrue(limited.limitReached(4));
    }

    private long epochMillis(ZoneId zoneId, int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zoneId)
                .toInstant()
                .toEpochMilli();
    }
}
