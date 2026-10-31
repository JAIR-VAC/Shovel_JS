package com.kve.common.search;

import com.kve.common.event.EventEntry;
import com.kve.common.kubejs.EventExporter;
import com.kve.common.kubejs.KubeJSBlockAttrExporter;
import com.kve.common.kubejs.KubeJSExporter;
import com.kve.common.kubejs.KubeJSItemAttrExporter;
import com.kve.common.kubejs.KubeJSMobExporter;
import com.kve.common.kubejs.LootTableExporter;
import com.kve.common.kubejs.TagExporter;
import com.kve.common.kubejs.TradeExporter;
import com.kve.common.packrules.PackRules;
import com.kve.common.recipe.RecipeData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ProjectReferences {
    private ProjectReferences() {}

    public enum Domain { RECIPE, ITEM, BLOCK, MOB, TAG, LOOT, EVENT, TRADE, PACK_RULE, STRUCTURE }

    /** Registro contra el que se puede comprobar si el id existe. NAME = id propio de KVE (receta, tag, evento...), no se comprueba. */
    public enum Kind { ITEM, BLOCK, ENTITY, FLUID, EFFECT, ATTRIBUTE, SOUND, RECIPE_TYPE, PROFESSION, TAG, NAME, OTHER }

    /**
     * @param domain  editor duenio de la entrada
     * @param entryId id para volver a encontrar la entrada (recipeId, itemId, tag key...)
     * @param field   donde aparece dentro de la entrada ("ingredient", "equipment (mainhand)"...)
     * @param kind    tipo de id, para validarlo contra el registro correcto
     * @param id      el id referenciado, tal cual
     */
    public record Ref(Domain domain, String entryId, String field, Kind kind, String id) {
        /** Namespace del id ("create" en "create:cogwheel"); "" si no tiene. Ignora un '#' de tag. */
        public String namespace() {
            String s = id.startsWith("#") ? id.substring(1) : id;
            int i = s.indexOf(':');
            return i > 0 ? s.substring(0, i).toLowerCase(Locale.ROOT) : "minecraft";
        }
    }

    /** Todas las referencias de todos los dominios en memoria (estructuras aparte, ver {@link IdUsageIndex}). */
    public static List<Ref> collect() {
        List<Ref> out = new ArrayList<>();
        recipes(out);
        items(out);
        blocks(out);
        mobs(out);
        tags(out);
        loot(out);
        events(out);
        trades(out);
        packRules(out);
        return out;
    }

    private static void recipes(List<Ref> out) {
        for (RecipeData r : KubeJSExporter.getAddedRecipes()) {
            String e = r.recipeId;
            add(out, Domain.RECIPE, e, "recipe id", Kind.NAME, r.recipeId);
            stacks(out, Domain.RECIPE, e, "ingredient", r.ingredients);
            stacks(out, Domain.RECIPE, e, "ingredient", r.shapelessIngredients);
            stack(out, Domain.RECIPE, e, "output", r.output);
            stack(out, Domain.RECIPE, e, "secondary output", r.secondaryOutput);
            stack(out, Domain.RECIPE, e, "combination center", r.combinationCenter);
            stacks(out, Domain.RECIPE, e, "combination pedestal", r.combinationPedestals);
            add(out, Domain.RECIPE, e, "recipe type", Kind.RECIPE_TYPE, r.customTypeId);
            for (RecipeData.MachineSlot s : r.machineInputs) machineSlot(out, e, "machine input", s);
            for (RecipeData.MachineSlot s : r.machineOutputs) machineSlot(out, e, "machine output", s);
        }
    }

    private static void items(List<Ref> out) {
        for (KubeJSItemAttrExporter.ItemEntry e : KubeJSItemAttrExporter.getEntries()) {
            add(out, Domain.ITEM, e.itemId, "item id", Kind.ITEM, e.itemId);
            for (KubeJSItemAttrExporter.AttrMod m : e.attributes.values()) {
                add(out, Domain.ITEM, e.itemId, "attribute", Kind.ATTRIBUTE, m.attrId);
            }
            if (e.food != null) {
                for (KubeJSItemAttrExporter.FoodEffect fx : e.food.effects) add(out, Domain.ITEM, e.itemId, "food effect", Kind.EFFECT, fx.effectId);
            }
            if (e.combat != null) {
                for (KubeJSItemAttrExporter.FoodEffect fx : e.combat.hitEffects) add(out, Domain.ITEM, e.itemId, "hit effect", Kind.EFFECT, fx.effectId);
                for (KubeJSItemAttrExporter.FoodEffect fx : e.combat.passiveEffects) add(out, Domain.ITEM, e.itemId, "passive effect", Kind.EFFECT, fx.effectId);
            }
        }
    }

    private static void blocks(List<Ref> out) {
        for (KubeJSBlockAttrExporter.BlockEntry e : KubeJSBlockAttrExporter.getEntries()) {
            add(out, Domain.BLOCK, e.blockId, "block id", Kind.BLOCK, e.blockId);
        }
    }

    private static void mobs(List<Ref> out) {
        for (KubeJSMobExporter.MobEntry e : KubeJSMobExporter.getEntries()) {
            add(out, Domain.MOB, e.entityId, "entity id", Kind.ENTITY, e.entityId);
            for (KubeJSMobExporter.AttrMod m : e.attributeMods) add(out, Domain.MOB, e.entityId, "attribute", Kind.ATTRIBUTE, m.attrId);
            for (KubeJSMobExporter.EquipmentItem eq : e.equipment) {
                add(out, Domain.MOB, e.entityId, "equipment (" + safeSlot(eq.slot) + ")", Kind.ITEM, eq.itemId);
            }
            for (KubeJSMobExporter.EquipmentBuild build : e.equipmentBuilds) {
                for (KubeJSMobExporter.EquipmentItem eq : build.equipment) {
                    add(out, Domain.MOB, e.entityId, "equipment build '" + build.name + "' (" + safeSlot(eq.slot) + ")", Kind.ITEM, eq.itemId);
                }
            }
        }
    }

    private static void tags(List<Ref> out) {
        for (TagExporter.TagEntry e : TagExporter.getEntries()) {
            add(out, Domain.TAG, e.key(), "tag id", Kind.NAME, e.tagId);
            Kind member = switch (e.regType) {
                case ITEM -> Kind.ITEM;
                case BLOCK -> Kind.BLOCK;
                case ENTITY_TYPE -> Kind.ENTITY;
                case FLUID -> Kind.FLUID;
            };
            for (String id : e.added) add(out, Domain.TAG, e.key(), "added to " + e.tagId, member, id);
            for (String id : e.removed) add(out, Domain.TAG, e.key(), "removed from " + e.tagId, member, id);
        }
    }

    private static void loot(List<Ref> out) {
        for (LootTableExporter.LootTableEntry e : LootTableExporter.getEntries()) {
            Kind target = switch (e.kind) {
                case ENTITY -> Kind.ENTITY;
                case BLOCK -> Kind.BLOCK;
                case CUSTOM_TABLE -> Kind.OTHER;
            };
            add(out, Domain.LOOT, e.key(), "loot table target", target, e.targetId);
            for (LootTableExporter.LootPoolData pool : e.pools) {
                for (LootTableExporter.LootEntryData entry : pool.entries) add(out, Domain.LOOT, e.key(), "pool entry", Kind.ITEM, entry.itemId);
            }
        }
    }

    private static void events(List<Ref> out) {
        for (EventEntry e : EventExporter.getEntries()) {
            add(out, Domain.EVENT, e.id, "event id", Kind.NAME, e.id);
            if (e.trigger != null) {
                add(out, Domain.EVENT, e.id, "trigger entity", Kind.ENTITY, e.trigger.entityId);
                add(out, Domain.EVENT, e.id, "trigger biome", Kind.OTHER, e.trigger.biomeId);
            }
            for (EventEntry.Condition c : e.conditions) add(out, Domain.EVENT, e.id, "condition: dimension", Kind.OTHER, c.dimensionId);
            for (EventEntry.Action a : e.actions) {
                add(out, Domain.EVENT, e.id, "action: give item", Kind.ITEM, a.itemId);
                add(out, Domain.EVENT, e.id, "action: spawn entity", Kind.ENTITY, a.spawnEntityId);
                add(out, Domain.EVENT, e.id, "action: play sound", Kind.SOUND, a.soundId);
            }
        }
    }

    private static void trades(List<Ref> out) {
        for (TradeExporter.TradeEntry e : TradeExporter.getEntries()) {
            add(out, Domain.TRADE, e.id, "trade id", Kind.NAME, e.id);
            add(out, Domain.TRADE, e.id, "profession", Kind.PROFESSION, e.professionId);
            add(out, Domain.TRADE, e.id, "merchant", Kind.ENTITY, e.entityTypeId);
            for (TradeExporter.TradeOffer o : e.offers) {
                add(out, Domain.TRADE, e.id, "offer input A", o.inputAIsTag ? Kind.TAG : Kind.ITEM, tagged(o.inputAId, o.inputAIsTag));
                add(out, Domain.TRADE, e.id, "offer input B", o.inputBIsTag ? Kind.TAG : Kind.ITEM, tagged(o.inputBId, o.inputBIsTag));
                add(out, Domain.TRADE, e.id, "offer output", o.outputIsTag ? Kind.TAG : Kind.ITEM, tagged(o.outputId, o.outputIsTag));
            }
        }
    }

    private static void packRules(List<Ref> out) {
        PackRules r = PackRules.current();
        for (PackRules.Rename n : r.renames) {
            add(out, Domain.PACK_RULE, "rename:" + n.targetId, "rename", n.block ? Kind.BLOCK : Kind.ITEM, n.targetId);
        }
        for (PackRules.InfoPage p : r.infoPages) add(out, Domain.PACK_RULE, "info:" + p.itemId, "recipe viewer info", Kind.ITEM, p.itemId);
        for (PackRules.Compostable c : r.compostables) add(out, Domain.PACK_RULE, "compost:" + c.itemId, "compostable", Kind.ITEM, c.itemId);
        for (PackRules.SpawnBlock b : r.spawnBlocks) add(out, Domain.PACK_RULE, "spawn:" + b.entityId, "blocked spawn", Kind.ENTITY, b.entityId);
    }

    private static void add(List<Ref> out, Domain d, String entryId, String field, Kind kind, String id) {
        if (id == null || id.isBlank()) return;
        String clean = id.trim();
        Kind k = clean.startsWith("#") && kind != Kind.NAME ? Kind.TAG : kind;
        out.add(new Ref(d, entryId == null ? "" : entryId, field, k, clean));
    }

    private static String tagged(String id, boolean isTag) {
        if (id == null || id.isBlank()) return id;
        return isTag && !id.startsWith("#") ? "#" + id : id;
    }

    private static void machineSlot(List<Ref> out, String entryId, String field, RecipeData.MachineSlot s) {
        if (s == null) return;
        switch (s.type) {
            case ITEM -> stack(out, Domain.RECIPE, entryId, field, s.item);
            case FLUID -> add(out, Domain.RECIPE, entryId, field + " (fluid)", Kind.FLUID, s.fluidId);
            case TAG -> add(out, Domain.RECIPE, entryId, field + " (tag)", Kind.TAG, tagged(s.tagId, true));
        }
    }

    private static void stacks(List<Ref> out, Domain d, String entryId, String field, List<ItemStack> stacks) {
        if (stacks == null) return;
        for (ItemStack s : stacks) stack(out, d, entryId, field, s);
    }

    private static void stack(List<Ref> out, Domain d, String entryId, String field, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        String tag = com.kve.common.recipe.TagIngredient.tagOf(stack);
        if (tag != null) { add(out, d, entryId, field, Kind.TAG, "#" + tag); return; }
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key != null) add(out, d, entryId, field, Kind.ITEM, key.toString());
    }

    private static String safeSlot(String slot) {
        return slot == null || slot.isBlank() ? "?" : slot.toLowerCase(Locale.ROOT);
    }
}
