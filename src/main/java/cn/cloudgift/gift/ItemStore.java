package cn.cloudgift.gift;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class ItemStore {

    private static final String ID_PATTERN = "[a-z0-9_-]+";

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, ItemStack> items = new ConcurrentHashMap<>();
    private YamlConfiguration yaml;

    public ItemStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "items.yml");
    }

    public void reload() {
        yaml = YamlConfiguration.loadConfiguration(file);
        items.clear();
        ConfigurationSection root = yaml.getConfigurationSection("items");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ItemStack item = root.getItemStack(key);
            if (item != null && item.getType() != Material.AIR) {
                items.put(key.toLowerCase(Locale.ROOT), item.clone());
            } else {
                plugin.getLogger().warning("无法读取保存物品: " + key);
            }
        }
    }

    public boolean isValidId(String id) {
        return id != null && id.toLowerCase(Locale.ROOT).matches(ID_PATTERN);
    }

    public void save(String id, ItemStack item) throws IOException {
        String normalized = id.toLowerCase(Locale.ROOT);
        if (!isValidId(normalized)) {
            throw new IllegalArgumentException("Invalid item id");
        }
        ItemStack copy = item.clone();
        yaml.set("items." + normalized, copy);
        yaml.save(file);
        items.put(normalized, copy);
    }

    public Optional<ItemStack> find(String id) {
        ItemStack item = items.get(id.toLowerCase(Locale.ROOT));
        return item == null ? Optional.empty() : Optional.of(item.clone());
    }
}
