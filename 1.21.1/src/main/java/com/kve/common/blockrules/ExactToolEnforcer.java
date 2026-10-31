package com.kve.common.blockrules;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.Set;

public final class ExactToolEnforcer {

    private ExactToolEnforcer() {}

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (player != null && player.isCreative()) return;
        Set<String> requiredItems = requiredItemsFor(event.getState());
        if (requiredItems.isEmpty()) return;
        if (!heldItemMatches(player, requiredItems)) {
            event.setNewSpeed(0.01f);
        }
    }

    @SubscribeEvent
    public static void onBreakEvent(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player != null && player.isCreative()) return;
        Set<String> requiredItems = requiredItemsFor(event.getState());
        if (requiredItems.isEmpty()) return;
        if (!heldItemMatches(player, requiredItems)) {
            event.setCanceled(true);
            if (player != null) {
                player.displayClientMessage(Component.translatable("gui.kve.blockrules.wrong_tool"), true);
            }
        }
    }

    private static Set<String> requiredItemsFor(BlockState state) {
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return ExactToolRequirements.requiredItemsFor(blockId.toString());
    }

    private static boolean heldItemMatches(Player player, Set<String> requiredItems) {
        if (player == null) return false;
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) return false;
        ResourceLocation heldId = BuiltInRegistries.ITEM.getKey(held.getItem());
        return heldId != null && requiredItems.contains(heldId.toString());
    }
}
