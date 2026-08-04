package cn.cloudgift.command.modules;

import cn.cloudgift.command.common.CommandContext;
import cn.cloudgift.command.common.CommandModule;
import java.util.List;
import java.util.function.Supplier;

/** Displays only the commands available to the current sender. */
public final class HelpCommand implements CommandModule {

    private final Supplier<List<CommandModule>> modules;

    public HelpCommand(Supplier<List<CommandModule>> modules) {
        this.modules = modules;
    }

    @Override
    public String name() {
        return "help";
    }

    @Override
    public String helpKey() {
        return "help.command.help";
    }

    @Override
    public boolean execute(CommandContext context) {
        if (!context.arguments().isEmpty()) {
            context.send("command.help.usage");
            return true;
        }
        context.send("help.header");
        for (CommandModule module : modules.get()) {
            if (module.canUse(context.sender())) {
                context.send(module.helpKey());
            }
        }
        return true;
    }
}
