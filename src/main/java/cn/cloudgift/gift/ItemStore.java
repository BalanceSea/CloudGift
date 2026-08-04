package cn.cloudgift.gift;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class ItemStore {

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_-]+");
    private static final String GUI_ITEM_PREFIX = "__cloudgift_gui_";

    private final File file;
    private final Logger logger;
    private final Map<String, ItemStack> items = new ConcurrentHashMap<>();
    private YamlConfiguration yaml;

    public ItemStore(JavaPlugin plugin) {
        this(plugin.getDataFolder(), plugin.getLogger());
    }

    ItemStore(File dataFolder, Logger logger) {
        this.file = new File(Objects.requireNonNull(dataFolder, "dataFolder"), "items.yml");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public synchronized void reload() {
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
                logger.warning("无法读取保存物品: " + key);
            }
        }
    }

    public boolean isValidId(String id) {
        return id != null && ID_PATTERN.matcher(id.toLowerCase(Locale.ROOT)).matches();
    }

    public synchronized void save(String id, ItemStack item) throws IOException {
        if (!isValidId(id)) {
            throw new IllegalArgumentException("Invalid item id");
        }
        String normalized = id.toLowerCase(Locale.ROOT);
        if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
            throw new IllegalArgumentException("Item cannot be empty");
        }
        ensureLoaded();
        ItemStack copy = item.clone();
        yaml.set("items." + normalized, copy);
        yaml.save(file);
        items.put(normalized, copy);
    }

    public synchronized boolean delete(String id) throws IOException {
        if (!isValidId(id)) {
            return false;
        }
        ensureLoaded();
        String normalized = id.toLowerCase(Locale.ROOT);
        if (!items.containsKey(normalized) && !yaml.contains("items." + normalized)) {
            return false;
        }
        yaml.set("items." + normalized, null);
        yaml.save(file);
        items.remove(normalized);
        return true;
    }

    public String nextGuiItemId(String giftId) {
        String normalizedGiftId = giftId == null ? "gift" : giftId.toLowerCase(Locale.ROOT);
        if (!isValidId(normalizedGiftId)) {
            normalizedGiftId = "gift";
        }
        String candidate;
        do {
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            candidate = GUI_ITEM_PREFIX + normalizedGiftId + "_" + suffix;
        } while (items.containsKey(candidate));
        return candidate;
    }

    public Optional<ItemStack> find(String id) {
        if (id == null) {
            return Optional.empty();
        }
        ItemStack item = items.get(id.toLowerCase(Locale.ROOT));
        return item == null ? Optional.empty() : Optional.of(item.clone());
    }

    private void ensureLoaded() {
        if (yaml == null) {
            reload();
        }
    }
}
