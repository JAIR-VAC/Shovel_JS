package com.kve.integration.kubejs;

import com.kve.integration.kubejs.MenuSlotProber;
import com.kve.common.blockrules.ExactToolRequirements;
import com.kve.common.kubejs.KubeJSExporter;
import com.kve.common.kubejs.KubeJSMobExporter;
import com.kve.common.mobrules.AllyRequirements;
import com.kve.common.mobrules.EnemyRequirements;
import com.kve.common.mobrules.MobBehaviorTypeFlags;
import com.kve.common.mobrules.AttackEffectRequirements;
import com.kve.common.mobrules.AttackEffectRule;
import com.kve.common.recipe.RecipeData;
import com.kve.common.worldgen.OreFeatureExporter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Global script binding registered as "ShovelAPI" by KveKubeJSPlugin. Every
 * method here is something a KubeJS script literally cannot do today
 * without hand-rolling it (or without pulling in a whole GUI-only class),
 * either because vanilla KubeJS's own API doesn't expose it, or because the
 * logic already lives buried inside one of KVE's screens.
 *
 * IMPORTANT: this class is only ever loaded by KubeJS's own ServiceLoader,
 * as a consequence of KveKubeJSPlugin#registerBindings being called - see
 * KveIntegrations for why that makes importing NBT/etc classes here safe
 * even though KVE has no hard dependency on KubeJS.
 */
public class KveScriptAPI {

    private KveScriptAPI() {}

    public static CompoundTag mergeNbt(CompoundTag base, CompoundTag patch) {
        if (base == null || patch == null) return base;
        for (String key : patch.getAllKeys()) {
            Tag patchValue = patch.get(key);
            Tag baseValue = base.get(key);
            if (patchValue instanceof CompoundTag patchCompound && baseValue instanceof CompoundTag baseCompound) {
                mergeNbt(baseCompound, patchCompound);
            } else {
                base.put(key, patchValue.copy());
            }
        }
        return base;
    }

    public static boolean isValidEquipSlot(String slot) {
        return KubeJSMobExporter.isValidSlot(slot);
    }

    /**
     * Adds a new ore vein feature to world gen. Returns a list of
     * validation problems (empty = success, the datapack files were
     * written). Example:
     * <pre>
     * startup_scripts or server_scripts - either works, see caveat above
     * let errors = ShovelAPI.addOreFeature('kve:ruby_ore', 'kve:ruby_ore', 8, 6, -48, 32)
     * if (errors.length) console.error(errors.join(', '))
     * </pre>
     * @param id            namespaced id for the feature, e.g. "kve:ruby_ore"
     * @param oreBlock      the block placed by the vein, e.g. "kve:ruby_ore"
     * @param veinSize      blocks per vein (vanilla iron=9, diamond=8)
     * @param veinsPerChunk placement attempts per chunk (not a guaranteed count)
     * @param minY          lowest Y the vein can generate at
     * @param maxY          highest Y the vein can generate at
     */
    public static List<String> addOreFeature(String id, String oreBlock, int veinSize, int veinsPerChunk, int minY, int maxY) {
        OreFeatureExporter.OreFeature f = new OreFeatureExporter.OreFeature();
        f.id = id;
        f.oreBlock = oreBlock;
        f.veinSize = veinSize;
        f.veinsPerChunk = veinsPerChunk;
        f.minY = minY;
        f.maxY = maxY;
        return OreFeatureExporter.addOreFeature(f);
    }

    /** Same as the 6-arg {@link #addOreFeature()}, with control over the biome filter, replaceable-block tag, and air-exposure discard chance. */
    public static List<String> addOreFeature(String id, String oreBlock, int veinSize, int veinsPerChunk, int minY, int maxY,
                                              String biomeFilter, String replaceableTarget, double discardChanceOnAirExposure) {
        OreFeatureExporter.OreFeature f = new OreFeatureExporter.OreFeature();
        f.id = id;
        f.oreBlock = oreBlock;
        f.veinSize = veinSize;
        f.veinsPerChunk = veinsPerChunk;
        f.minY = minY;
        f.maxY = maxY;
        f.biomeFilter = biomeFilter;
        f.replaceableTarget = replaceableTarget;
        f.discardChanceOnAirExposure = discardChanceOnAirExposure;
        return OreFeatureExporter.addOreFeature(f);
    }

    public static List<String> addOreFeature(String id, String oreBlock, int veinSize, int veinsPerChunk, int minY, int maxY,
                                              String biomeFilter, String replaceableTarget, double discardChanceOnAirExposure,
                                              String heightDistribution, List<String> biomeFilters,
                                              String deepslateOreBlock, String deepslateReplaceableTarget) {
        OreFeatureExporter.OreFeature f = new OreFeatureExporter.OreFeature();
        f.id = id;
        f.oreBlock = oreBlock;
        f.veinSize = veinSize;
        f.veinsPerChunk = veinsPerChunk;
        f.minY = minY;
        f.maxY = maxY;
        f.biomeFilter = biomeFilter;
        f.replaceableTarget = replaceableTarget;
        f.discardChanceOnAirExposure = discardChanceOnAirExposure;
        if ("trapezoid".equalsIgnoreCase(heightDistribution)) {
            f.heightDistribution = OreFeatureExporter.OreFeature.HeightDistribution.TRAPEZOID;
        }
        if (biomeFilters != null && !biomeFilters.isEmpty()) {
            f.biomeFilters = new ArrayList<>(biomeFilters);
        }
        if (deepslateOreBlock != null && !deepslateOreBlock.isBlank()) {
            OreFeatureExporter.OreTarget extra = new OreFeatureExporter.OreTarget();
            extra.oreBlock = deepslateOreBlock;
            if (deepslateReplaceableTarget != null && !deepslateReplaceableTarget.isBlank()) {
                extra.replaceableTarget = deepslateReplaceableTarget;
            }
            f.extraTargets = List.of(extra);
        }
        return OreFeatureExporter.addOreFeature(f);
    }

    public static void removeOreFeature(String id) {
        OreFeatureExporter.removeOreFeature(id);
    }

    /**
     * From now on, {@code blockId} can only be broken (with drops) by a
     * player holding exactly {@code itemId} - any other item is treated as
     * "wrong tool" (very slow, no drops), regardless of what tags it has.
     * Re-registering the same block replaces its requirement. Meant to be
     * called every time your script (re)runs - see
     * {@link #clearExactToolRequirements()} to wipe stale entries first if
     * your script conditionally decides which blocks get a requirement.
     * <pre>
     * ShovelAPI.requireExactTool('kve:sealed_vault_door', 'kve:vault_key')
     * </pre>
     */
    public static void requireExactTool(String blockId, String itemId) {
        ExactToolRequirements.register(blockId, itemId);
    }

    public static void requireExactToolAny(String blockId, List<String> itemIds) {
        ExactToolRequirements.register(blockId, itemIds);
    }

    public static void removeExactToolRequirement(String blockId) {
        ExactToolRequirements.unregister(blockId);
    }

    /** Wipes every exact-tool requirement - call at the top of your registration script before re-adding, so a since-removed line in the script actually takes effect on the next reload instead of leaving a stale requirement active. */
    public static void clearExactToolRequirements() {
        ExactToolRequirements.clearAll();
    }

    /**
     * From now on, {@code blockId} can only be broken (with drops) by a
     * player holding a real netherite-tier tool - enforced via Forge hooks
     * ({@code NetheriteTierEnforcer}), the same real mechanism
     * {@link #requireExactTool(String, String)} already uses for a
     * different kind of requirement vanilla can't express.
     * <pre>
     * ShovelAPI.requireNetheriteTier('kve:reinforced_vault_wall')
     * </pre>
     */
    public static void requireNetheriteTier(String blockId) {
        com.kve.common.blockrules.NetheriteTierRequirements.register(blockId);
    }

    public static void removeNetheriteTierRequirement(String blockId) {
        com.kve.common.blockrules.NetheriteTierRequirements.unregister(blockId);
    }

    /** Wipes every netherite-tier requirement - same reload-hygiene reasoning as {@link #clearExactToolRequirements()}. */
    public static void clearNetheriteTierRequirements() {
        com.kve.common.blockrules.NetheriteTierRequirements.clearAll();
    }

    // -----------------------------------------------------------------
    // Modded machine recipes WITHOUT that mod's KubeJS addon.
    //
    // Normally, adding a recipe for e.g. Mekanism's crushing machine or
    // Create's mixer needs a purpose-built KubeJS addon (KubeJS Mekanism,
    // KubeJS Create,...) installed alongside the base mod, because those
    // addons are what give you a typed builder function
    // (event.recipes.mekanism.crushing(...)) and register the recipe
    // serializer's schema with KubeJS. Without the addon you're stuck
    // hand-writing the raw recipe JSON yourself with event.custom({...}),
    // no fluid/tag helpers, no validation, no idea what fields the type
    // even wants.
    //
    // This reuses the exact same engine the GUI's Machine Recipe editor
    // already uses for this (RecipeData.RecipeType.CUSTOM_MACHINE + typed
    // MachineSlot inputs/outputs + KubeJSExporter's own semantic
    // validation) - it works for ANY recipe serializer id, addon or not,
    // because it writes the recipe as a plain datapack-style JSON file via
    // event.custom() under the hood, the same thing the addon's builder
    // would ultimately produce. If a recipe_types descriptor (see
    // RecipeTypeDescriptor/RecipeTypeDescriptorRegistry,
    // kubejs/data/kve/recipe_types/*.json) exists for recipeTypeId, extra
    // fields in extraJson get validated against it; if not, extraJson is
    // still accepted, just without that extra layer of checking.
    // -----------------------------------------------------------------

    /** An ITEM slot for {@link #addMachineRecipe()}. Returns null (logged, not thrown - so a script loop building many slots doesn't die on the first typo) if itemId isn't a real registered item. */
    public static RecipeData.MachineSlot itemSlot(String itemId, int count) {
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
            com.kve.CraftEditorMod.LOGGER.error("[Shovel JS] ShovelAPI.itemSlot: unknown item id '{}'", itemId);
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(rl);
        RecipeData.MachineSlot slot = new RecipeData.MachineSlot();
        slot.type = RecipeData.MachineSlotType.ITEM;
        slot.item = new ItemStack(item, Math.max(1, count));
        return slot;
    }

    /** A FLUID slot for {@link #addMachineRecipe()}. Existence of fluidId is checked at recipe-save time, not here (matches how the GUI's own SemanticValidator works). */
    public static RecipeData.MachineSlot fluidSlot(String fluidId, int amountMb) {
        RecipeData.MachineSlot slot = new RecipeData.MachineSlot();
        slot.type = RecipeData.MachineSlotType.FLUID;
        slot.fluidId = fluidId;
        slot.fluidAmount = amountMb;
        return slot;
    }

    /** A TAG slot for {@link #addMachineRecipe()} - {@code tagId} without the leading '#', e.g. "forge:ingots/gold". */
    public static RecipeData.MachineSlot tagSlot(String tagId, int count) {
        RecipeData.MachineSlot slot = new RecipeData.MachineSlot();
        slot.type = RecipeData.MachineSlotType.TAG;
        slot.tagId = tagId;
        slot.tagCount = Math.max(1, count);
        return slot;
    }

    /**
     * Adds (or replaces, if recipeId already exists) a recipe for any
     * modded machine's recipe type, addon or not. Returns validation
     * errors (empty = success, the recipe was written and is live
     * immediately - no restart needed, same as any other KubeJS recipe).
     * <pre>
     * ShovelAPI.addMachineRecipe(
     *     'kve:ruby_from_crushing',
     *     'mekanism:crushing',
     *     [ShovelAPI.itemSlot('kve:raw_ruby', 1)],
     *     [ShovelAPI.itemSlot('kve:ruby_dust', 4)]
     * )
     * </pre>
     * @param recipeId     namespaced id for the new recipe
     * @param recipeTypeId the modded recipe serializer id, e.g. "mekanism:crushing"
     * @param inputs       slots built with {@link #itemSlot()}/{@link #fluidSlot()}/{@link #tagSlot()} - null entries (a bad id) are skipped
     * @param outputs      same as inputs, for the result side
     */
    public static List<String> addMachineRecipe(String recipeId, String recipeTypeId, List<RecipeData.MachineSlot> inputs, List<RecipeData.MachineSlot> outputs) {
        return addMachineRecipe(recipeId, recipeTypeId, inputs, outputs, "");
    }

    /** Same as the 4-arg {@link #addMachineRecipe()}, plus a raw JSON object literal (as a string) merged into the recipe for fields the addon's own builder would normally set - processing time, energy cost, chances, whatever that recipe type needs beyond ingredients/results. Validated against a recipe_types descriptor for recipeTypeId if one is registered. */
    public static List<String> addMachineRecipe(String recipeId, String recipeTypeId, List<RecipeData.MachineSlot> inputs, List<RecipeData.MachineSlot> outputs, String extraJson) {
        RecipeData d = new RecipeData();
        d.recipeId = recipeId;
        d.type = RecipeData.RecipeType.CUSTOM_MACHINE;
        d.customTypeId = recipeTypeId;
        d.customExtraJson = extraJson == null ? "" : extraJson;
        d.machineInputs = new ArrayList<>();
        if (inputs != null) for (RecipeData.MachineSlot s : inputs) if (s != null) d.machineInputs.add(s);
        d.machineOutputs = new ArrayList<>();
        if (outputs != null) for (RecipeData.MachineSlot s : outputs) if (s != null) d.machineOutputs.add(s);

        List<String> errors = KubeJSExporter.validateRecipe(d);
        if (!errors.isEmpty()) return errors;
        KubeJSExporter.saveRecipe(d);
        return List.of();
    }

    public static void removeMachineRecipe(String recipeId) {
        KubeJSExporter.deleteRecipe(recipeId);
    }

    /**
     * Registers one attack effect for {@code entityId}: from now on,
     * whatever that mob hits in melee has {@code chance} probability of
     * receiving {@code effectId} for {@code durationTicks} at
     * {@code amplifier}. Adds to (does not replace) whatever's already
     * registered for {@code entityId} - a mob can have several attack
     * effects, matching how the Mob Editor GUI's attack-effects list
     * works. Call {@link #clearAttackEffects(String)} first if your script
     * wants to replace rather than accumulate on every reload.
     * <pre>
     * ShovelAPI.addAttackEffect('minecraft:zombie', 'minecraft:poison', 100, 0, 1.0)
     * </pre>
     * @param entityId      the attacking mob's entity id, e.g. "minecraft:zombie"
     * @param effectId      the potion effect id, e.g. "minecraft:poison"
     * @param durationTicks how long the effect lasts, in ticks (20 = 1 second)
     * @param amplifier     0 = level I, 1 = level II, etc.
     * @param chance        0.0-1.0 probability the effect is applied on any given hit
     */
    public static void addAttackEffect(String entityId, String effectId, int durationTicks, int amplifier, float chance) {
        AttackEffectRequirements.register(entityId, new AttackEffectRule(effectId, durationTicks, amplifier, chance));
    }

    /** Removes every attack effect registered for {@code entityId}. */
    public static void clearAttackEffects(String entityId) {
        AttackEffectRequirements.clear(entityId);
    }

    /** Wipes every attack effect for every mob - call at the top of your registration script before re-adding, same reasoning as {@link #clearExactToolRequirements()}. */
    public static void clearAllAttackEffects() {
        AttackEffectRequirements.clearAll();
    }

    /**
     * Registers {@code allyEntityId} as an ally of {@code entityId}: from
     * now on, neither can damage the other in combat (checked both ways -
     * see {@code AllyRequirements#areAllies}). Adds to (does not replace)
     * whatever's already registered for {@code entityId} - a mob can have
     * several allies, matching how the Mob Editor GUI's "Aliados" list
     * works. Call {@link #clearAllies(String)} first if your script wants
     * to replace rather than accumulate on every reload.
     * <pre>
     * ShovelAPI.addAlly('minecraft:wolf', 'minecraft:fox')
     * </pre>
     * @param entityId     the mob's entity id, e.g. "minecraft:wolf"
     * @param allyEntityId the entity id it should never fight, e.g. "minecraft:fox"
     */
    public static void addAlly(String entityId, String allyEntityId) {
        AllyRequirements.register(entityId, allyEntityId);
    }

    /** Removes every ally relationship registered FROM {@code entityId}. */
    public static void clearAllies(String entityId) {
        AllyRequirements.clear(entityId);
    }

    /** Wipes every ally relationship for every mob - call at the top of your registration script before re-adding, same reasoning as {@link #clearAllAttackEffects()}. */
    public static void clearAllAllies() {
        AllyRequirements.clearAll();
    }

    /**
     * Registers {@code enemyEntityId} as an enemy of {@code entityId}: from
     * now on, either one will always attack the other on sight (checked
     * both ways - see {@code EnemyRequirements#areEnemies}). Adds to (does
     * not replace) whatever's already registered for {@code entityId} - a
     * mob can have several forced enemies, matching how the Mob Editor
     * GUI's "Enemigos" list works. Call {@link #clearEnemies(String)}
     * first if your script wants to replace rather than accumulate on
     * every reload.
     * <pre>
     * ShovelAPI.addEnemy('minecraft:cow', 'minecraft:zombie')
     * </pre>
     * @param entityId      the mob's entity id, e.g. "minecraft:cow"
     * @param enemyEntityId the entity id it should always attack on sight, e.g. "minecraft:zombie"
     */
    public static void addEnemy(String entityId, String enemyEntityId) {
        EnemyRequirements.register(entityId, enemyEntityId);
    }

    /** Removes every enemy relationship registered FROM {@code entityId}. */
    public static void clearEnemies(String entityId) {
        EnemyRequirements.clear(entityId);
    }

    /** Wipes every enemy relationship for every mob - call at the top of your registration script before re-adding, same reasoning as {@link #clearAllAllies()}. */
    public static void clearAllEnemies() {
        EnemyRequirements.clearAll();
    }

    /**
     * Registra los flags de comportamiento extendido de {@code entityId}
     * (ver {@code MobBehaviorFlagsEnforcer} para la lista válida y que hace
     * cada uno). Reemplaza lo que hubiera antes para ese entityId en vez de
     * acumular - llamar de nuevo con una lista distinta reemplaza la
     * anterior.
     * <pre>
     * ShovelAPI.setMobBehaviorFlags('minecraft:cow', ['HOSTILE', 'RANGED_ATTACK'])
     * </pre>
     * @param entityId el entityId del mob, p.ej. "minecraft:cow"
     * @param flags    lista de flags (String[] o array JS) - valores válidos:
     *                 GRIEFS, HOSTILE, NEUTRAL, PASSIVE, FLEES_PLAYER, RANGED_ATTACK
     */
    public static void setMobBehaviorFlags(String entityId, String[] flags) {
        MobBehaviorTypeFlags.clear(entityId);
        if (flags == null) return;
        MobBehaviorTypeFlags.register(entityId, new java.util.LinkedHashSet<>(java.util.Arrays.asList(flags)));
    }

    /** Quita todos los flags de comportamiento extendido de {@code entityId}. */
    public static void clearMobBehaviorFlags(String entityId) {
        MobBehaviorTypeFlags.clear(entityId);
    }

    /** Wipes every extended behavior flag for every mob - call at the top of your registration script before re-adding, same reasoning as {@link #clearAllEnemies()}. */
    public static void clearAllMobBehaviorFlags() {
        MobBehaviorTypeFlags.clearAll();
    }

    /**
     * Probes {@code menuTypeId} (the addon's registered {@code MenuType}
     * id, e.g. {@code "mekanism:crushing"} - not necessarily the same id as
     * the recipe serializer/type) and returns what it found as a plain
     * script-friendly object:
     * <pre>
     * {
     *   success: boolean,
     *   reason: string | null,       // only set when success is false
 * slots: [ { x: int, y: int, role: "input"|"output" },... ]
     * }
     * </pre>
     * Always a PROPOSAL - nothing is written anywhere by this call. Example:
     * <pre>
     * let probe = ShovelAPI.probeMenuSlots('mekanism:crushing')
     * if (probe.success) probe.slots.forEach(s => console.log(`${s.role} @ ${s.x},${s.y}`))
     * else console.log('no se pudo cosechar: ' + probe.reason)
     * </pre>
     */
    public static Map<String, Object> probeMenuSlots(String menuTypeId) {
        MenuSlotProber.ProbeResult result = MenuSlotProber.probeMenuSlots(menuTypeId);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", result.success);
        out.put("reason", result.failureReason);

        List<Map<String, Object>> slots = new ArrayList<>();
        for (MenuSlotProber.ProbedSlot s : result.slots) {
            Map<String, Object> slotMap = new LinkedHashMap<>();
            slotMap.put("x", s.x);
            slotMap.put("y", s.y);
            slotMap.put("role", s.probableRole);
            slots.add(slotMap);
        }
        out.put("slots", slots);
        return out;
    }

    /** Borra todas las reglas del pack (y deshace los compostables) - el script lo llama primero en cada carga. */
    public static void clearPackRules() {
        com.kve.common.packrules.PackRulesRuntime.clear();
    }

    /**
     * Hace compostable un item con {@code chance} (0-1) de subir un nivel, o
     * lo saca de los compostables con 0.
     * <pre>ShovelAPI.setCompostable('minecraft:rotten_flesh', 0.3)</pre>
     */
    public static void setCompostable(String itemId, float chance) {
        com.kve.common.packrules.PackRulesRuntime.setCompostable(itemId, chance);
    }

    /**
     * Impide que un mob aparezca. {@code dimensionsCsv} vacio = todas las
     * dimensiones; {@code naturalOnly} = solo el spawn natural del mundo
     * (spawners, huevos, comandos y raids siguen funcionando).
     * <pre>ShovelAPI.blockSpawn('minecraft:phantom', '', true)</pre>
     */
    public static void blockSpawn(String entityId, String dimensionsCsv, boolean naturalOnly) {
        com.kve.common.packrules.PackRulesRuntime.blockSpawn(entityId, dimensionsCsv, naturalOnly);
    }

    /**
     * Gamerule con la que arranca cada mundo del pack. Se aplica una sola vez
     * por mundo: si un admin la cambia despues con /gamerule, no vuelve.
     * <pre>ShovelAPI.defaultGameRule('doInsomnia', 'false')</pre>
     */
    public static void defaultGameRule(String rule, String value) {
        com.kve.common.packrules.PackRulesRuntime.defaultGameRule(rule, value);
    }
}
