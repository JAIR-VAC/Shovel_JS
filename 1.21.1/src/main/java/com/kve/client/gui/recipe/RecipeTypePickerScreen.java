package com.kve.client.gui.recipe;

import com.kve.client.gui.SafeItemRender;
import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.KveScaledEditBox;
import com.kve.client.gui.ScrollBar;
import com.kve.client.gui.SearchFilter;

import com.kve.common.config.KVEConfig;
import com.kve.common.recipetype.RecipeTypeDescriptor;
import com.kve.common.recipetype.RecipeTypeDescriptorRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.*;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class RecipeTypePickerScreen extends Screen {

    private static final int GUI_W = 360;
    private static final int GUI_H = 280;
    private static final int LIST_H = 200;
    private static final int ROW_H = 20;
    private static final int HEADER_H = 16;

    public static final String EC_TABLE_SENTINEL_ID = "shovel_js:ec_table_shaped";
    public static final String EC_ENDER_SENTINEL_ID = "shovel_js:ec_ender_shaped";
    public static final String EC_FLUX_SENTINEL_ID  = "shovel_js:ec_flux_shaped";
    public static final String EC_COMBO_SENTINEL_ID = "shovel_js:ec_combination";

    private static final Map<String, Boolean> SECTION_EXPANDED = new HashMap<>();

    private final Screen parent;
    private final Consumer<String> onPick;

    private final List<Section> sections = new ArrayList<>();
    private final List<Row> visibleRows = new ArrayList<>();

    private EditBox searchBox;
    private String searchFilter = "";
    private ScrollBar scrollBar;

    private int L, T;

    private String flashMsg = "";
    private long flashAt = 0;
    private void flash(String msg) { flashMsg = msg; flashAt = System.currentTimeMillis(); }

    public RecipeTypePickerScreen(Screen parent, Consumer<String> onPick) {
        super(Component.literal("Tipo de Receta (Todos los Mods)"));
        this.parent = parent;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        L = (width - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        clearWidgets();
        setFocused(null);

        buildSections();

        searchBox = new KveScaledEditBox(font, L + 6, T + 18, GUI_W - 12, 14,
                Component.literal("Buscar mod/maquina/tipo (ej: mekanism, anvil, forge)..."));
        searchBox.setMaxLength(64);
        searchBox.setResponder(s -> { searchFilter = s; rebuildFilter(); });
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);

        scrollBar = new ScrollBar(L + GUI_W - 10, T + 38, LIST_H, 0, LIST_H / ROW_H);
        rebuildFilter();

        addRenderableWidget(new KvePlateButton(L + GUI_W / 2 - 40, T + GUI_H - 18, 80, 14, Component.literal("Cancelar"), b -> onClose()));
        addRenderableWidget(new KvePlateButton(L + GUI_W - 17, T + 2, 15, 14, Component.literal("X"), b -> onClose()));
    }

    private static final class Section {
        final String label;
        final int headerColor;
        final boolean modLoaded;
        final List<Entry> entries;
        Section(String label, int headerColor, boolean modLoaded, List<Entry> entries) {
            this.label = label; this.headerColor = headerColor; this.modLoaded = modLoaded; this.entries = entries;
        }
    }

    private static final class Entry {
        final String recipeSerializer;
        final String displayName;
        final ItemStack icon;
        final boolean hasStationItem;
        Entry(String recipeSerializer, String displayName, ItemStack icon, boolean hasStationItem) {
            this.recipeSerializer = recipeSerializer; this.displayName = displayName; this.icon = icon;
            this.hasStationItem = hasStationItem;
        }
    }

    private static final class Row {
        final Section section;
        final Entry entry;
        Row(Section s, Entry e) { section = s; entry = e; }
        boolean isHeader() { return entry == null; }
    }

    private void buildSections() {
        sections.clear();
        RecipeTypeDescriptorRegistry.loadIfNeeded();

        Map<String, List<RecipeTypeDescriptor>> byMod = new LinkedHashMap<>();
        Set<String> coveredSerializers = new HashSet<>();
        for (RecipeTypeDescriptor d : RecipeTypeDescriptorRegistry.all()) {
            if (d.isUnsupported()) continue; // igual que el exporter: no se ofrece un tipo que no se puede exportar
            String mod = (d.modId != null && !d.modId.isBlank()) ? d.modId : namespaceOf(d.recipeSerializer);
            byMod.computeIfAbsent(mod, k -> new ArrayList<>()).add(d);
            coveredSerializers.add(d.recipeSerializer);
        }

        List<String> modIds = new ArrayList<>(byMod.keySet());
        modIds.remove("minecraft");
        // Vanilla primero, despues los mods instalados y al final los que no estan (cada grupo por nombre)
        modIds.sort(Comparator.comparing((String m) -> !RecipeTypeDescriptorRegistry.isModLoadedViaForgePublic(m))
                .thenComparing(RecipeTypePickerScreen::modNameFor, String.CASE_INSENSITIVE_ORDER));
        modIds.add(0, "minecraft");

        for (String modId : modIds) {
            List<RecipeTypeDescriptor> descriptors = byMod.get(modId);
            if (descriptors == null || descriptors.isEmpty()) continue;
            descriptors.sort(Comparator.comparing(d -> effectiveDisplayName(d)));

            List<Entry> entries = new ArrayList<>();
            for (RecipeTypeDescriptor d : descriptors) {
                entries.add(new Entry(d.recipeSerializer, effectiveDisplayName(d), stationIconFor(d), d.hasStationItem()));
            }

            boolean loaded = "minecraft".equals(modId) || RecipeTypeDescriptorRegistry.isModLoadedViaForgePublic(modId);
            String label = "minecraft".equals(modId) ? "Vanilla Minecraft" : modNameFor(modId);
            sections.add(new Section(label, headerColorFor(modId), loaded, entries));

            if ("minecraft".equals(modId) && KVEConfig.isExtendedCraftingActive()) {
                List<Entry> ec = new ArrayList<>();
                ec.add(new Entry(EC_TABLE_SENTINEL_ID, com.kve.common.Lang.get("gui.kve.recipe.type.ec_table"), new ItemStack(net.minecraft.world.item.Items.CRAFTING_TABLE), true));
                ec.add(new Entry(EC_ENDER_SENTINEL_ID, com.kve.common.Lang.get("gui.kve.recipe.type.ec_ender"), new ItemStack(net.minecraft.world.item.Items.ENDER_CHEST), true));
                ec.add(new Entry(EC_FLUX_SENTINEL_ID, com.kve.common.Lang.get("gui.kve.recipe.type.ec_flux"), new ItemStack(net.minecraft.world.item.Items.REDSTONE_BLOCK), true));
                ec.add(new Entry(EC_COMBO_SENTINEL_ID, com.kve.common.Lang.get("gui.kve.recipe.type.ec_combo"), new ItemStack(net.minecraft.world.item.Items.NETHER_STAR), true));
                sections.add(new Section("Extended Crafting", headerColorFor("extendedcrafting"), true, ec));
            }
        }

        List<Entry> others = new ArrayList<>();
        try {
            for (var serializer : BuiltInRegistries.RECIPE_SERIALIZER) {
                ResourceLocation id = BuiltInRegistries.RECIPE_SERIALIZER.getKey(serializer);
                if (id == null) continue;
                String s = id.toString();
                if (coveredSerializers.contains(s)) continue;
                others.add(new Entry(s, s, ItemStack.EMPTY, true));
            }
        } catch (Exception ignored) {}
        if (!others.isEmpty()) {
            others.sort(Comparator.comparing(e -> e.recipeSerializer));
            sections.add(new Section("Otros / sin descriptor", KveGuiStyle.TEXT_MUTED, true, others));
        }
    }

    private static String namespaceOf(String recipeSerializer) {
        if (recipeSerializer == null) return "minecraft";
        int i = recipeSerializer.indexOf(':');
        return i > 0 ? recipeSerializer.substring(0, i) : "minecraft";
    }

    private static String effectiveDisplayName(RecipeTypeDescriptor d) {
        return (d.displayName != null && !d.displayName.isBlank()) ? d.displayName : d.recipeSerializer;
    }

    private static String modNameFor(String modId) {
        String pretty = RecipeTypeDescriptorRegistry.prettyModNamePublic(modId);
        if (pretty != null && !pretty.isBlank()) return pretty;
        return modId.isEmpty() ? modId : Character.toUpperCase(modId.charAt(0)) + modId.substring(1);
    }

    private static int headerColorFor(String modId) {
        if ("minecraft".equals(modId)) return -1;
        int hash = modId.hashCode();
        float hue = (Math.abs(hash) % 360) / 360f;
        int rgb = java.awt.Color.HSBtoRGB(hue, 0.55f, 0.95f);
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    /**
     * Wrapper publico de {@link #stationIconFor()} por type id (recipeSerializer)
     * en vez de un {@link RecipeTypeDescriptor} ya resuelto - para pantallas
     * que solo tienen el id a mano (ej. {@code MachineRecipeEditScreen}
     * mostrando el icono de la maquina inline, sin abrir este picker) y no
     * quieren duplicar la heuristica stationItem -> id del serializer -> mesa
     * de crafteo. {@link ItemStack#EMPTY} si el id esta vacio o no hay
     * descriptor registrado para el (caller decide el fallback visual, p.ej.
     * un slot vacio con "?").
     */
    public static ItemStack stationIconForTypeId(String typeId) {
        if (typeId == null || typeId.isBlank()) return ItemStack.EMPTY;
        RecipeTypeDescriptorRegistry.loadIfNeeded();
        return RecipeTypeDescriptorRegistry.find(typeId).map(RecipeTypePickerScreen::stationIconFor).orElse(ItemStack.EMPTY);
    }

    private static ItemStack stationIconFor(RecipeTypeDescriptor d) {
        if (d.hasStationItem()) {
            ItemStack s = itemStackFromId(d.stationItem);
            if (!s.isEmpty()) return s;
        }
        ItemStack guess = itemStackFromId(d.recipeSerializer);
        if (!guess.isEmpty()) return guess;
        return new ItemStack(net.minecraft.world.item.Items.CRAFTING_TABLE);
    }

    private static ItemStack itemStackFromId(String id) {
        try {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null) return ItemStack.EMPTY;
            Item item = BuiltInRegistries.ITEM.get(rl);
            return item == null || item == net.minecraft.world.item.Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    private void rebuildFilter() {
        visibleRows.clear();
        boolean searching = searchFilter != null && !searchFilter.isBlank();

        for (Section sec : sections) {
            List<Entry> matches = new ArrayList<>();
            for (Entry e : sec.entries) {
                if (!searching
                        || SearchFilter.matches(e.recipeSerializer, searchFilter)
                        || SearchFilter.matches(e.displayName, searchFilter)
                        || SearchFilter.matches(sec.label, searchFilter)) {
                    matches.add(e);
                }
            }
            if (matches.isEmpty()) continue;

            visibleRows.add(new Row(sec, null));
            boolean expanded = searching || SECTION_EXPANDED.getOrDefault(sec.label, "Vanilla Minecraft".equals(sec.label));
            if (expanded) {
                for (Entry e : matches) visibleRows.add(new Row(sec, e));
            }
        }

        if (scrollBar != null) scrollBar.setTotals(visibleRows.size(), LIST_H / ROW_H);
    }

    private void toggleSection(Section sec) {
        boolean cur = SECTION_EXPANDED.getOrDefault(sec.label, "Vanilla Minecraft".equals(sec.label));
        SECTION_EXPANDED.put(sec.label, !cur);
        rebuildFilter();
    }

    // Ver javadoc del picker viejo: volver a `parent` ANTES de invocar
    // onPick importa porque algunos tipos (los que tienen slotLayout real)
    // navegan solos a un MachineRecipeEditScreen dentro de onPick.
    private void pick(String id) {
        minecraft.setScreen(parent);
        onPick.accept(id);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, "Tipos de receta (" + countEntries() + ")");

        int listY = T + 38;
        int scroll = scrollBar.getScroll();
        int rowsVisible = LIST_H / ROW_H;
        for (int i = 0; i < rowsVisible; i++) {
            int idx = i + scroll;
            if (idx >= visibleRows.size()) break;
            Row row = visibleRows.get(idx);
            int ry = listY + i * ROW_H;

            if (row.isHeader()) {
                renderHeader(g, row.section, ry, mx, my);
            } else {
                renderEntryRow(g, row.section, row.entry, ry, mx, my);
            }
        }
        scrollBar.render(g);

        if (!flashMsg.isEmpty() && System.currentTimeMillis() - flashAt < 4500) {
            KveGuiStyle.drawTextSafe(g, flashMsg, L + 6, T + GUI_H - 30, GUI_W - 12, KveGuiStyle.ACCENT_GOLD);
        }

        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
    }

    private int countEntries() {
        int n = 0;
        for (Row r : visibleRows) if (!r.isHeader()) n++;
        return n;
    }

    private void renderHeader(GuiGraphics g, Section sec, int ry, int mx, int my) {
        boolean hov = mx >= L + 4 && mx < L + GUI_W - 12 && my >= ry && my < ry + HEADER_H;
        g.fill(L + 4, ry, L + GUI_W - 12, ry + HEADER_H, hov ? KveGuiStyle.BORDER_FIELD : KveGuiStyle.BG_TITLEBAR);
        g.fill(L + 4, ry, L + 6, ry + HEADER_H, sec.headerColor);

        boolean expanded = SECTION_EXPANDED.getOrDefault(sec.label, "Vanilla Minecraft".equals(sec.label))
                || (searchFilter != null && !searchFilter.isBlank());
        String arrow = expanded ? "\u25BC" : "\u25B6";
        String status = sec.modLoaded ? "" : " (no instalado)";
        int color = sec.modLoaded ? KveGuiStyle.ACCENT_GOLD : KveGuiStyle.TEXT_MUTED;
        KveGuiStyle.drawTextSafe(g, arrow + " " + sec.label + status + " (" + sec.entries.size() + ")",
                L + 10, ry + 4, GUI_W - 24, color);
    }

    private void renderEntryRow(GuiGraphics g, Section sec, Entry e, int ry, int mx, int my) {
        boolean hov = mx >= L + 4 && mx < L + GUI_W - 12 && my >= ry && my < ry + ROW_H - 1;
        g.fill(L + 4, ry, L + GUI_W - 12, ry + ROW_H - 1, hov ? KveGuiStyle.BORDER_FIELD : KveGuiStyle.BG_FIELD);

        int iconX = L + 8;
        int iconY = ry + 1;
        if (!e.icon.isEmpty()) {
            SafeItemRender.render(g, e.icon, iconX, iconY);
        }

        int textColor = sec.modLoaded ? KveGuiStyle.TEXT_PRIMARY : KveGuiStyle.TEXT_MUTED;
        String name = e.displayName;
        boolean showPickBtn = !e.hasStationItem;
        int maxW = GUI_W - 50 - (showPickBtn ? 22 : 0);
        KveGuiStyle.drawTextSafe(g, name, iconX + 20, ry + 6, maxW, textColor);

        if (showPickBtn) {
            int[] r = stationPickHotspot(ry);
            boolean btnHov = mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
            g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], btnHov ? KveGuiStyle.ACCENT_GOLD : KveGuiStyle.BORDER_FIELD);
            KveGuiStyle.drawTextSafe(g, "\u25CE", r[0] + 3, r[1] + 2, r[2], btnHov ? KveGuiStyle.TEXT_PRIMARY : KveGuiStyle.TEXT_MUTED);
            if (btnHov) {
                g.renderTooltip(font, Component.literal("Sin estacion - mira el bloque real y click para fijarla"), mx, my);
            }
        }
    }

    private int[] stationPickHotspot(int ry) {
        return new int[]{ L + GUI_W - 30, ry + 1, 16, ROW_H - 3 };
    }

    private void pickStationFromCrosshair(Entry e) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            flash("No se pudo leer el mundo.");
            return;
        }
        if (!(minecraft.hitResult instanceof BlockHitResult bhr)) {
            flash("Mira el bloque de la estacion antes de apretar este boton.");
            return;
        }
        BlockState state = minecraft.level.getBlockState(bhr.getBlockPos());
        Block block = state.getBlock();
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        if (id == null || block == net.minecraft.world.level.block.Blocks.AIR) {
            flash("Ese bloque no tiene id valido.");
            return;
        }
        boolean ok = RecipeTypeDescriptorRegistry.saveStationItemOverride(e.recipeSerializer, id.toString());
        if (ok) {
            flash("Estacion fijada: " + id + " -> " + e.recipeSerializer);
            buildSections();
            rebuildFilter();
        } else {
            flash("No se pudo guardar la estacion (ver log).");
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (scrollBar.mouseClicked(mx, my, btn)) return true;
        int listY = T + 38;
        int scroll = scrollBar.getScroll();
        int rowsVisible = LIST_H / ROW_H;
        for (int i = 0; i < rowsVisible; i++) {
            int idx = i + scroll;
            if (idx >= visibleRows.size()) break;
            Row row = visibleRows.get(idx);
            int ry = listY + i * ROW_H;
            int rh = row.isHeader() ? HEADER_H : ROW_H - 1;
            if (!row.isHeader() && !row.entry.hasStationItem) {
                int[] r = stationPickHotspot(ry);
                if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                    pickStationFromCrosshair(row.entry);
                    return true;
                }
            }
            if (mx >= L + 4 && mx < L + GUI_W - 12 && my >= ry && my < ry + rh) {
                if (row.isHeader()) {
                    toggleSection(row.section);
                } else {
                    pick(row.entry.recipeSerializer);
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (scrollBar.mouseDragged(mx, my, btn, dx, dy)) return true;
        return super.mouseDragged(mx, my, btn, dx, dy);
    }
    @Override public boolean mouseReleased(double mx, double my, int btn) {
        scrollBar.mouseReleased(mx, my, btn);
        return super.mouseReleased(mx, my, btn);
    }
    @Override public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        scrollBar.mouseScrolled(delta); return true;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override public boolean isPauseScreen() { return false; }
}
