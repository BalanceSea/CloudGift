package cn.cloudgift.gui;

import cn.cloudgift.gift.GiftDefinition;
import cn.cloudgift.gift.RewardDefinition;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Mutable editing state for a single gift, manipulated by the GUI and then persisted. */
public final class GiftDraft {

    private final String id;
    private final boolean existing;
    private String displayName;
    private String permission;
    private long cooldownMillis;
    private int maxClaims;
    private final List<RewardDefinition> rewards;
    private final Set<String> temporaryItemIds = new LinkedHashSet<>();

    private GiftDraft(String id, boolean existing, String displayName, String permission,
            long cooldownMillis, int maxClaims, List<RewardDefinition> rewards) {
        this.id = id;
        this.existing = existing;
        this.displayName = displayName;
        this.permission = permission;
        this.cooldownMillis = cooldownMillis;
        this.maxClaims = maxClaims;
        this.rewards = new ArrayList<>(rewards);
    }

    public static GiftDraft of(GiftDefinition gift) {
        return new GiftDraft(gift.id(), true, gift.displayName(), gift.permission(),
                gift.cooldownMillis(), gift.maxClaims(), gift.rewards());
    }

    public static GiftDraft fresh(String id) {
        return new GiftDraft(id, false, id, "", 24L * 3_600_000L, 0, List.of());
    }

    public GiftDefinition toDefinition() {
        return new GiftDefinition(id, displayName, permission, cooldownMillis, maxClaims, rewards);
    }

    public String id() {
        return id;
    }

    public boolean existing() {
        return existing;
    }

    public String displayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String permission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission == null ? "" : permission.trim();
    }

    public long cooldownMillis() {
        return cooldownMillis;
    }

    public double cooldownHours() {
        return cooldownMillis / 3_600_000.0D;
    }

    /** Adjusts cooldown by the given hours, clamped to a non-negative, sane range. */
    public void addCooldownHours(double deltaHours) {
        double hours = Math.max(0.0D, cooldownHours() + deltaHours);
        hours = Math.min(hours, 2_562_047_788.0D);
        this.cooldownMillis = Math.round(hours * 3_600_000.0D);
    }

    public int maxClaims() {
        return maxClaims;
    }

    /** Adjusts the usage limit; never drops below 0 (0 = unlimited). */
    public void addMaxClaims(int delta) {
        this.maxClaims = Math.max(0, Math.min(1_000_000, maxClaims + delta));
    }

    public List<RewardDefinition> rewards() {
        return rewards;
    }

    public int remainingRewardCapacity(int maximum) {
        return Math.max(0, maximum - rewards.size());
    }

    public void trackTemporaryItem(String itemId) {
        if (itemId != null && !itemId.isBlank()) {
            temporaryItemIds.add(itemId);
        }
    }

    public boolean isTemporaryItem(String itemId) {
        return temporaryItemIds.contains(itemId);
    }

    public List<String> temporaryItemIds() {
        return List.copyOf(temporaryItemIds);
    }

    public void releaseTemporaryItem(String itemId) {
        temporaryItemIds.remove(itemId);
    }

    public void commitTemporaryItems() {
        temporaryItemIds.clear();
    }
}
