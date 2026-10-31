package com.kve.common.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/**
 * Persiste los markers de {@link StructureSpawnCapRuntime} a disco (carpeta
 * de guardado del mundo, {@code data/kve_spawn_caps.dat} por dimension -
 * mismo mecanismo vanilla que usa un {@code MapItemSavedData} o cualquier
 * otro {@link SavedData}), para que sobrevivan un restart de server.
 * <p>
 * Sin esto, {@link StructureSpawnCapRuntime#ACTIVE} era puramente en
 * memoria: un marker registrado via {@code StructureWorldIO#place} dejaba
 * de spawnear apenas el server reiniciaba, hasta que alguien volviera a
 * apretar "Generar en el mundo" sobre esa misma estructura. Esta clase es
 * la mitad "elimina la limitacion" que SI se puede resolver con API
 * publica y estable (SavedData es API vanilla comun, sin mixins ni
 * reflection) - ver el javadoc de {@link StructureSpawnCapRuntime} para la
 * otra mitad (generacion vanilla real via structure_set/jigsaw), que
 * queda deliberadamente fuera de este archivo.
 */
public final class StructureSpawnCapSavedData extends SavedData {

    public static final String ID = "shovel_js_spawn_caps";

    /** Un marker ya resuelto a coordenadas de mundo, listo para pasarle a {@link StructureSpawnCapRuntime#register}. */
    public static final class PersistedMarker {
        public final BlockPos pos;
        public final StructureSpawnCap cap;

        public PersistedMarker(BlockPos pos, StructureSpawnCap cap) {
            this.pos = pos;
            this.cap = cap;
        }
    }

    private final List<PersistedMarker> markers = new ArrayList<>();

    public static StructureSpawnCapSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                StructureSpawnCapSavedData::load, StructureSpawnCapSavedData::new, ID);
    }

    public List<PersistedMarker> markersView() {
        return markers;
    }

    /** Agrega y marca dirty para que el proximo autosave lo escriba - el caller (Runtime) ya deduplico antes de llamar esto. */
    public void add(BlockPos pos, StructureSpawnCap cap) {
        markers.add(new PersistedMarker(pos.immutable(), cap));
        setDirty();
    }

    private static StructureSpawnCapSavedData load(CompoundTag root) {
        StructureSpawnCapSavedData data = new StructureSpawnCapSavedData();
        ListTag list = root.getList("markers", 10 );
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ListTag posTag = entry.getList("pos", 3 );
            if (posTag.size() < 3) continue;
            BlockPos pos = new BlockPos(posTag.getInt(0), posTag.getInt(1), posTag.getInt(2));

            CompoundTag capTag = entry.getCompound("cap");
            StructureSpawnCap cap = new StructureSpawnCap();
            cap.entityId = capTag.getString("entity");
            cap.minSpawnDelayTicks = capTag.getInt("minSpawnDelayTicks");
            cap.maxSpawnDelayTicks = capTag.getInt("maxSpawnDelayTicks");
            cap.spawnCount = capTag.getInt("spawnCount");
            cap.maxNearbyEntities = capTag.getInt("maxNearbyEntities");
            cap.requiredPlayerRange = capTag.getInt("requiredPlayerRange");
            cap.spawnRange = capTag.getInt("spawnRange");
            cap.minLightLevel = capTag.getInt("minLightLevel");
            cap.maxLightLevel = capTag.getInt("maxLightLevel");
            cap.blockOnPeaceful = capTag.getBoolean("blockOnPeaceful");
            cap.blockInDaylight = capTag.getBoolean("blockInDaylight");

            data.markers.add(new PersistedMarker(pos, cap));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        ListTag list = new ListTag();
        for (PersistedMarker m : markers) {
            CompoundTag entry = new CompoundTag();
            ListTag posTag = new ListTag();
            posTag.add(net.minecraft.nbt.IntTag.valueOf(m.pos.getX()));
            posTag.add(net.minecraft.nbt.IntTag.valueOf(m.pos.getY()));
            posTag.add(net.minecraft.nbt.IntTag.valueOf(m.pos.getZ()));
            entry.put("pos", posTag);

            CompoundTag capTag = new CompoundTag();
            capTag.putString("entity", m.cap.entityId);
            capTag.putInt("minSpawnDelayTicks", m.cap.minSpawnDelayTicks);
            capTag.putInt("maxSpawnDelayTicks", m.cap.maxSpawnDelayTicks);
            capTag.putInt("spawnCount", m.cap.spawnCount);
            capTag.putInt("maxNearbyEntities", m.cap.maxNearbyEntities);
            capTag.putInt("requiredPlayerRange", m.cap.requiredPlayerRange);
            capTag.putInt("spawnRange", m.cap.spawnRange);
            capTag.putInt("minLightLevel", m.cap.minLightLevel);
            capTag.putInt("maxLightLevel", m.cap.maxLightLevel);
            capTag.putBoolean("blockOnPeaceful", m.cap.blockOnPeaceful);
            capTag.putBoolean("blockInDaylight", m.cap.blockInDaylight);
            entry.put("cap", capTag);

            list.add(entry);
        }
        root.put("markers", list);
        return root;
    }
}
