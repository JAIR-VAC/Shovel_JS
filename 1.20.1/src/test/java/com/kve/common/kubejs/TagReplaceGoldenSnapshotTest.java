package com.kve.common.kubejs;

import com.kve.common.kubejs.TagExporter.RegistryType;
import com.kve.common.kubejs.TagExporter.TagEntry;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Golden/snapshot test for "replace" mode: a full vanilla
 * {@code "replace": true} datapack tag JSON, pinned exactly (path + pretty-
 * printed content) via {@code TagExporter#previewTagJS()}, same read-only
 * path the in-game preview uses.
 */
class TagReplaceGoldenSnapshotTest {

    @Test
    void replaceModeEmitsAVanillaReplaceTagDatapackFile() {
        TagEntry e = new TagEntry();
        e.regType = RegistryType.ITEM;
        e.tagId = "shovel_js:my_tag";
        e.replace = true;
        e.added.add("minecraft:iron_ingot");
        e.added.add("minecraft:gold_ingot");

        String preview = TagExporter.previewTagJS(e);

        Path expectedPath = Paths.get("kubejs", "data")
                .resolve("shovel_js").resolve("tags").resolve("items").resolve("my_tag.json");
        String expected = "// " + expectedPath + "\n"
                + "{\n"
                + "  \"replace\": true,\n"
                + "  \"values\": [\n"
                + "    \"minecraft:iron_ingot\",\n"
                + "    \"minecraft:gold_ingot\"\n"
                + "  ]\n"
                + "}";

        assertEquals(expected, preview);
    }

    @Test
    void replaceModeWithNoValidValuesIsReportedRatherThanWritingAnEmptyReplaceFile() throws Exception {
        TagEntry e = new TagEntry();
        e.regType = RegistryType.ITEM;
        e.tagId = "shovel_js:wipe_risk";
        e.replace = true;
        e.added.add("totally not an id");

        TagExporter.clearAll();
        try {
            TagExporter.loadEntry(e);
            java.lang.reflect.Method m = TagExporter.class.getDeclaredMethod("writeAll");
            m.setAccessible(true);
            m.invoke(null);

            assertTrue(TagExporter.getLastWriteWarnings().stream()
                    .anyMatch(w -> w.contains("totally not an id")));
        } finally {
            TagExporter.clearAll();
        }
    }
}
