package cn.cloudgift.service;

import cn.cloudgift.gift.GiftDefinition;
import cn.cloudgift.gift.ItemStore;
import cn.cloudgift.gift.RewardDefinition;
import java.util.Map;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;

public final class RewardService {

    private final JavaPlugin plugin;
    private final ItemStore itemStore;

    public RewardService(JavaPlugin plugin, ItemStore itemStore) {
        this.plugin = plugin;
        this.itemStore = itemStore;
    }

    public boolean grant(Player player, GiftDefinition gift) {
        boolean successful = true;
        for (RewardDefinition reward : gift.rewards()) {
            try {
                if (reward instanceof RewardDefinition.CommandReward commandReward) {
                    successful &= executeCommand(player, gift, commandReward.command());
                } else if (reward instanceof RewardDefinition.ItemReward itemReward) {
                    successful &= giveItem(player, gift, itemReward);
                }
            } catch (RuntimeException exception) {
                successful = false;
                plugin.getLogger().severe("发放礼包 " + gift.id() + " 奖励时发生异常: " + exception.getMessage());
                exception.printStackTrace();
            }
        }
        return successful;
    }

    private boolean executeCommand(Player player, GiftDefinition gift, String configuredCommand) {
        String command = configuredCommand
                .replace("%player%", player.getName())
                .replace("%uuid%", player.getUniqueId().toString())
                .replace("%gift%", gift.id());
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            command = PlaceholderAPI.setPlaceholders(player, command);
        }
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        boolean dispatched = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        if (!dispatched) {
            plugin.getLogger().warning("礼包 " + gift.id() + " 的控制台命令执行失败: " + command);
        }
        return dispatched;
    }

    private boolean giveItem(Player player, GiftDefinition gift, RewardDefinition.ItemReward reward) {
        ItemStack saved = itemStore.find(reward.itemId()).orElse(null);
        if (saved == null) {
            plugin.getLogger().warning("礼包 " + gift.id() + " 引用了不存在的保存物品: " + reward.itemId());
            return false;
        }

        int remaining = reward.amount() < 0 ? saved.getAmount() : reward.amount();
        int stackSize = Math.max(1, saved.getMaxStackSize());
        while (remaining > 0) {
            ItemStack part = saved.clone();
            int amount = Math.min(remaining, stackSize);
            part.setAmount(amount);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(part);
            for (ItemStack item : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
            remaining -= amount;
        }
        return true;
    }
}
