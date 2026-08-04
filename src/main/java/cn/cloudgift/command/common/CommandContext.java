package cn.cloudgift.command.common;

import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;

/** Execution context passed to one command module. */
public final class CommandContext {

    private final CommandServices services;
    private final CommandSender sender;
    private final String label;
    private final String rootCommand;
    private final CommandArguments arguments;

    public CommandContext(
            CommandServices services,
            CommandSender sender,
            String label,
            String rootCommand,
            CommandArguments arguments) {
        this.services = Objects.requireNonNull(services, "services");
        this.sender = Objects.requireNonNull(sender, "sender");
        this.label = Objects.requireNonNull(label, "label");
        this.rootCommand = Objects.requireNonNull(rootCommand, "rootCommand");
        this.arguments = Objects.requireNonNull(arguments, "arguments");
    }

    public CommandServices services() {
        return services;
    }

    public CommandSender sender() {
        return sender;
    }

    public String label() {
        return label;
    }

    public String rootCommand() {
        return rootCommand;
    }

    public CommandArguments arguments() {
        return arguments;
    }

    public boolean isRoot(String expected) {
        return rootCommand.equalsIgnoreCase(expected);
    }

    public CommandContext withArguments(CommandArguments replacement) {
        return new CommandContext(services, sender, label, rootCommand, replacement);
    }

    public void send(String key) {
        services.messages().send(sender, key);
    }

    public void send(String key, Map<String, String> values) {
        services.messages().send(sender, key, values);
    }

    public void send(String key, TagResolver... resolvers) {
        services.messages().send(sender, key, resolvers);
    }
}
