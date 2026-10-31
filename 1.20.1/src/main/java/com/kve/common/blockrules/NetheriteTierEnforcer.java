package com.kve.common.blockrules;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class NetheriteTierEnforcer {

    private NetheriteTierEnforcer() {}

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (player != null && player.isCreative()) return;
        if (!requiresNetherite(event.getState())) return;
        if (!heldIsNetherite(player)) {
            event.setNewSpeed(0.01f);
        }
    }

    @SubscribeEvent
    public static void onBreakEvent(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player != null && player.isCreative()) return;
        if (!requiresNetherite(event.getState())) return;
        if (!heldIsNetherite(player)) {
            event.setCanceled(true);
            if (player != null) {
                player.displayClientMessage(Component.translatable("gui.kve.blockrules.wrong_tool"), true);
            }
        }
    }

    private static boolean requiresNetherite(BlockState state) {
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return NetheriteTierRequirements.hasRequirement(blockId.toString());
    }

    private static boolean heldIsNetherite(Player player) {
        if (player == null) return false;
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) return false;
        return held.getItem() instanceof TieredItem tiered && tiered.getTier() == Tiers.NETHERITE;
    }
}
