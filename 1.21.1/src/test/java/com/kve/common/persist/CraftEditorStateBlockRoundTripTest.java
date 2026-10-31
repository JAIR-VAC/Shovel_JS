package com.kve.common.persist;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kve.common.kubejs.KubeJSBlockAttrExporter;
import com.kve.common.kubejs.KubeJSBlockAttrExporter.BlockEntry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class CraftEditorStateBlockRoundTripTest {

    @AfterEach
    void cleanup() {
        KubeJSBlockAttrExporter.clearAll();
    }

    private static JsonArray invokeSaveBlocks() throws Exception {
        // el estado guarda solo lo aplicado; lo cargado con loadEntry cuenta como aplicado
        KubeJSBlockAttrExporter.markApplied();
        Method m = CraftEditorState.class.getDeclaredMethod("saveBlocks");
        m.setAccessible(true);
        return (JsonArray) m.invoke(null);
    }

    private static void invokeLoadBlocks(JsonObject root) throws Exception {
        Method m = CraftEditorState.class.getDeclaredMethod("loadBlocks", JsonObject.class);
        m.setAccessible(true);
        m.invoke(null, root);
    }

    private static BlockEntry roundTrip(String blockId) throws Exception {
        JsonArray saved = invokeSaveBlocks();
        KubeJSBlockAttrExporter.clearAll();
        JsonObject root = new JsonObject();
        root.add("blocks", saved);
        invokeLoadBlocks(root);
        return KubeJSBlockAttrExporter.getEntry(blockId);
    }

    @Test
    void harvestToolSelectionSurvivesASaveLoadRoundTrip() throws Exception {
        BlockEntry e = new BlockEntry();
        e.blockId = "minecraft:obsidian";
        e.enabled = true;
        e.requiresTool = true;
        e.requiresToolSet = true;
        e.harvestTools.add("pickaxe");
        e.harvestToolItems.add("minecraft:diamond_pickaxe");
        e.harvestLevel = 3;
        KubeJSBlockAttrExporter.loadEntry(e);

        BlockEntry reloaded = roundTrip("minecraft:obsidian");

        assertNotNull(reloaded, "Block vanished after round-trip.");
        assertTrue(reloaded.requiresTool);
        assertEquals(1, reloaded.harvestTools.size(),
                "harvestTools was lost after round-trip (this is the exact bug reported: the tool choice disappears on game exit).");
        assertEquals("pickaxe", reloaded.harvestTools.get(0));
        assertEquals(1, reloaded.harvestToolItems.size());
        assertEquals("minecraft:diamond_pickaxe", reloaded.harvestToolItems.get(0));
        assertEquals(3, reloaded.harvestLevel);
    }

    @Test
    void multipleHarvestToolsSurviveRoundTrip() throws Exception {
        BlockEntry e = new BlockEntry();
        e.blockId = "minecraft:iron_ore";
        e.harvestTools.add("pickaxe");
        e.harvestTools.add("axe");
        e.harvestToolItems.add("minecraft:iron_pickaxe");
        e.harvestToolItems.add("minecraft:iron_axe");
        e.harvestLevel = 1;
        KubeJSBlockAttrExporter.loadEntry(e);

        BlockEntry reloaded = roundTrip("minecraft:iron_ore");

        assertNotNull(reloaded);
        assertEquals(2, reloaded.harvestTools.size());
        assertEquals(2, reloaded.harvestToolItems.size());
        assertEquals("pickaxe", reloaded.harvestTools.get(0));
        assertEquals("axe", reloaded.harvestTools.get(1));
    }

    @Test
    void blockWithNoHarvestToolChoiceStaysEmptyAfterRoundTrip() throws Exception {
        BlockEntry e = new BlockEntry();
        e.blockId = "minecraft:stone";
        e.destroySpeed = 1.5;
        KubeJSBlockAttrExporter.loadEntry(e);

        BlockEntry reloaded = roundTrip("minecraft:stone");

        assertNotNull(reloaded);
        assertEquals(1.5, reloaded.destroySpeed, 0.0001);
        assertTrue(reloaded.harvestTools.isEmpty(),
                "A block that never had a harvest-tool choice should not gain one from a round-trip.");
        assertTrue(reloaded.harvestToolItems.isEmpty());
        assertEquals(-1, reloaded.harvestLevel);
        assertFalse(reloaded.requireExactHarvestTool,
                "A block that never opted into exact-tool enforcement should not gain it from a round-trip.");
    }

    @Test
    void requireExactHarvestToolSurvivesASaveLoadRoundTrip() throws Exception {
        BlockEntry e = new BlockEntry();
        e.blockId = "minecraft:dirt";
        e.harvestTools.add("shovel");
        e.harvestToolItems.add("minecraft:netherite_shovel");
        e.harvestLevel = 3;
        e.requireExactHarvestTool = true;
        KubeJSBlockAttrExporter.loadEntry(e);

        BlockEntry reloaded = roundTrip("minecraft:dirt");

        assertNotNull(reloaded, "Block vanished after round-trip.");
        assertTrue(reloaded.requireExactHarvestTool,
                "requireExactHarvestTool was lost after round-trip.");
        assertEquals("minecraft:netherite_shovel", reloaded.harvestToolItems.get(0));
    }

    @Test
    void requireExactHarvestToolFalseIsOmittedNotWrittenAsFalse() throws Exception {
        BlockEntry e = new BlockEntry();
        e.blockId = "minecraft:dirt";
        e.harvestTools.add("shovel");
        e.harvestToolItems.add("minecraft:netherite_shovel");
        KubeJSBlockAttrExporter.loadEntry(e);

        JsonArray saved = invokeSaveBlocks();
        JsonObject o = saved.get(0).getAsJsonObject();
        assertFalse(o.has("requireExactHarvestTool"),
                "false is the 'no change'/default value - same omit-when-untouched convention as requiresTool, should not be written to disk at all");
    }
}
