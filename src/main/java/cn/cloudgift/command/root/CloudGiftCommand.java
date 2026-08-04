package cn.cloudgift.command.root;

import cn.cloudgift.command.common.CommandArguments;
import cn.cloudgift.command.common.CommandContext;
import cn.cloudgift.command.common.CommandModule;
import cn.cloudgift.command.common.CommandRouter;
import cn.cloudgift.command.common.CommandServices;
import cn.cloudgift.command.modules.AddCommand;
import cn.cloudgift.command.modules.ClaimCommand;
import cn.cloudgift.command.modules.HelpCommand;
import cn.cloudgift.command.modules.ListCommand;
import cn.cloudgift.command.modules.MenuCommand;
import cn.cloudgift.command.modules.ReloadCommand;
import cn.cloudgift.command.modules.RemoveCommand;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Root router for /cloudgift. Business logic lives in independent modules. */
public final class CloudGiftCommand implements CommandExecutor, TabCompleter {

    private final CommandServices services;
    private final CommandRouter router;
    private final HelpCommand help;

    public CloudGiftCommand(CommandServices services) {
        this.services = services;
        List<CommandModule> modules = new ArrayList<>();
        this.help = new HelpCommand(() -> modules);
        modules.add(help);
        modules.add(new ReloadCommand());
        modules.add(new MenuCommand());
        modules.add(new AddCommand());
        modules.add(new RemoveCommand());
        modules.add(new ClaimCommand());
        modules.add(new ListCommand());
        this.router = new CommandRouter(modules);
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        CommandContext context = new CommandContext(
                services, sender, label, "cloudgift", CommandArguments.of(args));
        if (context.arguments().isEmpty()) {
            return help.execute(context);
        }

        CommandModule module = router.find(context.arguments().get(0)).orElse(null);
        if (module == null) {
            context.send("command.unknown", Map.of("command", context.arguments().get(0)));
            return help.execute(context.withArguments(CommandArguments.empty()));
        }
        if (!module.canUse(sender)) {
            context.send("command.no-permission", Map.of(
                    "command", module.name(), "permission", module.permission()));
            return true;
        }
        return module.execute(context.withArguments(context.arguments().withoutFirst()));
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args) {
        CommandArguments arguments = CommandArguments.of(args);
        CommandContext context = new CommandContext(services, sender, alias, "cloudgift", arguments);
        if (arguments.size() <= 1) {
            String input = arguments.isEmpty() ? "" : arguments.get(0);
            return router.completeNames(sender, input);
        }

        Optional<CommandModule> found = router.find(arguments.get(0));
        if (found.isEmpty() || !found.get().canUse(sender)) {
            return List.of();
        }
        return found.get().complete(context.withArguments(arguments.withoutFirst()));
    }
}
