package com.kve.common.recipetype;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Canvas de solo entrada, estructuras de estacion por JSON y los
 * ids de estacion/decoracion contra los jars reales de extrasources/.
 */
class StationAndCanvasDescriptorTest {

    @Test
    void inputOnlyLayoutsSurviveForNoOutputTypes() {
        RecipeTypeDescriptorRegistry.reload();
        for (String id : List.of("aether:item_placement_ban", "immersiveengineering:thermoelectric_source",
                "botanypots:block_derived_soil", "apotheosis:purity_upgrade", "ars_nouveau:armor_upgrade")) {
            RecipeTypeDescriptor d = RecipeTypeDescriptorRegistry.find(id).orElseThrow();
            assertTrue(d.noOutput, id);
            assertNotNull(d.slotLayout, id + " perdio su canvas de solo entrada");
            assertTrue(d.slotLayout.hasOnlyInputs(), id);
        }
    }

    @Test
    void inputOnlyLayoutIsRejectedForTypesWithOutput() {
        JsonObject o = JsonParser.parseString("[{\"recipeSerializer\":\"test:x\",\"slotLayout\":{\"slots\":"
                + "[{\"id\":\"a\",\"role\":\"input\",\"x\":0,\"y\":0}]}}]").getAsJsonArray().get(0).getAsJsonObject();
        RecipeTypeDescriptor d = RecipeTypeDescriptorRegistry.fromJson(o);
        assertNull(d.slotLayout);
    }

    @Test
    void stationStructureIsParsedFromJson() {
        RecipeTypeDescriptorRegistry.reload();
        RecipeTypeDescriptor press = RecipeTypeDescriptorRegistry.find("create:pressing").orElseThrow();
        assertEquals(1, press.stationStructure.size());
        assertEquals(-2, press.stationStructure.get(0).y);
        assertEquals("create:depot", press.stationStructure.get(0).block);
        RecipeTypeDescriptor daisy = RecipeTypeDescriptorRegistry.find("botania:pure_daisy").orElseThrow();
        assertEquals(9, daisy.stationStructure.size());
    }

    @Test
    void stationIdsExistInReferenceJars() throws IOException {
        Path dir = Path.of(System.getProperty("kve.projectDir", "."), "extrasources");
        if (!Files.isDirectory(dir)) return;
        Set<String> ids = new HashSet<>();
        Set<String> namespaces = new HashSet<>();
        Pattern asset = Pattern.compile("assets/([^/]+)/(?:models/item|blockstates)/(.+)\\.json");
        Pattern lang = Pattern.compile("assets/([^/]+)/lang/en_us\\.json");
        try (DirectoryStream<Path> jars = Files.newDirectoryStream(dir, "*.jar")) {
            for (Path jar : jars) {
                try (ZipFile z = new ZipFile(jar.toFile())) {
                    for (ZipEntry e : java.util.Collections.list(z.entries())) {
                        Matcher m = asset.matcher(e.getName());
                        if (m.matches()) ids.add(m.group(1) + ":" + m.group(2));
                        // Un namespace cuenta como "de este jar" solo si trae su propio lang:
                        // otros mods meten assets sueltos de compat (Iron's trae algunos de apotheosis).
                        Matcher l = lang.matcher(e.getName());
                        if (l.matches()) namespaces.add(l.group(1));
                    }
                }
            }
        }
        RecipeTypeDescriptorRegistry.reload();
        List<String> bad = new ArrayList<>();
        for (RecipeTypeDescriptor d : RecipeTypeDescriptorRegistry.all()) {
            List<String> refs = new ArrayList<>();
            if (d.hasStationItem()) refs.add(d.stationItem);
            for (RecipeTypeDescriptor.StationBlock b : d.stationStructure) refs.add(b.block);
            if (d.slotLayout != null) for (RecipeTypeDescriptor.DecorItem di : d.slotLayout.decorItems) refs.add(di.item);
            for (String ref : refs) {
                String ns = ref.contains(":") ? ref.substring(0, ref.indexOf(':')) : "";
                if (namespaces.contains(ns) && !ids.contains(ref)) bad.add(d.recipeSerializer + " -> " + ref);
            }
        }
        assertTrue(bad.isEmpty(), "Ids de estacion que no existen en el jar del mod:\n" + String.join("\n", bad));
    }
}
