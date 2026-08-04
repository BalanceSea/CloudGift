package cn.cloudgift.command;

import cn.cloudgift.CloudGiftPlugin;
import cn.cloudgift.gift.GiftDefinition;
import cn.cloudgift.gift.GiftRegistry;
import cn.cloudgift.gift.ItemStore;
import cn.cloudgift.gui.GiftEditorGui;
import cn.cloudgift.message.MessageService;
import cn.cloudgift.service.ClaimService;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CloudGiftCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ADMIN_SUBCOMMANDS =
            List.of("reload", "saveitem", "list", "claim", "reset", "gui");

    private final CloudGiftPlugin plugin;
    private final GiftRegistry gifts;
    private final ItemStore items;
    private final ClaimService claims;
    private final MessageService messages;
    private final GiftEditorGui editorGui;

    public CloudGiftCommand(
            CloudGiftPlugin plugin,
            GiftRegistry gifts,
            ItemStore items,
            ClaimService claims,
            MessageService messages,
            GiftEditorGui editorGui) {
        this.plugin = plugin;
        this.gifts = gifts;
        this.items = items;
        this.claims = claims;
        this.messages = messages;
        this.editorGui = editorGui;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("gift")) {
            return handleGift(sender, args);
        }
        return handleAdmin(sender, args);
    }

    private boolean handleGift(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return true;
        }
        if (!sender.hasPermission("cloudgift.command.gift")) {
            messages.send(sender, "no-permission", Map.of("gift", args.length == 0 ? "" : args[0]));
            return true;
        }
        if (args.length != 1) {
            messages.send(sender, "usage-gift");
            return true;
        }
        claims.claim(player, args[0]);
        return true;
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("claim")) {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "players-only");
            } else if (!sender.hasPermission("cloudgift.command.gift")) {
                messages.send(sender, "no-permission", Map.of("gift", args.length < 2 ? "" : args[1]));
            } else if (args.length != 2) {
                messages.send(sender, "usage-gift");
            } else {
                claims.claim(player, args[1]);
            }
            return true;
        }
        if (!sender.hasPermission("cloudgift.admin")) {
            messages.send(sender, "no-permission", Map.of("gift", "admin"));
            return true;
        }
        if (args.length == 0) {
            messages.send(sender, "usage-admin");
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(sender);
            case "saveitem" -> saveItem(sender, args);
            case "list" -> list(sender);
            case "reset" -> reset(sender, args);
            case "gui", "editor" -> openGui(sender);
            default -> {
                messages.send(sender, "usage-admin");
                yield true;
            }
        };
    }

    private boolean reload(CommandSender sender) {
        try {
            int count = plugin.reloadCloudGift();
            messages.send(sender, "reload-success", Map.of("count", Integer.toString(count)));
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("重载 CloudGift 失败: " + exception.getMessage());
            messages.send(sender, "database-error");
        }
        return true;
    }

    private boolean saveItem(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return true;
        }
        if (args.length != 2 || !items.isValidId(args[1])) {
            messages.send(sender, "usage-saveitem");
            return true;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR || hand.getAmount() <= 0) {
            messages.send(sender, "empty-hand");
            return true;
        }
        try {
            items.save(args[1], hand);
            messages.send(sender, "item-saved", Map.of("item", args[1].toLowerCase(Locale.ROOT)));
        } catch (IOException | IllegalArgumentException exception) {
            plugin.getLogger().severe("保存物品失败: " + exception.getMessage());
            messages.send(sender, "database-error");
        }
        return true;
    }

    private boolean list(CommandSender sender) {
        List<GiftDefinition> all = gifts.all();
        messages.send(sender, "gift-list-header", Map.of("count", Integer.toString(all.size())));
        for (GiftDefinition gift : all) {
            messages.send(sender, "gift-list-entry",
                    Placeholder.unparsed("id", gift.id()),
                    Placeholder.component("display_name", messages.parse(gift.displayName())));
        }
        return true;
    }

    private boolean reset(CommandSender sender, String[] args) {
        if (args.length != 3) {
            messages.send(sender, "usage-reset");
            return true;
        }
        GiftDefinition gift = gifts.find(args[2]).orElse(null);
        if (gift == null) {
            messages.send(sender, "unknown-gift", Map.of("gift", args[2]));
            return true;
        }

        Player online = Bukkit.getPlayerExact(args[1]);
        UUID playerId;
        String playerLabel;
        if (online != null) {
            playerId = online.getUniqueId();
            playerLabel = online.getName();
        } else {
            try {
                playerId = UUID.fromString(args[1]);
                playerLabel = args[1];
            } catch (IllegalArgumentException exception) {
                messages.send(sender, "player-not-found", Map.of("player", args[1]));
                return true;
            }
        }
        claims.reset(sender, playerId, playerLabel, gift);
        return true;
    }

    private boolean openGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return true;
        }
        editorGui.openList(player);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("gift")) {
            return args.length == 1 ? matchingGiftIds(sender, args[0]) : List.of();
        }
        if (args.length == 1) {
            Stream<String> commands = sender.hasPermission("cloudgift.admin")
                    ? ADMIN_SUBCOMMANDS.stream()
                    : Stream.of("claim");
            return filter(commands, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("claim")) {
            return matchingGiftIds(sender, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("reset") && sender.hasPermission("cloudgift.admin")) {
            return filter(gifts.all().stream().map(GiftDefinition::id), args[2]);
        }
        return List.of();
    }

    private List<String> matchingGiftIds(CommandSender sender, String input) {
        return filter(gifts.all().stream()
                .filter(gift -> !gift.hasPermission() || sender.hasPermission(gift.permission()))
                .map(GiftDefinition::id), input);
    }

    private List<String> filter(Stream<String> candidates, String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        return candidates.filter(value -> value.startsWith(normalized)).sorted().toList();
    }
}
