package cn.cloudgift.gui;

import cn.cloudgift.gift.GiftDefinition;
import cn.cloudgift.gift.GiftRegistry;
import cn.cloudgift.gift.RewardDefinition;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
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
        if (!menu.owner().getUniqueId().equals(player.getUniqueId())) {
            return;
        }
        if (menu.type() == GiftMenuHolder.Type.ITEM_INPUT) {
            handleItemInputClick(player, event);
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
            case ITEM_INPUT -> { }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof GiftMenuHolder menu)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)
                || !menu.owner().getUniqueId().equals(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (menu.type() != GiftMenuHolder.Type.ITEM_INPUT) {
            event.setCancelled(true);
            return;
        }

        int topSize = event.getView().getTopInventory().getSize();
        boolean touchesControlSlot = event.getRawSlots().stream()
                .anyMatch(slot -> slot < topSize && !GiftEditorGui.isItemInputStorageSlot(slot));
        event.setCancelled(touchesControlSlot);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        if (inventory.getHolder() instanceof GiftMenuHolder menu
                && menu.type() == GiftMenuHolder.Type.ITEM_INPUT
                && event.getPlayer() instanceof Player player) {
            gui.closeItemInput(player, inventory);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        gui.closeActiveItemInput(player);
        gui.clearDraft(player.getUniqueId());
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
            case GiftEditorGui.SLOT_RESET_AT_MIDNIGHT -> {
                draft.toggleResetAtMidnight();
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
            if (event.isLeftClick()) {
                gui.openItemInput(player);
            } else if (event.isRightClick()) {
                gui.promptAddItem(player);
            }
            return;
        }
        if (slot == GiftEditorGui.SLOT_REWARDS_BACK) {
            gui.openEdit(player);
            return;
        }
        // Remove a reward with shift + left click.
        if (slot >= 0 && slot < GiftEditorGui.MAX_REWARDS && event.isShiftClick() && event.isLeftClick()) {
            List<RewardDefinition> rewards = draft.rewards();
            if (slot < rewards.size()) {
                gui.removeReward(player, slot);
            }
        }
    }

    private void handleItemInputClick(Player player, InventoryClickEvent event) {
        event.setCancelled(true);
        int rawSlot = event.getRawSlot();
        if (rawSlot == GiftEditorGui.SLOT_ITEM_INPUT_SAVE) {
            gui.saveItemInput(player, event.getView().getTopInventory());
            return;
        }
        if (rawSlot == GiftEditorGui.SLOT_ITEM_INPUT_CANCEL) {
            gui.cancelItemInput(player, event.getView().getTopInventory());
            return;
        }
        if (GiftEditorGui.isItemInputStorageSlot(rawSlot)) {
            event.setCancelled(!isAllowedStorageAction(event.getAction()));
            return;
        }

        int topSize = event.getView().getTopInventory().getSize();
        if (rawSlot >= topSize && event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            event.setCurrentItem(gui.moveIntoItemInput(
                    event.getView().getTopInventory(), event.getCurrentItem()));
            return;
        }
        if (rawSlot >= topSize
                && event.getAction() != InventoryAction.COLLECT_TO_CURSOR
                && event.getAction() != InventoryAction.UNKNOWN) {
            event.setCancelled(false);
        }
    }

    private boolean isAllowedStorageAction(InventoryAction action) {
        return switch (action) {
            case PICKUP_ALL, PICKUP_SOME, PICKUP_HALF, PICKUP_ONE,
                    PLACE_ALL, PLACE_SOME, PLACE_ONE, SWAP_WITH_CURSOR,
                    MOVE_TO_OTHER_INVENTORY, HOTBAR_SWAP, HOTBAR_MOVE_AND_READD,
                    DROP_ALL_SLOT, DROP_ONE_SLOT -> true;
            default -> false;
        };
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
