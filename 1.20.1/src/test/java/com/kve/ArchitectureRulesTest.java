package com.kve;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectureRulesTest {

    private static final Path MAIN = Path.of("src", "main", "java", "com", "kve");

    private record Rule(String name, Pattern pattern, String useInstead, Set<String> allowed) {}

    private static final List<Rule> RULES = List.of(
            new Rule("escapado JS a mano",
                    Pattern.compile(Pattern.quote(".replace(\"'\", \"\\\\'\")")),
                    "com.kve.common.util.JsStrings.escape/quote",
                    Set.of("common/util/JsStrings.java")),
            new Rule("helper privado de escapado JS",
                    Pattern.compile("private static String (jsEscape|jsString|toJsTextLiteral)\\("),
                    "com.kve.common.util.JsStrings",
                    Set.of()),
            new Rule("rename atomico a mano",
                    Pattern.compile("StandardCopyOption\\.ATOMIC_MOVE\\)"),
                    "com.kve.common.util.SafeFiles.writeAtomic/moveReplacing",
                    Set.of("common/util/SafeFiles.java", "common/structure/StructureNbtIO.java")),
            new Rule("formato %.4f a mano en codigo comun",
                    Pattern.compile("\"%\\.4f\""),
                    "com.kve.common.util.KveNumbers",
                    Set.of("common/util/KveNumbers.java")),
            new Rule("ruta kubejs/config escrita a mano en un configDir()",
                    Pattern.compile("configDir\\(\\)\\s*\\{\\s*return Paths\\.get\\(\"kubejs\", \"config\"\\)"),
                    "KveScriptPaths.CONFIG_ROOT",
                    Set.of()));

    @Test
    void sharedHelpersAreNotCopiedAgain() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String rel = MAIN.relativize(f).toString().replace('\\', '/');
                String[] lines = Files.readString(f, StandardCharsets.UTF_8).split("\n", -1);
                for (Rule r : RULES) {
                    if (r.allowed().contains(rel)) continue;
                    if (r.name().startsWith("formato") && rel.startsWith("client/")) continue;
                    for (int i = 0; i < lines.length; i++) {
                        String line = lines[i].trim();
                        if (line.startsWith("*") || line.startsWith("//") || line.startsWith("/*")) continue;
                        if (r.pattern().matcher(lines[i]).find()) {
                            violations.add(rel + ":" + (i + 1) + "  " + r.name() + " -> usar " + r.useInstead());
                        }
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), "Copias de helpers compartidos:\n  " + String.join("\n  ", violations));
    }

    private static final Set<String> COMMON_TO_CLIENT_LEGACY = Set.of(
            "common/config/KVEConfig.java",
            "common/customitem/CustomBlockDraft.java",
            "common/customitem/CustomBlockExporter.java",
            "common/customitem/CustomItemDraft.java",
            "common/customitem/CustomItemExporter.java",
            "common/customitem/TextureValidation.java",
            "common/kubejs/KubeJSItemAttrExporter.java",
            "common/kubejs/LootTableExporter.java",
            "common/kubejs/SemanticValidator.java",
            "common/kubejs/TagExporter.java",
            "common/network/PermissionAdminSyncPacket.java",
            "common/network/PermissionSyncPacket.java",
            "common/network/StructureRemoteResponsePacket.java",
            "common/network/StructureWorldGenerateResponsePacket.java",
            "common/network/StructureWorldScanResponsePacket.java",
            "common/persist/CraftEditorState.java",
            "common/structure/StructureNbtIO.java",
            "common/structure/StructureUsageIndex.java");

    @Test
    void commonCodeDoesNotGainNewClientDependencies() throws IOException {
        Pattern clientRef = Pattern.compile("com\\.kve\\.client\\.");
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN.resolve("common"))) {
            for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String rel = MAIN.relativize(f).toString().replace('\\', '/');
                if (COMMON_TO_CLIENT_LEGACY.contains(rel)) continue;
                for (String line : Files.readString(f, StandardCharsets.UTF_8).split("\n", -1)) {
                    String t = line.trim();
                    if (t.startsWith("*") || t.startsWith("//") || t.startsWith("/*")) continue;
                    if (clientRef.matcher(line).find()) { violations.add(rel + ": " + t); break; }
                }
            }
        }
        assertTrue(violations.isEmpty(), "common/ no debe usar com.kve.client (crashea en servidor dedicado):\n  "
                + String.join("\n  ", violations));
    }
}
