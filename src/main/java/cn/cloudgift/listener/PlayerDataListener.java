package cn.cloudgift.listener;

import cn.cloudgift.config.PluginSettings;
import cn.cloudgift.service.ClaimService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerDataListener implements Listener {

    private final ClaimService claims;
    private final PluginSettings settings;

    public PlayerDataListener(ClaimService claims, PluginSettings settings) {
        this.claims = claims;
        this.settings = settings;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (settings.preloadOnJoin()) {
            claims.preload(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        claims.unload(event.getPlayer().getUniqueId());
    }
}
