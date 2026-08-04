package cn.cloudgift.command.modules;

import cn.cloudgift.command.common.CommandContext;
import cn.cloudgift.command.common.CommandModule;
import cn.cloudgift.gift.GiftDefinition;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class ListCommand implements CommandModule {

    @Override
    public String name() {
        return "list";
    }

    @Override
    public String permission() {
        return "cloudgift.command.list";
    }

    @Override
    public String helpKey() {
        return "help.command.list";
    }

    @Override
    public boolean execute(CommandContext context) {
        if (!context.arguments().isEmpty()) {
            context.send("command.list.usage");
            return true;
        }
        List<GiftDefinition> all = context.services().gifts().all();
        context.send("gift-list-header", Map.of("count", Integer.toString(all.size())));
        for (GiftDefinition gift : all) {
            context.send("gift-list-entry",
                    Placeholder.unparsed("id", gift.id()),
                    Placeholder.component("display_name", context.services().messages().parse(gift.displayName())));
        }
        return true;
    }
}
