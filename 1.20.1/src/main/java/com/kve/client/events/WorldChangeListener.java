package com.kve.client.events;

import com.kve.CraftEditorMod;
import com.kve.client.gui.StartupNoticeScreen;
import com.kve.client.structure.StructureWorldBuildSession;
import com.kve.common.config.KVEConfig;
import com.kve.common.kubejs.ExternalScriptScanner;
import com.kve.common.persist.CraftEditorState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@OnlyIn(Dist.CLIENT)
public class WorldChangeListener {

    /**
     * Every
     * real block placed while a {@link StructureWorldBuildSession} is
     * armed grows that session's tracked boundary box to include it (see
     * {@link StructureWorldBuildSession#onBlockPlaced()}). {@code EntityPlaceEvent}
     * fires for ordinary player building (the normal case this feature is
     * for); it does NOT fire for blocks merely syncing down from the
     * server as chunk data, so this never mistakes "the world loaded
     * around me" for "the player placed a block". Cheap no-op when no
     * session is armed - the overwhelming common case - so this costs
     * nothing for a player who never uses the world-build feature at all.
     */
    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!isLocalPlayer(event.getEntity())) return;
        net.minecraft.core.BlockPos pos = event.getPos().immutable();
        Minecraft.getInstance().execute(() -> StructureWorldBuildSession.onBlockPlaced(pos));
    }

    private static boolean isLocalPlayer(net.minecraft.world.entity.Entity entity) {
        if (!(entity instanceof net.minecraft.world.entity.player.Player p)) return false;
        var local = Minecraft.getInstance().player;
        return local != null && local.getUUID().equals(p.getUUID());
    }

    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (!isLocalPlayer(event.getPlayer())) return;
        net.minecraft.core.BlockPos pos = event.getPos().immutable();
        Minecraft.getInstance().execute(() -> StructureWorldBuildSession.onBlockBroken(pos));
    }

    @SubscribeEvent
    public static void onLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        CraftEditorState.forceReload();
        ExternalScriptScanner.scan();

        com.kve.client.permission.ClientPermissionState.reset();
        com.kve.client.permission.ClientPermissionState.requestIfNeeded();

        if (KVEConfig.showStartupNotice) {
            Minecraft mc = Minecraft.getInstance();

            mc.execute(() -> {
                Screen current = mc.screen;
                mc.setScreen(new StartupNoticeScreen(current));
            });
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if (com.kve.client.permission.ClientPermissionState.canApply()
                && !com.kve.client.gui.workspace.pending.PendingChangeManager.isEmpty()) {
            com.kve.client.gui.workspace.pending.PendingChangeManager.ApplyResult result =
                    com.kve.client.gui.workspace.pending.PendingChangeManager.applyAll();
            if (!result.allSucceeded()) {
                CraftEditorMod.LOGGER.warn(
                        "[Shovel JS] onLoggedOut: {} pending change(s) failed to apply and were left queued (will still be lost on next login if never applied): {}",
                        result.failed().size(), result.failed());
            }
        }

        com.kve.client.gui.workspace.pending.PendingChangeManager.clear();

        CraftEditorState.saveNow();
        ExternalScriptScanner.reset();
        com.kve.common.kubejs.EntryScanner.reset();
        com.kve.client.permission.ClientPermissionState.reset();

        StructureWorldBuildSession.clear();

        com.kve.client.structure.StructureSceneRenderer.resetCache();
        com.kve.client.structure.StructureGhostPreviewRenderer.resetCache();
        com.kve.client.structure.StructureGhostPreview.clear();
        com.kve.client.gui.MachineStationPreview.resetCache();

        com.kve.client.permission.ClientPermissionAdminState.clear();
    }
}
