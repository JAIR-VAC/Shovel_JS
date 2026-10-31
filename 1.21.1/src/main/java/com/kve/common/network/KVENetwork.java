package com.kve.common.network;

import com.kve.CraftEditorMod;
import com.kve.common.permission.KvePermissionRole;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class KVENetwork {

    private KVENetwork() {
    }

    private static final String PROTOCOL_VERSION = "1";

    @SubscribeEvent
    static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional().executesOn(HandlerThread.MAIN);

        registrar.playToServer(PermissionRequestPacket.TYPE, PermissionRequestPacket.STREAM_CODEC,
                PermissionRequestPacket::handle);
        registrar.playToClient(PermissionSyncPacket.TYPE, PermissionSyncPacket.STREAM_CODEC,
                PermissionSyncPacket::handle);
        registrar.playToServer(PermissionAdminRequestPacket.TYPE, PermissionAdminRequestPacket.STREAM_CODEC,
                PermissionAdminRequestPacket::handle);
        registrar.playToClient(PermissionAdminSyncPacket.TYPE, PermissionAdminSyncPacket.STREAM_CODEC,
                PermissionAdminSyncPacket::handle);
        registrar.playToServer(PermissionSetRolePacket.TYPE, PermissionSetRolePacket.STREAM_CODEC,
                PermissionSetRolePacket::handle);
        registrar.playToServer(StructureRemoteRequestPacket.TYPE, StructureRemoteRequestPacket.STREAM_CODEC,
                StructureRemoteRequestPacket::handle);
        registrar.playToClient(StructureRemoteResponsePacket.TYPE, StructureRemoteResponsePacket.STREAM_CODEC,
                StructureRemoteResponsePacket::handle);
        registrar.playToServer(StructureWorldGenerateRequestPacket.TYPE, StructureWorldGenerateRequestPacket.STREAM_CODEC,
                StructureWorldGenerateRequestPacket::handle);
        registrar.playToClient(StructureWorldGenerateResponsePacket.TYPE, StructureWorldGenerateResponsePacket.STREAM_CODEC,
                StructureWorldGenerateResponsePacket::handle);
        registrar.playToServer(StructureWorldScanRequestPacket.TYPE, StructureWorldScanRequestPacket.STREAM_CODEC,
                StructureWorldScanRequestPacket::handle);
        registrar.playToClient(StructureWorldScanResponsePacket.TYPE, StructureWorldScanResponsePacket.STREAM_CODEC,
                StructureWorldScanResponsePacket::handle);
        registrar.playToServer(WorldGenLabTeleportPacket.TYPE, WorldGenLabTeleportPacket.STREAM_CODEC,
                WorldGenLabTeleportPacket::handle);

        CraftEditorMod.LOGGER.info("[Shovel JS] Network payloads registered (12 payload types).");
    }

    /** Viaje a la dimension de prueba de un preset de terreno; {@code ""} = volver. Ver {@link WorldGenLabTeleportPacket}. */
    public static void requestLabTeleport(String dimensionId) {
        PacketDistributor.sendToServer(new WorldGenLabTeleportPacket(dimensionId));
    }

    public static void sendPermission(ServerPlayer player, KvePermissionRole role) {
        PacketDistributor.sendToPlayer(player, new PermissionSyncPacket(role));
    }

    public static void requestPermissionRefresh() {
        PacketDistributor.sendToServer(new PermissionRequestPacket());
    }

    public static void requestAdminSnapshot() {
        PacketDistributor.sendToServer(new PermissionAdminRequestPacket());
    }

    public static void requestSetRole(String targetName, String roleOrClear) {
        PacketDistributor.sendToServer(new PermissionSetRolePacket(targetName, roleOrClear));
    }

    public static void requestStructureRemote(int requestId, String structureId) {
        PacketDistributor.sendToServer(new StructureRemoteRequestPacket(requestId, structureId));
    }

    public static void requestWorldGenerate(int requestId, net.minecraft.core.BlockPos origin,
            net.minecraft.nbt.CompoundTag structureTag, boolean inPlace) {
        PacketDistributor.sendToServer(new StructureWorldGenerateRequestPacket(requestId, origin, structureTag, inPlace));
    }

    public static void requestWorldScan(int requestId, net.minecraft.core.BlockPos origin, int sizeX, int sizeY,
            int sizeZ, boolean includeMobs) {
        PacketDistributor.sendToServer(new StructureWorldScanRequestPacket(requestId, origin, sizeX, sizeY, sizeZ, includeMobs));
    }

    public static void requestWorldScan(int requestId, net.minecraft.core.BlockPos origin, int sizeX, int sizeY,
            int sizeZ, boolean includeMobs, boolean includeAir) {
        PacketDistributor.sendToServer(new StructureWorldScanRequestPacket(requestId, origin, sizeX, sizeY, sizeZ, includeMobs, includeAir));
    }

    public static void requestWorldScan(int requestId, net.minecraft.core.BlockPos origin, int sizeX, int sizeY,
            int sizeZ, boolean includeMobs, boolean includeAir, boolean requireSealed) {
        PacketDistributor.sendToServer(new StructureWorldScanRequestPacket(requestId, origin, sizeX, sizeY, sizeZ, includeMobs, includeAir, requireSealed));
    }
}
