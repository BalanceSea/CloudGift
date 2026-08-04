package cn.cloudgift.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class GiftDraftTest {

    @Test
    void tracksTemporaryItemsUntilTheyAreCommitted() {
        GiftDraft draft = GiftDraft.fresh("starter");

        draft.trackTemporaryItem("__cloudgift_gui_starter_one");
        draft.trackTemporaryItem("__cloudgift_gui_starter_two");
        draft.trackTemporaryItem("__cloudgift_gui_starter_one");

        assertEquals(List.of(
                "__cloudgift_gui_starter_one",
                "__cloudgift_gui_starter_two"), draft.temporaryItemIds());
        assertTrue(draft.isTemporaryItem("__cloudgift_gui_starter_one"));

        draft.releaseTemporaryItem("__cloudgift_gui_starter_one");
        assertFalse(draft.isTemporaryItem("__cloudgift_gui_starter_one"));

        draft.commitTemporaryItems();
        assertTrue(draft.temporaryItemIds().isEmpty());
    }
}
