package com.kve.common.packtools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kve.common.kubejs.KubeJSMobExporter;
import com.kve.common.kubejs.TradeExporter;
import com.kve.common.persist.MobTemplateStore;
import com.kve.common.trade.TradeOfferTemplates;
import com.kve.common.util.SafeFiles;
import com.kve.common.worldgen.WorldGenEntry;
import com.kve.common.worldgen.WorldGenExporter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Biblioteca de plantillas COMPARTIDA entre packs. Las plantillas
 * de mobs ({@link MobTemplateStore}) y de comercios ({@link TradeOfferTemplates})
 * viven dentro de cada pack ({@code kubejs/config}); esta biblioteca guarda
 * paquetes con ellas en la carpeta del usuario ({@code ~/.shovel_js/library}),
 * asi un jefe o un set de comercios armado en un pack se reusa en otro sin
 * copiar archivos a mano. Un paquete es un JSON autocontenido: tambien se
 * puede pasar a otra persona.
 * <p>
 * Tambien lleva reglas de world gen (vetas, extras de bioma,
 * terreno) - "copiar una regla de un mundo a otro". Al importar no se aplican
 * solas: {@link ImportResult#worldGen()} las devuelve para que la pantalla las
 * encole como cambio pendiente (necesitan volver a entrar al mundo).
 */
public final class TemplateLibrary {

    private TemplateLibrary() {}

    public static final int FORMAT = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

    private static Path dirOverride = null;

    public static Path dir() {
        return dirOverride != null ? dirOverride : Paths.get(System.getProperty("user.home"), ".shovel_js", "library");
    }

    static void setDirForTests(Path dir) { dirOverride = dir; }

    public record BundleInfo(String fileName, String name, int mobs, int trades, int worldGen, long createdAt) {}

    public record ImportResult(int mobs, int trades, List<WorldGenEntry> worldGen) {}

    /** Paquete con TODAS las plantillas del pack actual. Devuelve el archivo creado. */
    public static Path exportCurrent(String name) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        root.addProperty("name", name);
        root.addProperty("createdAt", System.currentTimeMillis());
        JsonArray mobs = new JsonArray();
        for (MobTemplateStore.MobTemplate t : MobTemplateStore.getAll()) {
            JsonObject m = new JsonObject();
            m.addProperty("name", t.name);
            m.add("data", GSON.toJsonTree(t.data));
            mobs.add(m);
        }
        root.add("mobTemplates", mobs);
        JsonObject trades = new JsonObject();
        for (String n : TradeOfferTemplates.names()) {
            JsonArray offers = new JsonArray();
            for (TradeExporter.TradeOffer o : TradeOfferTemplates.get(n)) offers.add(TradeOfferTemplates.offerToJson(o));
            trades.add(n, offers);
        }
        root.add("tradeTemplates", trades);
        JsonArray rules = new JsonArray();
        for (WorldGenEntry e : WorldGenExporter.getEntries()) rules.add(WorldGenExporter.entryToJson(e));
        root.add("worldGenRules", rules);
        Path file = uniqueFile(slug(name));
        SafeFiles.writeAtomic(file, GSON.toJson(root));
        return file;
    }

    /** Paquete con UNA regla de world gen (boton "A la biblioteca" del editor de world gen). */
    public static Path exportWorldGenRule(WorldGenEntry e, String name) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        root.addProperty("name", name);
        root.addProperty("createdAt", System.currentTimeMillis());
        JsonArray rules = new JsonArray();
        rules.add(WorldGenExporter.entryToJson(e));
        root.add("worldGenRules", rules);
        Path file = uniqueFile(slug(name));
        SafeFiles.writeAtomic(file, GSON.toJson(root));
        return file;
    }

    /** Cantidad de plantillas de mobs, comercios y reglas de world gen del pack actual (lo que guardaria {@link #exportCurrent()}). */
    public static int[] currentCounts() {
        return new int[]{MobTemplateStore.getAll().size(), TradeOfferTemplates.names().size(), WorldGenExporter.getEntries().size()};
    }

    public static List<BundleInfo> list() {
        List<BundleInfo> out = new ArrayList<>();
        if (!Files.isDirectory(dir())) return out;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir(), "*.json")) {
            for (Path p : ds) {
                try {
                    JsonObject o = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
                    out.add(new BundleInfo(p.getFileName().toString(),
                            o.has("name") ? o.get("name").getAsString() : p.getFileName().toString(),
                            o.has("mobTemplates") ? o.getAsJsonArray("mobTemplates").size() : 0,
                            o.has("tradeTemplates") ? o.getAsJsonObject("tradeTemplates").size() : 0,
                            o.has("worldGenRules") ? o.getAsJsonArray("worldGenRules").size() : 0,
                            o.has("createdAt") ? o.get("createdAt").getAsLong() : 0L));
                } catch (Exception ignored) {
                }
            }
        } catch (IOException ignored) {}
        out.sort(Comparator.comparingLong(BundleInfo::createdAt).reversed());
        return out;
    }

    /**
     * Copia las plantillas del paquete al pack actual. Un nombre que ya existe
     * no se pisa: se importa como "nombre (2)".
     */
    public static ImportResult importBundle(String fileName) throws IOException {
        Path p = resolve(fileName);
        JsonObject root = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
        int mobs = 0, trades = 0;
        if (root.has("mobTemplates")) {
            for (JsonElement el : root.getAsJsonArray("mobTemplates")) {
                JsonObject m = el.getAsJsonObject();
                KubeJSMobExporter.MobEntry entry = GSON.fromJson(m.get("data"), KubeJSMobExporter.MobEntry.class);
                if (entry == null) continue;
                String name = m.get("name").getAsString();
                String unique = name;
                for (int i = 2; MobTemplateStore.exists(unique); i++) unique = name + " (" + i + ")";
                MobTemplateStore.saveOrReplace(unique, entry);
                mobs++;
            }
        }
        if (root.has("tradeTemplates")) {
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("tradeTemplates").entrySet()) {
                List<TradeExporter.TradeOffer> offers = new ArrayList<>();
                for (JsonElement el : e.getValue().getAsJsonArray()) offers.add(TradeOfferTemplates.offerFromJson(el.getAsJsonObject()));
                String unique = e.getKey();
                for (int i = 2; TradeOfferTemplates.names().contains(unique); i++) unique = e.getKey() + " (" + i + ")";
                TradeOfferTemplates.save(unique, offers);
                trades++;
            }
        }
        List<WorldGenEntry> rules = new ArrayList<>();
        if (root.has("worldGenRules")) {
            List<String> taken = new ArrayList<>();
            for (WorldGenEntry existing : WorldGenExporter.getEntries()) taken.add(existing.id);
            for (JsonElement el : root.getAsJsonArray("worldGenRules")) {
                WorldGenEntry e = WorldGenExporter.entryFromJson(el.getAsJsonObject());
                if (e == null || e.id == null || e.id.isBlank()) continue;
                e.id = uniqueRuleId(e.id, taken);
                taken.add(e.id);
                e.oreVeins.replaceAll(WorldGenEntry.OreVein::copyWithNewId);
                rules.add(e);
            }
        }
        return new ImportResult(mobs, trades, rules);
    }

    static String uniqueRuleId(String id, List<String> taken) {
        if (!taken.contains(id)) return id;
        for (int i = 2; ; i++) {
            String c = id + "_" + i;
            if (!taken.contains(c)) return c;
        }
    }

    public static void delete(String fileName) throws IOException {
        Files.deleteIfExists(resolve(fileName));
    }

    private static Path resolve(String fileName) throws IOException {
        if (fileName == null || !fileName.matches("[a-z0-9._-]+\\.json")) throw new IOException("Nombre de paquete invalido: " + fileName);
        return dir().resolve(fileName);
    }

    private static Path uniqueFile(String slug) {
        Path p = dir().resolve(slug + ".json");
        for (int i = 2; Files.exists(p); i++) p = dir().resolve(slug + "-" + i + ".json");
        return p;
    }

    static String slug(String name) {
        String s = name == null ? "" : name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-").replaceAll("-{2,}", "-");
        s = s.replaceAll("^-|-$", "");
        if (s.isEmpty()) s = "plantillas";
        return s.length() > 40 ? s.substring(0, 40) : s;
    }
}
