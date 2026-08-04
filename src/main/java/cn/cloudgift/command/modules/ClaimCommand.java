package cn.cloudgift.command.modules;

import cn.cloudgift.command.common.CommandContext;
import cn.cloudgift.command.common.CommandModule;
import cn.cloudgift.gift.GiftDefinition;
import cn.cloudgift.gift.GiftRegistry;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.bukkit.entity.Player;

/** Handles both /gift <id> and /cloudgift claim <id>. */
public final class ClaimCommand implements CommandModule {

    @Override
    public String name() {
        return "claim";
    }

    @Override
    public String permission() {
        return "cloudgift.command.gift";
    }

    @Override
    public String helpKey() {
        return "help.command.claim";
    }

    @Override
    public boolean execute(CommandContext context) {
        if (!(context.sender() instanceof Player player)) {
            context.send("players-only");
            return true;
        }
        String requestedId = context.arguments().isEmpty() ? "" : context.arguments().get(0);
        if (!context.sender().hasPermission(permission())) {
            context.send("no-permission", Map.of("gift", requestedId));
            return true;
        }
        if (!context.arguments().hasExactly(1)) {
            context.send(context.isRoot("gift") ? "usage-gift" : "command.claim.usage");
            return true;
        }
        context.services().claims().claim(player, requestedId);
        return true;
    }

    @Override
    public List<String> complete(CommandContext context) {
        if (!context.arguments().hasExactly(1)) {
            return List.of();
        }
        return matchingGiftIds(context.services().gifts(), context.sender(), context.arguments().get(0));
    }

    private List<String> matchingGiftIds(GiftRegistry gifts, org.bukkit.command.CommandSender sender, String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        Stream<String> candidates = gifts.all().stream()
                .filter(gift -> !gift.hasPermission() || sender.hasPermission(gift.permission()))
                .map(GiftDefinition::id);
        return candidates.filter(value -> value.startsWith(normalized)).sorted().toList();
    }
}
