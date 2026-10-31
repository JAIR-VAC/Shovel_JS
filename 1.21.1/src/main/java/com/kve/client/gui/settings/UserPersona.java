package com.kve.client.gui.settings;

import com.kve.common.kubejs.KubeJSBlockAttrExporter;
import com.kve.common.kubejs.KubeJSExporter;
import com.kve.common.kubejs.KubeJSItemAttrExporter;
import com.kve.common.kubejs.KubeJSMobExporter;
import com.kve.common.kubejs.LootTableExporter;
import com.kve.common.kubejs.TagExporter;
import com.kve.common.kubejs.TradeExporter;
import com.kve.common.persist.CraftEditorState;
import com.kve.common.structure.StructureFrequencyExporter;
import com.kve.common.worldgen.WorldGenExporter;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Para que viene cada persona al Studio. Lo elige en la
 * bienvenida ({@link StudioOnboardingScreen}) y decide la pestania con la que
 * arranca el shell, que pestanias se marcan como "las tuyas" y los primeros
 * pasos sugeridos - cada uno con su chequeo contra el estado real, asi la
 * lista muestra que ya se hizo en vez de repetir lo mismo siempre.
 */
@OnlyIn(Dist.CLIENT)
public enum UserPersona {

    MODPACK("minecraft:crafting_table", "gui.kve.studio.tab.recipes",
            Set.of("gui.kve.studio.tab.recipes", "gui.kve.studio.tab.tags", "gui.kve.studio.tab.loot", "gui.kve.studio.tab.properties"),
            List.of(
                    new Step("modpack.recipe", "gui.kve.studio.tab.recipes", () -> !KubeJSExporter.getAddedRecipes().isEmpty()),
                    new Step("modpack.item", "gui.kve.studio.tab.properties",
                            () -> !KubeJSItemAttrExporter.getEntries().isEmpty() || !KubeJSBlockAttrExporter.getEntries().isEmpty()),
                    new Step("modpack.tag", "gui.kve.studio.tab.tags", () -> !TagExporter.getEntries().isEmpty()))),

    SERVER("minecraft:emerald", "gui.kve.studio.tab.mobs",
            Set.of("gui.kve.studio.tab.mobs", "gui.kve.studio.tab.trades", "gui.kve.studio.tab.loot", "gui.kve.studio.tab.events"),
            List.of(
                    new Step("server.mob", "gui.kve.studio.tab.mobs", KubeJSMobExporter::hasEntries),
                    new Step("server.trade", "gui.kve.studio.tab.trades", () -> !TradeExporter.getEntries().isEmpty()),
                    new Step("server.loot", "gui.kve.studio.tab.loot", () -> !LootTableExporter.getEntries().isEmpty()))),

    SCRIPTER("minecraft:writable_book", "gui.kve.studio.tab.files",
            Set.of("gui.kve.studio.tab.files", "gui.kve.studio.tab.events", "gui.kve.studio.tab.scan"),
            List.of(
                    new Step("scripter.files", "gui.kve.studio.tab.files",
                            () -> Files.exists(Paths.get("kubejs", "config", "shovel_js_files_ui.json"))),
                    new Step("scripter.scan", "gui.kve.studio.tab.scan", UserPersona::hasOwnScripts),
                    new Step("scripter.events", "gui.kve.studio.tab.settings", KveClientSettings::betaEventsEnabled))),

    WORLD("minecraft:grass_block", "gui.kve.studio.tab.worldgen",
            Set.of("gui.kve.studio.tab.worldgen", "gui.kve.studio.tab.structures", "gui.kve.studio.tab.mobs"),
            List.of(
                    new Step("world.worldgen", "gui.kve.studio.tab.worldgen", () -> {
                        WorldGenExporter.loadIfNeeded();
                        return !WorldGenExporter.getEntries().isEmpty();
                    }),
                    new Step("world.structures", "gui.kve.studio.tab.structures", () -> !StructureFrequencyExporter.getEntries().isEmpty()),
                    new Step("world.mobs", "gui.kve.studio.tab.mobs", KubeJSMobExporter::hasEntries)));

    /** Un paso sugerido: texto ({@code gui.kve.persona.step.<key>}), pestania a la que lleva y si ya esta hecho. */
    public record Step(String key, String tabKey, BooleanSupplier done) {
        public boolean isDone() {
            try { return done.getAsBoolean(); } catch (RuntimeException e) { return false; }
        }
    }

    public final String iconItem;
    public final String startTab;
    public final Set<String> tabs;
    public final List<Step> steps;

    UserPersona(String iconItem, String startTab, Set<String> tabs, List<Step> steps) {
        this.iconItem = iconItem;
        this.startTab = startTab;
        this.tabs = tabs;
        this.steps = steps;
    }

    public String labelKey() { return "gui.kve.persona." + name().toLowerCase() + ".name"; }
    public String descKey() { return "gui.kve.persona." + name().toLowerCase() + ".desc"; }

    /** Paso comun a todos: aplicar al menos un cambio (deja historial en kubejs/config). */
    public static boolean hasAppliedSomething() {
        Path dir = Paths.get("kubejs", "config");
        if (!Files.isDirectory(dir)) return false;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "shovel_js_changelog_*.json")) {
            return ds.iterator().hasNext();
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean hasOwnScripts() {
        Path dir = Paths.get("kubejs", "server_scripts");
        if (!Files.isDirectory(dir)) return false;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.js")) {
            return ds.iterator().hasNext();
        } catch (IOException e) {
            return false;
        }
    }

    /** El perfil guardado, o null si el jugador todavia no eligio. */
    public static UserPersona current() {
        String v = KveClientSettings.getString(KveClientSettings.PERSONA, "");
        for (UserPersona p : values()) if (p.name().equalsIgnoreCase(v)) return p;
        return null;
    }

    public static void set(UserPersona p) {
        KveClientSettings.putString(KveClientSettings.PERSONA, p == null ? "" : p.name());
    }

    static void loadState() {
        try { CraftEditorState.loadIfNeeded(); } catch (RuntimeException ignored) {}
        try { TradeExporter.loadStateIfNeeded(); } catch (RuntimeException ignored) {}
    }
}
