package cn.cloudgift.command.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.command.CommandSender;

/** Case-insensitive command lookup and permission-aware top-level completion. */
public final class CommandRouter {

    private final Map<String, CommandModule> modulesByName;
    private final List<CommandModule> modules;

    public CommandRouter(List<CommandModule> modules) {
        this.modules = List.copyOf(modules);
        Map<String, CommandModule> lookup = new LinkedHashMap<>();
        for (CommandModule module : this.modules) {
            for (String name : module.names()) {
                String normalized = normalize(name);
                if (normalized.isBlank()) {
                    throw new IllegalArgumentException("Command name cannot be blank");
                }
                CommandModule previous = lookup.putIfAbsent(normalized, module);
                if (previous != null) {
                    throw new IllegalArgumentException("Duplicate command name: " + name);
                }
            }
        }
        this.modulesByName = Map.copyOf(lookup);
    }

    public Optional<CommandModule> find(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(modulesByName.get(normalize(name)));
    }

    public List<CommandModule> modules() {
        return modules;
    }

    public List<String> completeNames(CommandSender sender, String input) {
        String normalizedInput = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (CommandModule module : modules) {
            if (!module.canUse(sender)) {
                continue;
            }
            for (String name : module.names()) {
                if (name.toLowerCase(Locale.ROOT).startsWith(normalizedInput)) {
                    matches.add(name);
                }
            }
        }
        return matches.stream().distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
