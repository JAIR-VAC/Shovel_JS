package com.kve.client.permission;

import com.kve.common.network.PermissionAdminSyncPacket;

/**
 * Client-side cache of the last {@link PermissionAdminSyncPacket} received,
 * i.e. "what PermissionAdminScreen should currently show". Separate from
 * {@link ClientPermissionState} (which only tracks the local player's own
 * role) since this holds the whole online-player roster, only ever
 * populated for an ADMIN who opened the admin screen.
 */
public final class ClientPermissionAdminState {

    private ClientPermissionAdminState() {}

    private static volatile PermissionAdminSyncPacket lastSnapshot = null;

    public static void onSync(PermissionAdminSyncPacket msg) {
        lastSnapshot = msg;
    }

    public static PermissionAdminSyncPacket lastSnapshot() {
        return lastSnapshot;
    }

    public static void clear() {
        lastSnapshot = null;
    }
}
