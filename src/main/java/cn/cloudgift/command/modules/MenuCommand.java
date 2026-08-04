package cn.cloudgift.command.modules;

import cn.cloudgift.command.common.CommandContext;
import cn.cloudgift.command.common.CommandModule;
import org.bukkit.entity.Player;

public final class MenuCommand implements CommandModule {

    @Override
    public String name() {
        return "menu";
    }

    @Override
    public java.util.List<String> aliases() {
        return java.util.List.of("gui", "editor");
    }

    @Override
    public String permission() {
        return "cloudgift.command.menu";
    }

    @Override
    public String helpKey() {
        return "help.command.menu";
    }

    @Override
    public boolean execute(CommandContext context) {
        if (!context.arguments().isEmpty()) {
            context.send("command.menu.usage");
            return true;
        }
        if (!(context.sender() instanceof Player player)) {
            context.send("players-only");
            return true;
        }
        context.services().editorGui().openList(player);
        return true;
    }
}
