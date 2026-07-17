package cn.cloudgift.gift;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class GiftDefinitionTest {

    @Test
    void calculatesNextClaimFromPlayersLastClaimTime() {
        GiftDefinition gift = new GiftDefinition("monthly", "Monthly", "", 86_400_000L, List.of());

        assertEquals(1_086_400_000L, gift.nextClaimAt(1_000_000_000L));
    }

    @Test
    void saturatesWhenTimestampWouldOverflow() {
        GiftDefinition gift = new GiftDefinition("monthly", "Monthly", "", 10L, List.of());

        assertEquals(Long.MAX_VALUE, gift.nextClaimAt(Long.MAX_VALUE - 5L));
    }
}
