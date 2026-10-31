package com.kve.common.network;

import com.kve.CraftEditorMod;
import com.kve.common.permission.KvePermissionRole;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Registers the small SimpleChannel used purely for the multiplayer
 * permission handshake (see PermissionRequestPacket / PermissionSyncPacket).
 * Call {@link #register()} once from common setup (both sides load this
 * class - it doesn't touch any client-only class itself).
 */
public final class KVENetwork {

        private KVENetwork() {
        }

        private static final String PROTOCOL_VERSION = "1";

        public static final long STRUCTURE_NBT_READ_LIMIT = 256L * 1024 * 1024;

        public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
                        new ResourceLocation(CraftEditorMod.MODID, "main"),
                        () -> PROTOCOL_VERSION,
                        net.minecraftforge.network.NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION),
                        net.minecraftforge.network.NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION));

        private static boolean registered = false;

        public static synchronized void register() {
                if (registered)
                        return;
                registered = true;

                int id = 0;
                CHANNEL.registerMessage(id++, PermissionRequestPacket.class,
                                PermissionRequestPacket::encode, PermissionRequestPacket::decode,
                                PermissionRequestPacket::handle);
                CHANNEL.registerMessage(id++, PermissionSyncPacket.class,
                                PermissionSyncPacket::encode, PermissionSyncPacket::decode,
                                PermissionSyncPacket::handle);
                CHANNEL.registerMessage(id++, PermissionAdminRequestPacket.class,
                                PermissionAdminRequestPacket::encode, PermissionAdminRequestPacket::decode,
                                PermissionAdminRequestPacket::handle);
                CHANNEL.registerMessage(id++, PermissionAdminSyncPacket.class,
                                PermissionAdminSyncPacket::encode, PermissionAdminSyncPacket::decode,
                                PermissionAdminSyncPacket::handle);
                CHANNEL.registerMessage(id++, PermissionSetRolePacket.class,
                                PermissionSetRolePacket::encode, PermissionSetRolePacket::decode,
                                PermissionSetRolePacket::handle);
                CHANNEL.registerMessage(id++, StructureRemoteRequestPacket.class,
                                StructureRemoteRequestPacket::encode, StructureRemoteRequestPacket::decode,
                                StructureRemoteRequestPacket::handle);
                CHANNEL.registerMessage(id++, StructureRemoteResponsePacket.class,
                                StructureRemoteResponsePacket::encode, StructureRemoteResponsePacket::decode,
                                StructureRemoteResponsePacket::handle);
                CHANNEL.registerMessage(id++, StructureWorldGenerateRequestPacket.class,
                                StructureWorldGenerateRequestPacket::encode,
                                StructureWorldGenerateRequestPacket::decode,
                                StructureWorldGenerateRequestPacket::handle);
                CHANNEL.registerMessage(id++, StructureWorldGenerateResponsePacket.class,
                                StructureWorldGenerateResponsePacket::encode,
                                StructureWorldGenerateResponsePacket::decode,
                                StructureWorldGenerateResponsePacket::handle);
                CHANNEL.registerMessage(id++, StructureWorldScanRequestPacket.class,
                                StructureWorldScanRequestPacket::encode, StructureWorldScanRequestPacket::decode,
                                StructureWorldScanRequestPacket::handle);
                CHANNEL.registerMessage(id++, StructureWorldScanResponsePacket.class,
                                StructureWorldScanResponsePacket::encode, StructureWorldScanResponsePacket::decode,
                                StructureWorldScanResponsePacket::handle);
                CHANNEL.registerMessage(id++, WorldGenLabTeleportPacket.class,
                                WorldGenLabTeleportPacket::encode, WorldGenLabTeleportPacket::decode,
                                WorldGenLabTeleportPacket::handle);

                CraftEditorMod.LOGGER.info("[Shovel JS] Network channel registered ({} packet types).", id);
        }

        public static void sendPermission(ServerPlayer player, KvePermissionRole role) {
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new PermissionSyncPacket(role));
        }

        public static void requestPermissionRefresh() {
                CHANNEL.sendToServer(new PermissionRequestPacket());
        }

        public static void requestAdminSnapshot() {
                CHANNEL.sendToServer(new PermissionAdminRequestPacket());
        }

        public static void requestSetRole(String targetName, String roleOrClear) {
                CHANNEL.sendToServer(new PermissionSetRolePacket(targetName, roleOrClear));
        }

        /**
         * Viaje a la dimension de prueba de un preset de terreno; {@code ""} = volver.
         * Ver {@link WorldGenLabTeleportPacket}.
         */
        public static void requestLabTeleport(String dimensionId) {
                CHANNEL.sendToServer(new WorldGenLabTeleportPacket(dimensionId));
        }

        public static void requestStructureRemote(int requestId, String structureId) {
                CHANNEL.sendToServer(new StructureRemoteRequestPacket(requestId, structureId));
        }

        public static void requestWorldGenerate(int requestId, net.minecraft.core.BlockPos origin,
                        net.minecraft.nbt.CompoundTag structureTag, boolean inPlace) {
                CHANNEL.sendToServer(new StructureWorldGenerateRequestPacket(requestId, origin, structureTag, inPlace));
        }

        public static void requestWorldScan(int requestId, net.minecraft.core.BlockPos origin, int sizeX, int sizeY,
                        int sizeZ, boolean includeMobs) {
                CHANNEL.sendToServer(new StructureWorldScanRequestPacket(requestId, origin, sizeX, sizeY, sizeZ,
                                includeMobs));
        }

        public static void requestWorldScan(int requestId, net.minecraft.core.BlockPos origin, int sizeX, int sizeY,
                        int sizeZ, boolean includeMobs, boolean includeAir) {
                CHANNEL.sendToServer(new StructureWorldScanRequestPacket(requestId, origin, sizeX, sizeY, sizeZ,
                                includeMobs, includeAir));
        }

        public static void requestWorldScan(int requestId, net.minecraft.core.BlockPos origin, int sizeX, int sizeY,
                        int sizeZ, boolean includeMobs, boolean includeAir, boolean requireSealed) {
                CHANNEL.sendToServer(new StructureWorldScanRequestPacket(requestId, origin, sizeX, sizeY, sizeZ,
                                includeMobs, includeAir, requireSealed));
        }
}