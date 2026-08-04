package cn.cloudgift.command.modules;

import cn.cloudgift.command.common.CommandContext;
import cn.cloudgift.command.common.CommandModule;
import java.util.Map;
import java.util.logging.Level;

public final class ReloadCommand implements CommandModule {

    @Override
    public String name() {
        return "reload";
    }

    @Override
    public String permission() {
        return "cloudgift.command.reload";
    }

    @Override
    public String helpKey() {
        return "help.command.reload";
    }

    @Override
    public boolean execute(CommandContext context) {
        if (!context.arguments().isEmpty()) {
            context.send("command.reload.usage");
            return true;
        }
        try {
            int count = context.services().plugin().reloadCloudGift();
            context.send("reload-success", Map.of("count", Integer.toString(count)));
        } catch (RuntimeException exception) {
            context.services().plugin().getLogger().log(Level.SEVERE, "重载 CloudGift 失败", exception);
            context.send("command.reload.failed");
        }
        return true;
    }
}
