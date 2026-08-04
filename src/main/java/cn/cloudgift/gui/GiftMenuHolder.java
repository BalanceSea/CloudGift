package cn.cloudgift.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** Marks an inventory as one of the CloudGift editor menus so clicks can be routed safely. */
public final class GiftMenuHolder implements InventoryHolder {

    public enum Type {
        LIST,
        EDIT,
        REWARDS
    }

    private final Type type;
    private final Player owner;
    private Inventory inventory;

    public GiftMenuHolder(Type type, Player owner) {
        this.type = type;
        this.owner = owner;
    }

    public Type type() {
        return type;
    }

    public Player owner() {
        return owner;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
