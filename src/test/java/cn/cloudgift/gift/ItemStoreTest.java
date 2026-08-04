package cn.cloudgift.gift;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ItemStoreTest {

    @TempDir
    Path dataFolder;

    @Test
    void generatesUniqueValidGuiItemIds() throws IOException {
        ItemStore store = new ItemStore(dataFolder.toFile(), Logger.getAnonymousLogger());
        store.reload();
        String first = store.nextGuiItemId("starter");
        String second = store.nextGuiItemId("starter");

        assertTrue(first.startsWith("__cloudgift_gui_starter_"));
        assertTrue(store.isValidId(first));
        assertTrue(store.isValidId(second));
        assertNotEquals(first, second);
        assertFalse(store.isValidId("invalid item id"));
        assertFalse(store.delete(first));
    }
}
