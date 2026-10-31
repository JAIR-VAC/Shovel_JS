package com.kve.client.fun;

import net.minecraft.resources.ResourceLocation;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Que aleatorizar y que dejar quieto. Vive entre aperturas de la pantalla Fun. */
public final class FunOptions {

    public enum Domain {
        RECIPES("gui.kve.fun.domain.recipes"),
        ATTRIBUTES("gui.kve.fun.domain.attributes"),
        LOOT("gui.kve.fun.domain.loot"),
        MOBS("gui.kve.fun.domain.mobs"),
        TRADES("gui.kve.fun.domain.trades"),
        WORLDGEN("gui.kve.fun.domain.worldgen"),
        STRUCTURES("gui.kve.fun.domain.structures");

        public final String key;
        Domain(String key) { this.key = key; }

        /** Loot, vetas y estructuras leen datos que solo tiene el servidor integrado. */
        public boolean needsLocalServer() { return this == LOOT || this == WORLDGEN || this == STRUCTURES; }
    }

    public enum Intensity {
        MILD("gui.kve.fun.intensity.mild", 0.25),
        MEDIUM("gui.kve.fun.intensity.medium", 0.5),
        CHAOS("gui.kve.fun.intensity.chaos", 0.9);

        public final String key;
        public final double value;
        Intensity(String key, double value) { this.key = key; this.value = value; }
    }

    public static final FunOptions CURRENT = new FunOptions();

    public final Set<Domain> domains = EnumSet.of(Domain.RECIPES, Domain.ATTRIBUTES, Domain.MOBS);
    public Intensity intensity = Intensity.MEDIUM;
    public final Set<String> excludedMods = new LinkedHashSet<>();
    public String excludedIdsText = "";
    public String seedText = "";

    public long seed() {
        String s = seedText.trim();
        if (s.isEmpty()) return System.nanoTime();
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return s.hashCode(); }
    }

    public Set<String> excludedIds() {
        Set<String> out = new LinkedHashSet<>();
        for (String p : excludedIdsText.split("[,;\\s]+")) {
            String id = p.trim().toLowerCase(Locale.ROOT);
            if (!id.isEmpty()) out.add(id.contains(":") ? id : "minecraft:" + id);
        }
        return out;
    }

    /** False si el id (item, mob, receta, profesion...) o su mod estan excluidos. */
    public boolean allows(ResourceLocation id, Set<String> excludedIds) {
        if (id == null) return false;
        return !excludedMods.contains(id.getNamespace()) && !excludedIds.contains(id.toString());
    }
}
