package cn.cloudgift.gift;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class GiftRegistry {

    private static final String ID_PATTERN = "[a-z0-9_-]+";

    private final JavaPlugin plugin;
    private volatile Map<String, GiftDefinition> gifts = Map.of();

    public GiftRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public int reload() {
        Map<String, GiftDefinition> loaded = new LinkedHashMap<>();
        List<File> files = findGiftFiles();
        for (File file : files) {
            loadFile(file, loaded);
        }
        gifts = Collections.unmodifiableMap(loaded);
        return gifts.size();
    }

    public Optional<GiftDefinition> find(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(gifts.get(id.toLowerCase(Locale.ROOT)));
    }

    public List<GiftDefinition> all() {
        return List.copyOf(gifts.values());
    }

    private List<File> findGiftFiles() {
        List<File> files = new ArrayList<>();
        File singleFile = new File(plugin.getDataFolder(), "gifts.yml");
        if (singleFile.isFile()) {
            files.add(singleFile);
        }

        File directory = new File(plugin.getDataFolder(), "gifts");
        collectYamlFiles(directory, files);
        files.sort(Comparator.comparing(File::getAbsolutePath, String.CASE_INSENSITIVE_ORDER));
        return files;
    }

    private void collectYamlFiles(File directory, List<File> output) {
        if (!directory.isDirectory()) {
            return;
        }
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collectYamlFiles(child, output);
            } else if (child.getName().toLowerCase(Locale.ROOT).endsWith(".yml")) {
                output.add(child);
            }
        }
    }

    private void loadFile(File file, Map<String, GiftDefinition> loaded) {
        Logger logger = plugin.getLogger();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("gifts");
        if (root == null) {
            logger.warning("礼包文件缺少 gifts: 节点，已跳过: " + relativePath(file));
            return;
        }

        for (String rawId : root.getKeys(false)) {
            String id = rawId.toLowerCase(Locale.ROOT);
            if (!id.matches(ID_PATTERN) || id.length() > 128) {
                logger.warning("礼包 ID 只能包含小写字母、数字、下划线和连字符且最长 128 字符，已跳过: " + rawId);
                continue;
            }
            if (loaded.containsKey(id)) {
                logger.warning("发现重复礼包 ID，保留先载入的定义并跳过: " + id + " (" + relativePath(file) + ")");
                continue;
            }

            ConfigurationSection section = root.getConfigurationSection(rawId);
            if (section == null) {
                logger.warning("礼包配置不是有效节点，已跳过: " + id);
                continue;
            }
            GiftDefinition gift = parseGift(id, section, file);
            if (gift != null) {
                loaded.put(id, gift);
            }
        }
    }

    private GiftDefinition parseGift(String id, ConfigurationSection section, File source) {
        double hours = section.getDouble("cooldown-hours", 24.0D);
        if (!Double.isFinite(hours) || hours < 0.0D || hours > 2_562_047_788.0D) {
            plugin.getLogger().warning("礼包 " + id + " 的 cooldown-hours 无效，已跳过 (" + relativePath(source) + ")");
            return null;
        }
        long cooldownMillis = Math.round(hours * 3_600_000.0D);
        String displayName = section.getString("display-name", id);
        String permission = section.getString("permission", "").trim();
        List<RewardDefinition> rewards = parseRewards(id, section.getMapList("rewards"));
        return new GiftDefinition(id, displayName, permission, cooldownMillis, rewards);
    }

    private List<RewardDefinition> parseRewards(String giftId, List<Map<?, ?>> maps) {
        List<RewardDefinition> rewards = new ArrayList<>();
        int index = 0;
        for (Map<?, ?> map : maps) {
            index++;
            Object typeValue = map.get("type");
            String type = typeValue == null ? "" : String.valueOf(typeValue).toLowerCase(Locale.ROOT);
            if (type.equals("command")) {
                Object commandValue = map.get("command");
                String command = commandValue == null ? "" : String.valueOf(commandValue).trim();
                if (!command.isEmpty()) {
                    rewards.add(new RewardDefinition.CommandReward(command));
                    continue;
                }
            } else if (type.equals("item")) {
                Object itemValue = map.get("item");
                String itemId = itemValue == null ? "" : String.valueOf(itemValue).toLowerCase(Locale.ROOT).trim();
                int amount = parseAmount(map.get("amount"));
                if (!itemId.isEmpty() && amount != 0) {
                    rewards.add(new RewardDefinition.ItemReward(itemId, amount));
                    continue;
                }
            }
            plugin.getLogger().warning("礼包 " + giftId + " 的第 " + index + " 个奖励无效，已跳过。");
        }
        return rewards;
    }

    private int parseAmount(Object value) {
        if (value == null) {
            return -1;
        }
        try {
            int amount = Integer.parseInt(String.valueOf(value));
            return amount > 0 && amount <= 1_000_000 ? amount : 0;
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private String relativePath(File file) {
        return plugin.getDataFolder().toPath().relativize(file.toPath()).toString();
    }
}
