package com.kve.client.permission;

import com.kve.common.permission.KvePermissionRole;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Client-side cache of "what can I do with KVE right now". Every gated
 * entry point (KeyTickHandler, MainMenuScreen, CraftEditorState's mutating
 * methods, RecipeSubsetIO) reads this instead of talking to the network
 * directly.
 *
 * Resolution order, cheapest/safest first:
 *   1. Singleplayer or an integrated (non-dedicated) server -> always ADMIN.
 *      This is the overwhelming common case (a player editing their own
 *      world) and keeps this whole permission system invisible to them.
 *   2. Connected to a real multiplayer server -> fail-closed to NONE until
 *      a {@code PermissionSyncPacket} arrives, then whatever the server said.
 *
 * A server that doesn't have this mod's networking (e.g. an older KVE
 * version, or the permission packets got swallowed by a proxy) will simply
 * never reply, and the client stays fail-closed at NONE - the safer
 * default for a shared server, at the cost of needing this mod on both
 * sides to actually unlock editing on multiplayer. This is documented in
 * the deliverable README.
 */
public final class ClientPermissionState {

    private ClientPermissionState() {}

    private static volatile KvePermissionRole cachedRole = null;
    private static volatile boolean requested = false;

    /** Called from the network thread (already hopped to the client thread by the packet handler) when a sync arrives. */
    public static void onSync(KvePermissionRole role) {
        cachedRole = role;
    }

    public static void requestIfNeeded() {
        if (isIntegratedTrusted()) return;
        if (Minecraft.getInstance().getConnection() == null) return;
        if (!requested) {
            requested = true;
            com.kve.common.network.KVENetwork.requestPermissionRefresh();
        }
    }

    public static void reset() {
        cachedRole = null;
        requested = false;
    }

    @OnlyIn(Dist.CLIENT)
    private static boolean isIntegratedTrusted() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getSingleplayerServer() != null;
    }

    public static KvePermissionRole currentRole() {
        if (isIntegratedTrusted()) return KvePermissionRole.ADMIN;
        requestIfNeeded();
        return cachedRole != null ? cachedRole : KvePermissionRole.NONE;
    }

    public static boolean canView() { return currentRole().atLeast(KvePermissionRole.VIEW); }
    public static boolean canEdit() { return currentRole().atLeast(KvePermissionRole.EDIT); }

    /**
     * EDIT can draft and Save (queue) changes, but only ADMIN can actually
     * Apply the queue to disk - the real, server-visible write. This is
     * exactly the extension {@link KvePermissionRole#ADMIN}'s own javadoc
     * already reserved. Discard is intentionally NOT gated by
     * this - discarding only clears the calling player's own local draft
     * queue, it never touches disk, so requiring EDIT (via each screen's
     * existing per-action checks) is enough for it.
     */
    public static boolean canApply() { return currentRole().atLeast(KvePermissionRole.ADMIN); }

    public static boolean isResolved() {
        return isIntegratedTrusted() || cachedRole != null;
    }
}
