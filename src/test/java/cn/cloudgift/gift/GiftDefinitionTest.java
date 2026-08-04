package cn.cloudgift.gift;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class GiftDefinitionTest {

    @Test
    void calculatesNextClaimFromPlayersLastClaimTime() {
        GiftDefinition gift = new GiftDefinition("monthly", "Monthly", "", 86_400_000L, 0, List.of());

        assertEquals(1_086_400_000L, gift.nextClaimAt(1_000_000_000L));
    }

    @Test
    void saturatesWhenTimestampWouldOverflow() {
        GiftDefinition gift = new GiftDefinition("monthly", "Monthly", "", 10L, 0, List.of());

        assertEquals(Long.MAX_VALUE, gift.nextClaimAt(Long.MAX_VALUE - 5L));
    }

    @Test
    void treatsNonPositiveMaxClaimsAsUnlimited() {
        GiftDefinition unlimited = new GiftDefinition("monthly", "Monthly", "", 10L, 0, List.of());

        assertFalse(unlimited.hasClaimLimit());
        assertFalse(unlimited.limitReached(9_999));
    }

    @Test
    void reportsLimitReachedOnceCountMeetsMax() {
        GiftDefinition limited = new GiftDefinition("monthly", "Monthly", "", 10L, 3, List.of());

        assertTrue(limited.hasClaimLimit());
        assertFalse(limited.limitReached(2));
        assertTrue(limited.limitReached(3));
        assertTrue(limited.limitReached(4));
    }
}
