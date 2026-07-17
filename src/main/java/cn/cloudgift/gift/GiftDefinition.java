package cn.cloudgift.gift;

import java.util.List;

public record GiftDefinition(
        String id,
        String displayName,
        String permission,
        long cooldownMillis,
        List<RewardDefinition> rewards) {

    public GiftDefinition {
        rewards = List.copyOf(rewards);
    }

    public boolean hasPermission() {
        return permission != null && !permission.isBlank();
    }

    public long nextClaimAt(long lastClaimAt) {
        if (lastClaimAt > Long.MAX_VALUE - cooldownMillis) {
            return Long.MAX_VALUE;
        }
        return lastClaimAt + cooldownMillis;
    }
}
