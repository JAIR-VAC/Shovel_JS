package com.kve.mixin;

import com.kve.common.structure.JigsawSpawnCapBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TemplateStructurePiece.class)
public abstract class TemplateStructurePieceMixin {

    @Shadow protected StructurePlaceSettings placeSettings;
    @Shadow protected BlockPos templatePosition;

    @Shadow
    protected abstract ResourceLocation makeTemplateLocation();

    @Inject(
            method = "postProcess(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/structure/BoundingBox;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/core/BlockPos;)V",
            at = @At("RETURN")
    )
    private void kve$onPostProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator chunkGenerator,
                                   RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pos,
                                   CallbackInfo ci) {
        if (placeSettings == null || templatePosition == null) return;
        ServerLevel serverLevel = level.getLevel();
        if (serverLevel == null) return;
        ResourceLocation id;
        try {
            id = makeTemplateLocation();
        } catch (RuntimeException ex) {
            return; // nombre de plantilla invalido en una pieza de otro mod - nunca romper la generacion
        }
        JigsawSpawnCapBridge.onPiecePlaced(serverLevel, id, templatePosition, placeSettings);
    }
}
