package com.kve.common.packtools;

import com.kve.common.kubejs.KubeJSExporter;
import com.kve.common.kubejs.KubeJSItemAttrExporter;
import com.kve.common.kubejs.KubeJSMobExporter;
import com.kve.common.recipe.RecipeData;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Revision de balance del pack. No opina de gustos: marca lo que
 * es objetivamente un problema o casi seguro un error de tipeo.
 * <ul>
 *   <li><b>Bucles de duplicacion</b>: A se convierte en B y B vuelve a A dejando
 *       mas de lo que habia (ej. una receta nueva "1 bloque -> 10 lingotes" contra
 *       la vanilla "9 lingotes -> 1 bloque"). Se miran las recetas del pack contra
 *       si mismas y contra las del juego que pase la pantalla.</li>
 *   <li><b>Valores que el juego recorta en silencio</b>: vida de mob &gt; 1024,
 *       dano &gt; 2048, armadura &gt; 30... el atributo vanilla tiene ese tope y el
 *       numero escrito nunca se aplica.</li>
 *   <li><b>Fuera de escala</b>: armas y armaduras muy por encima de netherite,
 *       salidas de receta de mas de 64.</li>
 * </ul>
 */
public final class BalanceCheck {

    private BalanceCheck() {}

    public enum Severity { ERROR, WARN }

    public record Finding(Severity severity, String area, String where, String key, List<String> args) {}

    /** Una conversion simple: {@code inCount()} de un solo item -> {@code outCount()} de otro. */
    public record Conversion(String input, int inCount, String output, int outCount, String source) {}

    static final double MAX_HEALTH = 1024, MAX_ATTACK = 2048, MAX_ARMOR = 30, MAX_TOUGHNESS = 20;
    static final double NETHERITE_SWORD = 7, NETHERITE_CHEST_ARMOR = 8, NETHERITE_TOUGHNESS = 3;

    public static List<Finding> run(List<Conversion> gameConversions) {
        List<Finding> out = new ArrayList<>();
        List<Conversion> pack = new ArrayList<>();
        for (RecipeData r : KubeJSExporter.getAddedRecipes()) {
            if (!r.enabled) continue;
            Conversion c = conversionOf(r);
            if (c != null) pack.add(c);
            checkOutputCount(r, out);
        }
        out.addAll(dupeLoops(pack, gameConversions));
        for (KubeJSMobExporter.MobEntry m : KubeJSMobExporter.getEntries()) if (m.enabled) checkMob(m, out);
        for (KubeJSItemAttrExporter.ItemEntry e : KubeJSItemAttrExporter.getEntries()) if (e.enabled) checkItem(e, out);
        out.sort(Comparator.comparing(Finding::severity));
        return out;
    }

    /**
     * Pares A->B (del pack) y B->A (del pack o del juego) cuya vuelta completa
     * deja mas A del que entro: (outB/inA) * (outA/inB) &gt; 1.
     */
    public static List<Finding> dupeLoops(List<Conversion> pack, List<Conversion> game) {
        List<Finding> out = new ArrayList<>();
        Map<String, List<Conversion>> byInput = new HashMap<>();
        for (Conversion c : pack) byInput.computeIfAbsent(c.input(), k -> new ArrayList<>()).add(c);
        for (Conversion c : game) byInput.computeIfAbsent(c.input(), k -> new ArrayList<>()).add(c);
        Map<String, Finding> unique = new LinkedHashMap<>();
        for (Conversion there : pack) {
            for (Conversion back : byInput.getOrDefault(there.output(), List.of())) {
                if (!back.output().equals(there.input()) || back == there) continue;
                long start = (long) there.inCount() * back.inCount();
                long end = (long) there.outCount() * back.outCount();
                if (end <= start) continue;
                String key = there.input().compareTo(there.output()) < 0 ? there.input() + "|" + there.output() : there.output() + "|" + there.input();
                unique.putIfAbsent(key, new Finding(Severity.ERROR, "dupe", there.source(), "gui.kve.balance.dupe",
                        List.of(there.inCount() + "x " + there.input(), there.outCount() + "x " + there.output(),
                                back.source(), String.valueOf(start), String.valueOf(end))));
            }
        }
        out.addAll(unique.values());
        return out;
    }

    static Conversion conversionOf(RecipeData r) {
        List<ItemStack> ins = new ArrayList<>();
        ItemStack out = r.output;
        switch (r.type) {
            case VANILLA_SHAPED -> { if (r.ingredients != null) ins.addAll(r.ingredients); }
            case VANILLA_SHAPELESS -> { if (r.shapelessIngredients != null) ins.addAll(r.shapelessIngredients); }
            case CUSTOM_MACHINE -> {
                if (r.machineInputs == null || r.machineOutputs == null || r.machineInputs.size() != 1 || r.machineOutputs.isEmpty()) return null;
                RecipeData.MachineSlot i = r.machineInputs.get(0), o = r.machineOutputs.get(0);
                if (i.type != RecipeData.MachineSlotType.ITEM || o.type != RecipeData.MachineSlotType.ITEM || o.chance < 0.999) return null;
                ins.add(i.item);
                out = o.item;
            }
            default -> { return null; }
        }
        if (out == null || out.isEmpty()) return null;
        String inId = null;
        int count = 0;
        for (ItemStack s : ins) {
            if (s == null || s.isEmpty()) continue;
            if (com.kve.common.recipe.TagIngredient.tagOf(s) != null) return null;
            String id = KubeJSExporter.itemId(s);
            if (inId == null) inId = id;
            else if (!inId.equals(id)) return null;
            count += s.getCount();
        }
        if (inId == null || count <= 0) return null;
        return new Conversion(inId, count, KubeJSExporter.itemId(out), out.getCount(), r.recipeId);
    }

    private static void checkOutputCount(RecipeData r, List<Finding> out) {
        int max = r.output != null && !r.output.isEmpty() ? r.output.getCount() : 0;
        if (r.machineOutputs != null) for (RecipeData.MachineSlot s : r.machineOutputs) {
            if (s.type == RecipeData.MachineSlotType.ITEM && !s.item.isEmpty()) max = Math.max(max, s.item.getCount());
        }
        if (max > 64) out.add(new Finding(Severity.ERROR, "recipe", r.recipeId, "gui.kve.balance.stack_over_64", List.of(String.valueOf(max))));
    }

    static void checkMob(KubeJSMobExporter.MobEntry m, List<Finding> out) {
        cap(m.health, MAX_HEALTH, "health", m.entityId, out);
        cap(m.attackDamage, MAX_ATTACK, "attack_damage", m.entityId, out);
        cap(m.armor, MAX_ARMOR, "armor", m.entityId, out);
        cap(m.armorToughness, MAX_TOUGHNESS, "armor_toughness", m.entityId, out);
        if (!Double.isNaN(m.health) && m.health <= 0) {
            out.add(new Finding(Severity.ERROR, "mob", m.entityId, "gui.kve.balance.zero_health", List.of(fmt(m.health))));
        }
        if (!Double.isNaN(m.knockbackResist) && m.knockbackResist > 1) {
            out.add(new Finding(Severity.WARN, "mob", m.entityId, "gui.kve.balance.capped", List.of("knockback_resistance", fmt(m.knockbackResist), "1")));
        }
    }

    private static void cap(double v, double max, String attr, String where, List<Finding> out) {
        if (!Double.isNaN(v) && v > max) {
            out.add(new Finding(Severity.ERROR, "mob", where, "gui.kve.balance.capped", List.of(attr, fmt(v), fmt(max))));
        }
    }

    static final int MAX_STACK = 99;

    static void checkItem(KubeJSItemAttrExporter.ItemEntry e, List<Finding> out) {
        if (e.maxStackSize > MAX_STACK) {
            out.add(new Finding(Severity.ERROR, "item", e.itemId, "gui.kve.balance.capped",
                    List.of("max_stack_size", String.valueOf(e.maxStackSize), String.valueOf(MAX_STACK))));
        }
        if (isAdd(e.attackDamage, e.attackDamageOp) && e.attackDamage > NETHERITE_SWORD * 2) {
            out.add(new Finding(Severity.WARN, "item", e.itemId, "gui.kve.balance.over_netherite",
                    List.of("attack_damage", fmt(e.attackDamage), fmt(NETHERITE_SWORD))));
        }
        if (isAdd(e.armor, e.armorOp) && e.armor > NETHERITE_CHEST_ARMOR * 1.5) {
            out.add(new Finding(Severity.WARN, "item", e.itemId, "gui.kve.balance.over_netherite",
                    List.of("armor", fmt(e.armor), fmt(NETHERITE_CHEST_ARMOR))));
        }
        if (isAdd(e.armorToughness, e.armorToughnessOp) && e.armorToughness > NETHERITE_TOUGHNESS * 2) {
            out.add(new Finding(Severity.WARN, "item", e.itemId, "gui.kve.balance.over_netherite",
                    List.of("armor_toughness", fmt(e.armorToughness), fmt(NETHERITE_TOUGHNESS))));
        }
        if (isAdd(e.movementSpeed, e.movementSpeedOp) && e.movementSpeed > 0.1) {
            out.add(new Finding(Severity.WARN, "item", e.itemId, "gui.kve.balance.fast",
                    List.of(fmt(e.movementSpeed))));
        }
    }

    private static boolean isAdd(double v, KubeJSItemAttrExporter.Op op) {
        return !Double.isNaN(v) && op == KubeJSItemAttrExporter.Op.ADDITION;
    }

    static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(Math.round(v * 100) / 100.0);
    }
}
