package cn.cloudgift;

import cn.cloudgift.command.CloudGiftCommand;
import cn.cloudgift.config.PluginSettings;
import cn.cloudgift.gift.GiftRegistry;
import cn.cloudgift.gift.ItemStore;
import cn.cloudgift.listener.PlayerDataListener;
import cn.cloudgift.message.MessageService;
import cn.cloudgift.placeholder.CloudGiftExpansion;
import cn.cloudgift.service.ClaimService;
import cn.cloudgift.service.RewardService;
import cn.cloudgift.storage.ClaimRepository;
import cn.cloudgift.storage.JdbcClaimRepository;
import java.io.File;
import java.sql.SQLException;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CloudGiftPlugin extends JavaPlugin {

    private PluginSettings settings;
    private MessageService messages;
    private GiftRegistry gifts;
    private ItemStore items;
    private ClaimRepository repository;
    private ClaimService claims;
    private CloudGiftExpansion expansion;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing("messages.yml");
        saveResourceIfMissing("gifts.yml");
        saveResourceIfMissing("items.yml");

        settings = new PluginSettings(this);
        messages = new MessageService(this);
        items = new ItemStore(this);
        items.reload();
        gifts = new GiftRegistry(this);
        int giftCount = gifts.reload();

        try {
            repository = new JdbcClaimRepository(this);
        } catch (SQLException | RuntimeException exception) {
            getLogger().severe("数据库初始化失败，CloudGift 将被禁用: " + exception.getMessage());
            exception.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        RewardService rewards = new RewardService(this, items);
        claims = new ClaimService(this, gifts, repository, rewards, messages, settings);
        CloudGiftCommand commandHandler = new CloudGiftCommand(this, gifts, items, claims, messages);
        registerCommand("gift", commandHandler);
        registerCommand("cloudgift", commandHandler);
        getServer().getPluginManager().registerEvents(new PlayerDataListener(claims, settings), this);

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            expansion = new CloudGiftExpansion(this, gifts, claims, settings);
            if (expansion.register()) {
                getLogger().info("PlaceholderAPI 变量已注册。");
            } else {
                getLogger().warning("PlaceholderAPI 变量注册失败。");
            }
        }

        Bukkit.getOnlinePlayers().forEach(claims::preload);
        getLogger().info("CloudGift 已启用，共载入 " + giftCount + " 个礼包。");
    }

    @Override
    public void onDisable() {
        if (expansion != null) {
            expansion.unregister();
        }
        if (claims != null) {
            claims.shutdown();
        }
        if (repository != null) {
            repository.close();
        }
    }

    public int reloadCloudGift() {
        reloadConfig();
        settings.reload();
        messages.reload();
        items.reload();
        return gifts.reload();
    }

    private void saveResourceIfMissing(String name) {
        if (!new File(getDataFolder(), name).isFile()) {
            saveResource(name, false);
        }
    }

    private void registerCommand(String name, CloudGiftCommand handler) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            throw new IllegalStateException("plugin.yml 中缺少命令: " + name);
        }
        command.setExecutor(handler);
        command.setTabCompleter(handler);
    }
}
