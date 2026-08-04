package cn.cloudgift.gui;

import cn.cloudgift.gift.GiftDefinition;
import cn.cloudgift.gift.GiftRegistry;
import cn.cloudgift.gift.ItemStore;
import cn.cloudgift.gift.RewardDefinition;
import cn.cloudgift.message.MessageService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

/** Builds and manages the in-game gift editor menus. */
public final class GiftEditorGui {

    static final int MAX_REWARDS = 45;
    static final int ITEM_INPUT_STORAGE_SIZE = 45;
    static final int ITEM_INPUT_INVENTORY_SIZE = 54;
    static final int SLOT_ITEM_INPUT_SAVE = 49;
    static final int SLOT_ITEM_INPUT_CANCEL = 53;

    private final JavaPlugin plugin;
    private final GiftRegistry gifts;
    private final ItemStore items;
    private final MessageService messages;
    private final ChatInputService chatInput;
    // Active editing draft per player, shared across the edit and rewards menus.
    private final Map<UUID, GiftDraft> drafts = new ConcurrentHashMap<>();
    private final Map<UUID, Inventory> itemInputInventories = new ConcurrentHashMap<>();

    public GiftEditorGui(
            JavaPlugin plugin,
            GiftRegistry gifts,
            ItemStore items,
            MessageService messages,
            ChatInputService chatInput) {
        this.plugin = plugin;
        this.gifts = gifts;
        this.items = items;
        this.messages = messages;
        this.chatInput = chatInput;
    }

    // === List menu ===

    public void openList(Player player) {
        closeActiveItemInput(player);
        clearDraft(player.getUniqueId());
        GiftMenuHolder holder = new GiftMenuHolder(GiftMenuHolder.Type.LIST, player);
        Inventory inventory = Bukkit.createInventory(holder, 54, Component.text("礼包编辑器"));
        holder.setInventory(inventory);

        List<GiftDefinition> all = gifts.all();
        for (int i = 0; i < all.size() && i < 45; i++) {
            inventory.setItem(i, listIcon(all.get(i)));
        }
        inventory.setItem(49, button(Material.EMERALD_BLOCK, "<green>新建礼包",
                "<gray>点击后在聊天栏输入新礼包 ID"));
        inventory.setItem(53, button(Material.BARRIER, "<red>关闭", List.of()));
        player.openInventory(inventory);
    }

    private ItemStack listIcon(GiftDefinition gift) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>ID: <white>" + gift.id());
        lore.add("<gray>冷却: <white>" + trim(gift.cooldownMillis() / 3_600_000.0D) + " 小时");
        lore.add("<gray>次数上限: <white>" + (gift.hasClaimLimit() ? gift.maxClaims() : "无限"));
        lore.add("<gray>权限: <white>" + (gift.hasPermission() ? gift.permission() : "无"));
        lore.add("<gray>奖励数: <white>" + gift.rewards().size());
        lore.add("");
        lore.add("<yellow>点击编辑");
        return button(Material.CHEST, gift.displayName(), lore);
    }

    // === Icon / item helpers ===

    private ItemStack button(Material material, String nameMiniMessage, String loreLine) {
        List<String> lore = loreLine == null ? List.of() : List.of(loreLine);
        return button(material, nameMiniMessage, lore);
    }

    private ItemStack button(Material material, String nameMiniMessage, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(clean(messages.parse(nameMiniMessage)));
            if (!loreLines.isEmpty()) {
                List<Component> lore = new ArrayList<>();
                for (String line : loreLines) {
                    lore.add(clean(messages.parse(line)));
                }
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Removes the default italic styling Minecraft applies to custom item names. */
    private Component clean(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    private String trim(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    // === Edit menu ===

    static final int SLOT_NAME = 10;
    static final int SLOT_PERMISSION = 12;
    static final int SLOT_COOLDOWN = 14;
    static final int SLOT_MAXCLAIMS = 16;
    static final int SLOT_REWARDS = 22;
    static final int SLOT_BACK = 48;
    static final int SLOT_SAVE = 49;
    static final int SLOT_DELETE = 50;

    public void openEditFor(Player player, GiftDefinition gift) {
        replaceDraft(player.getUniqueId(), GiftDraft.of(gift));
        openEdit(player);
    }

    public void beginNew(Player player, String id) {
        replaceDraft(player.getUniqueId(), GiftDraft.fresh(id));
        openEdit(player);
    }

    public GiftDraft draft(UUID playerId) {
        return drafts.get(playerId);
    }

    public void clearDraft(UUID playerId) {
        GiftDraft removed = drafts.remove(playerId);
        cleanupTemporaryItems(removed);
    }

    public void openEdit(Player player) {
        GiftDraft draft = drafts.get(player.getUniqueId());
        if (draft == null) {
            openList(player);
            return;
        }
        GiftMenuHolder holder = new GiftMenuHolder(GiftMenuHolder.Type.EDIT, player);
        Inventory inventory = Bukkit.createInventory(holder, 54, Component.text("编辑礼包: " + draft.id()));
        holder.setInventory(inventory);

        inventory.setItem(SLOT_NAME, button(Material.NAME_TAG, "<aqua>显示名称",
                List.of("<gray>当前: <white>" + draft.displayName(), "", "<yellow>点击后在聊天栏输入")));
        inventory.setItem(SLOT_PERMISSION, button(Material.PAPER, "<aqua>权限节点",
                List.of("<gray>当前: <white>" + (draft.permission().isBlank() ? "无" : draft.permission()),
                        "", "<yellow>点击后在聊天栏输入", "<dark_gray>输入 none 可清空")));
        inventory.setItem(SLOT_COOLDOWN, button(Material.CLOCK, "<aqua>冷却时间（小时）",
                List.of("<gray>当前: <white>" + trim(draft.cooldownHours()) + " 小时",
                        "", "<yellow>左键 +1 / 右键 -1", "<yellow>Shift 左键 +24 / Shift 右键 -24")));
        inventory.setItem(SLOT_MAXCLAIMS, button(Material.REPEATER, "<aqua>使用次数上限",
                List.of("<gray>当前: <white>" + (draft.maxClaims() == 0 ? "无限（0）" : draft.maxClaims()),
                        "<dark_gray>次数上限优先于冷却时间",
                        "", "<yellow>左键 +1 / 右键 -1", "<yellow>Shift 左键 +10 / Shift 右键 -10")));
        inventory.setItem(SLOT_REWARDS, button(Material.CHEST, "<aqua>奖励列表",
                List.of("<gray>共 <white>" + draft.rewards().size() + "</white> 项奖励", "", "<yellow>点击查看/编辑")));
        inventory.setItem(SLOT_BACK, button(Material.ARROW, "<gray>返回列表", "<dark_gray>放弃未保存的更改"));
        inventory.setItem(SLOT_SAVE, button(Material.LIME_DYE, "<green>保存",
                List.of("<gray>写入礼包所属 YAML 并重载")));
        if (draft.existing()) {
            inventory.setItem(SLOT_DELETE, button(Material.LAVA_BUCKET, "<red>删除礼包",
                    List.of("<gray>Shift + 左键 确认删除")));
        }
        player.openInventory(inventory);
    }

    // === Rewards menu ===

    static final int SLOT_ADD_COMMAND = 48;
    static final int SLOT_ADD_ITEM = 50;
    static final int SLOT_REWARDS_BACK = 53;

    public void openRewards(Player player) {
        GiftDraft draft = drafts.get(player.getUniqueId());
        if (draft == null) {
            openList(player);
            return;
        }
        GiftMenuHolder holder = new GiftMenuHolder(GiftMenuHolder.Type.REWARDS, player);
        Inventory inventory = Bukkit.createInventory(holder, 54, Component.text("奖励: " + draft.id()));
        holder.setInventory(inventory);

        List<RewardDefinition> rewards = draft.rewards();
        for (int i = 0; i < rewards.size() && i < MAX_REWARDS; i++) {
            inventory.setItem(i, rewardIcon(rewards.get(i)));
        }
        inventory.setItem(SLOT_ADD_COMMAND, button(Material.COMMAND_BLOCK, "<green>添加命令奖励",
                List.of("<gray>点击后输入命令", "<dark_gray>可用 %player% %uuid% %gift%")));
        inventory.setItem(SLOT_ADD_ITEM, button(Material.ITEM_FRAME, "<green>添加物品奖励",
                List.of("<yellow>左键: 打开物品投放界面",
                        "<gray>可像箱子一样放入多件物品",
                        "<yellow>右键: 输入已有物品 ID")));
        inventory.setItem(SLOT_REWARDS_BACK, button(Material.ARROW, "<gray>返回编辑", List.of()));
        player.openInventory(inventory);
    }

    private ItemStack rewardIcon(RewardDefinition reward) {
        if (reward instanceof RewardDefinition.CommandReward commandReward) {
            return button(Material.COMMAND_BLOCK, "<yellow>命令奖励",
                    List.of("<gray>" + commandReward.command(), "", "<red>Shift + 左键 删除"));
        }
        RewardDefinition.ItemReward itemReward = (RewardDefinition.ItemReward) reward;
        ItemStack saved = items.find(itemReward.itemId()).orElse(null);
        if (saved == null) {
            return button(Material.CHEST_MINECART, "<red>物品奖励缺失",
                    List.of("<gray>物品: <white>" + itemReward.itemId(),
                            "<gray>数量: <white>" + itemReward.amount(), "", "<red>Shift + 左键 删除"));
        }

        int configuredAmount = itemReward.amount() < 0 ? saved.getAmount() : itemReward.amount();
        saved.setAmount(Math.min(Math.max(1, configuredAmount), Math.max(1, saved.getMaxStackSize())));
        ItemMeta meta = saved.getItemMeta();
        if (meta != null) {
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            if (!lore.isEmpty()) {
                lore.add(Component.empty());
            }
            lore.add(clean(messages.parse("<gray>奖励数量: <white>" + configuredAmount)));
            lore.add(clean(messages.parse("<dark_gray>物品 ID: " + itemReward.itemId())));
            lore.add(Component.empty());
            lore.add(clean(messages.parse("<red>Shift + 左键 删除")));
            meta.lore(lore);
            saved.setItemMeta(meta);
        }
        return saved;
    }

    public void openItemInput(Player player) {
        GiftDraft draft = drafts.get(player.getUniqueId());
        if (draft == null) {
            openList(player);
            return;
        }
        if (!hasRewardCapacity(player, draft)) {
            return;
        }

        closeActiveItemInput(player);
        GiftMenuHolder holder = new GiftMenuHolder(GiftMenuHolder.Type.ITEM_INPUT, player);
        Inventory inventory = Bukkit.createInventory(
                holder,
                ITEM_INPUT_INVENTORY_SIZE,
                Component.text("放入礼包物品: " + draft.id()));
        holder.setInventory(inventory);

        ItemStack filler = button(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray>控制区域", List.of());
        for (int slot = ITEM_INPUT_STORAGE_SIZE; slot < ITEM_INPUT_INVENTORY_SIZE; slot++) {
            inventory.setItem(slot, filler);
        }
        inventory.setItem(SLOT_ITEM_INPUT_SAVE, button(Material.LIME_DYE, "<green>保存物品奖励",
                List.of("<gray>保存前 45 格内的全部物品", "<gray>保存后原物品会返还给你")));
        inventory.setItem(SLOT_ITEM_INPUT_CANCEL, button(Material.ARROW, "<red>取消并返回",
                List.of("<gray>不保存，并返还已放入的物品")));

        itemInputInventories.put(player.getUniqueId(), inventory);
        player.openInventory(inventory);
    }

    public void saveItemInput(Player player, Inventory inventory) {
        GiftDraft draft = drafts.get(player.getUniqueId());
        if (draft == null || !isOwnedItemInput(player, inventory)) {
            return;
        }

        List<ItemStack> inputItems = copyItemInputContents(inventory);
        if (inputItems.isEmpty()) {
            messages.send(player, "gui-item-input-empty");
            return;
        }
        int remaining = draft.remainingRewardCapacity(MAX_REWARDS);
        if (inputItems.size() > remaining) {
            messages.send(player, "gui-item-input-too-many", Map.of(
                    "count", Integer.toString(inputItems.size()),
                    "remaining", Integer.toString(remaining)));
            return;
        }

        Map<String, ItemStack> itemBatch = new LinkedHashMap<>();
        List<String> savedItemIds = new ArrayList<>(inputItems.size());
        for (ItemStack inputItem : inputItems) {
            String itemId;
            do {
                itemId = items.nextGuiItemId(draft.id());
            } while (itemBatch.containsKey(itemId));
            itemBatch.put(itemId, inputItem);
            savedItemIds.add(itemId);
        }
        try {
            items.saveAll(itemBatch);
        } catch (IOException | RuntimeException exception) {
            rollbackInputItems(draft, savedItemIds);
            plugin.getLogger().log(Level.SEVERE, "从礼包物品投放 GUI 批量保存失败", exception);
            messages.send(player, "gui-item-add-failed");
            return;
        }

        for (int index = 0; index < inputItems.size(); index++) {
            String itemId = savedItemIds.get(index);
            ItemStack inputItem = inputItems.get(index);
            draft.trackTemporaryItem(itemId);
            draft.rewards().add(new RewardDefinition.ItemReward(itemId, inputItem.getAmount()));
        }

        int returned = closeItemInput(player, inventory, false);
        messages.send(player, "gui-items-added", Map.of(
                "count", Integer.toString(inputItems.size()),
                "returned", Integer.toString(returned)));
        openRewardsNextTick(player);
    }

    public void cancelItemInput(Player player, Inventory inventory) {
        closeItemInput(player, inventory);
        openRewardsNextTick(player);
    }

    public int closeItemInput(Player player, Inventory inventory) {
        return closeItemInput(player, inventory, true);
    }

    private int closeItemInput(Player player, Inventory inventory, boolean notifyPlayer) {
        if (!isOwnedItemInput(player, inventory)) {
            return 0;
        }
        itemInputInventories.remove(player.getUniqueId(), inventory);
        List<ItemStack> returnedItems = takeItemInputContents(inventory);
        if (returnedItems.isEmpty()) {
            return 0;
        }

        Map<Integer, ItemStack> overflow = player.getInventory().addItem(
                returnedItems.toArray(ItemStack[]::new));
        overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        if (notifyPlayer) {
            messages.send(player, "gui-item-input-returned", Map.of(
                    "count", Integer.toString(returnedItems.size())));
        }
        return returnedItems.size();
    }

    public void closeActiveItemInput(Player player) {
        Inventory inventory = itemInputInventories.get(player.getUniqueId());
        if (inventory != null) {
            closeItemInput(player, inventory);
        }
    }

    public void removeReward(Player player, int index) {
        GiftDraft draft = drafts.get(player.getUniqueId());
        if (draft == null || index < 0 || index >= draft.rewards().size()) {
            return;
        }
        RewardDefinition removed = draft.rewards().remove(index);
        if (removed instanceof RewardDefinition.ItemReward itemReward
                && draft.isTemporaryItem(itemReward.itemId())) {
            try {
                items.delete(itemReward.itemId());
                draft.releaseTemporaryItem(itemReward.itemId());
            } catch (IOException exception) {
                plugin.getLogger().log(Level.WARNING,
                        "清理未保存的 GUI 物品失败: " + itemReward.itemId(), exception);
                draft.rewards().add(index, removed);
                messages.send(player, "gui-item-remove-failed");
                openRewards(player);
                return;
            }
        }
        openRewards(player);
    }

    static boolean isUsableItem(ItemStack item) {
        return item != null && item.getType() != Material.AIR && item.getAmount() > 0;
    }

    static boolean isItemInputStorageSlot(int rawSlot) {
        return rawSlot >= 0 && rawSlot < ITEM_INPUT_STORAGE_SIZE;
    }

    public ItemStack moveIntoItemInput(Inventory inventory, ItemStack source) {
        if (!isUsableItem(source)) {
            return source;
        }

        ItemStack remaining = source.clone();
        for (int slot = 0; slot < ITEM_INPUT_STORAGE_SIZE && remaining.getAmount() > 0; slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (!isUsableItem(existing) || !existing.isSimilar(remaining)) {
                continue;
            }
            int capacity = existing.getMaxStackSize() - existing.getAmount();
            if (capacity <= 0) {
                continue;
            }
            int moved = Math.min(capacity, remaining.getAmount());
            existing.setAmount(existing.getAmount() + moved);
            remaining.setAmount(remaining.getAmount() - moved);
        }
        for (int slot = 0; slot < ITEM_INPUT_STORAGE_SIZE && remaining.getAmount() > 0; slot++) {
            if (isUsableItem(inventory.getItem(slot))) {
                continue;
            }
            int moved = Math.min(remaining.getAmount(), remaining.getMaxStackSize());
            ItemStack placed = remaining.clone();
            placed.setAmount(moved);
            inventory.setItem(slot, placed);
            remaining.setAmount(remaining.getAmount() - moved);
        }
        return remaining.getAmount() > 0 ? remaining : null;
    }

    // === Persistence ===

    public void save(Player player) {
        GiftDraft draft = drafts.get(player.getUniqueId());
        if (draft == null) {
            return;
        }
        try {
            gifts.save(draft.toDefinition());
            draft.commitTemporaryItems();
            drafts.remove(player.getUniqueId());
            messages.send(player, "gui-saved", Map.of("gift", draft.id()));
            openList(player);
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().severe("保存礼包失败: " + exception.getMessage());
            messages.send(player, "gui-save-failed");
        }
    }

    public void delete(Player player) {
        GiftDraft draft = drafts.get(player.getUniqueId());
        if (draft == null) {
            return;
        }
        try {
            gifts.delete(draft.id());
            clearDraft(player.getUniqueId());
            messages.send(player, "gui-deleted", Map.of("gift", draft.id()));
            openList(player);
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().severe("删除礼包失败: " + exception.getMessage());
            messages.send(player, "gui-save-failed");
        }
    }

    // === Chat-driven field edits ===

    public void promptDisplayName(Player player) {
        promptText(player, "gui-prompt-name", value -> {
            GiftDraft draft = drafts.get(player.getUniqueId());
            if (draft != null) {
                draft.setDisplayName(value);
            }
            openEdit(player);
        });
    }

    public void promptPermission(Player player) {
        promptText(player, "gui-prompt-permission", value -> {
            GiftDraft draft = drafts.get(player.getUniqueId());
            if (draft != null) {
                draft.setPermission(value.equalsIgnoreCase("none") ? "" : value);
            }
            openEdit(player);
        });
    }

    public void promptNewGift(Player player) {
        promptText(player, "gui-prompt-newid", value -> {
            String id = value.toLowerCase(Locale.ROOT);
            if (!gifts.isValidId(id)) {
                messages.send(player, "gui-invalid-id");
                openList(player);
                return;
            }
            if (gifts.find(id).isPresent()) {
                messages.send(player, "gui-duplicate-id", Map.of("gift", id));
                openList(player);
                return;
            }
            beginNew(player, id);
        });
    }

    public void promptAddCommand(Player player) {
        GiftDraft currentDraft = drafts.get(player.getUniqueId());
        if (currentDraft == null || !hasRewardCapacity(player, currentDraft)) {
            return;
        }
        promptText(player, "gui-prompt-command", value -> {
            GiftDraft draft = drafts.get(player.getUniqueId());
            if (draft != null && !value.isBlank() && hasRewardCapacity(player, draft)) {
                draft.rewards().add(new RewardDefinition.CommandReward(value));
            }
            openRewards(player);
        });
    }

    public void promptAddItem(Player player) {
        GiftDraft currentDraft = drafts.get(player.getUniqueId());
        if (currentDraft == null || !hasRewardCapacity(player, currentDraft)) {
            return;
        }
        promptText(player, "gui-prompt-item", value -> {
            GiftDraft draft = drafts.get(player.getUniqueId());
            if (draft == null) {
                return;
            }
            if (!hasRewardCapacity(player, draft)) {
                openRewards(player);
                return;
            }
            String[] parts = value.trim().split("\\s+");
            String itemId = parts[0].toLowerCase(Locale.ROOT);
            int amount = 1;
            if (parts.length >= 2) {
                try {
                    amount = Integer.parseInt(parts[1]);
                } catch (NumberFormatException ignored) {
                    amount = 1;
                }
            }
            if (!items.isValidId(itemId) || items.find(itemId).isEmpty()) {
                messages.send(player, "gui-item-missing", Map.of("item", itemId));
                openRewards(player);
                return;
            }
            amount = Math.max(1, Math.min(1_000_000, amount));
            draft.rewards().add(new RewardDefinition.ItemReward(itemId, amount));
            openRewards(player);
        });
    }

    private void promptText(Player player, String promptMessage, java.util.function.Consumer<String> callback) {
        player.closeInventory();
        messages.send(player, promptMessage);
        chatInput.await(player, callback);
    }

    private void openRewardsNextTick(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && drafts.containsKey(player.getUniqueId())) {
                openRewards(player);
            }
        });
    }

    public void shutdown() {
        List<Map.Entry<UUID, Inventory>> activeInputs = List.copyOf(itemInputInventories.entrySet());
        for (Map.Entry<UUID, Inventory> entry : activeInputs) {
            Inventory inventory = entry.getValue();
            if (inventory.getHolder() instanceof GiftMenuHolder holder) {
                closeItemInput(holder.owner(), inventory);
            }
        }
        itemInputInventories.clear();

        List<GiftDraft> activeDrafts = List.copyOf(drafts.values());
        drafts.clear();
        activeDrafts.forEach(this::cleanupTemporaryItems);
    }

    private void replaceDraft(UUID playerId, GiftDraft replacement) {
        GiftDraft previous = drafts.put(playerId, replacement);
        cleanupTemporaryItems(previous);
    }

    private void cleanupTemporaryItems(GiftDraft draft) {
        if (draft == null) {
            return;
        }
        List<String> itemIds = draft.temporaryItemIds();
        try {
            items.deleteAll(itemIds);
            itemIds.forEach(draft::releaseTemporaryItem);
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "批量清理未保存的 GUI 物品失败", exception);
        }
    }

    private boolean isOwnedItemInput(Player player, Inventory inventory) {
        if (inventory == null || !(inventory.getHolder() instanceof GiftMenuHolder holder)) {
            return false;
        }
        return holder.type() == GiftMenuHolder.Type.ITEM_INPUT
                && holder.owner().getUniqueId().equals(player.getUniqueId());
    }

    private List<ItemStack> copyItemInputContents(Inventory inventory) {
        List<ItemStack> result = new ArrayList<>();
        for (int slot = 0; slot < ITEM_INPUT_STORAGE_SIZE; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (isUsableItem(item)) {
                result.add(item.clone());
            }
        }
        return result;
    }

    private List<ItemStack> takeItemInputContents(Inventory inventory) {
        List<ItemStack> result = new ArrayList<>();
        for (int slot = 0; slot < ITEM_INPUT_STORAGE_SIZE; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (isUsableItem(item)) {
                result.add(item.clone());
                inventory.setItem(slot, null);
            }
        }
        return result;
    }

    private void rollbackInputItems(GiftDraft draft, List<String> savedItemIds) {
        try {
            items.deleteAll(savedItemIds);
        } catch (IOException | RuntimeException exception) {
            savedItemIds.forEach(draft::trackTemporaryItem);
            plugin.getLogger().log(Level.SEVERE, "批量回滚礼包投放物品失败", exception);
        }
    }

    private boolean hasRewardCapacity(Player player, GiftDraft draft) {
        if (draft.remainingRewardCapacity(MAX_REWARDS) > 0) {
            return true;
        }
        messages.send(player, "gui-rewards-full");
        return false;
    }
}
