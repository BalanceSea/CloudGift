package cn.cloudgift.gui;

import cn.cloudgift.gift.GiftDefinition;
import cn.cloudgift.gift.GiftRegistry;
import cn.cloudgift.gift.RewardDefinition;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

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
        if (menu.type() == GiftMenuHolder.Type.REWARDS && handleDirectItemInput(player, event)) {
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

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof GiftMenuHolder menu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || menu.type() != GiftMenuHolder.Type.REWARDS
                || !menu.owner().getUniqueId().equals(player.getUniqueId())
                || event.getRawSlots().size() != 1) {
            return;
        }

        GiftDraft draft = gui.draft(player.getUniqueId());
        if (draft == null) {
            return;
        }
        int rawSlot = event.getRawSlots().iterator().next();
        if (!isEmptyRewardSlot(rawSlot, draft.rewards().size())) {
            return;
        }
        ItemStack input = event.getNewItems().get(rawSlot);
        if (GiftEditorGui.isUsableItem(input)) {
            gui.addDirectItemNextTick(player, input);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        gui.clearDraft(event.getPlayer().getUniqueId());
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
        if (slot >= 0 && slot < GiftEditorGui.MAX_REWARDS && event.isShiftClick() && event.isLeftClick()) {
            List<RewardDefinition> rewards = draft.rewards();
            if (slot < rewards.size()) {
                gui.removeReward(player, slot);
            }
        }
    }

    private boolean handleDirectItemInput(Player player, InventoryClickEvent event) {
        GiftDraft draft = gui.draft(player.getUniqueId());
        if (draft == null) {
            return false;
        }
        if (event.isShiftClick() && event.getClickedInventory() == player.getInventory()) {
            ItemStack input = event.getCurrentItem();
            if (GiftEditorGui.isUsableItem(input)) {
                gui.addDirectItem(player, input);
                return true;
            }
            return false;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot != GiftEditorGui.SLOT_ADD_ITEM
                && !isEmptyRewardSlot(rawSlot, draft.rewards().size())) {
            return false;
        }
        ItemStack input = directInputFromClick(player, event);
        if (!GiftEditorGui.isUsableItem(input)) {
            return false;
        }
        gui.addDirectItem(player, input);
        return true;
    }

    private ItemStack directInputFromClick(Player player, InventoryClickEvent event) {
        if (event.getClick() == ClickType.NUMBER_KEY) {
            int hotbarButton = event.getHotbarButton();
            return hotbarButton >= 0 ? player.getInventory().getItem(hotbarButton) : null;
        }
        if (event.getClick() == ClickType.SWAP_OFFHAND) {
            return player.getInventory().getItemInOffHand();
        }
        if (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT) {
            return event.getCursor();
        }
        return null;
    }

    private boolean isEmptyRewardSlot(int rawSlot, int rewardCount) {
        return rawSlot >= rewardCount && rawSlot >= 0 && rawSlot < GiftEditorGui.MAX_REWARDS;
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
