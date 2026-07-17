package cn.cloudgift.placeholder;

import cn.cloudgift.config.PluginSettings;
import cn.cloudgift.gift.GiftDefinition;
import cn.cloudgift.gift.GiftRegistry;
import cn.cloudgift.service.ClaimService;
import java.util.Locale;
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
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
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
            OptionalLong lastClaim = claims.cachedLastClaim(player.getUniqueId(), gift.id());
            if (lastClaim.isEmpty()) {
                return settings.availableTime();
            }
            long next = gift.nextClaimAt(lastClaim.getAsLong());
            return next <= System.currentTimeMillis() ? settings.availableTime() : settings.format(next);
        }
        return null;
    }
}
