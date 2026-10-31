package com.kve.common.kubejs;

import com.kve.common.kubejs.KubeJSBlockAttrExporter.BlockEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Golden/snapshot tests for {@link KubeJSBlockAttrExporter#previewBlockJS()}
 * (the same read-only preview path used by the in-game "Preview" button
 * and by IdUsageScreen). Pins the exact {@code MutableBlock} property names
 * - {@code destroySpeed}, {@code explosionResistance}, {@code lightEmission},
 * {@code friction}, {@code requiredTool} - confirmed against
 * https://kubejs.com/wiki/ref/MutableBlock, so a refactor that silently
 * renames or reorders one of these is caught immediately here instead of
 * only being noticed in-game after a restart.
 */
class KubeJSBlockAttrExporterGoldenTest {

    @Test
    void onlyTouchedFieldsAreEmitted() {
        BlockEntry e = new BlockEntry();
        e.blockId = "minecraft:stone";
        e.destroySpeed = 1.5;

        String script = KubeJSBlockAttrExporter.previewBlockJS(e);

        assertTrue(script.contains("BlockEvents.modification(event => {"));
        assertTrue(script.contains("event.modify('minecraft:stone', block => {"));
        assertTrue(script.contains("block.destroySpeed = 1.5"));
        assertFalse(script.contains("explosionResistance"), "untouched fields (-1) must not appear at all");
        assertFalse(script.contains("lightEmission"));
        assertFalse(script.contains("friction"));
        assertFalse(script.contains("requiredTool"), "requiredTool must be omitted unless requiresToolSet is true");
    }

    @Test
    void allFieldsTogetherProduceExactExpectedLines() {
        BlockEntry e = new BlockEntry();
        e.blockId = "shovel_js_testmod:reinforced_stone";
        e.destroySpeed = 5;
        e.explosionResistance = 1200;
        e.lightEmission = 7;
        e.friction = 0.4;
        e.requiresTool = true;
        e.requiresToolSet = true;

        String script = KubeJSBlockAttrExporter.previewBlockJS(e);

        String expected =
                "// startup_scripts/shovel_js/blocks/attributes.js:\n" +
                "BlockEvents.modification(event => {\n" +
                "  event.modify('shovel_js_testmod:reinforced_stone', block => {\n" +
                "    block.destroySpeed = 5\n" +
                "    block.explosionResistance = 1200\n" +
                "    block.lightEmission = 7\n" +
                "    block.friction = 0.4\n" +
                "    try {\n" +
                "      block.requiredTool = true\n" +
                "    } catch (ex) {\n" +
                "      console.warn('[Shovel JS] block.requiredTool no soportado para shovel_js_testmod:reinforced_stone (KubeJS-Mods/KubeJS#418): ' + ex)\n" +
                "    }\n" +
                "  })\n" +
                "\n})\n";
        assertEquals(expected, script);
    }

    @Test
    void requiresToolSetEmitsRealAssignmentWrappedInTryCatch() {
        BlockEntry e = new BlockEntry();
        e.blockId = "minecraft:dirt";
        e.requiresTool = false;
        e.requiresToolSet = true;

        String script = KubeJSBlockAttrExporter.previewBlockJS(e);
        assertTrue(script.contains("block.requiredTool = false"),
                "an explicit requiresToolSet must emit the real assignment, not just a comment");
        assertTrue(script.contains("try {") && script.contains("} catch (ex) {"),
                "the assignment must stay defensively wrapped since KubeJS-Mods/KubeJS#418 still crashes for some blocks");
    }

    @Test
    void lightEmissionZeroIsEmittedNotTreatedAsUnset() {
        BlockEntry e = new BlockEntry();
        e.blockId = "minecraft:glass";
        e.lightEmission = 0;

        String script = KubeJSBlockAttrExporter.previewBlockJS(e);
        assertTrue(script.contains("block.lightEmission = 0"),
                "0 is a valid explicit light level and must not be confused with the -1 'no change' sentinel");
    }

    @Test
    void nullEntryProducesPlaceholderCommentInsteadOfThrowing() {
        String script = KubeJSBlockAttrExporter.previewBlockJS(null);
        assertNotNull(script);
        assertTrue(script.startsWith("//"));
    }

    @Test
    void invalidBlockIdIsFlaggedByValidateBlock() {
        BlockEntry e = new BlockEntry();
        e.blockId = "";
        assertFalse(KubeJSBlockAttrExporter.validateBlock(e).isEmpty());
    }

    @Test
    void outOfRangeLightEmissionIsFlaggedByValidateBlock() {
        BlockEntry e = new BlockEntry();
        e.blockId = "minecraft:stone";
        e.lightEmission = 20;
        assertFalse(KubeJSBlockAttrExporter.validateBlock(e).isEmpty());
    }

    @Test
    void harvestToolNeverEmittedAsBlockProperty() {
        BlockEntry e = new BlockEntry();
        e.blockId = "minecraft:grass_block";
        e.harvestTools.add("pickaxe");
        e.harvestLevel = 2;

        String script = KubeJSBlockAttrExporter.previewBlockJS(e);

        assertFalse(script.contains("block.harvestTool"),
                "harvestTool is not a real MutableBlock property - must never be assigned on the block object:\n" + script);
        assertFalse(script.contains("block.harvestLevel"),
                "harvestLevel is not a real MutableBlock property - must never be assigned on the block object:\n" + script);
    }

    @Test
    void harvestToolAndLevelExportAsTagAdditions() {
        BlockEntry e = new BlockEntry();
        e.blockId = "minecraft:grass_block";
        e.harvestTools.add("pickaxe");
        e.harvestLevel = 2;

        String script = KubeJSBlockAttrExporter.previewBlockJS(e);

        assertTrue(script.contains("ServerEvents.tags('block', event => {"), script);
        assertTrue(script.contains("event.add('minecraft:mineable/pickaxe', 'minecraft:grass_block')"), script);
        assertTrue(script.contains("event.add('minecraft:needs_iron_tool', 'minecraft:grass_block')"), script);
    }

    @Test
    void multipleHarvestToolsEachEmitTheirOwnMineableTag() {
        BlockEntry e = new BlockEntry();
        e.blockId = "minecraft:shovel_js_multi_tool_block";
        e.harvestTools.add("pickaxe");
        e.harvestTools.add("axe");

        String script = KubeJSBlockAttrExporter.previewBlockJS(e);

        assertTrue(script.contains("event.add('minecraft:mineable/pickaxe', 'minecraft:shovel_js_multi_tool_block')"), script);
        assertTrue(script.contains("event.add('minecraft:mineable/axe', 'minecraft:shovel_js_multi_tool_block')"),
                "selecting several tool types must emit one mineable/<tool> tag per type - vanilla treats multiple mineable/* tags as OR, not AND:\n" + script);
    }

    @Test
    void harvestLevelWoodNeedsNoTierTag() {
        BlockEntry e = new BlockEntry();
        e.blockId = "minecraft:dirt";
        e.harvestLevel = 0;

        String script = KubeJSBlockAttrExporter.previewBlockJS(e);
        assertFalse(script.contains("needs_"), "level 0 (wood) is the untagged baseline tier - no needs_*_tool tag should be added:\n" + script);
    }

    @Test
    void harvestLevelNetheriteHasNoVanillaTagAndIsNotSilentlyDropped() {
        BlockEntry e = new BlockEntry();
        e.blockId = "minecraft:obsidian";
        e.harvestLevel = 4;

        String script = KubeJSBlockAttrExporter.previewBlockJS(e);
        assertFalse(script.contains("needs_netherite_tool"), "vanilla has no needs_netherite_tool tag - must not emit a no-op tag add:\n" + script);
        assertTrue(script.contains("//"), "should explain why netherite couldn't be enforced instead of silently dropping the setting:\n" + script);
    }

    @Test
    void noHarvestFieldsMeansNoTagsBlockAtAll() {
        BlockEntry e = new BlockEntry();
        e.blockId = "minecraft:stone";
        e.destroySpeed = 1.5;

        String script = KubeJSBlockAttrExporter.previewBlockJS(e);
        assertFalse(script.contains("ServerEvents.tags"), "no harvestTool/harvestLevel set - the tags block should be omitted entirely:\n" + script);
    }

    @Test
    void exactToolOffByDefaultProducesNoExactToolBlock() {
        BlockEntry e = new BlockEntry();
        e.blockId = "minecraft:dirt";
        e.harvestTools.add("shovel");
        e.harvestToolItems.add("minecraft:netherite_shovel");
        e.harvestLevel = 3;

        String script = KubeJSBlockAttrExporter.previewBlockJS(e);
        assertFalse(script.contains("ShovelAPI.requireExactTool"),
                "requireExactHarvestTool defaults to false - existing blocks must keep their old (tag-only) behavior:\n" + script);
    }

    @Test
    void exactToolOnWithNoItemPickedProducesNoExactToolBlock() {
        BlockEntry e = new BlockEntry();
        e.blockId = "minecraft:dirt";
        e.harvestTools.add("shovel");
        e.harvestToolItems.add("");
        e.harvestLevel = 1;
        e.requireExactHarvestTool = true;

        String script = KubeJSBlockAttrExporter.previewBlockJS(e);
        assertFalse(script.contains("ShovelAPI.requireExactTool"),
                "nothing to compare the held item against - the block must be silently omitted, not emit a check that always fails:\n" + script);
    }

    @Test
    void exactToolOnEmitsExpectedApiRegistration() {
        BlockEntry e = new BlockEntry();
        e.blockId = "minecraft:dirt";
        e.harvestTools.add("shovel");
        e.harvestToolItems.add("minecraft:netherite_shovel");
        e.harvestLevel = 3;
        e.requireExactHarvestTool = true;

        String script = KubeJSBlockAttrExporter.previewBlockJS(e);

        String expectedExactBlock =
                "\n// server_scripts/shovel_js/blocks/exact_tool.js:\n" +
                "ShovelAPI.clearExactToolRequirements()\n" +
                "ShovelAPI.requireExactTool('minecraft:dirt', 'minecraft:netherite_shovel')\n";

        assertTrue(script.contains(expectedExactBlock),
                "exact-tool ShovelAPI registration didn't match expected output:\n" + script);
        assertFalse(script.contains("BlockEvents.broken"),
                "exact-tool requirement must go through ShovelAPI now, not a hand-rolled BlockEvents.broken simulation:\n" + script);
        assertTrue(script.contains("ServerEvents.tags('block'"),
                "exact-tool check must not replace the existing category+tier tags export:\n" + script);
    }

    @Test
    void exactToolWithMultipleItemsAcceptsAnyOfThem() {
        BlockEntry e = new BlockEntry();
        e.blockId = "minecraft:ancient_debris";
        e.harvestTools.add("pickaxe");
        e.harvestTools.add("pickaxe");
        e.harvestToolItems.add("minecraft:diamond_pickaxe");
        e.harvestToolItems.add("minecraft:netherite_pickaxe");
        e.requireExactHarvestTool = true;

        String script = KubeJSBlockAttrExporter.previewBlockJS(e);
        assertTrue(script.contains(
                "ShovelAPI.requireExactToolAny('minecraft:ancient_debris', "
                        + "['minecraft:diamond_pickaxe', 'minecraft:netherite_pickaxe'])"), script);
    }

    @Test
    void requireExactHarvestToolWithoutAnyItemFailsValidation() {
        BlockEntry e = new BlockEntry();
        e.blockId = "minecraft:dirt";
        e.requireExactHarvestTool = true;

        assertFalse(KubeJSBlockAttrExporter.validateBlock(e).isEmpty(),
                "toggling 'exact tool required' on with nothing picked to match against must be flagged, not silently accepted");
    }

    @Test
    void requireExactHarvestToolWithBlankItemStillFailsValidation() {
        BlockEntry e = new BlockEntry();
        e.blockId = "minecraft:dirt";
        e.harvestTools.add("shovel");
        e.harvestToolItems.add("");
        e.requireExactHarvestTool = true;

        assertFalse(KubeJSBlockAttrExporter.validateBlock(e).isEmpty(),
                "a blank harvestToolItems entry doesn't count as a real pick");
    }

    @Test
    void copyPreservesRequireExactHarvestTool() {
        BlockEntry e = new BlockEntry();
        e.blockId = "minecraft:dirt";
        e.harvestTools.add("shovel");
        e.harvestToolItems.add("minecraft:netherite_shovel");
        e.requireExactHarvestTool = true;

        BlockEntry c = e.copy();
        assertTrue(c.requireExactHarvestTool, "copy() must carry requireExactHarvestTool over, same as every other field");
    }
}
