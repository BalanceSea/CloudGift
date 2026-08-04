package cn.cloudgift.command.common;

import cn.cloudgift.CloudGiftPlugin;
import cn.cloudgift.gift.GiftRegistry;
import cn.cloudgift.gift.ItemStore;
import cn.cloudgift.gui.GiftEditorGui;
import cn.cloudgift.message.MessageService;
import cn.cloudgift.service.ClaimService;
import java.util.Objects;

/** Dependencies shared by command modules. Modules receive this bundle instead of the plugin singleton. */
public record CommandServices(
        CloudGiftPlugin plugin,
        GiftRegistry gifts,
        ItemStore items,
        ClaimService claims,
        MessageService messages,
        GiftEditorGui editorGui) {

    public CommandServices {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(gifts, "gifts");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(claims, "claims");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(editorGui, "editorGui");
    }
}
