package com.kve.common.structure;

public final class StructureSpawnCap {

    /** namespace:path entity id, e.g. "minecraft:zombie". Empty until set. */
    public String entityId = "";

    public int minSpawnDelayTicks = 200;
    public int maxSpawnDelayTicks = 800;
    public int spawnCount = 4;

    /** Don't spawn if this many of {@link #entityId} already exist within {@link #spawnRange} (vanilla default: 6). */
    public int maxNearbyEntities = 6;
    /** Don't spawn unless a player is within this many blocks (vanilla default: 16). */
    public int requiredPlayerRange = 16;
    /** Horizontal radius mobs can appear within, centered on the marker (vanilla default: 4). */
    public int spawnRange = 4;

    /** Don't spawn above this light level (0-15). */
    public int minLightLevel = 0;
    public int maxLightLevel = 7;
    /** Don't spawn at all on Peaceful difficulty - the same rule vanilla hostile spawning follows. */
    public boolean blockOnPeaceful = true;
    /** Don't spawn during daylight hours. Off by default since not every capped mob is a hostile that cares. */
    public boolean blockInDaylight = false;

    public StructureSpawnCap() {}

    public StructureSpawnCap copy() {
        StructureSpawnCap c = new StructureSpawnCap();
        c.entityId = entityId;
        c.minSpawnDelayTicks = minSpawnDelayTicks;
        c.maxSpawnDelayTicks = maxSpawnDelayTicks;
        c.spawnCount = spawnCount;
        c.maxNearbyEntities = maxNearbyEntities;
        c.requiredPlayerRange = requiredPlayerRange;
        c.spawnRange = spawnRange;
        c.minLightLevel = minLightLevel;
        c.maxLightLevel = maxLightLevel;
        c.blockOnPeaceful = blockOnPeaceful;
        c.blockInDaylight = blockInDaylight;
        return c;
    }
}
