package com.kve.common.network;

import com.kve.CraftEditorMod;
import com.kve.common.permission.KvePermissionManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Registered unconditionally on {@code MinecraftForge.EVENT_BUS} (both
 * sides load this class fine - it only touches server-side types, and only
 * runs its body when the logging-in entity is actually a ServerPlayer,
 * i.e. only ever true on the logical server). Pushes the player's
 * resolved {@link com.kve.common.permission.KvePermissionRole} the moment
 * they join, so the client doesn't have to ask first for the common case.
 */
public final class ServerPermissionSync {

    private ServerPermissionSync() {}

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        try {
            KVENetwork.sendPermission(player, KvePermissionManager.resolve(player));
        } catch (Exception e) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Failed to sync permission for {}: {}",
                    player.getGameProfile().getName(), e.getMessage());
        }
    }
}
