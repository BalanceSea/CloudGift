package cn.cloudgift.gift;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

public record GiftDefinition(
        String id,
        String displayName,
        String permission,
        long cooldownMillis,
        boolean resetAtMidnight,
        int maxClaims,
        List<RewardDefinition> rewards) {

    public GiftDefinition {
        rewards = List.copyOf(rewards);
    }

    public boolean hasPermission() {
        return permission != null && !permission.isBlank();
    }

    public boolean hasClaimLimit() {
        return maxClaims > 0;
    }

    public long nextClaimAt(long lastClaimAt, ZoneId zoneId) {
        Objects.requireNonNull(zoneId, "zoneId");
        if (resetAtMidnight) {
            return Instant.ofEpochMilli(lastClaimAt)
                    .atZone(zoneId)
                    .toLocalDate()
                    .plusDays(1L)
                    .atStartOfDay(zoneId)
                    .toInstant()
                    .toEpochMilli();
        }
        if (lastClaimAt > Long.MAX_VALUE - cooldownMillis) {
            return Long.MAX_VALUE;
        }
        return lastClaimAt + cooldownMillis;
    }

    /**
     * Returns the latest persisted claim time that may be atomically replaced by a claim at {@code now}.
     */
    public long claimableLastClaimAt(long now, ZoneId zoneId) {
        Objects.requireNonNull(zoneId, "zoneId");
        if (!resetAtMidnight) {
            return now - Math.min(now, Math.max(0L, cooldownMillis));
        }
        long startOfToday = Instant.ofEpochMilli(now)
                .atZone(zoneId)
                .toLocalDate()
                .atStartOfDay(zoneId)
                .toInstant()
                .toEpochMilli();
        return startOfToday == Long.MIN_VALUE ? Long.MIN_VALUE : startOfToday - 1L;
    }

    /** Returns true when the given claim count has reached this gift's usage limit. */
    public boolean limitReached(int claimCount) {
        return hasClaimLimit() && claimCount >= maxClaims;
    }
}
