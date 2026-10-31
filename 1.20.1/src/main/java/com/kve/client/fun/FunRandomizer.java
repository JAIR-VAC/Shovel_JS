package com.kve.client.fun;

import com.kve.client.gui.workspace.pending.ApplyMode;
import com.kve.client.gui.workspace.pending.PendingChange;
import com.kve.client.gui.workspace.pending.PendingChangeManager;
import com.kve.client.gui.workspace.pending.PendingDomain;
import com.kve.client.fun.FunOptions.Domain;
import com.kve.client.recipe.ExistingRecipeLoader;
import com.kve.client.worldgen.ExistingOreScanner;
import com.kve.common.Lang;
import com.kve.common.kubejs.KubeJSExporter;
import com.kve.common.kubejs.KubeJSItemAttrExporter;
import com.kve.common.kubejs.KubeJSMobExporter;
import com.kve.common.kubejs.LootTableExporter;
import com.kve.common.kubejs.TradeExporter;
import com.kve.common.recipe.RecipeData;
import com.kve.common.recipe.RecipeGridRules;
import com.kve.common.recipetype.RecipeTypeDescriptorRegistry;
import com.kve.common.recipe.TagIngredient;
import com.kve.common.worldgen.OreOverride;
import com.kve.common.worldgen.OreOverrideExporter;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.IntConsumer;

/**
 * Aleatorizador de la herramienta Fun. Respeta la progresion: cada item tiene un
 * puntaje (pasos de crafteo desde materiales crudos + rareza + material), y los
 * valores nuevos se reparten por ranking para que algo facil de conseguir nunca
 * supere a algo dificil del mismo tipo, ni un mob comun a un jefe.
 * <p>
 * Corre por lotes en el hilo del cliente ({@link #step}); nada se toca hasta la
 * ultima fase, que deja un cambio pendiente por categoria.
 */
@OnlyIn(Dist.CLIENT)
public final class FunRandomizer {

    private static final double INF = Double.POSITIVE_INFINITY;
    private static final int PASSES = 10;
    private static final int MAX_RECIPES = 500;
    private static final String[] HIT_EFFECTS = {"minecraft:slowness", "minecraft:weakness", "minecraft:poison",
            "minecraft:wither", "minecraft:glowing", "minecraft:hunger"};
    private static final String[] MOB_BUFFS = {"minecraft:speed", "minecraft:strength", "minecraft:resistance",
            "minecraft:regeneration", "minecraft:fire_resistance", "minecraft:jump_boost", "minecraft:absorption"};
    private static final String[] CREATURE_BUFFS = {"minecraft:speed", "minecraft:jump_boost", "minecraft:regeneration"};
    private static final String[] TOUGH_FLAGS = {"FIRE_IMMUNE", "FALL_IMMUNE", "DROWN_IMMUNE", "NO_KNOCKBACK"};
    /** Materiales de armadura de mob, de mas facil a mas dificil. */
    private static final String[] ARMOR_TIERS = {"leather", "chainmail", "golden", "iron", "diamond", "netherite"};
    private static final String[] SWORD_TIERS = {"wooden", "stone", "golden", "iron", "diamond", "netherite"};
    private static final String[] ARMOR_SLOTS = {"HEAD", "CHEST", "LEGS", "FEET"};
    private static final String[] ARMOR_PIECES = {"helmet", "chestplate", "leggings", "boots"};
    /** Duracion "permanente" para efectos que el mob trae al aparecer. */
    private static final int SPAWN_EFFECT_TICKS = 1_000_000;
    private static final Set<String> KNOWN_BOSSES = Set.of("minecraft:ender_dragon", "minecraft:wither",
            "minecraft:warden", "minecraft:elder_guardian");

    private static final class Phase {
        final String labelKey;
        final int total;
        final IntConsumer unit;
        final Runnable finish;
        int done;
        Phase(String labelKey, int total, IntConsumer unit, Runnable finish) {
            this.labelKey = labelKey; this.total = Math.max(0, total); this.unit = unit; this.finish = finish;
        }
    }

    private final FunOptions opts;
    private final Set<String> excludedIds;
    private final Random rnd;
    private final double intensity;
    private final List<Phase> phases = new ArrayList<>();
    private int phaseIdx;
    private boolean finished;
    private final Map<Domain, Integer> results = new EnumMap<>(Domain.class);
    private final List<String> notes = new ArrayList<>();

    private final List<FunCompat.RecipeView> recipes;
    private final Set<Item> produced = new HashSet<>();
    private final Map<Item, Double> steps = new HashMap<>();
    private final Map<Item, Double> score = new HashMap<>();
    private final Map<Item, Set<Item>> madeFrom = new HashMap<>();
    private final Map<Item, Set<Item>> usedIn = new HashMap<>();
    private final List<Item> pool = new ArrayList<>();

    private final List<Runnable> stagers = new ArrayList<>();

    public FunRandomizer(FunOptions opts) {
        this.opts = opts;
        this.excludedIds = opts.excludedIds();
        this.rnd = new Random(opts.seed());
        this.intensity = opts.intensity.value;
        this.recipes = FunCompat.recipes();
        for (FunCompat.RecipeView r : recipes) {
            if (r.result().isEmpty()) continue;
            Item out = r.result().getItem();
            produced.add(out);
            for (Ingredient ing : r.ingredients()) {
                if (ing.isEmpty()) continue;
                for (ItemStack s : ing.getItems()) {
                    madeFrom.computeIfAbsent(out, k -> new HashSet<>()).add(s.getItem());
                    usedIn.computeIfAbsent(s.getItem(), k -> new HashSet<>()).add(out);
                }
            }
        }
        phases.add(new Phase("gui.kve.fun.step.progression", PASSES, k -> relaxPass(), this::finishScores));
        boolean local = Minecraft.getInstance().getSingleplayerServer() != null;
        for (Domain d : Domain.values()) {
            if (!opts.domains.contains(d)) continue;
            if (d.needsLocalServer() && !local) {
                notes.add(Lang.get("gui.kve.fun.note.needs_local", Lang.get(d.key)));
                continue;
            }
            switch (d) {
                case RECIPES -> planRecipes();
                case ATTRIBUTES -> planAttributes();
                case MOBS -> planMobs();
                case LOOT -> planLoot();
                case TRADES -> planTrades();
                case WORLDGEN -> planWorldgen();
                case STRUCTURES -> planStructures();
            }
        }
        phases.add(new Phase("gui.kve.fun.step.queue", 1, k -> { for (Runnable r : stagers) r.run(); }, () -> {}));
    }

    // ---- avance ----

    /** Corre unidades hasta agotar el presupuesto; true cuando termino todo. */
    public boolean step(long budgetNanos) {
        long end = System.nanoTime() + budgetNanos;
        while (!finished && System.nanoTime() < end) {
            if (phaseIdx >= phases.size()) { finished = true; break; }
            Phase p = phases.get(phaseIdx);
            if (p.done < p.total) {
                try {
                    p.unit.accept(p.done);
                } catch (Throwable t) {
                    com.kve.CraftEditorMod.LOGGER.debug("[Shovel JS] Fun: unidad {} de {} fallo: {}", p.done, p.labelKey, t.toString());
                }
                p.done++;
            } else {
                try { p.finish.run(); } catch (Throwable t) {
                    com.kve.CraftEditorMod.LOGGER.warn("[Shovel JS] Fun: fase {} fallo: {}", p.labelKey, t.toString());
                }
                phaseIdx++;
            }
        }
        return finished;
    }

    public boolean finished() { return finished; }

    public int totalUnits() {
        int t = 0;
        for (Phase p : phases) t += p.total + 1;
        return t;
    }

    public int doneUnits() {
        int d = 0;
        for (int i = 0; i < phases.size(); i++) {
            Phase p = phases.get(i);
            d += p.done + (i < phaseIdx ? 1 : 0);
        }
        return d;
    }

    public String currentLabel() {
        if (finished || phaseIdx >= phases.size()) return Lang.get("gui.kve.fun.step.done");
        return Lang.get(phases.get(phaseIdx).labelKey);
    }

    public Map<Domain, Integer> results() { return results; }

    public List<String> notes() { return notes; }

    // ---- progresion ----

    private double raw(Item item) {
        Double s = steps.get(item);
        if (s != null) return s;
        return produced.contains(item) ? INF : 0;
    }

    private void relaxPass() {
        for (FunCompat.RecipeView r : recipes) {
            if (r.result().isEmpty()) continue;
            double worst = 0;
            boolean ok = true;
            for (Ingredient ing : r.ingredients()) {
                if (ing.isEmpty()) continue;
                double best = INF;
                for (ItemStack s : ing.getItems()) best = Math.min(best, raw(s.getItem()));
                if (best == INF) { ok = false; break; }
                worst = Math.max(worst, best);
            }
            if (!ok) continue;
            Item out = r.result().getItem();
            double cand = worst + 1;
            if (cand < raw(out)) steps.put(out, cand);
        }
    }

    private void finishScores() {
        Set<Item> known = new HashSet<>(produced);
        known.addAll(usedIn.keySet());
        for (Item item : known) {
            double s = raw(item);
            if (s == INF) s = 3;
            score.put(item, Math.min(8, s) + rarityBonus(item) + FunCompat.tierBonus(item));
        }
        for (Item item : known) {
            if (item == Items.AIR || item instanceof SpawnEggItem) continue;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (!opts.allows(id, excludedIds) || id.getPath().contains("debug") || id.getPath().contains("command")) continue;
            pool.add(item);
        }
        pool.sort(Comparator.comparing(i -> BuiltInRegistries.ITEM.getKey(i).toString()));
    }

    private static int rarityBonus(Item item) {
        try {
            return switch (new ItemStack(item).getRarity()) {
                case COMMON -> 0;
                case UNCOMMON -> 1;
                case RARE -> 2;
                case EPIC -> 3;
            };
        } catch (Throwable t) {
            return 0;
        }
    }

    private double scoreOf(Item item) { return score.getOrDefault(item, 3.0); }

    /** Item al azar del pool con puntaje en [min, max] que no este en {@code avoid}; null si no hay. */
    private Item pick(double min, double max, Set<Item> avoid) {
        List<Item> c = new ArrayList<>();
        for (Item it : pool) {
            double s = scoreOf(it);
            if (s >= min && s <= max && !avoid.contains(it)) c.add(it);
        }
        return c.isEmpty() ? null : c.get(rnd.nextInt(c.size()));
    }

    private double jitter(double spread) { return 1 + (rnd.nextDouble() * 2 - 1) * intensity * spread; }

    private boolean allowed(ResourceLocation id) { return opts.allows(id, excludedIds); }

    private static String sanitize(String s) { return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_"); }

    private static String idOf(Item item) { return BuiltInRegistries.ITEM.getKey(item).toString(); }

    /**
     * Reparte {@code values} (ya aleatorizados) entre {@code members} ordenados por dificultad:
     * el mas dificil recibe el valor mas alto, asi se conserva la progresion.
     */
    private static <T> Map<T, Double> rankAssign(List<T> members, Map<T, Double> difficulty, Map<T, Double> original,
                                                 Map<T, Double> values) {
        List<T> byDifficulty = new ArrayList<>(members);
        byDifficulty.sort(Comparator.<T>comparingDouble(difficulty::get).thenComparingDouble(original::get));
        List<Double> sorted = new ArrayList<>();
        for (T m : members) sorted.add(values.get(m));
        Collections.sort(sorted);
        Map<T, Double> out = new LinkedHashMap<>();
        for (int k = 0; k < byDifficulty.size(); k++) out.put(byDifficulty.get(k), sorted.get(k));
        return out;
    }

    // ---- recetas ----

    private void planRecipes() {
        List<FunCompat.RecipeView> chosen = new ArrayList<>();
        double p = 0.15 + 0.35 * intensity;
        for (FunCompat.RecipeView r : recipes) {
            if (r.result().isEmpty() || r.id().getNamespace().equals("shovel_js")) continue;
            if (!allowed(r.id()) || !allowed(BuiltInRegistries.ITEM.getKey(r.result().getItem()))) continue;
            if (rnd.nextDouble() < p) chosen.add(r);
        }
        Collections.shuffle(chosen, rnd);
        if (chosen.size() > MAX_RECIPES) chosen = new ArrayList<>(chosen.subList(0, MAX_RECIPES));
        List<RecipeData> planned = new ArrayList<>();
        List<String> removals = new ArrayList<>();
        final List<FunCompat.RecipeView> list = chosen;
        phases.add(new Phase("gui.kve.fun.step.recipes", list.size(), k -> {
            FunCompat.RecipeView r = list.get(k);
            RecipeData d;
            try { d = ExistingRecipeLoader.toRecipeData(r.id(), r.recipe(), r.result()); } catch (Throwable t) { return; }
            boolean table = d.type == RecipeData.RecipeType.VANILLA_SHAPED || d.type == RecipeData.RecipeType.VANILLA_SHAPELESS;
            if (!(table ? mutateRecipe(d, r.result().getItem()) : mutateStation(d, r.result().getItem()))) return;
            d.recipeId = sanitize("fun_" + r.id().getNamespace() + "_" + r.id().getPath());
            if (!KubeJSExporter.validateRecipe(d).isEmpty()) return;
            planned.add(d);
            removals.add(r.id().toString());
        }, () -> {
            if (planned.isEmpty()) return;
            results.put(Domain.RECIPES, planned.size());
            stagers.add(() -> stageRecipes(planned, removals));
        }));
    }

    private boolean mutateRecipe(RecipeData d, Item result) {
        boolean shaped = d.type == RecipeData.RecipeType.VANILLA_SHAPED;
        List<ItemStack> ings = shaped ? d.ingredients : d.shapelessIngredients;
        double t = scoreOf(result);
        List<Item> distinct = new ArrayList<>();
        for (ItemStack s : ings) {
            if (s.isEmpty() || TagIngredient.tagOf(s) != null || distinct.contains(s.getItem())) continue;
            distinct.add(s.getItem());
        }
        boolean canAdd = shaped ? ings.stream().anyMatch(ItemStack::isEmpty) : ings.size() < 9;
        Set<Item> avoid = new HashSet<>(distinct);
        avoid.add(result);
        if (!distinct.isEmpty() && (!canAdd || rnd.nextDouble() < 0.6) && swapIngredient(ings, result)) return true;
        if (!canAdd) return false;
        Item extra = pick(0, Math.max(0, t - 1), avoid);
        if (extra == null) return false;
        if (shaped) {
            List<Integer> empty = new ArrayList<>();
            for (int i = 0; i < ings.size(); i++) if (ings.get(i).isEmpty()) empty.add(i);
            ings.set(empty.get(rnd.nextInt(empty.size())), new ItemStack(extra));
        } else {
            ings.add(new ItemStack(extra));
        }
        return true;
    }

    /**
     * Cambia un ingrediente por otro de dificultad parecida (y menor que el resultado) en todos los
     * lugares donde aparece. Mantiene la cantidad de cada slot.
     */
    private boolean swapIngredient(List<ItemStack> ings, Item result) {
        List<Item> distinct = new ArrayList<>();
        for (ItemStack s : ings) {
            if (s.isEmpty() || TagIngredient.tagOf(s) != null || distinct.contains(s.getItem())) continue;
            distinct.add(s.getItem());
        }
        if (distinct.isEmpty()) return false;
        Set<Item> avoid = new HashSet<>(distinct);
        avoid.add(result);
        Item x = distinct.get(rnd.nextInt(distinct.size()));
        double sx = scoreOf(x);
        Item repl = pick(Math.max(0, sx - 1), Math.min(sx + 1, scoreOf(result) - 0.5), avoid);
        if (repl == null) return false;
        for (int i = 0; i < ings.size(); i++) {
            ItemStack s = ings.get(i);
            if (!s.isEmpty() && s.getItem() == x && TagIngredient.tagOf(s) == null) ings.set(i, new ItemStack(repl, s.getCount()));
        }
        return true;
    }

    /**
     * Recetas de estaciones (hornos, maquinas de mods, tipos JSON conocidos): solo se cambian
     * ingredientes, nunca se agregan, porque cada estacion tiene sus slots fijos.
     */
    private boolean mutateStation(RecipeData d, Item result) {
        if (d.type != RecipeData.RecipeType.CUSTOM_JSON || d.customTypeId == null || d.customTypeId.isBlank()) return false;
        var desc = RecipeTypeDescriptorRegistry.find(d.customTypeId).orElse(null);
        if (desc == null || desc.isUnsupported() || desc.isRitualView() || desc.isNodeGridView()) return false;
        if (RecipeGridRules.shouldRouteToMachineEditor(d)) {
            RecipeGridRules.migrateForMachineEditor(d);
            d.type = RecipeData.RecipeType.CUSTOM_MACHINE;
            List<RecipeData.MachineSlot> items = new ArrayList<>();
            List<ItemStack> view = new ArrayList<>();
            for (RecipeData.MachineSlot s : d.machineInputs) {
                if (s.type != RecipeData.MachineSlotType.ITEM || s.item.isEmpty()) continue;
                items.add(s);
                view.add(s.item);
            }
            if (!swapIngredient(view, result)) return false;
            for (int i = 0; i < items.size(); i++) items.get(i).item = view.get(i);
            return true;
        }
        if (KubeJSExporter.isPositionalCustomType(d.customTypeId)) return swapIngredient(d.ingredients, result);
        return swapIngredient(d.shapelessIngredients, result);
    }

    private void stageRecipes(List<RecipeData> planned, List<String> removals) {
        Map<String, RecipeData> previous = new HashMap<>();
        for (RecipeData d : planned) {
            for (RecipeData a : KubeJSExporter.getAddedRecipes()) if (a.recipeId.equals(d.recipeId)) previous.put(d.recipeId, a);
            KubeJSExporter.stageRecipe(d);
        }
        for (String r : removals) KubeJSExporter.stageRemoveVanilla(r);
        String summary = summary(planned.stream().map(d -> d.recipeId).toList());
        PendingChangeManager.queue(PendingChange.of(PendingDomain.RECIPES, "fun:recipes", PendingChange.Action.EDIT, false,
                        () -> { KubeJSExporter.commitAdded(); KubeJSExporter.commitRemoved(); },
                        () -> {
                            for (RecipeData d : planned) KubeJSExporter.discardStagedRecipe(d.recipeId, previous.get(d.recipeId));
                            for (String r : removals) KubeJSExporter.discardStagedRemoveVanilla(r);
                        })
                .withDiff(null, () -> summary));
    }

    private static String summary(List<String> ids) {
        StringBuilder sb = new StringBuilder("// Shovel JS - Fun (" + ids.size() + ")\n");
        for (String id : ids) sb.append("// ").append(id).append('\n');
        return sb.toString();
    }

    // ---- atributos de items ----

    private record ItemStat(Item item, String group, double damage, double armor, double toughness) {}

    private void planAttributes() {
        List<Item> items = new ArrayList<>();
        for (Item it : BuiltInRegistries.ITEM) {
            if (it == Items.AIR || !allowed(BuiltInRegistries.ITEM.getKey(it))) continue;
            items.add(it);
        }
        List<ItemStat> stats = new ArrayList<>();
        phases.add(new Phase("gui.kve.fun.step.attributes", items.size(), k -> {
            Item it = items.get(k);
            double dmg = FunCompat.itemAttribute(it, "minecraft:generic.attack_damage");
            double arm = FunCompat.itemAttribute(it, "minecraft:generic.armor");
            double tough = FunCompat.itemAttribute(it, "minecraft:generic.armor_toughness");
            if (!Double.isNaN(dmg) && dmg > 0 && !FunCompat.isArmorSlot(it)) {
                stats.add(new ItemStat(it, weaponGroup(it), dmg, Double.NaN, Double.NaN));
            } else if (!Double.isNaN(arm) && arm > 0 && FunCompat.isArmorSlot(it)) {
                stats.add(new ItemStat(it, "armor_" + FunCompat.slotName(it), Double.NaN, arm, Double.isNaN(tough) ? 0 : tough));
            }
        }, () -> finishAttributes(stats)));
    }

    private static String weaponGroup(Item it) {
        if (it instanceof SwordItem) return "sword";
        if (it instanceof AxeItem) return "axe";
        if (it instanceof PickaxeItem) return "pickaxe";
        if (it instanceof ShovelItem) return "shovel";
        if (it instanceof HoeItem) return "hoe";
        return "weapon";
    }

    private void finishAttributes(List<ItemStat> stats) {
        Map<String, List<ItemStat>> groups = new LinkedHashMap<>();
        for (ItemStat s : stats) groups.computeIfAbsent(s.group(), g -> new ArrayList<>()).add(s);
        Map<Item, KubeJSItemAttrExporter.ItemEntry> out = new LinkedHashMap<>();
        for (List<ItemStat> g : groups.values()) {
            Map<ItemStat, Double> diff = new HashMap<>(), orig = new HashMap<>(), vals = new HashMap<>(), tVals = new HashMap<>(), tOrig = new HashMap<>();
            boolean weapons = !Double.isNaN(g.get(0).damage());
            for (ItemStat s : g) {
                double base = weapons ? s.damage() : s.armor();
                diff.put(s, scoreOf(s.item()));
                orig.put(s, base);
                vals.put(s, base * jitter(0.5));
                tOrig.put(s, s.toughness());
                tVals.put(s, s.toughness() * jitter(0.5));
            }
            Map<ItemStat, Double> assigned = rankAssign(g, diff, orig, vals);
            Map<ItemStat, Double> tough = weapons ? Map.of() : rankAssign(g, diff, tOrig, tVals);
            for (ItemStat s : g) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(s.item());
                KubeJSItemAttrExporter.ItemEntry prev = KubeJSItemAttrExporter.getEntry(id.toString());
                KubeJSItemAttrExporter.ItemEntry e = prev != null ? prev.copy() : new KubeJSItemAttrExporter.ItemEntry();
                e.itemId = id.toString();
                if (weapons) {
                    e.attackDamage = Math.max(0.5, Math.round(assigned.get(s) * 2) / 2.0);
                    e.attackDamageOp = KubeJSItemAttrExporter.Op.ADDITION;
                    if (rnd.nextDouble() < intensity * 0.4) addHitEffect(e, scoreOf(s.item()));
                    addWeaponExtras(e, s);
                } else {
                    e.armor = Math.max(1, Math.round(assigned.get(s)));
                    e.armorOp = KubeJSItemAttrExporter.Op.ADDITION;
                    if (s.toughness() > 0) {
                        e.armorToughness = Math.max(0, Math.round(tough.get(s) * 2) / 2.0);
                        e.armorToughnessOp = KubeJSItemAttrExporter.Op.ADDITION;
                    }
                    if (rnd.nextDouble() < intensity * 0.3) addPassiveEffect(e, armorEffects(s.group()), scoreOf(s.item()));
                }
                out.put(s.item(), e);
            }
        }
        if (out.isEmpty()) return;
        results.put(Domain.ATTRIBUTES, out.size());
        List<KubeJSItemAttrExporter.ItemEntry> entries = new ArrayList<>(out.values());
        stagers.add(() -> {
            Map<String, KubeJSItemAttrExporter.ItemEntry> previous = new HashMap<>();
            for (KubeJSItemAttrExporter.ItemEntry e : entries) {
                previous.put(e.itemId, KubeJSItemAttrExporter.getEntry(e.itemId));
                KubeJSItemAttrExporter.stageItem(e);
            }
            PendingChangeManager.queue(PendingChange.of(PendingDomain.CONTENT, "fun:attributes", PendingChange.Action.EDIT, true,
                            KubeJSItemAttrExporter::commitItems,
                            () -> { for (var e : entries) KubeJSItemAttrExporter.discardStagedItem(e.itemId, previous.get(e.itemId)); })
                    .withDiff(null, () -> summary(entries.stream().map(e -> e.itemId).toList())));
        });
    }

    private static String[] armorEffects(String group) {
        return switch (group) {
            case "armor_head" -> new String[]{"minecraft:night_vision", "minecraft:water_breathing"};
            case "armor_chest" -> new String[]{"minecraft:resistance", "minecraft:fire_resistance"};
            case "armor_legs" -> new String[]{"minecraft:speed", "minecraft:haste"};
            default -> new String[]{"minecraft:jump_boost", "minecraft:slow_falling", "minecraft:speed"};
        };
    }

    /** Herramientas: prisa al sostenerlas; armas fuertes: a veces prenden fuego o roban vida. */
    private void addWeaponExtras(KubeJSItemAttrExporter.ItemEntry e, ItemStat s) {
        double tier = scoreOf(s.item());
        boolean tool = !s.group().equals("sword") && !s.group().equals("weapon");
        if (tool && rnd.nextDouble() < intensity * 0.2) addPassiveEffect(e, new String[]{"minecraft:haste"}, tier);
        if (tool) return;
        if (e.combat == null) e.combat = new KubeJSItemAttrExporter.CombatProps();
        if (e.combat.igniteSeconds == 0 && rnd.nextDouble() < intensity * 0.12) e.combat.igniteSeconds = 2 + rnd.nextInt(3);
        if (e.combat.lifestealPct == 0 && tier >= 4 && rnd.nextDouble() < intensity * 0.1) e.combat.lifestealPct = 5 + rnd.nextInt(11);
        if (e.combat.isEmpty()) e.combat = null;
    }

    private void addPassiveEffect(KubeJSItemAttrExporter.ItemEntry e, String[] options, double tier) {
        if (e.combat == null) e.combat = new KubeJSItemAttrExporter.CombatProps();
        String fx = options[rnd.nextInt(options.length)];
        for (var existing : e.combat.passiveEffects) if (fx.equals(existing.effectId)) return;
        int amp = tier >= 6 && intensity >= 0.9 ? 1 : 0;
        e.combat.passiveEffects.add(new KubeJSItemAttrExporter.FoodEffect(fx, 220, amp, 1.0f));
    }

    private void addHitEffect(KubeJSItemAttrExporter.ItemEntry e, double tier) {
        if (e.combat == null) e.combat = new KubeJSItemAttrExporter.CombatProps();
        String fx = HIT_EFFECTS[rnd.nextInt(HIT_EFFECTS.length)];
        for (var existing : e.combat.hitEffects) if (fx.equals(existing.effectId)) return;
        int duration = 40 + (int) Math.round(tier * 20);
        int amp = tier >= 5 ? 1 : 0;
        float chance = (float) (0.2 + 0.2 * intensity);
        e.combat.hitEffects.add(new KubeJSItemAttrExporter.FoodEffect(fx, duration, amp, chance));
    }

    // ---- mobs ----

    /** {@code gear}: 0 no usa equipo, 1 solo armadura (ya trae su arma, ej. arco), 2 armadura y arma. */
    private record MobStat(EntityType<?> type, String group, boolean boss, double health, double damage, double speed, int gear) {}

    private List<EntityType<?>> livingTypes() {
        List<EntityType<?>> out = new ArrayList<>();
        for (EntityType<?> t : BuiltInRegistries.ENTITY_TYPE) {
            if (t.getCategory() == MobCategory.MISC) continue;
            if (!allowed(BuiltInRegistries.ENTITY_TYPE.getKey(t))) continue;
            out.add(t);
        }
        return out;
    }

    private boolean isBoss(EntityType<?> t, double health) {
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(t).toString();
        return KNOWN_BOSSES.contains(id) || FunCompat.isBossTagged(t) || health >= 150;
    }

    private void planMobs() {
        List<EntityType<?>> types = livingTypes();
        List<MobStat> stats = new ArrayList<>();
        phases.add(new Phase("gui.kve.fun.step.mobs", types.size(), k -> {
            EntityType<?> t = types.get(k);
            double h = FunCompat.mobBase(t, "health");
            if (Double.isNaN(h) || h <= 0) return;
            boolean boss = isBoss(t, h);
            int gear = !boss && t.getCategory() == MobCategory.MONSTER ? FunCompat.gearKind(t) : 0;
            stats.add(new MobStat(t, boss ? "boss" : t.getCategory().getName(), boss, h,
                    FunCompat.mobBase(t, "damage"), FunCompat.mobBase(t, "speed"), gear));
        }, () -> finishMobs(stats)));
    }

    private void finishMobs(List<MobStat> stats) {
        Map<MobStat, Double> health = new HashMap<>(), damage = new HashMap<>();
        double minBossHealth = INF, minBossDamage = INF;
        for (MobStat s : stats) {
            if (!s.boss()) continue;
            double h = s.health() * (1 + rnd.nextDouble() * intensity * 0.5);
            health.put(s, h);
            minBossHealth = Math.min(minBossHealth, h);
            if (!Double.isNaN(s.damage()) && s.damage() > 0) {
                double d = s.damage() * (1 + rnd.nextDouble() * intensity * 0.4);
                damage.put(s, d);
                minBossDamage = Math.min(minBossDamage, d);
            }
        }
        Map<String, List<MobStat>> groups = new LinkedHashMap<>();
        for (MobStat s : stats) if (!s.boss()) groups.computeIfAbsent(s.group(), g -> new ArrayList<>()).add(s);
        for (List<MobStat> g : groups.values()) {
            Map<MobStat, Double> diff = new HashMap<>(), orig = new HashMap<>(), vals = new HashMap<>();
            for (MobStat s : g) { diff.put(s, s.health()); orig.put(s, s.health()); vals.put(s, s.health() * jitter(0.5)); }
            for (Map.Entry<MobStat, Double> e : rankAssign(g, diff, orig, vals).entrySet()) {
                double cap = Math.max(e.getKey().health(), minBossHealth * 0.85);
                health.put(e.getKey(), Math.min(e.getValue(), cap));
            }
            List<MobStat> fighters = g.stream().filter(s -> !Double.isNaN(s.damage()) && s.damage() > 0).toList();
            if (fighters.isEmpty()) continue;
            Map<MobStat, Double> dDiff = new HashMap<>(), dOrig = new HashMap<>(), dVals = new HashMap<>();
            for (MobStat s : fighters) { dDiff.put(s, s.health()); dOrig.put(s, s.damage()); dVals.put(s, s.damage() * jitter(0.4)); }
            for (Map.Entry<MobStat, Double> e : rankAssign(fighters, dDiff, dOrig, dVals).entrySet()) {
                double cap = Math.max(e.getKey().damage(), minBossDamage * 0.9);
                damage.put(e.getKey(), Math.min(e.getValue(), cap));
            }
        }
        Map<MobStat, Double> rank = new HashMap<>();
        for (List<MobStat> g : groups.values()) {
            List<MobStat> sorted = new ArrayList<>(g);
            sorted.sort(Comparator.comparingDouble(MobStat::health));
            for (int i = 0; i < sorted.size(); i++) rank.put(sorted.get(i), sorted.size() == 1 ? 0.5 : i / (double) (sorted.size() - 1));
        }
        List<KubeJSMobExporter.MobEntry> entries = new ArrayList<>();
        for (MobStat s : stats) {
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(s.type()).toString();
            KubeJSMobExporter.MobEntry prev = KubeJSMobExporter.getEntry(id);
            KubeJSMobExporter.MobEntry e = prev != null ? prev.copy() : new KubeJSMobExporter.MobEntry();
            e.entityId = id;
            e.health = Math.max(1, Math.round(health.getOrDefault(s, s.health())));
            if (damage.containsKey(s)) e.attackDamage = Math.max(0.5, Math.round(damage.get(s) * 2) / 2.0);
            if (!Double.isNaN(s.speed()) && s.speed() > 0) e.movementSpeed = Math.round(s.speed() * jitter(0.15) * 1000) / 1000.0;
            addMobExtras(e, s, rank.getOrDefault(s, 1.0), groups.getOrDefault(s.group(), List.of()));
            entries.add(e);
        }
        if (entries.isEmpty()) return;
        results.put(Domain.MOBS, entries.size());
        stagers.add(() -> {
            Map<String, KubeJSMobExporter.MobEntry> previous = new HashMap<>();
            for (KubeJSMobExporter.MobEntry e : entries) {
                previous.put(e.entityId, KubeJSMobExporter.getEntry(e.entityId));
                KubeJSMobExporter.stageMob(e);
            }
            PendingChangeManager.queue(PendingChange.of(PendingDomain.CONTENT, "fun:mobs", PendingChange.Action.EDIT, false,
                            KubeJSMobExporter::commitMobs,
                            () -> { for (var e : entries) KubeJSMobExporter.discardStagedMob(e.entityId, previous.get(e.entityId)); })
                    .withDiff(null, () -> summary(entries.stream().map(e -> e.entityId).toList())));
        });
    }

    /** Efectos, equipo, aliados y comportamientos; los mas fuertes de cada grupo reciben lo mejor. */
    private void addMobExtras(KubeJSMobExporter.MobEntry e, MobStat s, double rank, List<MobStat> group) {
        if (s.boss()) {
            if (rnd.nextDouble() < intensity * 0.3) addMobEffect(e, new String[]{"minecraft:resistance", "minecraft:regeneration"}, 0);
            return;
        }
        if (s.type().getCategory() == MobCategory.MONSTER) {
            if (rnd.nextDouble() < intensity * 0.35) addAttackEffect(e, rank);
            if (rnd.nextDouble() < intensity * 0.3) addMobEffect(e, MOB_BUFFS, rank >= 0.8 && intensity >= 0.9 ? 1 : 0);
            if (s.gear() > 0 && rnd.nextDouble() < 0.15 + intensity * 0.5) addGear(e, s.gear() == 2, rank);
            else if (rnd.nextDouble() < intensity * 0.3) {
                double base = Double.isNaN(e.armor) ? Math.max(0, FunCompat.mobBase(s.type(), "armor")) : e.armor;
                e.armor = Math.max(base, Math.round(2 + rank * 10 * intensity));
            }
            if (rnd.nextDouble() < intensity * 0.25) addAllies(e, s, group);
            if (rnd.nextDouble() < intensity * 0.2) e.behaviorFlags.add(TOUGH_FLAGS[rnd.nextInt(TOUGH_FLAGS.length)]);
        } else {
            if (rnd.nextDouble() < intensity * 0.15) addMobEffect(e, CREATURE_BUFFS, 0);
            boolean noAttitude = e.behaviorFlags.stream().noneMatch(KubeJSMobExporter.EXCLUSIVE_ATTITUDE_FLAGS::contains)
                    && !e.behaviorFlags.contains("FLEES_PLAYER");
            if (s.type().getCategory() == MobCategory.CREATURE && noAttitude && rnd.nextDouble() < intensity * 0.15) {
                e.behaviorFlags.add(rnd.nextBoolean() ? "NEUTRAL" : "FLEES_PLAYER");
            }
            if (rnd.nextDouble() < intensity * 0.1) e.behaviorFlags.add(TOUGH_FLAGS[rnd.nextInt(TOUGH_FLAGS.length)]);
        }
    }

    private void addAttackEffect(KubeJSMobExporter.MobEntry e, double rank) {
        String fx = HIT_EFFECTS[rnd.nextInt(HIT_EFFECTS.length)];
        for (var existing : e.attackEffects) if (fx.equals(existing.effectId)) return;
        int duration = 60 + (int) Math.round(rank * 100);
        e.attackEffects.add(new KubeJSItemAttrExporter.FoodEffect(fx, duration, rank >= 0.85 ? 1 : 0, (float) (0.25 + 0.25 * intensity)));
    }

    private void addMobEffect(KubeJSMobExporter.MobEntry e, String[] options, int amp) {
        String fx = options[rnd.nextInt(options.length)];
        for (var existing : e.mobEffects) if (fx.equals(existing.effectId)) return;
        e.mobEffects.add(new KubeJSItemAttrExporter.FoodEffect(fx, SPAWN_EFFECT_TICKS, amp, (float) (0.5 + 0.4 * intensity)));
    }

    /** Armadura (y arma) del material que toca por ranking; la intensidad sube el techo del material. */
    private void addGear(KubeJSMobExporter.MobEntry e, boolean weapon, double rank) {
        int maxTier = intensity >= 0.9 ? ARMOR_TIERS.length - 1 : intensity >= 0.5 ? 4 : 3;
        int tier = Math.min(maxTier, (int) Math.floor(rank * (maxTier + 1) * (0.7 + rnd.nextDouble() * 0.5)));
        double spawn = Math.round((0.3 + 0.4 * intensity) * 100) / 100.0;
        for (int i = 0; i < ARMOR_SLOTS.length; i++) {
            if (hasSlot(e, ARMOR_SLOTS[i]) || rnd.nextDouble() > 0.4 + 0.4 * intensity) continue;
            String id = "minecraft:" + ARMOR_TIERS[tier] + "_" + ARMOR_PIECES[i];
            if (BuiltInRegistries.ITEM.containsKey(ResourceLocation.tryParse(id))) {
                e.equipment.add(new KubeJSMobExporter.EquipmentItem(ARMOR_SLOTS[i], id, 0.05, "", spawn));
            }
        }
        if (weapon && !hasSlot(e, "MAINHAND") && rnd.nextDouble() < 0.6) {
            String id = "minecraft:" + SWORD_TIERS[tier] + "_sword";
            e.equipment.add(new KubeJSMobExporter.EquipmentItem("MAINHAND", id, 0.05, "", spawn));
        }
    }

    private static boolean hasSlot(KubeJSMobExporter.MobEntry e, String slot) {
        for (var eq : e.equipment) if (slot.equals(eq.slot)) return true;
        return false;
    }

    private void addAllies(KubeJSMobExporter.MobEntry e, MobStat self, List<MobStat> group) {
        List<MobStat> others = new ArrayList<>(group);
        others.remove(self);
        if (others.isEmpty()) return;
        Collections.shuffle(others, rnd);
        int n = 1 + (intensity >= 0.9 && others.size() > 1 ? 1 : 0);
        for (int i = 0; i < n; i++) {
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(others.get(i).type()).toString();
            if (!e.allies.contains(id) && !e.enemies.contains(id)) e.allies.add(id);
        }
    }

    // ---- estructuras ----

    private record SetSpread(String id, int spacing, int separation) {}

    /**
     * Cambia cada cuanto aparece cada set de estructuras (espaciado/separacion). El orden de
     * rareza se respeta: un set mas raro que otro sigue siendolo despues del cambio.
     */
    private void planStructures() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        var access = mc.level.registryAccess();
        List<String> ids = new ArrayList<>();
        for (String id : com.kve.common.structure.StructureFrequencyExporter.listAllStructureSetIds(access)) {
            if (allowed(ResourceLocation.tryParse(id))) ids.add(id);
        }
        List<SetSpread> sets = new ArrayList<>();
        phases.add(new Phase("gui.kve.fun.step.structures", ids.size(), k -> {
            int[] spread = com.kve.common.structure.StructureFrequencyExporter.liveSpread(access, ids.get(k));
            if (spread != null && spread[0] > 1) sets.add(new SetSpread(ids.get(k), spread[0], spread[1]));
        }, () -> finishStructures(sets, access)));
    }

    private void finishStructures(List<SetSpread> sets, net.minecraft.core.RegistryAccess access) {
        List<SetSpread> chosen = new ArrayList<>();
        for (SetSpread s : sets) if (rnd.nextDouble() < 0.3 + 0.6 * intensity) chosen.add(s);
        if (chosen.isEmpty()) return;
        Map<SetSpread, Double> diff = new HashMap<>(), orig = new HashMap<>(), vals = new HashMap<>();
        for (SetSpread s : chosen) {
            diff.put(s, (double) s.spacing());
            orig.put(s, (double) s.spacing());
            vals.put(s, s.spacing() * Math.pow(2, (rnd.nextDouble() * 2 - 1) * intensity));
        }
        List<com.kve.common.structure.StructureFrequencyExporter.StructureSetOverride> out = new ArrayList<>();
        for (Map.Entry<SetSpread, Double> e : rankAssign(chosen, diff, orig, vals).entrySet()) {
            SetSpread s = e.getKey();
            int spacing = (int) Math.max(2, Math.min(4096, Math.round(e.getValue())));
            int separation = (int) Math.max(1, Math.min(spacing - 1, Math.round(s.separation() * spacing / (double) s.spacing())));
            if (spacing == s.spacing() && separation == s.separation()) continue;
            var prev = com.kve.common.structure.StructureFrequencyExporter.getEntry(s.id());
            var ov = prev != null ? prev.copy() : new com.kve.common.structure.StructureFrequencyExporter.StructureSetOverride();
            ov.structureSetId = s.id();
            ov.spacingOverride = spacing;
            ov.separationOverride = separation;
            out.add(ov);
        }
        if (out.isEmpty()) return;
        results.put(Domain.STRUCTURES, out.size());
        stagers.add(() -> {
            Map<String, com.kve.common.structure.StructureFrequencyExporter.StructureSetOverride> previous = new HashMap<>();
            for (var ov : out) previous.put(ov.structureSetId, com.kve.common.structure.StructureFrequencyExporter.stageOverride(ov));
            PendingChangeManager.queue(PendingChange.of(PendingDomain.WORLD, "fun:structures", PendingChange.Action.EDIT, true,
                            () -> com.kve.common.structure.StructureFrequencyExporter.commitAll(access),
                            () -> { for (var ov : out) com.kve.common.structure.StructureFrequencyExporter.discardStagedOverride(ov.structureSetId, previous.get(ov.structureSetId)); })
                    .withDiff(null, () -> summary(out.stream().map(ov -> ov.structureSetId + " " + ov.spacingOverride + "/" + ov.separationOverride).toList())));
        });
    }

    // ---- loot ----

    private record LootTarget(LootTableExporter.TargetKind kind, String id, double tierCap, Item self) {}

    private void planLoot() {
        List<LootTarget> targets = new ArrayList<>();
        double pe = 0.35 + 0.5 * intensity;
        for (EntityType<?> t : livingTypes()) {
            double h = FunCompat.mobBase(t, "health");
            if (Double.isNaN(h) || rnd.nextDouble() >= pe) continue;
            double cap = isBoss(t, h) ? 99 : 1 + h / 12;
            targets.add(new LootTarget(LootTableExporter.TargetKind.ENTITY, BuiltInRegistries.ENTITY_TYPE.getKey(t).toString(), cap, null));
        }
        double pb = 0.05 + 0.15 * intensity;
        int blocks = 0;
        for (Block b : BuiltInRegistries.BLOCK) {
            Item self = b.asItem();
            if (self == Items.AIR || blocks >= 150) continue;
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
            if (!allowed(id) || rnd.nextDouble() >= pb) continue;
            targets.add(new LootTarget(LootTableExporter.TargetKind.BLOCK, id.toString(), -1, self));
            blocks++;
        }
        List<LootTableExporter.LootTableEntry> planned = new ArrayList<>();
        phases.add(new Phase("gui.kve.fun.step.loot", targets.size(), k -> {
            LootTarget t = targets.get(k);
            LootTableExporter.TriggerEvent ev = t.kind() == LootTableExporter.TargetKind.ENTITY
                    ? LootTableExporter.TriggerEvent.DEATH : LootTableExporter.TriggerEvent.BREAK;
            String key = t.kind().name() + "|" + ev.name() + "|" + t.id();
            LootTableExporter.LootTableEntry prev = LootTableExporter.getEntry(key);
            LootTableExporter.LootTableEntry e = prev != null ? prev.copy() : LootTableExporter.tryLoadCurrentLootTable(t.kind(), ev, t.id());
            if (e == null || e.pools.isEmpty()) return;
            double cap = t.self() != null ? scoreOf(t.self()) + 1 : t.tierCap();
            List<Item> related = relatedItems(t.id(), e, cap);
            if (related.isEmpty()) return;
            LootTableExporter.LootPoolData pool = new LootTableExporter.LootPoolData();
            pool.rolls = 1;
            pool.condition = LootTableExporter.Condition.RANDOM_CHANCE;
            pool.chance = Math.round((0.05 + 0.25 * intensity) * 100) / 100.0;
            int n = Math.min(related.size(), 1 + (rnd.nextDouble() < intensity ? 1 : 0));
            for (int i = 0; i < n; i++) {
                LootTableExporter.LootEntryData en = new LootTableExporter.LootEntryData();
                en.itemId = idOf(related.get(i));
                en.weight = 1;
                en.minCount = 1;
                en.maxCount = scoreOf(related.get(i)) < 1.5 ? 1 + rnd.nextInt(3) : 1;
                pool.entries.add(en);
            }
            e.pools.add(pool);
            planned.add(e);
        }, () -> {
            if (planned.isEmpty()) return;
            results.put(Domain.LOOT, planned.size());
            stagers.add(() -> {
                Map<String, LootTableExporter.LootTableEntry> previous = new HashMap<>();
                for (var e : planned) {
                    previous.put(e.key(), LootTableExporter.getEntry(e.key()));
                    LootTableExporter.stageLootTable(e);
                }
                PendingChangeManager.queue(PendingChange.of(PendingDomain.LOOT, "fun:loot", PendingChange.Action.EDIT, false,
                                LootTableExporter::commitLootTables,
                                () -> { for (var e : planned) LootTableExporter.discardStagedLootTable(e.key(), previous.get(e.key())); })
                        .withDiff(null, () -> summary(planned.stream().map(LootTableExporter.LootTableEntry::key).toList())));
            });
        }));
    }

    /** Items relacionados: hechos con sus drops, ingredientes de sus drops o con nombre parecido. */
    private List<Item> relatedItems(String targetId, LootTableExporter.LootTableEntry table, double cap) {
        Set<Item> drops = new HashSet<>();
        for (var p : table.pools) {
            for (var en : p.entries) {
                ResourceLocation rl = ResourceLocation.tryParse(en.itemId);
                if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) drops.add(BuiltInRegistries.ITEM.get(rl));
            }
        }
        Set<Item> cand = new HashSet<>();
        for (Item d : drops) {
            cand.addAll(usedIn.getOrDefault(d, Set.of()));
            cand.addAll(madeFrom.getOrDefault(d, Set.of()));
        }
        String path = targetId.substring(targetId.indexOf(':') + 1);
        List<String> tokens = new ArrayList<>();
        for (String tk : path.split("[_/]")) if (tk.length() >= 4) tokens.add(tk);
        if (!tokens.isEmpty()) {
            for (Item it : pool) {
                String p = BuiltInRegistries.ITEM.getKey(it).getPath();
                for (String tk : tokens) if (p.contains(tk)) { cand.add(it); break; }
            }
        }
        cand.removeAll(drops);
        List<Item> out = new ArrayList<>();
        Set<Item> poolSet = new HashSet<>(pool);
        for (Item it : cand) if (poolSet.contains(it) && scoreOf(it) <= cap) out.add(it);
        out.sort(Comparator.comparing(FunRandomizer::idOf));
        Collections.shuffle(out, rnd);
        return out;
    }

    // ---- comercios ----

    private record TradeSlot(String professionId, boolean wandering, int level) {}

    private void planTrades() {
        List<TradeSlot> slots = new ArrayList<>();
        for (ResourceLocation prof : BuiltInRegistries.VILLAGER_PROFESSION.keySet()) {
            String p = prof.toString();
            if (p.equals("minecraft:none") || p.equals("minecraft:nitwit") || !allowed(prof)) continue;
            for (int lvl = 1; lvl <= 5; lvl++) slots.add(new TradeSlot(p, false, lvl));
        }
        if (allowed(new ResourceLocation("minecraft", "wandering_trader"))) {
            slots.add(new TradeSlot("", true, 1));
            slots.add(new TradeSlot("", true, 2));
        }
        List<TradeExporter.TradeEntry> planned = new ArrayList<>();
        double p = 0.4 + 0.5 * intensity;
        phases.add(new Phase("gui.kve.fun.step.trades", slots.size(), k -> {
            TradeSlot s = slots.get(k);
            if (rnd.nextDouble() >= p) return;
            TradeExporter.TradeEntry e = new TradeExporter.TradeEntry();
            e.mode = TradeExporter.TradeEditMode.ADD_OFFERS;
            e.level = s.level();
            if (s.wandering()) {
                e.targetKind = TradeExporter.TradeTargetKind.WANDERING_TRADER;
                e.entityTypeId = "minecraft:wandering_trader";
                e.id = "fun_wandering_" + s.level();
            } else {
                e.targetKind = TradeExporter.TradeTargetKind.VILLAGER;
                e.professionId = s.professionId();
                e.id = sanitize("fun_" + s.professionId() + "_" + s.level());
            }
            double[] range = s.wandering() ? (s.level() == 1 ? new double[]{0, 2.5} : new double[]{3, 99}) : levelRange(s.level());
            int offers = s.wandering() && s.level() == 1 ? 3 : 1 + (rnd.nextDouble() < intensity ? 1 : 0);
            Set<Item> used = new HashSet<>();
            used.add(Items.EMERALD);
            for (int i = 0; i < offers; i++) {
                Item it = pick(range[0], range[1], used);
                if (it == null) break;
                used.add(it);
                e.offers.add(offer(it, s.wandering() ? 3 : s.level()));
            }
            if (e.offers.isEmpty() || !TradeExporter.validateTrade(e).isEmpty()) return;
            planned.add(e);
        }, () -> {
            if (planned.isEmpty()) return;
            results.put(Domain.TRADES, planned.size());
            stagers.add(() -> stageTrades(planned));
        }));
    }

    private static double[] levelRange(int level) {
        return switch (level) {
            case 1 -> new double[]{0, 1.5};
            case 2 -> new double[]{1, 2.5};
            case 3 -> new double[]{2, 3.5};
            case 4 -> new double[]{3, 5};
            default -> new double[]{4, 99};
        };
    }

    private TradeExporter.TradeOffer offer(Item item, int level) {
        double s = scoreOf(item);
        int price = (int) Math.max(1, Math.min(64, Math.round((2 + s * 1.5 * level) * (0.7 + rnd.nextDouble() * 0.6))));
        TradeExporter.TradeOffer o = new TradeExporter.TradeOffer();
        if (rnd.nextBoolean()) {
            o.inputAId = "minecraft:emerald";
            o.inputACount = price;
            o.outputId = idOf(item);
            o.outputCount = s < 1 ? 2 + rnd.nextInt(7) : 1;
        } else {
            o.inputAId = idOf(item);
            o.inputACount = s < 1 ? 8 + rnd.nextInt(13) : 1 + rnd.nextInt(4);
            o.outputId = "minecraft:emerald";
            o.outputCount = Math.max(1, price / 3);
        }
        o.maxUses = 12;
        o.villagerXp = switch (level) { case 1 -> 2; case 2 -> 5; case 3 -> 10; case 4 -> 15; default -> 30; };
        o.priceMultiplier = 0.05f;
        return o;
    }

    private void stageTrades(List<TradeExporter.TradeEntry> planned) {
        Map<String, TradeExporter.TradeEntry> previous = new HashMap<>();
        boolean restart = false;
        for (var e : planned) {
            previous.put(e.id, TradeExporter.getEntry(e.id));
            TradeExporter.stageTrade(e);
            restart |= TradeExporter.requiresRestartFor(e);
        }
        boolean live = !restart && Minecraft.getInstance().isLocalServer();
        PendingChangeManager.queue(PendingChange.of(PendingDomain.TRADES, "fun:trades", PendingChange.Action.EDIT, restart,
                        () -> {
                            try { TradeExporter.commitTrades(); }
                            catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
                        },
                        () -> { for (var e : planned) TradeExporter.discardStagedTrade(e.id, previous.get(e.id)); })
                .withDiff(null, () -> summary(planned.stream().map(e -> e.id).toList()))
                .withMode(restart ? ApplyMode.RESTART : live ? ApplyMode.LIVE : ApplyMode.RELOAD));
    }

    // ---- generacion de mundo ----

    private void planWorldgen() {
        List<ExistingOreScanner.ExistingOre> scanned = ExistingOreScanner.scan();
        List<ExistingOreScanner.ExistingOre> ores = new ArrayList<>();
        if (scanned != null) {
            for (var o : scanned) {
                ResourceLocation pid = ResourceLocation.tryParse(o.placedId());
                ResourceLocation bid = o.oreBlock() == null || o.oreBlock().isEmpty() ? null : ResourceLocation.tryParse(o.oreBlock());
                if (pid == null || !allowed(pid) || (bid != null && !allowed(bid))) continue;
                ores.add(o);
            }
        }
        List<OreOverride> overrides = new ArrayList<>();
        phases.add(new Phase("gui.kve.fun.step.worldgen", ores.size(), k -> {
            var o = ores.get(k);
            OreOverride prev = OreOverrideExporter.getEntry(o.placedId());
            OreOverride ov = prev != null ? prev.copy() : o.toOverride();
            if (ov.mode != OreOverride.Mode.MODIFY || ov.count == null || ov.veinSize == null || ov.count <= 0 || ov.veinSize <= 0) return;
            overrides.add(ov);
        }, () -> finishWorldgen(overrides)));
    }

    private static double abundance(int count, int vein, Integer rarity) {
        return count * (double) vein / (rarity == null || rarity <= 0 ? 1 : rarity);
    }

    private void finishWorldgen(List<OreOverride> overrides) {
        Map<OreOverride, Double> orig = new HashMap<>(), vals = new HashMap<>();
        Map<OreOverride, Integer> veins = new HashMap<>();
        for (OreOverride o : overrides) {
            double a = abundance(o.count, o.veinSize, o.rarity);
            orig.put(o, a);
            vals.put(o, a * jitter(0.6));
            veins.put(o, Math.max(1, Math.min(64, (int) Math.round(o.veinSize * jitter(0.4)))));
        }
        Map<OreOverride, Double> assigned = rankAssign(overrides, orig, orig, vals);
        List<OreOverride> planned = new ArrayList<>();
        for (OreOverride o : overrides) {
            int vein = veins.get(o);
            int rarity = o.rarity == null || o.rarity <= 0 ? 1 : o.rarity;
            int count = (int) Math.max(1, Math.min(64, Math.round(assigned.get(o) * rarity / vein)));
            o.veinSize = vein;
            o.count = count;
            if (o.validate().isEmpty()) planned.add(o);
        }
        if (planned.isEmpty()) return;
        results.put(Domain.WORLDGEN, planned.size());
        stagers.add(() -> {
            Map<String, OreOverride> previous = new HashMap<>();
            for (OreOverride o : planned) {
                previous.put(o.key(), OreOverrideExporter.getEntry(o.key()));
                OreOverrideExporter.stage(o);
            }
            PendingChangeManager.queue(PendingChange.of(PendingDomain.WORLDGEN, "fun:worldgen", PendingChange.Action.EDIT, true,
                            OreOverrideExporter::commit,
                            () -> { for (OreOverride o : planned) OreOverrideExporter.discardStaged(o.key(), previous.get(o.key())); })
                    .withDiff(null, () -> summary(planned.stream().map(OreOverride::key).toList())));
        });
    }
}
