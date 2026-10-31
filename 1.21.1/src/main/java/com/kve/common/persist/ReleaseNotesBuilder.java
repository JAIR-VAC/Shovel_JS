package com.kve.common.persist;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Notas de version
 * en Markdown a partir del historial de cambios que KVE ya guarda por pestaña
 * (KveChangeLog, kubejs/config/kve_changelog_*.json). Pensado para pegar tal
 * cual en CurseForge/Modrinth/Discord al publicar una version del pack:
 * agrupado por dia (mas reciente primero) y dentro por seccion (Recetas,
 * Tags, Loot...), sin repetir el mismo cambio dos veces el mismo dia.
 * <p>
 * Logica pura (sin juego) para poder testearla: la pantalla le pasa las
 * entradas ya leidas.
 */
public final class ReleaseNotesBuilder {

    private ReleaseNotesBuilder() {}

    /** Una linea del historial: {@code action} = CREATE/EDIT/DELETE (nombre del enum de PendingChange). */
    public record Line(long timestamp, String action, String entryId) {}

    /** Titulo legible de cada log de KveChangeLog.Log, en el orden en que salen las secciones. */
    public static final Map<String, String> SECTION_TITLES = new LinkedHashMap<>();
    static {
        SECTION_TITLES.put("recipes", "Recetas");
        SECTION_TITLES.put("tags", "Tags");
        SECTION_TITLES.put("loot", "Loot tables");
        SECTION_TITLES.put("content_items", "Items");
        SECTION_TITLES.put("content_blocks", "Bloques");
        SECTION_TITLES.put("content_mobs", "Mobs");
        SECTION_TITLES.put("trades", "Comercios de aldeanos");
        SECTION_TITLES.put("events", "Eventos");
        SECTION_TITLES.put("worldgen", "Generacion de mundo");
        SECTION_TITLES.put("pack_rules", "Reglas del pack");
    }

    private static String verb(String action) {
        return switch (action) {
            case "CREATE" -> "Agregado";
            case "DELETE" -> "Quitado";
            default -> "Cambiado";
        };
    }

    public static String build(Map<String, List<Line>> byLog, long sinceMs, ZoneId zone, String title) {
        TreeMap<LocalDate, Map<String, Set<String>>> days = new TreeMap<>((a, b) -> b.compareTo(a));
        int total = 0;
        for (String logId : orderedLogs(byLog.keySet())) {
            List<Line> lines = new ArrayList<>(byLog.getOrDefault(logId, List.of()));
            lines.sort((a, b) -> Long.compare(a.timestamp(), b.timestamp()));
            for (Line l : lines) {
                if (l.timestamp() < sinceMs) continue;
                LocalDate day = Instant.ofEpochMilli(l.timestamp()).atZone(zone).toLocalDate();
                String section = SECTION_TITLES.getOrDefault(logId, logId);
                if (days.computeIfAbsent(day, d -> new LinkedHashMap<>())
                        .computeIfAbsent(section, s -> new LinkedHashSet<>())
                        .add("- " + verb(l.action()) + ": `" + l.entryId().replace("`", "'") + "`")) {
                    total++;
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(title == null || title.isBlank() ? "Notas de version" : title).append("\n\n");
        if (total == 0) {
            sb.append("_Sin cambios registrados en este periodo._\n");
            return sb.toString();
        }
        sb.append("_").append(total).append(total == 1 ? " cambio" : " cambios").append(" hechos con Shovel JS._\n");
        for (Map.Entry<LocalDate, Map<String, Set<String>>> day : days.entrySet()) {
            sb.append("\n## ").append(day.getKey()).append("\n");
            for (Map.Entry<String, Set<String>> sec : day.getValue().entrySet()) {
                sb.append("\n### ").append(sec.getKey()).append("\n\n");
                for (String line : sec.getValue()) sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    private static List<String> orderedLogs(Set<String> present) {
        List<String> out = new ArrayList<>();
        for (String k : SECTION_TITLES.keySet()) if (present.contains(k)) out.add(k);
        for (String k : present) if (!out.contains(k)) out.add(k);
        return out;
    }
}
