package cn.cloudgift.command.common;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.CommandSender;

/** Contract implemented by one independently routed subcommand. */
public interface CommandModule {

    String name();

    default List<String> aliases() {
        return List.of();
    }

    /** Empty permission means the module is visible to every sender. */
    default String permission() {
        return "";
    }

    String helpKey();

    boolean execute(CommandContext context);

    default List<String> complete(CommandContext context) {
        return List.of();
    }

    default boolean canUse(CommandSender sender) {
        String required = permission();
        return required == null || required.isBlank() || sender.hasPermission(required);
    }

    default List<String> names() {
        List<String> names = new ArrayList<>(1 + aliases().size());
        names.add(name());
        names.addAll(aliases());
        return List.copyOf(names);
    }
}
