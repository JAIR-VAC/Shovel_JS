package com.kve.client;

import com.kve.client.events.WorldChangeListener;
import com.kve.client.keybind.KeyBindings;
import com.kve.client.keybind.KeyTickHandler;
import com.kve.client.overlay.ToggleButtonOverlay;
import com.kve.common.config.KVEConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

import static com.kve.CraftEditorMod.LOGGER;
import static com.kve.CraftEditorMod.NAME;
import static com.kve.CraftEditorMod.VERSION;

/**
 * Arranque SOLO de cliente, separado de CraftEditorMod: cualquier referencia a Minecraft/ClientLevel/
 * KeyMapping dentro de la clase principal hacia que un servidor dedicado
 * intentara cargar esas clases al verificarla y el mod no arrancaba.
 */
@OnlyIn(Dist.CLIENT)
public final class KveClientSetup {
    private KveClientSetup() {}

    /** Constructor del mod: los KeyMapping tienen que existir antes de RegisterKeyMappingsEvent (ver CraftEditorMod). */
    public static void registerModBus(IEventBus modBus) {
        KeyBindings.register();
        modBus.register(KeyBindings.class);
    }

    /** FMLClientSetupEvent. */
    public static void applyUiConfig() {
        com.kve.client.gui.KveGuiStyle.applyTheme(com.kve.client.gui.KveGuiStyle.Theme.fromId(KVEConfig.theme));
        if (!KVEConfig.customAccentColor.isBlank()) {
            com.kve.client.gui.KveGuiStyle.applyCustomAccentColor(KVEConfig.customAccentColor);
        }
        com.kve.client.gui.KveGuiStyle.setUiTextScale(KVEConfig.uiTextScale);
        com.kve.client.keybind.KveShellKeybinds.loadFromConfig();
    }

    /** El runtime pregunta por EditPermission; aca se conecta al rol negociado con el servidor. */
    public static void installPermissionHooks() {
        com.kve.common.permission.EditPermission.canEdit = com.kve.client.permission.ClientPermissionState::canEdit;
        com.kve.common.permission.EditPermission.canApply = com.kve.client.permission.ClientPermissionState::canApply;
    }

    public static void run() {
        MinecraftForge.EVENT_BUS.register(com.kve.client.gui.KveTruncatedTextTooltip.class);
        MinecraftForge.EVENT_BUS.register(com.kve.client.gui.KveUiScale.class);
        MinecraftForge.EVENT_BUS.register(com.kve.client.gui.KveTooltipDefer.class);
        com.kve.common.kubejs.ExternalScriptScanner.setMainThreadExecutor(net.minecraft.client.Minecraft::getInstance);
        MinecraftForge.EVENT_BUS.register(ToggleButtonOverlay.class);
        MinecraftForge.EVENT_BUS.register(KeyTickHandler.class);
            // -
            // ver Javadoc de la clase para el por que no alcanza con
            // cancelar el evento de tecla.
        MinecraftForge.EVENT_BUS.register(com.kve.client.keybind.KveTypingKeyGuard.class);
        MinecraftForge.EVENT_BUS.register(WorldChangeListener.class);
            // Comerciantes modded: el Trade Editor clasifica entidades y
            // lista oficios observados usando el nivel del CLIENTE (no el del server
            // integrado desde el hilo de render). Codigo comun no puede referenciar
            // ClientLevel, por eso se inyecta aca.
            com.kve.common.trade.MerchantTypeDetector.setLevelSupplier(
                    () -> net.minecraft.client.Minecraft.getInstance().level);
            com.kve.common.trade.MerchantTypeDetector.setEntitySupplier(() -> {
                var lvl = net.minecraft.client.Minecraft.getInstance().level;
                return lvl == null ? null : lvl.entitiesForRendering();
            });
        MinecraftForge.EVENT_BUS.register(com.kve.client.events.ConsoleCommandCapture.class);
        MinecraftForge.EVENT_BUS.register(com.kve.client.structure.StructureGhostPreviewRenderer.class);
        MinecraftForge.EVENT_BUS.register(com.kve.client.structure.StructureWorldBuildRenderer.class);
        MinecraftForge.EVENT_BUS.register(com.kve.integration.viewer.KveViewerOverlay.class);
            LOGGER.info("[Shovel JS] {} v{} initialized. EC={}", NAME, VERSION,
                    KVEConfig.isExtendedCraftingActive() ? "detected" : "not found");
    }
}
