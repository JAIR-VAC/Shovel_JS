package com.kve.common.recipe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class UncraftableAudit {

    private UncraftableAudit() {}

    private static final Set<String> TECHNICAL_PATHS = Set.of(
            "air", "barrier", "light", "debug_stick", "knowledge_book", "command_block", "chain_command_block",
            "repeating_command_block", "command_block_minecart", "structure_block", "structure_void", "jigsaw",
            "bedrock", "spawner", "end_portal_frame", "petrified_oak_slab", "player_head", "written_book",
            "filled_map", "enchanted_book", "potion", "splash_potion", "lingering_potion", "tipped_arrow");

    public static boolean isTechnical(String id) {
        int c = id.indexOf(':');
        String path = c >= 0 ? id.substring(c + 1) : id;
        if (path.endsWith("_spawn_egg") || path.startsWith("infested_")) return true;
        return id.startsWith("minecraft:") && TECHNICAL_PATHS.contains(path);
    }

    public static String namespaceOf(String id) {
        int c = id.indexOf(':');
        return c >= 0 ? id.substring(0, c) : "minecraft";
    }

    /** Items de {@code modId} (todos los mods si es "" ) que ninguna receta produce, ordenados. */
    public static List<String> find(Collection<String> registeredIds, Set<String> producedIds, String modId, boolean skipTechnical) {
        List<String> out = new ArrayList<>();
        for (String id : registeredIds) {
            if (!modId.isEmpty() && !namespaceOf(id).equals(modId)) continue;
            if (producedIds.contains(id)) continue;
            if (skipTechnical && isTechnical(id)) continue;
            out.add(id);
        }
        out.sort(String::compareTo);
        return out;
    }

    /** mod -> {items sin receta, items totales}, ordenado de mas a menos items sin receta (desempate: nombre). */
    public static Map<String, int[]> summaryByMod(Collection<String> registeredIds, Set<String> producedIds, boolean skipTechnical) {
        Map<String, int[]> counts = new TreeMap<>();
        for (String id : registeredIds) {
            if (skipTechnical && isTechnical(id)) continue;
            int[] c = counts.computeIfAbsent(namespaceOf(id), k -> new int[2]);
            c[1]++;
            if (!producedIds.contains(id)) c[0]++;
        }
        List<Map.Entry<String, int[]>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort((a, b) -> a.getValue()[0] != b.getValue()[0] ? Integer.compare(b.getValue()[0], a.getValue()[0]) : a.getKey().compareTo(b.getKey()));
        Map<String, int[]> out = new LinkedHashMap<>();
        for (Map.Entry<String, int[]> e : sorted) out.put(e.getKey(), e.getValue());
        return out;
    }
}
