package cn.cloudgift.gui;

import cn.cloudgift.gift.GiftDefinition;
import cn.cloudgift.gift.GiftRegistry;
import cn.cloudgift.gift.RewardDefinition;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

/** Routes clicks inside the CloudGift editor menus to the appropriate GUI actions. */
public final class GiftMenuListener implements Listener {

    private final GiftEditorGui gui;
    private final GiftRegistry gifts;

    public GiftMenuListener(GiftEditorGui gui, GiftRegistry gifts) {
        this.gui = gui;
        this.gifts = gifts;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof GiftMenuHolder menu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder() instanceof GiftMenuHolder)) {
            return;
        }
        int slot = event.getRawSlot();
        switch (menu.type()) {
            case LIST -> handleList(player, slot);
            case EDIT -> handleEdit(player, slot, event);
            case REWARDS -> handleRewards(player, slot, event);
        }
    }

    private void handleList(Player player, int slot) {
        if (slot == 53) {
            player.closeInventory();
            return;
        }
        if (slot == 49) {
            gui.promptNewGift(player);
            return;
        }
        if (slot < 0 || slot >= 45) {
            return;
        }
        List<GiftDefinition> all = gifts.all();
        if (slot < all.size()) {
            gui.openEditFor(player, all.get(slot));
        }
    }

    private void handleEdit(Player player, int slot, InventoryClickEvent event) {
        GiftDraft draft = gui.draft(player.getUniqueId());
        if (draft == null) {
            gui.openList(player);
            return;
        }
        switch (slot) {
            case GiftEditorGui.SLOT_NAME -> gui.promptDisplayName(player);
            case GiftEditorGui.SLOT_PERMISSION -> gui.promptPermission(player);
            case GiftEditorGui.SLOT_COOLDOWN -> {
                draft.addCooldownHours(cooldownDelta(event));
                gui.openEdit(player);
            }
            case GiftEditorGui.SLOT_MAXCLAIMS -> {
                draft.addMaxClaims(maxClaimsDelta(event));
                gui.openEdit(player);
            }
            case GiftEditorGui.SLOT_REWARDS -> gui.openRewards(player);
            case GiftEditorGui.SLOT_BACK -> {
                gui.clearDraft(player.getUniqueId());
                gui.openList(player);
            }
            case GiftEditorGui.SLOT_SAVE -> gui.save(player);
            case GiftEditorGui.SLOT_DELETE -> {
                if (draft.existing() && event.isShiftClick() && event.isLeftClick()) {
                    gui.delete(player);
                }
            }
            default -> { }
        }
    }

    private void handleRewards(Player player, int slot, InventoryClickEvent event) {
        GiftDraft draft = gui.draft(player.getUniqueId());
        if (draft == null) {
            gui.openList(player);
            return;
        }
        if (slot == GiftEditorGui.SLOT_ADD_COMMAND) {
            gui.promptAddCommand(player);
            return;
        }
        if (slot == GiftEditorGui.SLOT_ADD_ITEM) {
            gui.promptAddItem(player);
            return;
        }
        if (slot == GiftEditorGui.SLOT_REWARDS_BACK) {
            gui.openEdit(player);
            return;
        }
        // Remove a reward with shift + left click.
        if (slot >= 0 && slot < 45 && event.isShiftClick() && event.isLeftClick()) {
            List<RewardDefinition> rewards = draft.rewards();
            if (slot < rewards.size()) {
                rewards.remove(slot);
                gui.openRewards(player);
            }
        }
    }

    private double cooldownDelta(InventoryClickEvent event) {
        double step = event.isShiftClick() ? 24.0D : 1.0D;
        return event.isRightClick() ? -step : step;
    }

    private int maxClaimsDelta(InventoryClickEvent event) {
        int step = event.isShiftClick() ? 10 : 1;
        return event.isRightClick() ? -step : step;
    }
}
