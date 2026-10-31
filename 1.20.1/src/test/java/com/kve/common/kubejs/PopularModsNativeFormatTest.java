package com.kve.common.kubejs;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kve.common.recipe.RecipeData;
import com.kve.common.recipetype.RecipeTypeDescriptorRegistry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mismo
 * criterio que {@link RawJsonTemplateNativeFormatTest} - cada caso arma con
 * los slots/campos del editor una receta REAL del jar del mod (carpeta
 * extrasources/unsoported mod/mods, ruta indicada en cada test) y compara el
 * JSON exportado. Los items del mod se reemplazan por items vanilla (sin el
 * mod cargado no existen); lo que se verifica es la FORMA (claves, anidado,
 * tipos). Donde el jar escribe el resultado como id suelto y el serializer
 * acepta tambien el objeto {"item": ...} (confirmado en su bytecode), el
 * esperado usa el objeto, que es lo que KVE emite.
 */
class PopularModsNativeFormatTest {

    @BeforeEach
    void noModsLoaded() {
        RecipeTypeDescriptorRegistry.setModLoadedCheckForTests(m -> false);
        RecipeTypeDescriptorRegistry.reload();
    }

    @AfterEach
    void restore() {
        RecipeTypeDescriptorRegistry.setModLoadedCheckForTests(null);
    }

    private static RecipeData machine(String type) {
        RecipeData r = new RecipeData();
        r.recipeId = "native_test";
        r.type = RecipeData.RecipeType.CUSTOM_MACHINE;
        r.customTypeId = type;
        return r;
    }

    private static RecipeData grid(String type, ItemStack... ingredients) {
        RecipeData r = new RecipeData();
        r.recipeId = "native_test";
        r.type = RecipeData.RecipeType.CUSTOM_JSON;
        r.customTypeId = type;
        r.shapelessIngredients = new ArrayList<>(List.of(ingredients));
        r.output = ItemStack.EMPTY;
        return r;
    }

    private static RecipeData.MachineSlot item(ItemStack s) {
        RecipeData.MachineSlot m = new RecipeData.MachineSlot();
        m.item = s;
        return m;
    }

    private static RecipeData.MachineSlot tag(String id, int count) {
        RecipeData.MachineSlot m = new RecipeData.MachineSlot();
        m.type = RecipeData.MachineSlotType.TAG;
        m.tagId = id;
        m.tagCount = count;
        return m;
    }

    private static RecipeData.MachineSlot fluid(String id, int amount) {
        RecipeData.MachineSlot m = new RecipeData.MachineSlot();
        m.type = RecipeData.MachineSlotType.FLUID;
        m.fluidId = id;
        m.fluidAmount = amount;
        return m;
    }

    private static RecipeData.MachineSlot gap() {
        return new RecipeData.MachineSlot();
    }

    private static JsonObject exported(RecipeData r) {
        String js = KubeJSExporter.previewRecipeJS(r);
        int start = js.indexOf("event.custom(");
        int end = js.lastIndexOf(").id('shovel_js:");
        assertTrue(start >= 0 && end > start, "no event.custom(...) in:\n" + js);
        return JsonParser.parseString(js.substring(start + "event.custom(".length(), end)).getAsJsonObject();
    }

    private static void assertJson(String expected, JsonObject actual) {
        JsonObject e = JsonParser.parseString(expected).getAsJsonObject();
        assertEquals(e, actual, "\nexpected: " + e + "\nactual:   " + actual);
    }

    @Test
    void twilightCrumbleHornUsesBareBlockIds() {
        RecipeData r = machine("twilightforest:crumble_horn");
        r.machineInputs.add(item(new ItemStack(Items.STONE_BRICKS)));
        r.machineOutputs.add(item(new ItemStack(Items.CRACKED_STONE_BRICKS)));
        assertJson("{\"type\":\"twilightforest:crumble_horn\",\"from\":\"minecraft:stone_bricks\",\"to\":\"minecraft:cracked_stone_bricks\"}", exported(r));
    }

    @Test
    void twilightTransformationPowderMatchesJar() {
        RecipeData r = machine("twilightforest:transformation_powder");
        r.machineInputs.add(item(new ItemStack(Items.COW_SPAWN_EGG)));
        r.machineOutputs.add(item(new ItemStack(Items.SHEEP_SPAWN_EGG)));
        assertJson("{\"type\":\"twilightforest:transformation_powder\",\"from\":\"minecraft:cow\",\"to\":\"minecraft:sheep\",\"reversible\":true}", exported(r));
    }

    @Test
    void twilightUncraftingMatchesJar() {
        // data/twilightforest/recipes/uncrafting/tipped_arrow.json:
        // {"cost":4,"input":{"count":8,"ingredient":{"item":"minecraft:tipped_arrow"}},"key":{"A":{"item":"minecraft:arrow"}},"pattern":["AAA","A A","AAA"]}
        RecipeData r = new RecipeData();
        r.recipeId = "native_test";
        r.type = RecipeData.RecipeType.CUSTOM_JSON;
        r.customTypeId = "twilightforest:uncrafting";
        r.gridWidth = 3;
        r.gridHeight = 3;
        r.ingredients = new ArrayList<>();
        for (int i = 0; i < 9; i++) r.ingredients.add(i == 4 ? ItemStack.EMPTY : new ItemStack(Items.ARROW));
        r.output = new ItemStack(Items.TIPPED_ARROW, 8);
        r.customExtraJson = "{\"cost\": 4}";
        assertTrue(KubeJSExporter.validateRecipe(r).isEmpty(), "uncrafting debe validar como grilla posicional");
        assertJson("{\"type\":\"twilightforest:uncrafting\",\"input\":{\"ingredient\":{\"item\":\"minecraft:tipped_arrow\"},\"count\":8},"
                + "\"pattern\":[\"AAA\",\"A A\",\"AAA\"],\"key\":{\"A\":{\"item\":\"minecraft:arrow\"}},\"cost\":4}", exported(r));
    }

    @Test
    void aetherEnchantingMatchesJar() {
        RecipeData r = machine("aether:enchanting");
        r.machineInputs.add(tag("aether:accepted_music_discs", 1));
        r.machineOutputs.add(item(new ItemStack(Items.MUSIC_DISC_CAT)));
        assertJson("{\"type\":\"aether:enchanting\",\"category\":\"enchanting_misc\",\"cookingtime\":500,\"experience\":1.0,"
                + "\"ingredient\":{\"tag\":\"aether:accepted_music_discs\"},\"result\":{\"item\":\"minecraft:music_disc_cat\"}}", exported(r));
    }

    @Test
    void aetherRepairingIsInputOnly() {
        RecipeData r = grid("aether:repairing", new ItemStack(Items.BOW));
        assertTrue(KubeJSExporter.validateRecipe(r).isEmpty(), "un tipo solo-entrada no debe exigir item de salida");
        assertJson("{\"type\":\"aether:repairing\",\"category\":\"enchanting_repair\",\"ingredient\":{\"item\":\"minecraft:bow\"},\"repairTime\":600}", exported(r));
    }

    @Test
    void aetherIncubationTakesTheEntityFromItsSpawnEgg() {
        RecipeData r = machine("aether:incubation");
        r.machineInputs.add(item(new ItemStack(Items.EGG)));
        r.machineOutputs.add(item(new ItemStack(Items.CHICKEN_SPAWN_EGG)));
        r.customExtraJson = "{\"tag\": \"{Hungry:1b,IsBaby:1b,MoaType:\\\"aether:black\\\",PlayerGrown:1b}\"}";
        assertJson("{\"type\":\"aether:incubation\",\"ingredient\":{\"item\":\"minecraft:egg\"},\"entity\":\"minecraft:chicken\","
                + "\"tag\":\"{Hungry:1b,IsBaby:1b,MoaType:\\\"aether:black\\\",PlayerGrown:1b}\",\"incubationtime\":1000}", exported(r));

        RecipeData notAnEgg = machine("aether:incubation");
        notAnEgg.machineInputs.add(item(new ItemStack(Items.EGG)));
        notAnEgg.machineOutputs.add(item(new ItemStack(Items.STONE)));
        assertTrue(KubeJSExporter.previewRecipeJS(notAnEgg).contains("NO EXPORTADO"), "un item que no es huevo de generacion no da una entidad");
    }

    @Test
    void aetherItemBanIsInputOnlyWithBlockBypass() {
        RecipeData r = grid("aether:item_placement_ban", new ItemStack(Items.FIRE_CHARGE));
        r.customExtraJson = "{\"bypass\": \"#aether:allowed_flammables\"}";
        assertTrue(KubeJSExporter.validateRecipe(r).isEmpty());
        assertTrue(RecipeTypeDescriptorRegistry.find("aether:item_placement_ban").orElseThrow().noOutput, "el editor no debe exigir salida");
        assertJson("{\"type\":\"aether:item_placement_ban\",\"biome\":\"#aether:ultracold\",\"bypass\":{\"tag\":\"aether:allowed_flammables\"},"
                + "\"ingredient\":{\"item\":\"minecraft:fire_charge\"}}", exported(r));
    }

    @Test
    void aetherBlockBanUsesBlockIngredient() {
        RecipeData r = grid("aether:block_placement_ban", new ItemStack(Items.TORCH));
        r.customExtraJson = "{\"bypass\": \"minecraft:netherrack\"}";
        assertJson("{\"type\":\"aether:block_placement_ban\",\"biome\":\"#aether:ultracold\",\"bypass\":{\"block\":\"minecraft:netherrack\"},"
                + "\"ingredient\":{\"block\":\"minecraft:torch\"}}", exported(r));
    }

    @Test
    void aetherPlacementConversionUsesBlockKeys() {
        RecipeData r = machine("aether:placement_conversion");
        r.machineInputs.add(item(new ItemStack(Items.MAGMA_BLOCK)));
        r.machineOutputs.add(item(new ItemStack(Items.OBSIDIAN)));
        assertJson("{\"type\":\"aether:placement_conversion\",\"biome\":\"#aether:ultracold\","
                + "\"ingredient\":{\"block\":\"minecraft:magma_block\"},\"result\":{\"block\":\"minecraft:obsidian\"}}", exported(r));
    }

    @Test
    void deepAetherCombiningNeedsAllThreeIngredients() {
        RecipeData r = machine("deep_aether:combining");
        r.machineInputs.add(item(new ItemStack(Items.AMETHYST_SHARD)));
        r.machineInputs.add(item(new ItemStack(Items.SWEET_BERRIES)));
        r.machineInputs.add(item(new ItemStack(Items.PRISMARINE_CRYSTALS)));
        r.machineOutputs.add(item(new ItemStack(Items.HONEY_BOTTLE)));
        assertJson("{\"type\":\"deep_aether:combining\",\"category\":\"combinable_misc\",\"experience\":0.1,"
                + "\"ingredients\":[{\"item\":\"minecraft:amethyst_shard\"},{\"item\":\"minecraft:sweet_berries\"},{\"item\":\"minecraft:prismarine_crystals\"}],"
                + "\"output\":{\"item\":\"minecraft:honey_bottle\"},\"processing_time\":100}", exported(r));

        RecipeData missing = machine("deep_aether:combining");
        missing.machineInputs.add(item(new ItemStack(Items.AMETHYST_SHARD)));
        missing.machineOutputs.add(item(new ItemStack(Items.HONEY_BOTTLE)));
        assertTrue(KubeJSExporter.previewRecipeJS(missing).contains("NO EXPORTADO"), "el Combiner lee 3 ingredientes fijos: con menos no se exporta");
    }

    @Test
    void cataclysmWeaponFusionMatchesJar() {
        RecipeData r = machine("cataclysm:weapon_fusion");
        r.machineInputs.add(item(new ItemStack(Items.SHIELD)));
        r.machineInputs.add(item(new ItemStack(Items.BLAZE_ROD)));
        r.machineOutputs.add(item(new ItemStack(Items.NETHERITE_SWORD)));
        assertJson("{\"type\":\"cataclysm:weapon_fusion\",\"base\":{\"item\":\"minecraft:shield\"},"
                + "\"addition\":{\"item\":\"minecraft:blaze_rod\"},\"result\":{\"item\":\"minecraft:netherite_sword\"}}", exported(r));
    }

    @Test
    void cataclysmAmethystBlessUsesSingleObjectIngredients() {
        RecipeData r = machine("cataclysm:amethyst_bless");
        r.machineInputs.add(item(new ItemStack(Items.COD)));
        r.machineOutputs.add(item(new ItemStack(Items.COOKED_COD)));
        assertJson("{\"type\":\"cataclysm:amethyst_bless\",\"ingredients\":{\"item\":\"minecraft:cod\"},"
                + "\"result\":{\"item\":\"minecraft:cooked_cod\"},\"time\":120}", exported(r));
    }

    @Test
    void apotheosisFletchingMatchesJar() {
        RecipeData r = machine("apotheosis:fletching");
        r.machineInputs.add(item(new ItemStack(Items.FLINT)));
        r.machineInputs.add(item(new ItemStack(Items.STICK)));
        r.machineInputs.add(item(new ItemStack(Items.FEATHER)));
        r.machineOutputs.add(item(new ItemStack(Items.ARROW, 6)));
        assertJson("{\"type\":\"apotheosis:fletching\",\"ingredients\":[{\"item\":\"minecraft:flint\"},{\"item\":\"minecraft:stick\"},"
                + "{\"item\":\"minecraft:feather\"}],\"result\":{\"item\":\"minecraft:arrow\",\"count\":6}}", exported(r));
    }

    @Test
    void apotheosisInfusionWritesStatsObjects() {
        RecipeData r = machine("apotheosis:enchanting");
        r.machineInputs.add(item(new ItemStack(Items.CARROT)));
        r.machineOutputs.add(item(new ItemStack(Items.GOLDEN_CARROT)));
        r.customExtraJson = "{\"eterna\": \"10\", \"quanta\": \"10\", \"arcana\": \"0\", \"maxEterna\": \"10\", \"maxQuanta\": \"30\"}";
        assertJson("{\"type\":\"apotheosis:enchanting\",\"input\":{\"item\":\"minecraft:carrot\"},"
                + "\"requirements\":{\"eterna\":10,\"quanta\":10,\"arcana\":0},"
                + "\"max_requirements\":{\"eterna\":10,\"quanta\":30,\"arcana\":-1},"
                + "\"result\":{\"item\":\"minecraft:golden_carrot\"}}", exported(r));
    }

    @Test
    void adAstraCompressingUsesIdCountResult() {
        RecipeData r = machine("ad_astra:compressing");
        r.machineInputs.add(tag("ad_astra:calorite_blocks", 1));
        r.machineOutputs.add(item(new ItemStack(Items.IRON_INGOT, 9)));
        r.customExtraJson = "{\"cookingtime\": \"800\"}";
        assertJson("{\"type\":\"ad_astra:compressing\",\"cookingtime\":800,\"energy\":20,"
                + "\"ingredient\":{\"tag\":\"ad_astra:calorite_blocks\"},\"result\":{\"id\":\"minecraft:iron_ingot\",\"count\":9}}", exported(r));
    }

    @Test
    void adAstraCryoFreezingUsesMillibuckets() {
        RecipeData r = machine("ad_astra:cryo_freezing");
        r.machineInputs.add(item(new ItemStack(Items.BLUE_ICE)));
        r.machineOutputs.add(fluid("ad_astra:cryo_fuel", 10));
        assertJson("{\"type\":\"ad_astra:cryo_freezing\",\"cookingtime\":120,\"energy\":40,"
                + "\"ingredient\":{\"item\":\"minecraft:blue_ice\"},\"result\":{\"fluid\":\"ad_astra:cryo_fuel\",\"millibuckets\":10}}", exported(r));
    }

    @Test
    void adAstraRefiningWithFluidTagMatchesJar() {
        RecipeData r = machine("ad_astra:refining");
        r.machineInputs.add(fluid("#ad_astra:oil", 5));
        r.machineOutputs.add(fluid("ad_astra:fuel", 5));
        assertJson("{\"type\":\"ad_astra:refining\",\"cookingtime\":1,\"energy\":30,"
                + "\"input\":{\"ingredient\":{\"tag\":\"ad_astra:oil\"},\"millibuckets\":5},"
                + "\"result\":{\"fluid\":\"ad_astra:fuel\",\"millibuckets\":5}}", exported(r));
    }

    @Test
    void adAstraOxygenLoadingWithPlainFluidUsesFluidValue() {
        RecipeData r = machine("ad_astra:oxygen_loading");
        r.machineInputs.add(fluid("minecraft:water", 25));
        r.machineOutputs.add(fluid("ad_astra:oxygen", 25));
        assertJson("{\"type\":\"ad_astra:oxygen_loading\",\"cookingtime\":1,\"energy\":30,"
                + "\"input\":{\"ingredient\":{\"fluid\":\"minecraft:water\"},\"millibuckets\":25},"
                + "\"result\":{\"fluid\":\"ad_astra:oxygen\",\"millibuckets\":25}}", exported(r));
    }

    @Test
    void adAstraNasaWorkbenchKeepsTheFourteenPositions() {
        ItemStack[] parts = {new ItemStack(Items.LIGHTNING_ROD), new ItemStack(Items.IRON_BLOCK), new ItemStack(Items.IRON_BLOCK),
                new ItemStack(Items.IRON_BLOCK), new ItemStack(Items.IRON_BLOCK), new ItemStack(Items.IRON_BLOCK), new ItemStack(Items.IRON_BLOCK),
                new ItemStack(Items.PRISMARINE_SHARD), new ItemStack(Items.BUCKET), new ItemStack(Items.BUCKET), new ItemStack(Items.PRISMARINE_SHARD),
                new ItemStack(Items.PRISMARINE_SHARD), new ItemStack(Items.FURNACE), new ItemStack(Items.PRISMARINE_SHARD)};
        RecipeData r = machine("ad_astra:nasa_workbench");
        for (ItemStack p : parts) r.machineInputs.add(item(p));
        r.machineOutputs.add(item(new ItemStack(Items.FIREWORK_ROCKET)));
        JsonObject out = exported(r);
        assertEquals(14, out.getAsJsonArray("ingredients").size());
        assertEquals("minecraft:lightning_rod", out.getAsJsonArray("ingredients").get(0).getAsJsonObject().get("item").getAsString());
        assertEquals("minecraft:furnace", out.getAsJsonArray("ingredients").get(12).getAsJsonObject().get("item").getAsString());
        assertEquals(JsonParser.parseString("{\"id\":\"minecraft:firework_rocket\",\"count\":1}"), out.get("result"));

        RecipeData missing = machine("ad_astra:nasa_workbench");
        missing.machineInputs.add(item(parts[0]));
        missing.machineOutputs.add(item(new ItemStack(Items.FIREWORK_ROCKET)));
        assertTrue(KubeJSExporter.previewRecipeJS(missing).contains("NO EXPORTADO"), "faltan piezas: no se exporta un cohete con posiciones corridas");
    }

    @Test
    void apotheosisSalvagingUsesChanceAsMinimum() {
        RecipeData r = machine("apotheosis:salvaging");
        r.machineInputs.add(item(new ItemStack(Items.DIAMOND_HORSE_ARMOR)));
        RecipeData.MachineSlot leather = item(new ItemStack(Items.LEATHER, 2));
        leather.chance = 0.5;
        RecipeData.MachineSlot diamond = item(new ItemStack(Items.DIAMOND, 4));
        diamond.chance = 0.5;
        r.machineOutputs.add(leather);
        r.machineOutputs.add(diamond);
        assertJson("{\"type\":\"apotheosis:salvaging\",\"input\":{\"item\":\"minecraft:diamond_horse_armor\"},\"outputs\":["
                + "{\"min_count\":1,\"max_count\":2,\"stack\":{\"item\":\"minecraft:leather\"}},"
                + "{\"min_count\":2,\"max_count\":4,\"stack\":{\"item\":\"minecraft:diamond\"}}]}", exported(r));
    }

    @Test
    void apotheosisSpawnerModifierMatchesJar() {
        RecipeData r = grid("apotheosis:spawner_modifier", new ItemStack(Items.TURTLE_EGG), new ItemStack(Items.QUARTZ));
        r.customExtraJson = "{\"stat\": \"baby\", \"value\": \"false\", \"consumes_offhand\": \"false\"}";
        assertJson("{\"type\":\"apotheosis:spawner_modifier\",\"mainhand\":{\"item\":\"minecraft:turtle_egg\"},"
                + "\"offhand\":{\"item\":\"minecraft:quartz\"},\"consumes_offhand\":false,"
                + "\"stat_changes\":[{\"id\":\"baby\",\"value\":false,\"min\":-1,\"max\":-1}]}", exported(r));
    }

    @Test
    void apotheosisSpawnerNumericStatWithLimits() {
        RecipeData r = grid("apotheosis:spawner_modifier", new ItemStack(Items.CLOCK));
        r.customExtraJson = "{\"stat\": \"max_delay\", \"value\": \"-10\", \"min\": \"20\", \"max\": \"-1\"}";
        JsonObject out = exported(r);
        assertFalse(out.has("offhand"));
        assertEquals(JsonParser.parseString("[{\"id\":\"max_delay\",\"value\":-10,\"min\":20,\"max\":-1}]"), out.get("stat_changes"));
    }

    @Test
    void apotheosisReforgingNeedsNoItemsAtAll() {
        RecipeData r = grid("apotheosis:reforging");
        assertEquals(List.of(), KubeJSExporter.validateRecipe(r), "reforging no lleva ingredientes ni salida");
        assertJson("{\"type\":\"apotheosis:reforging\",\"rarity\":\"apotheosis:common\",\"material_cost\":1,\"sigil_cost\":0,\"level_cost\":2}", exported(r));
    }

    @Test
    void aetherCanvasShowsTheRealFuel() {
        var layout = RecipeTypeDescriptorRegistry.find("aether:enchanting").orElseThrow().slotLayout;
        assertNotNull(layout);
        assertTrue(layout.unified, "canvas de estacion");
        assertEquals("aether:ambrosium_shard", layout.decorItems.get(0).item);
        assertFalse(layout.notes.isEmpty());
    }

    @Test
    void draconicFusionUsesTheRitualRing() {
        var d = RecipeTypeDescriptorRegistry.find("draconicevolution:fusion_crafting").orElseThrow();
        assertTrue(d.hasRitualView());
        assertEquals("catalyst", d.ritualView.centerSlotId);
    }

    @Test
    void draconicFusionMatchesJar() {
        RecipeData r = machine("draconicevolution:fusion_crafting");
        r.machineInputs.add(tag("forge:storage_blocks/draconium", 4));
        r.machineInputs.add(item(new ItemStack(Items.NETHER_STAR, 3)));
        r.machineInputs.add(item(new ItemStack(Items.DRAGON_EGG)));
        r.machineInputs.add(item(new ItemStack(Items.NETHER_STAR, 3)));
        r.machineOutputs.add(item(new ItemStack(Items.DIAMOND_BLOCK, 4)));
        r.customExtraJson = "{\"tier\": \"WYVERN\", \"total_energy\": \"50000000\"}";
        String star = "{\"item\":\"minecraft:nether_star\"}";
        assertJson("{\"type\":\"draconicevolution:fusion_crafting\","
                + "\"catalyst\":{\"type\":\"draconicevolution:ingredient_stack\",\"count\":4,\"items\":[{\"tag\":\"forge:storage_blocks/draconium\"}]},"
                + "\"ingredients\":[" + star + "," + star + "," + star + ",{\"item\":\"minecraft:dragon_egg\"}," + star + "," + star + "," + star + "],"
                + "\"result\":{\"item\":\"minecraft:diamond_block\",\"count\":4},\"tier\":\"WYVERN\",\"total_energy\":50000000}", exported(r));
    }

    @Test
    void ironsCauldronFillUsesForgeFluidStack() {
        RecipeData r = machine("irons_spellbooks:alchemist_cauldron_fill");
        r.machineInputs.add(item(new ItemStack(Items.POTION)));
        r.machineInputs.add(fluid("irons_spellbooks:blood", 250));
        r.machineOutputs.add(item(new ItemStack(Items.GLASS_BOTTLE)));
        assertJson("{\"type\":\"irons_spellbooks:alchemist_cauldron_fill\",\"input\":{\"item\":\"minecraft:potion\"},"
                + "\"fluid\":{\"Amount\":250,\"FluidName\":\"irons_spellbooks:blood\"},\"result\":{\"item\":\"minecraft:glass_bottle\"}}", exported(r));
    }

    @Test
    void ironsBrewWithOnlyByproductKeepsEmptyResults() {
        RecipeData r = machine("irons_spellbooks:alchemist_cauldron_brew");
        r.machineInputs.add(fluid("irons_spellbooks:blood", 1000));
        r.machineInputs.add(item(new ItemStack(Items.LEATHER)));
        r.machineOutputs.add(item(new ItemStack(Items.PAPER)));
        assertJson("{\"type\":\"irons_spellbooks:alchemist_cauldron_brew\",\"base_fluid\":{\"Amount\":1000,\"FluidName\":\"irons_spellbooks:blood\"},"
                + "\"input\":{\"item\":\"minecraft:leather\"},\"results\":[],\"byproduct\":{\"item\":\"minecraft:paper\"}}", exported(r));
    }

    @Test
    void ironsBrewFluidResultsSkipTheByproductGap() {
        RecipeData r = machine("irons_spellbooks:alchemist_cauldron_brew");
        r.machineInputs.add(fluid("irons_spellbooks:rare_ink", 1000));
        r.machineInputs.add(tag("forge:ingots/gold", 1));
        r.machineOutputs.add(gap());
        r.machineOutputs.add(fluid("irons_spellbooks:epic_ink", 250));
        assertJson("{\"type\":\"irons_spellbooks:alchemist_cauldron_brew\",\"base_fluid\":{\"Amount\":1000,\"FluidName\":\"irons_spellbooks:rare_ink\"},"
                + "\"input\":{\"tag\":\"forge:ingots/gold\"},\"results\":[{\"Amount\":250,\"FluidName\":\"irons_spellbooks:epic_ink\"}]}", exported(r));
    }

    @Test
    void vineryWineFermentationMatchesJar() {
        RecipeData r = machine("vinery:wine_fermentation");
        r.machineInputs.add(item(new ItemStack(Items.SUGAR)));
        r.machineInputs.add(item(new ItemStack(Items.KELP)));
        r.machineInputs.add(item(new ItemStack(Items.IRON_INGOT)));
        r.machineOutputs.add(item(new ItemStack(Items.HONEY_BOTTLE)));
        r.customExtraJson = "{\"juiceType\": \"white_taiga\"}";
        assertJson("{\"type\":\"vinery:wine_fermentation\",\"juice\":{\"type\":\"white_taiga\",\"amount\":7},"
                + "\"ingredients\":[{\"item\":\"minecraft:sugar\"},{\"item\":\"minecraft:kelp\"},{\"item\":\"minecraft:iron_ingot\"}],"
                + "\"wine_bottle\":{\"required\":true},\"result\":{\"item\":\"minecraft:honey_bottle\"}}", exported(r));
    }

    @Test
    void refurbishedSlicingMatchesJar() {
        RecipeData r = machine("refurbished_furniture:cutting_board_slicing");
        r.machineInputs.add(item(new ItemStack(Items.DRIED_KELP)));
        r.machineOutputs.add(item(new ItemStack(Items.BLACK_DYE)));
        assertJson("{\"type\":\"refurbished_furniture:cutting_board_slicing\",\"ingredient\":{\"item\":\"minecraft:dried_kelp\"},"
                + "\"result\":\"minecraft:black_dye\",\"count\":1}", exported(r));
    }

    @Test
    void refurbishedFryingPanUsesBareResultId() {
        RecipeData r = machine("refurbished_furniture:frying_pan_cooking");
        r.machineInputs.add(item(new ItemStack(Items.BREAD)));
        r.machineOutputs.add(item(new ItemStack(Items.COOKED_BEEF)));
        r.customExtraJson = "{\"time\": \"400\"}";
        assertJson("{\"type\":\"refurbished_furniture:frying_pan_cooking\",\"category\":\"food\",\"ingredient\":{\"item\":\"minecraft:bread\"},"
                + "\"result\":\"minecraft:cooked_beef\",\"time\":400}", exported(r));
    }

    @Test
    void refurbishedWorkbenchUsesCountedMaterials() {
        RecipeData r = machine("refurbished_furniture:workbench_constructing");
        r.machineInputs.add(tag("minecraft:planks", 6));
        r.machineInputs.add(item(new ItemStack(Items.WHEAT, 16)));
        r.machineInputs.add(item(new ItemStack(Items.WHITE_WOOL, 2)));
        r.machineInputs.add(item(new ItemStack(Items.BLACK_DYE)));
        r.machineOutputs.add(item(new ItemStack(Items.BLACK_BED, 2)));
        assertJson("{\"type\":\"refurbished_furniture:workbench_constructing\",\"materials\":[{\"tag\":\"minecraft:planks\",\"count\":6},"
                + "{\"item\":\"minecraft:wheat\",\"count\":16},{\"item\":\"minecraft:white_wool\",\"count\":2},{\"item\":\"minecraft:black_dye\",\"count\":1}],"
                + "\"result\":{\"item\":\"minecraft:black_bed\",\"count\":2},\"show_notification\":false}", exported(r));
    }

    @Test
    void simplePlanesMatchesJar() {
        RecipeData r = machine("simpleplanes:plane_workbench");
        r.machineInputs.add(item(new ItemStack(Items.IRON_INGOT, 4)));
        r.machineOutputs.add(item(new ItemStack(Items.MINECART)));
        assertJson("{\"type\":\"simpleplanes:plane_workbench\",\"ingredient\":{\"item\":\"minecraft:iron_ingot\"},\"ingredient_amount\":4,"
                + "\"material_amount\":64,\"result\":{\"item\":\"minecraft:minecart\"}}", exported(r));
    }

    @Test
    void celestisynthRequiresAllSixMaterials() {
        RecipeData r = machine("celestisynth:starlit_factory");
        r.machineInputs.add(item(new ItemStack(Items.GHAST_TEAR)));
        r.machineInputs.add(item(new ItemStack(Items.GOLD_INGOT)));
        r.machineInputs.add(item(new ItemStack(Items.IRON_INGOT)));
        r.machineInputs.add(item(new ItemStack(Items.FEATHER)));
        r.machineInputs.add(item(new ItemStack(Items.PHANTOM_MEMBRANE)));
        r.machineInputs.add(item(new ItemStack(Items.NETHERITE_INGOT)));
        r.machineOutputs.add(item(new ItemStack(Items.NETHERITE_SWORD)));
        JsonObject out = exported(r);
        assertEquals(JsonParser.parseString("{\"item\":\"minecraft:feather\"}"), out.get("core_material"));
        assertEquals(JsonParser.parseString("{\"item\":\"minecraft:netherite_ingot\"}"), out.get("extra_core_material"));
        assertEquals(400, out.get("forging_time").getAsInt());

        RecipeData missing = machine("celestisynth:starlit_factory");
        missing.machineInputs.add(item(new ItemStack(Items.GHAST_TEAR)));
        missing.machineOutputs.add(item(new ItemStack(Items.NETHERITE_SWORD)));
        assertTrue(KubeJSExporter.previewRecipeJS(missing).contains("NO EXPORTADO"));
    }
}
