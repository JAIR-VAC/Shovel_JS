package com.kve.common.structure;

public enum StructureTerrainAdjustment {
    NONE,
    /** Desplaza el origin.y para asentar el punto mas bajo de la estructura sobre el terreno real (evita flotar). */
    BURY,
    BEARD_THIN,
    BEARD_BOX,
    ENCAPSULATE,
    BIOME_BLEND;

    public static StructureTerrainAdjustment parse(String raw) {
        if (raw == null || raw.isEmpty()) return NONE;
        try {
            return valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return NONE;
        }
    }

    public StructureTerrainAdjustment next() {
        StructureTerrainAdjustment[] v = values();
        return v[(ordinal() + 1) % v.length];
    }
}
