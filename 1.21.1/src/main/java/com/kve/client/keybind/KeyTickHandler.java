package com.kve.client.keybind;

import com.kve.client.gui.recipe.CraftEditorScreen;
import com.kve.client.gui.properties.ItemAttributeScreen;
import com.kve.client.gui.loot.LootTableEditorScreen;
import com.kve.client.gui.mob.MobEditorScreen;
import com.kve.client.gui.studio.KveStudioShell;
import com.kve.client.gui.tag.TagEditorScreen;
import com.kve.client.overlay.ToggleButtonOverlay;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Polls the key bindings once per client tick. Registered ONLY on
 * NeoForge.EVENT_BUS, since TickEvent.ClientTickEvent is a regular
 * Forge-bus event (not IModBusEvent) - keeping this separate from
 * KeyBindings (which handles the mod-bus RegisterKeyMappingsEvent) avoids
 * Forge rejecting the class for mixing bus types.
 */
@OnlyIn(Dist.CLIENT)
public class KeyTickHandler {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        while (KeyBindings.openEditor.consumeClick()) {
            if (mc.screen == null) com.kve.client.access.KveAccessGate.open(KveStudioShell::new);
        }
        while (KeyBindings.toggleButton.consumeClick()) {
            ToggleButtonOverlay.toggle();
        }
        while (KeyBindings.openRecipes.consumeClick()) {
            if (mc.screen == null) com.kve.client.access.KveAccessGate.open(CraftEditorScreen::new);
        }
        while (KeyBindings.openMobs.consumeClick()) {
            if (mc.screen == null) com.kve.client.access.KveAccessGate.open(MobEditorScreen::new);
        }
        while (KeyBindings.openItems.consumeClick()) {
            if (mc.screen == null) com.kve.client.access.KveAccessGate.open(ItemAttributeScreen::new);
        }
        while (KeyBindings.openTags.consumeClick()) {
            if (mc.screen == null) com.kve.client.access.KveAccessGate.open(TagEditorScreen::new);
        }
        while (KeyBindings.openLoot.consumeClick()) {
            if (mc.screen == null) com.kve.client.access.KveAccessGate.open(LootTableEditorScreen::new);
        }
        while (KeyBindings.openCode.consumeClick()) {
            if (mc.screen == null) com.kve.client.access.KveAccessGate.open(com.kve.client.gui.code.CodeEditorScreen::new);
        }
        while (KeyBindings.openEvents.consumeClick()) {
            if (mc.screen == null) com.kve.client.access.KveAccessGate.open(com.kve.client.gui.event.EventEditorScreen::new);
        }
        while (KeyBindings.openTrades.consumeClick()) {
            if (mc.screen == null) com.kve.client.access.KveAccessGate.open(com.kve.client.gui.trade.TradeEditorScreen::new);
        }
        while (KeyBindings.openWorldGen.consumeClick()) {
            if (mc.screen == null) com.kve.client.access.KveAccessGate.open(() -> new com.kve.client.gui.worldgen.WorldGenListScreen(null));
        }
        while (KeyBindings.openBlocks.consumeClick()) {
            if (mc.screen == null) com.kve.client.access.KveAccessGate.open(com.kve.client.gui.properties.BlockAttributeScreen::new);
        }
        while (KeyBindings.openStructures.consumeClick()) {
            if (mc.screen == null) com.kve.client.access.KveAccessGate.open(com.kve.client.structure.StructureEditorScreen::new);
        }
        while (KeyBindings.structureGhostAnchor.consumeClick()) {
            if (mc.screen == null && com.kve.client.structure.StructureGhostPreview.isEnabled()
                    && mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bhr) {
                com.kve.client.structure.StructureGhostPreview.setAnchor(bhr.getBlockPos().relative(bhr.getDirection()));
            }
        }
    }
}
