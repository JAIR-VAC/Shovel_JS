package com.kve;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * (separacion en 2 jars): el jar del runtime ({@code kve}) tiene
 * que funcionar SIN el jar del editor ({@code kve_editor}). Si una clase del
 * runtime referencia una del editor, un modpack que quita el editor crashea
 * con NoClassDefFoundError en cuanto se ejecuta ese camino. Este test lee las
 * clases compiladas del runtime y falla si alguna nombra un paquete del
 * editor (misma lista que EDITOR_PACKAGES en build.gradle).
 */
class RuntimeEditorSplitTest {

    static final String[] EDITOR = {"com/kve/client/", "com/kve/editor/", "com/kve/integration/viewer/", "com/kve/common/network/"};

    private static boolean isEditor(String internalName) {
        for (String p : EDITOR) if (internalName.startsWith(p)) return true;
        return false;
    }

    @Test
    void runtimeClassesDoNotReferenceEditorPackages() throws Exception {
        Path classes = Paths.get(System.getProperty("kve.projectDir", "."), "build", "classes", "java", "main");
        Assumptions.assumeTrue(Files.isDirectory(classes), "sin clases compiladas");
        Pattern ref = Pattern.compile("com[/.]kve[/.](client|editor|integration[/.]viewer|common[/.]network)[/.][A-Za-z0-9_/.$]+");
        List<String> problems = new ArrayList<>();
        int scanned = 0;
        try (Stream<Path> walk = Files.walk(classes)) {
            for (Path p : (Iterable<Path>) walk.filter(f -> f.toString().endsWith(".class"))::iterator) {
                String internal = classes.relativize(p).toString().replace('\\', '/');
                if (isEditor(internal)) continue;
                scanned++;
                String bytes = new String(Files.readAllBytes(p), StandardCharsets.ISO_8859_1);
                TreeSet<String> found = new TreeSet<>();
                Matcher m = ref.matcher(bytes);
                while (m.find()) found.add(m.group());
                if (!found.isEmpty()) problems.add(internal + " -> " + found);
            }
        }
        assertTrue(scanned > 50, "se escanearon muy pocas clases: " + scanned);
        assertTrue(problems.isEmpty(), "Clases del runtime que referencian el editor:\n" + String.join("\n", problems));
    }
}
