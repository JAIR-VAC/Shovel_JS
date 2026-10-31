package com.kve.common.kubejs;

import com.kve.common.kubejs.TradeExporter.EditorDraft;
import com.kve.common.kubejs.TradeExporter.TradeEditMode;
import com.kve.common.kubejs.TradeExporter.TradeEntry;
import com.kve.common.kubejs.TradeExporter.TradeOffer;
import com.kve.common.kubejs.TradeExporter.TradeTargetKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** El borrador del editor de trades sobrevive a salir del Studio. */
class TradeEditorDraftTest {

    @Test
    void draftRoundTripKeepsEverythingTheEditorShows() {
        TradeEntry e = new TradeEntry();
        e.id = "armorer_extra";
        e.category = "herreros";
        e.targetKind = TradeTargetKind.VILLAGER;
        e.professionId = "minecraft:armorer";
        e.level = 3;
        e.mode = TradeEditMode.ADD_OFFERS;
        TradeOffer o = new TradeOffer();
        o.inputAId = "minecraft:emerald"; o.inputACount = 5;
        o.outputId = "minecraft:iron_chestplate"; o.outputCount = 1;
        o.maxUses = 7;
        e.offers.add(o);

        EditorDraft back = TradeExporter.draftFromJson(TradeExporter.draftToJson(new EditorDraft(e, "armorer_extra", 0, "arm")));

        assertEquals("armorer_extra", back.currentId());
        assertEquals(0, back.selectedOffer());
        assertEquals("arm", back.search());
        assertEquals("minecraft:armorer", back.entry().professionId);
        assertEquals(3, back.entry().level);
        assertEquals(TradeEditMode.ADD_OFFERS, back.entry().mode);
        assertEquals(1, back.entry().offers.size());
        assertEquals(5, back.entry().offers.get(0).inputACount);
        assertEquals(7, back.entry().offers.get(0).maxUses);
    }
}
