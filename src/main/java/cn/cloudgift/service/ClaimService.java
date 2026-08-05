package cn.cloudgift.service;

import cn.cloudgift.config.PluginSettings;
import cn.cloudgift.gift.GiftDefinition;
import cn.cloudgift.gift.GiftRegistry;
import cn.cloudgift.message.MessageService;
import cn.cloudgift.storage.ClaimAttempt;
import cn.cloudgift.storage.ClaimRecord;
import cn.cloudgift.storage.ClaimRepository;
import java.sql.SQLException;
import java.util.Map;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class ClaimService {

    private final JavaPlugin plugin;
    private final GiftRegistry giftRegistry;
    private final ClaimRepository repository;
    private final RewardService rewardService;
    private final MessageService messages;
    private final PluginSettings settings;
    private final Map<UUID, Map<String, ClaimRecord>> cache = new ConcurrentHashMap<>();
    private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();
    private final Set<UUID> loading = ConcurrentHashMap.newKeySet();
    private final Set<ClaimKey> inFlight = ConcurrentHashMap.newKeySet();
    private volatile boolean shuttingDown;

    public ClaimService(
            JavaPlugin plugin,
            GiftRegistry giftRegistry,
            ClaimRepository repository,
            RewardService rewardService,
            MessageService messages,
            PluginSettings settings) {
        this.plugin = plugin;
        this.giftRegistry = giftRegistry;
        this.repository = repository;
        this.rewardService = rewardService;
        this.messages = messages;
        this.settings = settings;
    }

    public void claim(Player player, String requestedGiftId) {
        GiftDefinition gift = giftRegistry.find(requestedGiftId).orElse(null);
        if (gift == null) {
            messages.send(player, "unknown-gift", Map.of("gift", requestedGiftId));
            return;
        }
        if (gift.hasPermission() && !player.hasPermission(gift.permission())) {
            messages.send(player, "no-permission", giftDisplayName(gift));
            return;
        }

        ClaimKey key = new ClaimKey(player.getUniqueId(), gift.id());
        if (!inFlight.add(key)) {
            messages.send(player, "claim-busy");
            return;
        }
        long requestedAt = System.currentTimeMillis();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ClaimAttempt attempt = repository.attemptClaim(
                        player.getUniqueId(),
                        gift.id(),
                        gift.claimableLastClaimAt(requestedAt, settings.zoneId()),
                        gift.maxClaims(),
                        requestedAt);
                runOnMainThread(() -> finishClaim(player.getUniqueId(), gift, key, attempt));
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.SEVERE, "领取礼包时数据库操作失败", exception);
                runOnMainThread(() -> {
                    inFlight.remove(key);
                    Player online = Bukkit.getPlayer(player.getUniqueId());
                    if (online != null) {
                        messages.send(online, "database-error");
                    }
                });
            }
        });
    }

    public void preload(Player player) {
        UUID playerId = player.getUniqueId();
        if (!loading.add(playerId)) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Map<String, ClaimRecord> claims = new ConcurrentHashMap<>(repository.loadClaims(playerId));
                runOnMainThread(() -> {
                    loading.remove(playerId);
                    if (Bukkit.getPlayer(playerId) != null) {
                        cache.put(playerId, claims);
                        loaded.add(playerId);
                    }
                });
            } catch (SQLException exception) {
                loading.remove(playerId);
                plugin.getLogger().log(Level.SEVERE, "载入玩家礼包记录失败: " + playerId, exception);
            }
        });
    }

    public void ensurePreloaded(Player player) {
        if (!loaded.contains(player.getUniqueId())) {
            preload(player);
        }
    }

    public void unload(UUID playerId) {
        loaded.remove(playerId);
        loading.remove(playerId);
        cache.remove(playerId);
        inFlight.removeIf(key -> key.playerId().equals(playerId));
    }

    public boolean isLoaded(UUID playerId) {
        return loaded.contains(playerId);
    }

    public OptionalLong cachedLastClaim(UUID playerId, String giftId) {
        ClaimRecord record = cachedRecord(playerId, giftId);
        return record == null ? OptionalLong.empty() : OptionalLong.of(record.lastClaimAt());
    }

    public OptionalInt cachedClaimCount(UUID playerId, String giftId) {
        ClaimRecord record = cachedRecord(playerId, giftId);
        return record == null ? OptionalInt.empty() : OptionalInt.of(record.claimCount());
    }

    private ClaimRecord cachedRecord(UUID playerId, String giftId) {
        Map<String, ClaimRecord> claims = cache.get(playerId);
        return claims == null ? null : claims.get(giftId);
    }

    public boolean canClaimCached(Player player, GiftDefinition gift, long now) {
        if (gift.hasPermission() && !player.hasPermission(gift.permission())) {
            return false;
        }
        if (!isLoaded(player.getUniqueId())) {
            ensurePreloaded(player);
            return false;
        }
        ClaimRecord record = cachedRecord(player.getUniqueId(), gift.id());
        if (record == null) {
            return true;
        }
        // Usage limit takes priority over the cooldown check.
        if (gift.limitReached(record.claimCount())) {
            return false;
        }
        return gift.nextClaimAt(record.lastClaimAt(), settings.zoneId()) <= now;
    }

    public void reset(CommandSender sender, UUID playerId, String playerLabel, GiftDefinition gift) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                repository.reset(playerId, gift.id());
                runOnMainThread(() -> {
                    Map<String, ClaimRecord> claims = cache.get(playerId);
                    if (claims != null) {
                        claims.remove(gift.id());
                    }
                    messages.send(sender, "reset-success",
                            Placeholder.unparsed("player", playerLabel),
                            giftDisplayName(gift));
                });
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.SEVERE, "重置礼包领取记录失败", exception);
                runOnMainThread(() -> messages.send(sender, "database-error"));
            }
        });
    }

    public void shutdown() {
        shuttingDown = true;
        cache.clear();
        loaded.clear();
        loading.clear();
        inFlight.clear();
    }

    private void finishClaim(UUID playerId, GiftDefinition gift, ClaimKey key, ClaimAttempt attempt) {
        inFlight.remove(key);
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }
        cache.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>())
                .put(gift.id(), new ClaimRecord(attempt.lastClaimAt(), attempt.claimCount()));
        loaded.add(playerId);

        if (attempt.status() == ClaimAttempt.Status.LIMIT_REACHED) {
            messages.send(player, "limit-reached",
                    giftDisplayName(gift),
                    Placeholder.unparsed("limit", Integer.toString(gift.maxClaims())),
                    Placeholder.unparsed("used", Integer.toString(attempt.claimCount())));
            return;
        }
        if (!attempt.accepted()) {
            long nextClaim = gift.nextClaimAt(attempt.lastClaimAt(), settings.zoneId());
            messages.send(player, "cooldown",
                    giftDisplayName(gift),
                    Placeholder.unparsed("next_time", settings.format(nextClaim)));
            return;
        }

        boolean granted = rewardService.grant(player, gift);
        messages.send(player, granted ? "claim-success" : "reward-error", giftDisplayName(gift));
    }

    private TagResolver giftDisplayName(GiftDefinition gift) {
        return Placeholder.component("gift", messages.parse(gift.displayName()));
    }

    private void runOnMainThread(Runnable runnable) {
        if (!shuttingDown && plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    private record ClaimKey(UUID playerId, String giftId) {}
}
