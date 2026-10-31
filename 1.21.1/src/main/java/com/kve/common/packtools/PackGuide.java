package com.kve.common.packtools;

import com.kve.common.customitem.CustomBlockDraft;
import com.kve.common.customitem.CustomBlockState;
import com.kve.common.customitem.CustomItemDraft;
import com.kve.common.customitem.CustomItemState;
import com.kve.common.kubejs.KubeJSExporter;
import com.kve.common.kubejs.KubeJSItemAttrExporter;
import com.kve.common.kubejs.KubeJSMobExporter;
import com.kve.common.kubejs.LootTableExporter;
import com.kve.common.kubejs.TagExporter;
import com.kve.common.kubejs.TradeExporter;
import com.kve.common.recipe.RecipeData;
import com.kve.common.recipe.TagIngredient;
import com.kve.common.worldgen.WorldGenEntry;
import com.kve.common.worldgen.WorldGenExporter;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class PackGuide {

    private PackGuide() {}

    /** Archivo junto a las notas de version, en la carpeta del juego. */
    public static Path file(Path gameDir) { return gameDir.resolve("shovel_js_guia_del_pack.md"); }

    public static String build(String packName) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(packName == null || packName.isBlank() ? "Guia del pack" : packName).append("\n\n");
        sb.append("_Generado por Shovel JS el ").append(LocalDate.now()).append("._\n\n");

        List<CustomItemDraft> items = new ArrayList<>(CustomItemState.getDrafts());
        items.sort(Comparator.comparing(CustomItemDraft::fullId));
        if (!items.isEmpty()) {
            sb.append("## Items nuevos (").append(items.size()).append(")\n\n| Id | Nombre | Stack |\n|---|---|---|\n");
            for (CustomItemDraft d : items) {
                sb.append("| `").append(d.fullId()).append("` | ").append(cell(d.displayName)).append(" | ").append(d.maxStackSize).append(" |\n");
            }
            sb.append('\n');
        }
        List<CustomBlockDraft> blocks = new ArrayList<>(CustomBlockState.getDrafts());
        blocks.sort(Comparator.comparing(CustomBlockDraft::fullId));
        if (!blocks.isEmpty()) {
            sb.append("## Bloques nuevos (").append(blocks.size()).append(")\n\n| Id | Nombre |\n|---|---|\n");
            for (CustomBlockDraft d : blocks) sb.append("| `").append(d.fullId()).append("` | ").append(cell(d.displayName)).append(" |\n");
            sb.append('\n');
        }

        Map<String, List<RecipeData>> byType = new TreeMap<>();
        for (RecipeData r : KubeJSExporter.getAddedRecipes()) {
            if (!r.enabled) continue;
            byType.computeIfAbsent(typeLabel(r), k -> new ArrayList<>()).add(r);
        }
        if (!byType.isEmpty()) {
            int total = byType.values().stream().mapToInt(List::size).sum();
            sb.append("## Recetas nuevas (").append(total).append(")\n\n");
            for (Map.Entry<String, List<RecipeData>> e : byType.entrySet()) {
                sb.append("### ").append(e.getKey()).append("\n\n");
                for (RecipeData r : e.getValue()) sb.append("- ").append(describe(r)).append('\n');
                sb.append('\n');
            }
        }
        List<String> removed = new ArrayList<>(KubeJSExporter.getRemovedRecipes());
        if (!removed.isEmpty()) {
            sb.append("## Recetas quitadas (").append(removed.size()).append(")\n\n");
            removed.stream().sorted().forEach(id -> sb.append("- `").append(id).append("`\n"));
            sb.append('\n');
        }

        List<KubeJSItemAttrExporter.ItemEntry> changedItems = new ArrayList<>();
        for (KubeJSItemAttrExporter.ItemEntry e : KubeJSItemAttrExporter.getEntries()) if (e.enabled) changedItems.add(e);
        if (!changedItems.isEmpty()) {
            changedItems.sort(Comparator.comparing(e -> e.itemId));
            sb.append("## Items modificados (").append(changedItems.size()).append(")\n\n");
            for (KubeJSItemAttrExporter.ItemEntry e : changedItems) sb.append("- `").append(e.itemId).append("`").append(itemSummary(e)).append('\n');
            sb.append('\n');
        }
        List<KubeJSMobExporter.MobEntry> mobs = new ArrayList<>();
        for (KubeJSMobExporter.MobEntry m : KubeJSMobExporter.getEntries()) if (m.enabled) mobs.add(m);
        if (!mobs.isEmpty()) {
            mobs.sort(Comparator.comparing(m -> m.entityId));
            sb.append("## Mobs modificados (").append(mobs.size()).append(")\n\n| Mob | Vida | Dano | Armadura | Velocidad |\n|---|---|---|---|---|\n");
            for (KubeJSMobExporter.MobEntry m : mobs) {
                sb.append("| `").append(m.entityId).append("` | ").append(num(m.health)).append(" | ").append(num(m.attackDamage))
                  .append(" | ").append(num(m.armor)).append(" | ").append(num(m.movementSpeed)).append(" |\n");
            }
            sb.append('\n');
        }

        List<LootTableExporter.LootTableEntry> loot = new ArrayList<>();
        for (LootTableExporter.LootTableEntry e : LootTableExporter.getEntries()) if (e.enabled) loot.add(e);
        if (!loot.isEmpty()) {
            sb.append("## Loot (").append(loot.size()).append(")\n\n");
            loot.stream().sorted(Comparator.comparing(e -> e.targetId))
                    .forEach(e -> sb.append("- `").append(e.targetId).append("` (").append(e.kind.name().toLowerCase())
                            .append(", ").append(e.pools.size()).append(" pools)\n"));
            sb.append('\n');
        }
        List<TradeExporter.TradeEntry> trades = new ArrayList<>();
        for (TradeExporter.TradeEntry e : TradeExporter.getEntries()) if (e.enabled) trades.add(e);
        if (!trades.isEmpty()) {
            sb.append("## Comercios (").append(trades.size()).append(")\n\n");
            for (TradeExporter.TradeEntry e : trades) {
                sb.append("- **").append(e.professionId.isBlank() ? e.id : e.professionId).append("**: ").append(e.offers.size()).append(" ofertas\n");
                for (TradeExporter.TradeOffer o : e.offers) {
                    sb.append("  - ").append(o.inputACount).append("x `").append(o.inputAId).append('`');
                    if (o.inputBId != null && !o.inputBId.isBlank()) sb.append(" + ").append(o.inputBCount).append("x `").append(o.inputBId).append('`');
                    sb.append(" -> `").append(o.outputId).append("`\n");
                }
            }
            sb.append('\n');
        }
        List<TagExporter.TagEntry> tags = new ArrayList<>();
        for (TagExporter.TagEntry t : TagExporter.getEntries()) if (t.enabled) tags.add(t);
        if (!tags.isEmpty()) {
            sb.append("## Tags (").append(tags.size()).append(")\n\n");
            tags.stream().sorted(Comparator.comparing(t -> t.tagId)).forEach(t -> {
                sb.append("- `#").append(t.tagId).append('`');
                if (!t.added.isEmpty()) sb.append(" +").append(t.added.size());
                if (!t.removed.isEmpty()) sb.append(" -").append(t.removed.size());
                if (t.replace) sb.append(" (reemplaza)");
                sb.append('\n');
            });
            sb.append('\n');
        }
        List<WorldGenEntry> wg = new ArrayList<>();
        for (WorldGenEntry e : WorldGenExporter.getEntries()) if (e.enabled) wg.add(e);
        if (!wg.isEmpty()) {
            sb.append("## Generacion de mundo (").append(wg.size()).append(")\n\n");
            for (WorldGenEntry e : wg) {
                sb.append("- **").append(e.displayName.isBlank() ? e.id : e.displayName).append("** en ").append(String.join(", ", e.biomeIds)).append('\n');
                for (WorldGenEntry.OreVein v : e.oreVeins) {
                    sb.append("  - Veta de `").append(v.oreBlock).append("`, Y ").append(v.minY).append("..").append(v.maxY)
                      .append(v.rarity > 0 ? ", una cada " + v.rarity + " chunks" : ", " + v.veinsPerChunk + " por chunk").append('\n');
                }
                for (String f : e.removedFeatures) sb.append("  - Quita `").append(f).append("`\n");
                for (WorldGenEntry.SpawnRule r : e.spawns) {
                    sb.append("  - ").append(r.remove ? "Ya no aparece `" : "Aparece `").append(r.entityId).append('`')
                      .append(r.remove ? "" : " (peso " + r.weight + ", grupos de " + r.minCount + "-" + r.maxCount + ")").append('\n');
                }
                for (WorldGenEntry.Patch p : e.patches) sb.append("  - Parches de `").append(p.block).append("`\n");
            }
            sb.append('\n');
        }
        if (sb.indexOf("## ") < 0) sb.append("_Este pack todavia no tiene contenido hecho con Shovel JS._\n");
        return sb.toString();
    }

    static String typeLabel(RecipeData r) {
        return switch (r.type) {
            case VANILLA_SHAPED -> "Crafteo con forma";
            case VANILLA_SHAPELESS -> "Crafteo sin forma";
            case CUSTOM_JSON, CUSTOM_MACHINE -> r.customTypeId == null || r.customTypeId.isBlank() ? "Otros" : r.customTypeId;
            default -> r.type.name().toLowerCase().replace('_', ' ');
        };
    }

    static String describe(RecipeData r) {
        Map<String, Integer> in = new LinkedHashMap<>();
        List<ItemStack> stacks = new ArrayList<>();
        if (r.ingredients != null) stacks.addAll(r.ingredients);
        if (r.shapelessIngredients != null) stacks.addAll(r.shapelessIngredients);
        for (ItemStack s : stacks) {
            if (s == null || s.isEmpty()) continue;
            String tag = TagIngredient.tagOf(s);
            in.merge(tag != null ? "#" + tag : KubeJSExporter.itemId(s), s.getCount(), Integer::sum);
        }
        List<String> outs = new ArrayList<>();
        if (r.output != null && !r.output.isEmpty()) outs.add(r.output.getCount() + "x `" + KubeJSExporter.itemId(r.output) + "`");
        if (r.machineInputs != null) for (RecipeData.MachineSlot s : r.machineInputs) addSlot(in, s);
        if (r.machineOutputs != null) for (RecipeData.MachineSlot s : r.machineOutputs) {
            Map<String, Integer> one = new LinkedHashMap<>();
            addSlot(one, s);
            one.forEach((k, v) -> outs.add(v + (s.type == RecipeData.MachineSlotType.FLUID ? " mB `" : "x `") + k + "`"
                    + (s.chance < 0.999 ? " (" + Math.round(s.chance * 100) + "%)" : "")));
        }
        List<String> ins = new ArrayList<>();
        in.forEach((k, v) -> ins.add(v + "x `" + k + "`"));
        return (outs.isEmpty() ? "_(sin salida)_" : String.join(", ", outs)) + " <- " + (ins.isEmpty() ? "_(sin ingredientes)_" : String.join(", ", ins));
    }

    private static void addSlot(Map<String, Integer> into, RecipeData.MachineSlot s) {
        switch (s.type) {
            case ITEM -> { if (!s.item.isEmpty()) into.merge(KubeJSExporter.itemId(s.item), s.item.getCount(), Integer::sum); }
            case TAG -> { if (!s.tagId.isBlank()) into.merge(s.tagId.startsWith("#") ? s.tagId : "#" + s.tagId, s.tagCount, Integer::sum); }
            case FLUID -> { if (!s.fluidId.isBlank()) into.merge(s.fluidId, s.fluidAmount, Integer::sum); }
        }
    }

    private static String itemSummary(KubeJSItemAttrExporter.ItemEntry e) {
        List<String> parts = new ArrayList<>();
        if (!Double.isNaN(e.attackDamage)) parts.add("dano " + num(e.attackDamage));
        if (!Double.isNaN(e.armor)) parts.add("armadura " + num(e.armor));
        if (e.maxStackSize > 0) parts.add("stack " + e.maxStackSize);
        if (e.maxDamage > 0) parts.add("durabilidad " + e.maxDamage);
        if (e.tooltip != null && !e.tooltip.isEmpty()) parts.add("tooltip");
        return parts.isEmpty() ? "" : ": " + String.join(", ", parts);
    }

    private static String num(double v) {
        return Double.isNaN(v) ? "-" : BalanceCheck.fmt(v);
    }

    private static String cell(String s) {
        return s == null || s.isBlank() ? "-" : s.replace("|", "\\|");
    }
}
