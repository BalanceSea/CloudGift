package cn.cloudgift.gift;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
        saveAll(Collections.singletonMap(id, item));
    }

    public synchronized void saveAll(Map<String, ItemStack> batch) throws IOException {
        Objects.requireNonNull(batch, "batch");
        if (batch.isEmpty()) {
            return;
        }

        Map<String, ItemStack> normalizedItems = new LinkedHashMap<>();
        for (Map.Entry<String, ItemStack> entry : batch.entrySet()) {
            String id = entry.getKey();
            ItemStack item = entry.getValue();
            if (!isValidId(id)) {
                throw new IllegalArgumentException("Invalid item id");
            }
            if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
                throw new IllegalArgumentException("Item cannot be empty");
            }
            String normalized = id.toLowerCase(Locale.ROOT);
            if (normalizedItems.putIfAbsent(normalized, item.clone()) != null) {
                throw new IllegalArgumentException("Duplicate item id: " + normalized);
            }
        }

        ensureLoaded();
        Map<String, Object> previousValues = new LinkedHashMap<>();
        normalizedItems.forEach((id, item) -> {
            String path = "items." + id;
            previousValues.put(id, yaml.get(path));
            yaml.set(path, item);
        });

        try {
            saveYamlAtomically();
        } catch (IOException | RuntimeException exception) {
            previousValues.forEach((id, value) -> yaml.set("items." + id, value));
            throw exception;
        }
        normalizedItems.forEach((id, item) -> items.put(id, item.clone()));
    }

    public synchronized boolean delete(String id) throws IOException {
        if (!isValidId(id)) {
            return false;
        }
        return deleteAll(Collections.singleton(id)).contains(id.toLowerCase(Locale.ROOT));
    }

    public synchronized Set<String> deleteAll(Iterable<String> ids) throws IOException {
        Objects.requireNonNull(ids, "ids");
        ensureLoaded();
        Map<String, Object> previousValues = new LinkedHashMap<>();
        for (String id : ids) {
            if (!isValidId(id)) {
                continue;
            }
            String normalized = id.toLowerCase(Locale.ROOT);
            String path = "items." + normalized;
            if (!previousValues.containsKey(normalized)
                    && (items.containsKey(normalized) || yaml.contains(path))) {
                previousValues.put(normalized, yaml.get(path));
            }
        }
        if (previousValues.isEmpty()) {
            return Set.of();
        }

        previousValues.keySet().forEach(id -> yaml.set("items." + id, null));
        try {
            saveYamlAtomically();
        } catch (IOException | RuntimeException exception) {
            previousValues.forEach((itemId, previous) -> yaml.set("items." + itemId, previous));
            throw exception;
        }
        previousValues.keySet().forEach(items::remove);
        return new LinkedHashSet<>(previousValues.keySet());
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

    private void saveYamlAtomically() throws IOException {
        File parent = file.getParentFile();
        Files.createDirectories(parent.toPath());
        File temporary = Files.createTempFile(parent.toPath(), "items-", ".tmp").toFile();
        try {
            yaml.save(temporary);
            try {
                Files.move(temporary.toPath(), file.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary.toPath());
        }
    }
}
