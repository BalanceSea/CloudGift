package cn.cloudgift.message;

import java.io.File;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class MessageService {

    private final JavaPlugin plugin;
    private final File file;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private volatile YamlConfiguration messages;
    private volatile Component prefix = Component.empty();

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "messages.yml");
        reload();
    }

    public void reload() {
        messages = YamlConfiguration.loadConfiguration(file);
        prefix = miniMessage.deserialize(messages.getString("prefix", ""));
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public void send(CommandSender sender, String key, Map<String, String> values) {
        TagResolver[] resolvers = values.entrySet().stream()
                .map(entry -> Placeholder.unparsed(entry.getKey(), entry.getValue()))
                .toArray(TagResolver[]::new);
        send(sender, key, resolvers);
    }

    public void send(CommandSender sender, String key, TagResolver... resolvers) {
        String template = messages.getString(key, "<red>Missing message: " + key);
        Component body = miniMessage.deserialize(template, resolvers);
        sender.sendMessage(prefix.append(body));
    }

    public Component parse(String miniMessageText) {
        return miniMessage.deserialize(miniMessageText);
    }
}
