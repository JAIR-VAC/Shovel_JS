package com.kve.client.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Holds the key mapping definitions and registers them via
 * RegisterKeyMappingsEvent, which fires on the MOD event bus.
 *
 * IMPORTANT: this class must ONLY contain mod-bus (IModBusEvent) listeners.
 * The per-tick "was a key just pressed" polling logic lives in
 * {@link KeyTickHandler} instead, since TickEvent fires on the Forge bus -
 * mixing both event kinds in one class registered on a single bus makes
 * Forge reject the whole class at load time.
 */
@OnlyIn(Dist.CLIENT)
public class KeyBindings {

    private static final int KEY_UNBOUND = InputConstants.UNKNOWN.getValue();

    public static KeyMapping openEditor;
    public static KeyMapping toggleButton;
    public static KeyMapping openRecipes;
    public static KeyMapping openMobs;
    public static KeyMapping openItems;
    public static KeyMapping openTags;
    public static KeyMapping openLoot;
    /** Anchors the armed ghost at whatever block the player is currently looking at. Only does anything while {@link com.kve.client.structure.StructureGhostPreview} is armed - see {@link KeyTickHandler}. */
    public static KeyMapping structureGhostAnchor;
    public static KeyMapping openCode;
    public static KeyMapping openEvents;
    public static KeyMapping openTrades;
    public static KeyMapping openWorldGen;
    public static KeyMapping openBlocks;
    public static KeyMapping openStructures;

    public static void register() {
        openEditor = new KeyMapping(
                "key.kve.open_editor", KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, KEY_UNBOUND, "key.categories.shovel_js");
        toggleButton = new KeyMapping(
                "key.kve.toggle_button", KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, KEY_UNBOUND, "key.categories.shovel_js");
        openRecipes = new KeyMapping(
                "key.kve.open_recipes", KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, KEY_UNBOUND, "key.categories.shovel_js");
        openMobs = new KeyMapping(
                "key.kve.open_mobs", KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, KEY_UNBOUND, "key.categories.shovel_js");
        openItems = new KeyMapping(
                "key.kve.open_items", KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, KEY_UNBOUND, "key.categories.shovel_js");
        openTags = new KeyMapping(
                "key.kve.open_tags", KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, KEY_UNBOUND, "key.categories.shovel_js");
        openLoot = new KeyMapping(
                "key.kve.open_loot", KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, KEY_UNBOUND, "key.categories.shovel_js");
        openCode = new KeyMapping(
                "key.kve.open_code", KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, KEY_UNBOUND, "key.categories.shovel_js");
        openEvents = new KeyMapping(
                "key.kve.open_events", KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, KEY_UNBOUND, "key.categories.shovel_js");
        openTrades = new KeyMapping(
                "key.kve.open_trades", KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, KEY_UNBOUND, "key.categories.shovel_js");
        openWorldGen = new KeyMapping(
                "key.kve.open_worldgen", KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, KEY_UNBOUND, "key.categories.shovel_js");
        openBlocks = new KeyMapping(
                "key.kve.open_blocks", KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, KEY_UNBOUND, "key.categories.shovel_js");
        openStructures = new KeyMapping(
                "key.kve.open_structures", KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, KEY_UNBOUND, "key.categories.shovel_js");
        structureGhostAnchor = new KeyMapping(
                "key.kve.structure_ghost_anchor", KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, KEY_UNBOUND, "key.categories.shovel_js");
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(openEditor);
        event.register(toggleButton);
        event.register(openRecipes);
        event.register(openMobs);
        event.register(openItems);
        event.register(openTags);
        event.register(openLoot);
        event.register(structureGhostAnchor);
        event.register(openCode);
        event.register(openEvents);
        event.register(openTrades);
        event.register(openWorldGen);
        event.register(openBlocks);
        event.register(openStructures);
    }
}
