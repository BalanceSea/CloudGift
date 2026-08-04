package cn.cloudgift.config;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class PluginSettings {

    private final JavaPlugin plugin;
    private volatile DateTimeFormatter formatter;
    private volatile ZoneId zoneId;
    private volatile String availableTime;
    private volatile String noPermissionTime;
    private volatile String unknownGift;
    private volatile String loadingTime;
    private volatile String limitReachedTime;
    private volatile boolean preloadOnJoin;

    public PluginSettings(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration config = plugin.getConfig();
        String pattern = config.getString("time.pattern", "yyyy-MM-dd HH:mm:ss");
        String configuredZone = config.getString("time.zone-id", "Asia/Shanghai");
        try {
            zoneId = ZoneId.of(configuredZone);
        } catch (DateTimeException exception) {
            plugin.getLogger().warning("无效的 time.zone-id: " + configuredZone + "，已使用 Asia/Shanghai。");
            zoneId = ZoneId.of("Asia/Shanghai");
        }
        try {
            formatter = DateTimeFormatter.ofPattern(pattern).withZone(zoneId);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("无效的 DateTimeFormatter 格式: " + pattern + "，已使用 yyyy-MM-dd HH:mm:ss。");
            formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(zoneId);
        }
        availableTime = config.getString("placeholder.available-time", "可领取");
        noPermissionTime = config.getString("placeholder.no-permission-time", "无权限");
        unknownGift = config.getString("placeholder.unknown-gift", "未知礼包");
        loadingTime = config.getString("placeholder.loading-time", "数据加载中");
        limitReachedTime = config.getString("placeholder.limit-reached-time", "次数已用尽");
        preloadOnJoin = config.getBoolean("data.preload-on-join", true);
    }

    public String format(long epochMillis) {
        try {
            return formatter.format(Instant.ofEpochMilli(epochMillis));
        } catch (DateTimeException | ArithmeticException exception) {
            return "-";
        }
    }

    public String availableTime() {
        return availableTime;
    }

    public String noPermissionTime() {
        return noPermissionTime;
    }

    public String unknownGift() {
        return unknownGift;
    }

    public String loadingTime() {
        return loadingTime;
    }

    public String limitReachedTime() {
        return limitReachedTime;
    }

    public boolean preloadOnJoin() {
        return preloadOnJoin;
    }
}
