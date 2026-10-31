package com.kve.common.lootrules;

import com.kve.CraftEditorMod;
import com.kve.common.kubejs.LootTableExporter;
import com.kve.common.kubejs.LootTableExporter.LootEntryData;
import com.kve.common.kubejs.LootTableExporter.LootTableEntry;
import com.kve.common.kubejs.LootTableExporter.TargetKind;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.HashSet;
import java.util.Set;

public final class InteractLootEnforcer {

    private InteractLootEnforcer() {}

    /** One-shot warning per bad item id per server run, so a typo'd itemId logs once instead of spamming on every interaction. */
    private static final Set<String> WARNED_BAD_ITEM_IDS = new HashSet<>();

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (event.getLevel().isClientSide()) return;

        Entity target = event.getTarget();
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        if (typeId == null) return;

        handle(TargetKind.ENTITY, typeId.toString(), event.getEntity());
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (event.getLevel().isClientSide()) return;

        BlockState state = event.getLevel().getBlockState(event.getPos());
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (blockId == null) return;

        handle(TargetKind.BLOCK, blockId.toString(), event.getEntity());
    }

    private static void handle(TargetKind kind, String targetId, Player player) {
        LootTableEntry entry = LootTableExporter.interactEntryFor(kind, targetId);
        if (entry == null) return;

        int looting = player.level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getHolder(Enchantments.LOOTING)
                .map(h -> EnchantmentHelper.getItemEnchantmentLevel(h, player.getMainHandItem()))
                .orElse(0);
        LootTableExporter.rollInteractLoot(entry, looting, picked -> give(player, picked));
    }

    private static void give(Player player, LootEntryData picked) {
        ResourceLocation itemRl = ResourceLocation.tryParse(picked.itemId);
        Item item = itemRl == null ? null : BuiltInRegistries.ITEM.getOptional(itemRl).orElse(null);
        if (item == null) {
            if (WARNED_BAD_ITEM_IDS.add(picked.itemId)) {
                CraftEditorMod.LOGGER.warn("[Shovel JS] interact loot: item id desconocido '{}', se omite", picked.itemId);
            }
            return;
        }

        int min = Math.min(picked.minCount, picked.maxCount);
        int max = Math.max(picked.minCount, picked.maxCount);
        int count = min == max ? Math.max(1, min) : min + player.getRandom().nextInt(max - min + 1);

        ItemStack stack = new ItemStack(item, count);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
