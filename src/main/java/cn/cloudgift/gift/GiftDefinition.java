package cn.cloudgift.gift;

import java.util.List;

public record GiftDefinition(
        String id,
        String displayName,
        String permission,
        long cooldownMillis,
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

    public long nextClaimAt(long lastClaimAt) {
        if (lastClaimAt > Long.MAX_VALUE - cooldownMillis) {
            return Long.MAX_VALUE;
        }
        return lastClaimAt + cooldownMillis;
    }

    /** Returns true when the given claim count has reached this gift's usage limit. */
    public boolean limitReached(int claimCount) {
        return hasClaimLimit() && claimCount >= maxClaims;
    }
}
