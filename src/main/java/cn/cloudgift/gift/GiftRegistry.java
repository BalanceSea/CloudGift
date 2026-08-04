package cn.cloudgift.gift;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Loads gift definitions from the legacy file and the modular gifts directory. */
public final class GiftRegistry {

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_-]+");
    private static final String GIFT_DIRECTORY = "gifts";
    private static final List<String> LEGACY_FILES = List.of("gifts.yml", "gifts.yaml");
    private static final double MAX_COOLDOWN_HOURS = 2_562_047_788.0D;
    private static final int MAX_CLAIMS = 1_000_000;

    private final File dataFolder;
    private final Logger logger;
    private final Object fileLock = new Object();
    private volatile Map<String, GiftDefinition> gifts = Map.of();
    // Each definition remembers its source so the editor can update the correct YAML file.
    private volatile Map<String, File> sources = Map.of();

    public GiftRegistry(JavaPlugin plugin) {
        this(plugin.getDataFolder(), plugin.getLogger());
    }

    public GiftRegistry(File dataFolder, Logger logger) {
        this.dataFolder = dataFolder;
        this.logger = logger;
    }

    /** Creates the modular gift directory without changing existing files. */
    public void ensureGiftDirectory() throws IOException {
        Files.createDirectories(giftDirectory().toPath());
    }

    /** Reloads all gift files and atomically publishes one immutable snapshot. */
    public int reload() {
        synchronized (fileLock) {
            Map<String, GiftDefinition> loaded = new LinkedHashMap<>();
            Map<String, File> loadedSources = new LinkedHashMap<>();
            for (File file : findGiftFiles()) {
                loadFile(file, loaded, loadedSources);
            }
            gifts = Collections.unmodifiableMap(new LinkedHashMap<>(loaded));
            sources = Collections.unmodifiableMap(new LinkedHashMap<>(loadedSources));
            return loaded.size();
        }
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

    /** Returns true if the id is well-formed for use as a gift key. */
    public boolean isValidId(String id) {
        return id != null && id.length() <= 128 && ID_PATTERN.matcher(id).matches();
    }

    /**
     * Saves an existing gift back to its source file, or creates gifts/<id>.yml for a new gift.
     * The replacement is written through a temporary file to avoid leaving a half-written YAML.
     */
    public void save(GiftDefinition gift) throws IOException {
        if (gift == null || !isValidId(gift.id())) {
            throw new IllegalArgumentException("Invalid gift id");
        }
        synchronized (fileLock) {
            File target = sources.get(gift.id());
            if (target == null) {
                ensureGiftDirectory();
                target = new File(giftDirectory(), gift.id() + ".yml");
            }
            YamlConfiguration yaml = loadForWrite(target);
            writeGift(yaml, gift);
            saveAtomically(yaml, target);
            reload();
        }
    }

    /** Removes a gift from its source file and reloads the registry. */
    public void delete(String id) throws IOException {
        if (!isValidId(id)) {
            return;
        }
        String normalized = id.toLowerCase(Locale.ROOT);
        synchronized (fileLock) {
            File target = sources.get(normalized);
            if (target == null || !target.isFile()) {
                return;
            }
            YamlConfiguration yaml = loadForWrite(target);
            yaml.set("gifts." + normalized, null);
            saveAtomically(yaml, target);
            reload();
        }
    }

    private YamlConfiguration loadForWrite(File file) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        if (!file.isFile()) {
            return yaml;
        }
        try {
            yaml.load(file);
            return yaml;
        } catch (InvalidConfigurationException exception) {
            throw new IOException("礼包文件格式无效: " + relativePath(file), exception);
        }
    }

    private void saveAtomically(YamlConfiguration yaml, File target) throws IOException {
        Path targetPath = target.toPath();
        Path parent = targetPath.getParent();
        if (parent == null) {
            throw new IOException("礼包文件没有父目录: " + target);
        }
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "." + target.getName() + ".", ".tmp");
        boolean moved = false;
        try {
            yaml.save(temporary.toFile());
            try {
                Files.move(temporary, targetPath, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private void writeGift(YamlConfiguration yaml, GiftDefinition gift) {
        String base = "gifts." + gift.id();
        yaml.set(base + ".display-name", gift.displayName());
        yaml.set(base + ".permission", gift.hasPermission() ? gift.permission() : "");
        yaml.set(base + ".cooldown-hours", gift.cooldownMillis() / 3_600_000.0D);
        yaml.set(base + ".max-claims", gift.maxClaims());

        List<Map<String, Object>> rewards = new ArrayList<>();
        for (RewardDefinition reward : gift.rewards()) {
            Map<String, Object> map = new LinkedHashMap<>();
            if (reward instanceof RewardDefinition.CommandReward commandReward) {
                map.put("type", "command");
                map.put("command", commandReward.command());
            } else if (reward instanceof RewardDefinition.ItemReward itemReward) {
                map.put("type", "item");
                map.put("item", itemReward.itemId());
                map.put("amount", itemReward.amount());
            }
            if (!map.isEmpty()) {
                rewards.add(map);
            }
        }
        yaml.set(base + ".rewards", rewards);
    }

    private List<File> findGiftFiles() {
        List<File> files = new ArrayList<>();
        File directory = giftDirectory();
        collectYamlFiles(directory, files);
        files.sort(fileComparator());

        // Root files are a compatibility fallback and deliberately load after gifts/*.yml.
        for (String legacyName : LEGACY_FILES) {
            File legacyFile = new File(dataFolder, legacyName);
            if (legacyFile.isFile()) {
                files.add(legacyFile);
            }
        }
        return files;
    }

    private void collectYamlFiles(File directory, List<File> output) {
        if (!directory.isDirectory()) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory.toPath())) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> isYamlFile(path.getFileName().toString()))
                    .map(Path::toFile)
                    .forEach(output::add);
        } catch (IOException exception) {
            logger.log(Level.WARNING, "扫描礼包目录失败: " + relativePath(directory), exception);
        }
    }

    private Comparator<File> fileComparator() {
        return Comparator.comparing(this::relativePath, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(File::getAbsolutePath, String.CASE_INSENSITIVE_ORDER);
    }

    private boolean isYamlFile(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".yml") || lower.endsWith(".yaml");
    }

    private void loadFile(File file, Map<String, GiftDefinition> loaded, Map<String, File> loadedSources) {
        YamlConfiguration yaml;
        try {
            yaml = new YamlConfiguration();
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            logger.log(Level.WARNING, "礼包文件无法读取，已跳过: " + relativePath(file), exception);
            return;
        }

        ConfigurationSection root = yaml.getConfigurationSection("gifts");
        if (root == null) {
            logger.warning("礼包文件缺少 gifts: 节点，已跳过: " + relativePath(file));
            return;
        }

        for (String rawId : root.getKeys(false)) {
            String id = rawId.toLowerCase(Locale.ROOT);
            if (!isValidId(id)) {
                logger.warning("礼包 ID 只能包含小写字母、数字、下划线和连字符且最长 128 字符，已跳过: "
                        + rawId + " (" + relativePath(file) + ")");
                continue;
            }
            if (loaded.containsKey(id)) {
                logger.warning("发现重复礼包 ID，保留先载入的定义并跳过: " + id
                        + " (" + relativePath(file) + ", 已使用 " + relativePath(loadedSources.get(id)) + ")");
                continue;
            }

            ConfigurationSection section = root.getConfigurationSection(rawId);
            if (section == null) {
                logger.warning("礼包配置不是有效节点，已跳过: " + id + " (" + relativePath(file) + ")");
                continue;
            }
            GiftDefinition gift = parseGift(id, section, file);
            if (gift != null) {
                loaded.put(id, gift);
                loadedSources.put(id, file);
            }
        }
    }

    private GiftDefinition parseGift(String id, ConfigurationSection section, File source) {
        double hours = section.getDouble("cooldown-hours", 24.0D);
        if (!Double.isFinite(hours) || hours < 0.0D || hours > MAX_COOLDOWN_HOURS) {
            logger.warning("礼包 " + id + " 的 cooldown-hours 无效，已跳过 (" + relativePath(source) + ")");
            return null;
        }
        long cooldownMillis = Math.round(hours * 3_600_000.0D);
        String displayName = section.getString("display-name", id);
        if (displayName == null || displayName.isBlank()) {
            displayName = id;
        }
        String permission = section.getString("permission", "");
        permission = permission == null ? "" : permission.trim();
        int maxClaims = Math.max(0, Math.min(MAX_CLAIMS, section.getInt("max-claims", 0)));
        List<RewardDefinition> rewards = parseRewards(id, section.getMapList("rewards"), source);
        return new GiftDefinition(id, displayName, permission, cooldownMillis, maxClaims, rewards);
    }

    private List<RewardDefinition> parseRewards(String giftId, List<Map<?, ?>> maps, File source) {
        List<RewardDefinition> rewards = new ArrayList<>();
        int index = 0;
        for (Map<?, ?> map : maps) {
            index++;
            Object typeValue = map.get("type");
            String type = typeValue == null ? "" : String.valueOf(typeValue).toLowerCase(Locale.ROOT).trim();
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
            logger.warning("礼包 " + giftId + " 的第 " + index + " 个奖励无效，已跳过 ("
                    + relativePath(source) + ")");
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

    private File giftDirectory() {
        return new File(dataFolder, GIFT_DIRECTORY);
    }

    private String relativePath(File file) {
        if (file == null) {
            return "<unknown>";
        }
        Path root = dataFolder.toPath().toAbsolutePath().normalize();
        Path path = file.toPath().toAbsolutePath().normalize();
        try {
            return root.relativize(path).toString().replace(File.separatorChar, '/');
        } catch (IllegalArgumentException exception) {
            return file.getPath();
        }
    }
}
