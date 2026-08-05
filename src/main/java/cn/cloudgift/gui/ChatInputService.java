package cn.cloudgift.gui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Captures the next chat line a player types after being prompted, delivering it back on the main
 * thread. Used by the GUI editor for text fields such as display name and permission.
 */
public final class ChatInputService implements Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, Consumer<String>> pending = new ConcurrentHashMap<>();

    public ChatInputService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers a callback to receive the player's next chat message. Typing "cancel" aborts and the
     * callback is dropped. The callback runs on the main server thread.
     */
    public void await(Player player, Consumer<String> callback) {
        pending.put(player.getUniqueId(), callback);
    }

    public boolean isAwaiting(UUID playerId) {
        return pending.containsKey(playerId);
    }

    public void cancel(UUID playerId) {
        pending.remove(playerId);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onChat(AsyncPlayerChatEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        Consumer<String> callback = pending.remove(playerId);
        if (callback == null) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!message.equalsIgnoreCase("cancel")) {
                callback.accept(message);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }
}
