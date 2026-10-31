package com.kve.common.persist;

import com.kve.common.recipe.UncraftableAudit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

/** Notas de version, instantaneas del pack e items sin receta. */
class PackToolsLogicTest {

    private static final ZoneId UTC = ZoneOffset.UTC;

    private static long at(int y, int m, int d, int h) {
        return LocalDate.of(y, m, d).atTime(h, 0).toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    @Test
    void releaseNotesGroupByDayThenSectionWithoutDuplicates() {
        Map<String, List<ReleaseNotesBuilder.Line>> byLog = Map.of(
                "recipes", List.of(
                        new ReleaseNotesBuilder.Line(at(2026, 9, 27, 10), "CREATE", "copper_gear"),
                        new ReleaseNotesBuilder.Line(at(2026, 9, 27, 11), "CREATE", "copper_gear"),
                        new ReleaseNotesBuilder.Line(at(2026, 9, 28, 9), "DELETE", "minecraft:stick")),
                "tags", List.of(new ReleaseNotesBuilder.Line(at(2026, 9, 28, 12), "EDIT", "forge:ingots/copper")));
        String md = ReleaseNotesBuilder.build(byLog, 0, UTC, "Mi Pack 1.4.0");
        assertTrue(md.startsWith("# Mi Pack 1.4.0\n"));
        assertTrue(md.contains("_3 cambios hechos"), md);
        assertTrue(md.indexOf("## 2026-09-28") < md.indexOf("## 2026-09-27"), "dia mas reciente primero");
        assertTrue(md.indexOf("### Recetas") < md.indexOf("### Tags"), "orden de secciones fijo");
        assertTrue(md.contains("- Quitado: `minecraft:stick`"));
        assertTrue(md.contains("- Cambiado: `forge:ingots/copper`"));
        assertEquals(1, md.split("Agregado: `copper_gear`", -1).length - 1, "sin repetir el mismo cambio el mismo dia");
    }

    @Test
    void releaseNotesRespectTheSinceFilter() {
        Map<String, List<ReleaseNotesBuilder.Line>> byLog = Map.of("loot", List.of(
                new ReleaseNotesBuilder.Line(at(2026, 1, 1, 0), "CREATE", "old")));
        String md = ReleaseNotesBuilder.build(byLog, at(2026, 9, 1, 0), UTC, "");
        assertTrue(md.contains("Sin cambios registrados"));
        assertTrue(md.startsWith("# Notas de version"));
    }

    private static void write(Path root, String rel, String content) throws IOException {
        Path p = root.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content, StandardCharsets.UTF_8);
    }

    @Test
    void snapshotContainsOnlyKveFilesAndRestoresThem(@TempDir Path game) throws IOException {
        write(game, "kubejs/server_scripts/shovel_js/recipes/create/add.js", "v1");
        write(game, "kubejs/startup_scripts/shovel_js/items/attributes.js", "items");
        write(game, "kubejs/config/shovel_js_state.json", "{}");
        write(game, "kubejs/config/shovel_js_recipe_replacements.json", "[]");
        write(game, "kubejs/config/shovel_js_backups/old.json", "no");
        write(game, "kubejs/config/other_mod.json", "no");
        write(game, "kubejs/server_scripts/someone_else.js", "no");
        write(game, "config/shovel_js.properties", "a=1");

        Path zip = PackSnapshotManager.create(game, "Antes de v1.4!");
        assertTrue(zip.getFileName().toString().endsWith("-antes-de-v1.4.zip"), zip.toString());
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            Set<String> names = new java.util.HashSet<>();
            zf.stream().forEach(e -> names.add(e.getName()));
            assertEquals(Set.of("kubejs/server_scripts/shovel_js/recipes/create/add.js", "kubejs/startup_scripts/shovel_js/items/attributes.js",
                    "kubejs/config/shovel_js_state.json", "kubejs/config/shovel_js_recipe_replacements.json", "config/shovel_js.properties"), names);
        }

        write(game, "kubejs/server_scripts/shovel_js/recipes/create/add.js", "v2");
        write(game, "kubejs/server_scripts/shovel_js/recipes/mekanism/add.js", "nuevo");
        int n = PackSnapshotManager.restore(game, zip.getFileName().toString());
        assertEquals(5, n);
        assertEquals("v1", Files.readString(game.resolve("kubejs/server_scripts/shovel_js/recipes/create/add.js")));
        assertFalse(Files.exists(game.resolve("kubejs/server_scripts/shovel_js/recipes/mekanism/add.js")), "lo creado despues se va");
        assertTrue(Files.exists(game.resolve("kubejs/server_scripts/someone_else.js")), "nunca toca archivos ajenos a Shovel JS");
        assertTrue(PackSnapshotManager.list(game).stream().anyMatch(i -> i.fileName().contains("antes-de-restaurar")),
                "restaurar guarda antes el estado actual");
    }

    @Test
    void snapshotEntriesAndNamesAreSandboxed(@TempDir Path game) {
        assertTrue(PackSnapshotManager.isAllowedEntry("kubejs/server_scripts/shovel_js/a.js"));
        assertFalse(PackSnapshotManager.isAllowedEntry("kubejs/server_scripts/shovel_js/../../evil.js"));
        assertFalse(PackSnapshotManager.isAllowedEntry("/etc/passwd"));
        assertFalse(PackSnapshotManager.isAllowedEntry("mods/evil.jar"));
        assertFalse(PackSnapshotManager.isAllowedEntry("kubejs/config/shovel_js_backups/x.json"));
        assertThrows(IOException.class, () -> PackSnapshotManager.restore(game, "../outside.zip"));
        assertThrows(IOException.class, () -> PackSnapshotManager.delete(game, "a/b.zip"));
    }

    @Test
    void snapshotCompareListsAddedRemovedAndChangedFiles(@TempDir Path game) throws IOException {
        write(game, "kubejs/server_scripts/shovel_js/recipes/create/add.js", "v1");
        write(game, "kubejs/server_scripts/shovel_js/tags/tags.js", "tags");
        write(game, "kubejs/assets/create/lang/es_es.json", "{\"a\":\"b\"}");
        write(game, "kubejs/assets/create/textures/x.png", "no es de Shovel JS");
        Path zip = PackSnapshotManager.create(game, "base");
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            assertNotNull(zf.getEntry("kubejs/assets/create/lang/es_es.json"), "los nombres visibles viajan en la instantanea");
            assertNull(zf.getEntry("kubejs/assets/create/textures/x.png"));
        }

        write(game, "kubejs/server_scripts/shovel_js/recipes/create/add.js", "v2");
        Files.delete(game.resolve("kubejs/server_scripts/shovel_js/tags/tags.js"));
        write(game, "kubejs/server_scripts/shovel_js/loot/tables.js", "loot");
        PackSnapshotManager.Diff d = PackSnapshotManager.compare(game, zip.getFileName().toString());
        assertEquals(List.of("kubejs/server_scripts/shovel_js/loot/tables.js"), d.added());
        assertEquals(List.of("kubejs/server_scripts/shovel_js/tags/tags.js"), d.removed());
        assertEquals(List.of("kubejs/server_scripts/shovel_js/recipes/create/add.js"), d.changed());
        assertFalse(d.isEmpty());
        assertThrows(IOException.class, () -> PackSnapshotManager.compare(game, "../x.zip"));
    }

    @Test
    void uncraftableAuditFiltersByModAndSkipsTechnicalItems() {
        List<String> registered = List.of("create:cogwheel", "create:shaft", "create:brass_ingot", "create:cow_spawn_egg",
                "minecraft:stick", "minecraft:command_block", "minecraft:bedrock", "mekanism:ingot_osmium");
        Set<String> produced = Set.of("create:cogwheel", "minecraft:stick");
        assertEquals(List.of("create:brass_ingot", "create:shaft"), UncraftableAudit.find(registered, produced, "create", true));
        assertEquals(List.of("create:brass_ingot", "create:cow_spawn_egg", "create:shaft"), UncraftableAudit.find(registered, produced, "create", false));
        Map<String, int[]> summary = UncraftableAudit.summaryByMod(registered, produced, true);
        assertEquals("create", summary.keySet().iterator().next(), "el mod con mas items sin receta primero");
        assertArrayEquals(new int[]{2, 3}, summary.get("create"));
        assertArrayEquals(new int[]{0, 1}, summary.get("minecraft"), "command_block/bedrock son tecnicos");
    }

    @Test
    void restoringTheOldestOfAFullSetDoesNotPruneItFirst(@TempDir Path game) throws IOException {
        write(game, "kubejs/server_scripts/shovel_js/recipes/create/add.js", "v1");
        Path oldest = PackSnapshotManager.create(game, "0");
        write(game, "kubejs/server_scripts/shovel_js/recipes/create/add.js", "v2");
        for (int i = 1; i < PackSnapshotManager.MAX_SNAPSHOTS; i++) PackSnapshotManager.create(game, "z" + i);
        assertEquals(PackSnapshotManager.MAX_SNAPSHOTS, PackSnapshotManager.list(game).size());

        PackSnapshotManager.restore(game, oldest.getFileName().toString());
        assertEquals("v1", Files.readString(game.resolve("kubejs/server_scripts/shovel_js/recipes/create/add.js")));
    }
}
