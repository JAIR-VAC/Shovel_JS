package com.kve.client.worldgen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kve.common.worldgen.OreOverride;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lista los minerales que el mundo genera HOY (vanilla y de mods): cada
 * placed feature cuyo configured es {@code minecraft:ore} o
 * {@code minecraft:scattered_ore}, con su JSON real (codec del propio
 * juego), el paso de generacion y los biomas donde aparece. Los registros de
 * worldgen no se mandan al cliente, asi que hace falta el servidor integrado
 * (un jugador / LAN) - mismo limite que ExistingRecipeLoader#tryFillFromRawJson.
 */
@OnlyIn(Dist.CLIENT)
public final class ExistingOreScanner {
    private ExistingOreScanner() {}

    public record ExistingOre(String placedId, JsonObject placed, JsonObject configured, String step,
                              List<String> biomes, int dimMinY, int dimHeight, String oreBlock) {
        /** Override "sin cambios" listo para editar. */
        public OreOverride toOverride() {
            return OreOverride.fromOriginal(placedId, placed, configured, step, biomes, dimMinY, dimHeight);
        }
    }

    /** Null si no hay servidor integrado (servidor dedicado o fuera de un mundo). */
    public static List<ExistingOre> scan() {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) return null;
        RegistryAccess access = server.registryAccess();
        Registry<PlacedFeature> placedReg = access.registryOrThrow(Registries.PLACED_FEATURE);
        Registry<Biome> biomeReg = access.registryOrThrow(Registries.BIOME);

        Map<ResourceLocation, String> stepOf = new HashMap<>();
        Map<ResourceLocation, List<String>> biomesOf = new HashMap<>();
        Map<ResourceLocation, ResourceKey<Level>> dimOf = new HashMap<>();
        GenerationStep.Decoration[] steps = GenerationStep.Decoration.values();
        for (Map.Entry<ResourceKey<Biome>, Biome> be : biomeReg.entrySet()) {
            Holder<Biome> bh = biomeReg.getHolderOrThrow(be.getKey());
            ResourceKey<Level> dim = bh.is(BiomeTags.IS_NETHER) ? Level.NETHER : bh.is(BiomeTags.IS_END) ? Level.END : Level.OVERWORLD;
            List<HolderSet<PlacedFeature>> features = be.getValue().getGenerationSettings().features();
            for (int i = 0; i < features.size() && i < steps.length; i++) {
                for (Holder<PlacedFeature> ph : features.get(i)) {
                    ResourceLocation id = ph.unwrapKey().map(ResourceKey::location).orElse(null);
                    if (id == null) continue;
                    stepOf.putIfAbsent(id, steps[i].getSerializedName());
                    biomesOf.computeIfAbsent(id, k -> new ArrayList<>()).add(be.getKey().location().toString());
                    dimOf.putIfAbsent(id, dim);
                }
            }
        }

        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, access);
        List<ExistingOre> out = new ArrayList<>();
        for (Map.Entry<ResourceKey<PlacedFeature>, PlacedFeature> e : placedReg.entrySet()) {
            ResourceLocation id = e.getKey().location();
            if ("shovel_js".equals(id.getNamespace())) continue;
            PlacedFeature pf = e.getValue();
            ConfiguredFeature<?, ?> cf = pf.feature().value();
            if (cf.feature() != Feature.ORE && cf.feature() != Feature.SCATTERED_ORE) continue;
            try {
                JsonElement placed = PlacedFeature.DIRECT_CODEC.encodeStart(ops, pf).result().orElse(null);
                if (placed == null || !placed.isJsonObject()) continue;
                JsonObject configured = null;
                if (pf.feature().unwrapKey().isPresent()) {
                    JsonElement c = ConfiguredFeature.DIRECT_CODEC.encodeStart(ops, cf).result().orElse(null);
                    if (c != null && c.isJsonObject()) configured = c.getAsJsonObject();
                }
                ServerLevel level = server.getLevel(dimOf.getOrDefault(id, Level.OVERWORLD));
                int minY = level != null ? level.getMinBuildHeight() : -64;
                int height = level != null ? level.getHeight() : 384;
                OreOverride probe = OreOverride.fromOriginal(id.toString(), placed.getAsJsonObject(), configured, null, null, minY, height);
                String block = probe.targetBlocks.isEmpty() ? "" : probe.targetBlocks.get(0);
                out.add(new ExistingOre(id.toString(), placed.getAsJsonObject(), configured,
                        stepOf.getOrDefault(id, "underground_ores"), biomesOf.getOrDefault(id, List.of()),
                        minY, height, block));
            } catch (Exception ignored) {
            }
        }
        out.sort(Comparator.comparing(ExistingOre::placedId));
        return out;
    }
}
