package cn.cloudgift.command.modules;

import cn.cloudgift.command.common.CommandContext;
import cn.cloudgift.command.common.CommandModule;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Saves the item in a player's main hand for later item rewards. */
public final class AddCommand implements CommandModule {

    @Override
    public String name() {
        return "add";
    }

    @Override
    public List<String> aliases() {
        return List.of("saveitem");
    }

    @Override
    public String permission() {
        return "cloudgift.command.add";
    }

    @Override
    public String helpKey() {
        return "help.command.add";
    }

    @Override
    public boolean execute(CommandContext context) {
        if (!(context.sender() instanceof Player player)) {
            context.send("players-only");
            return true;
        }
        if (!context.arguments().hasExactly(1)) {
            context.send("command.add.usage");
            return true;
        }
        String itemId = context.arguments().get(0).toLowerCase(Locale.ROOT);
        if (!context.services().items().isValidId(itemId)) {
            context.send("command.add.usage");
            return true;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR || hand.getAmount() <= 0) {
            context.send("empty-hand");
            return true;
        }
        try {
            context.services().items().save(itemId, hand);
            context.send("item-saved", Map.of("item", itemId));
        } catch (IOException | IllegalArgumentException exception) {
            context.services().plugin().getLogger().log(Level.SEVERE, "保存物品失败", exception);
            context.send("command.add.failed");
        }
        return true;
    }
}
