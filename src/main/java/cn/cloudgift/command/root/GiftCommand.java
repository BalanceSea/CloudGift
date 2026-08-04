package cn.cloudgift.command.root;

import cn.cloudgift.command.common.CommandArguments;
import cn.cloudgift.command.common.CommandContext;
import cn.cloudgift.command.common.CommandServices;
import cn.cloudgift.command.modules.ClaimCommand;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Compatibility root for the short player-facing /gift <id> command. */
public final class GiftCommand implements CommandExecutor, TabCompleter {

    private final CommandServices services;
    private final ClaimCommand claim = new ClaimCommand();

    public GiftCommand(CommandServices services) {
        this.services = services;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        return claim.execute(new CommandContext(
                services, sender, label, "gift", CommandArguments.of(args)));
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args) {
        if (!claim.canUse(sender)) {
            return List.of();
        }
        return claim.complete(new CommandContext(
                services, sender, alias, "gift", CommandArguments.of(args)));
    }
}
