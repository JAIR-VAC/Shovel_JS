package com.kve.mixin;

import com.kve.common.structure.JigsawSpawnCapBridge;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SinglePoolElement.class)
public abstract class SinglePoolElementMixin {

    @Shadow @Final
    protected Either<ResourceLocation, StructureTemplate> template;

    @Shadow
    protected abstract StructurePlaceSettings getSettings(Rotation rotation, BoundingBox box, net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings liquidSettings, boolean keepJigsaws);

    @Inject(
            method = "place(Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Rotation;Lnet/minecraft/world/level/levelgen/structure/BoundingBox;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/structure/templatesystem/LiquidSettings;Z)Z",
            at = @At("RETURN")
    )
    private void kve$onPlace(StructureTemplateManager structureTemplateManager, WorldGenLevel level,
                              StructureManager structureManager, ChunkGenerator chunkGenerator,
                              BlockPos offset, BlockPos pos, Rotation rotation, BoundingBox box,
                              RandomSource random, net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings liquidSettings, boolean keepJigsaws,
                              CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        ServerLevel serverLevel = level.getLevel();
        if (serverLevel == null) return;

        ResourceLocation id = this.template.left().orElse(null);
        if (id == null) return;

        StructurePlaceSettings settings = this.getSettings(rotation, box, liquidSettings, keepJigsaws);
        JigsawSpawnCapBridge.onPiecePlaced(serverLevel, id, offset, settings);
    }
}
