package cn.cloudgift.placeholder;

import cn.cloudgift.config.PluginSettings;
import cn.cloudgift.gift.GiftDefinition;
import cn.cloudgift.gift.GiftRegistry;
import cn.cloudgift.service.ClaimService;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.OptionalLong;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import cn.cloudgift.CloudGiftPlugin;

public final class CloudGiftExpansion extends PlaceholderExpansion {

    private final CloudGiftPlugin plugin;
    private final GiftRegistry gifts;
    private final ClaimService claims;
    private final PluginSettings settings;

    public CloudGiftExpansion(
            CloudGiftPlugin plugin,
            GiftRegistry gifts,
            ClaimService claims,
            PluginSettings settings) {
        this.plugin = plugin;
        this.gifts = gifts;
        this.claims = claims;
        this.settings = settings;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "cloudgift";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String parameters) {
        if (player == null) {
            return "";
        }
        String normalized = parameters.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("can_")) {
            String giftId = normalized.substring("can_".length());
            GiftDefinition gift = gifts.find(giftId).orElse(null);
            if (gift == null) {
                return "no";
            }
            return claims.canClaimCached(player, gift, System.currentTimeMillis()) ? "yes" : "no";
        }
        if (normalized.startsWith("next_")) {
            String giftId = normalized.substring("next_".length());
            GiftDefinition gift = gifts.find(giftId).orElse(null);
            if (gift == null) {
                return settings.unknownGift();
            }
            if (gift.hasPermission() && !player.hasPermission(gift.permission())) {
                return settings.noPermissionTime();
            }
            if (!claims.isLoaded(player.getUniqueId())) {
                claims.ensurePreloaded(player);
                return settings.loadingTime();
            }
            OptionalInt count = claims.cachedClaimCount(player.getUniqueId(), gift.id());
            // Usage limit takes priority over the cooldown display.
            if (count.isPresent() && gift.limitReached(count.getAsInt())) {
                return settings.limitReachedTime();
            }
            OptionalLong lastClaim = claims.cachedLastClaim(player.getUniqueId(), gift.id());
            if (lastClaim.isEmpty()) {
                return settings.availableTime();
            }
            long next = gift.nextClaimAt(lastClaim.getAsLong(), settings.zoneId());
            return next <= System.currentTimeMillis() ? settings.availableTime() : settings.format(next);
        }
        if (normalized.startsWith("used_")) {
            String giftId = normalized.substring("used_".length());
            if (gifts.find(giftId).isEmpty()) {
                return settings.unknownGift();
            }
            return Integer.toString(claims.cachedClaimCount(player.getUniqueId(), giftId).orElse(0));
        }
        if (normalized.startsWith("limit_")) {
            String giftId = normalized.substring("limit_".length());
            GiftDefinition gift = gifts.find(giftId).orElse(null);
            if (gift == null) {
                return settings.unknownGift();
            }
            return gift.hasClaimLimit() ? Integer.toString(gift.maxClaims()) : "∞";
        }
        if (normalized.startsWith("remaining_")) {
            String giftId = normalized.substring("remaining_".length());
            GiftDefinition gift = gifts.find(giftId).orElse(null);
            if (gift == null) {
                return settings.unknownGift();
            }
            if (!gift.hasClaimLimit()) {
                return "∞";
            }
            int used = claims.cachedClaimCount(player.getUniqueId(), giftId).orElse(0);
            return Integer.toString(Math.max(0, gift.maxClaims() - used));
        }
        return null;
    }
}
