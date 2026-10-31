package com.kve.common.lootrules;

import com.kve.CraftEditorMod;
import com.kve.common.itemrules.ItemIds;
import com.kve.common.kubejs.KubeJSItemAttrExporter;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class HideFromLootModifier extends LootModifier {

    public static final MapCodec<HideFromLootModifier> CODEC =
            RecordCodecBuilder.mapCodec(inst -> codecStart(inst).apply(inst, HideFromLootModifier::new));

    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, CraftEditorMod.MODID);

    static {
        SERIALIZERS.register("hide_from_loot", () -> CODEC);
    }

    public HideFromLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        if (loot.isEmpty() || !KubeJSItemAttrExporter.hasAppliedEntries()) return loot;
        loot.removeIf(HideFromLootModifier::hidden);
        return loot;
    }

    private static boolean hidden(ItemStack stack) {
        if (stack.isEmpty()) return false;
        KubeJSItemAttrExporter.ItemEntry e = KubeJSItemAttrExporter.getAppliedEntry(ItemIds.idOf(stack.getItem()));
        return e != null && e.enabled && e.hideFromLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
