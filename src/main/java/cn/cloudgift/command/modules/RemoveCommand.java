package cn.cloudgift.command.modules;

import cn.cloudgift.command.common.CommandContext;
import cn.cloudgift.command.common.CommandModule;
import cn.cloudgift.gift.GiftDefinition;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Removes a player's claim record; reset remains as a compatibility alias. */
public final class RemoveCommand implements CommandModule {

    @Override
    public String name() {
        return "remove";
    }

    @Override
    public List<String> aliases() {
        return List.of("reset");
    }

    @Override
    public String permission() {
        return "cloudgift.command.remove";
    }

    @Override
    public String helpKey() {
        return "help.command.remove";
    }

    @Override
    public boolean execute(CommandContext context) {
        if (!context.arguments().hasExactly(2)) {
            context.send("command.remove.usage");
            return true;
        }
        String playerInput = context.arguments().get(0);
        String giftId = context.arguments().get(1);
        GiftDefinition gift = context.services().gifts().find(giftId).orElse(null);
        if (gift == null) {
            context.send("unknown-gift", Map.of("gift", giftId));
            return true;
        }

        Player online = Bukkit.getPlayerExact(playerInput);
        UUID playerId;
        String playerLabel;
        if (online != null) {
            playerId = online.getUniqueId();
            playerLabel = online.getName();
        } else {
            try {
                playerId = UUID.fromString(playerInput);
                playerLabel = playerInput;
            } catch (IllegalArgumentException exception) {
                context.send("player-not-found", Map.of("player", playerInput));
                return true;
            }
        }
        context.services().claims().reset(context.sender(), playerId, playerLabel, gift);
        return true;
    }

    @Override
    public List<String> complete(CommandContext context) {
        if (context.arguments().size() == 1) {
            String input = context.arguments().get(0).toLowerCase(java.util.Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(java.util.Locale.ROOT).startsWith(input))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }
        if (context.arguments().size() == 2) {
            String input = context.arguments().get(1).toLowerCase(java.util.Locale.ROOT);
            return context.services().gifts().all().stream()
                    .map(GiftDefinition::id)
                    .filter(id -> id.startsWith(input))
                    .sorted()
                    .toList();
        }
        return List.of();
    }
}
