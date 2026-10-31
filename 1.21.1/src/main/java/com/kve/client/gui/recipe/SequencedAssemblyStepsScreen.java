package com.kve.client.gui.recipe;

import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.KveScaledEditBox;
import com.kve.client.gui.properties.TooltipEditScreen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kve.common.Lang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class SequencedAssemblyStepsScreen extends Screen {

    private static final int MAX_STEPS = 6;
    private static final int MAX_SALVAGE = 3;
    private static final int GUI_W = 340;
    private static final int ROW_H = 16;
    private static final int GUI_H = 150 + MAX_STEPS * ROW_H + MAX_SALVAGE * ROW_H;

    private static final class StepRow {
        String type;
        String extra = "";
        int amount = 250;
        StepRow(String type) { this.type = type; }
    }

    private static final class SalvageRow {
        String item = "";
        double chancePercent = 10.0;
    }

    private final Screen parent;
    private final Consumer<String> onSave;

    private String transitionalItem = "";
    private int loops = 5;
    private String primaryOutput = "";
    private int primaryCount = 1;
    private double primaryChancePercent = 100.0;
    private final List<StepRow> steps = new ArrayList<>();
    private final List<SalvageRow> salvage = new ArrayList<>();

    private int L, T;

    public SequencedAssemblyStepsScreen(Screen parent, String existingExtraJson, Consumer<String> onSave) {
        super(Lang.c("gui.kve.sequenced.title"));
        this.parent = parent;
        this.onSave = onSave;
        loadFrom(existingExtraJson);
    }

    private void loadFrom(String json) {
        if (json == null || json.isBlank()) return;
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            if (o.has("transitionalItem")) {
                JsonObject ti = o.getAsJsonObject("transitionalItem");
                if (ti != null && ti.has("item")) transitionalItem = ti.get("item").getAsString();
            }
            if (o.has("loops") && o.get("loops").isJsonPrimitive()) {
                loops = o.get("loops").getAsInt();
            }
            if (o.has("results") && o.get("results").isJsonArray()) {
                JsonArray results = o.getAsJsonArray("results");
                boolean first = true;
                for (var el : results) {
                    JsonObject ro = el.getAsJsonObject();
                    String id = ro.has("item") ? ro.get("item").getAsString() : "";
                    double chance = ro.has("chance") ? ro.get("chance").getAsDouble() * 100.0 : 100.0;
                    if (first) {
                        primaryOutput = id;
                        primaryCount = ro.has("count") ? ro.get("count").getAsInt() : 1;
                        primaryChancePercent = ro.has("chance") ? chance : 100.0;
                        first = false;
                    } else if (salvage.size() < MAX_SALVAGE) {
                        SalvageRow sr = new SalvageRow();
                        sr.item = id;
                        sr.chancePercent = chance;
                        salvage.add(sr);
                    }
                }
            }
            if (o.has("sequence") && o.get("sequence").isJsonArray()) {
                for (var el : o.getAsJsonArray("sequence")) {
                    if (steps.size() >= MAX_STEPS) break;
                    JsonObject so = el.getAsJsonObject();
                    String stype = so.has("type") ? so.get("type").getAsString() : "";
                    StepRow row;
                    if ("create:deploying".equals(stype)) {
                        row = new StepRow("deploy");
                        row.extra = extractSecondIngredientId(so);
                    } else if ("create:pressing".equals(stype)) {
                        row = new StepRow("press");
                    } else if ("create:cutting".equals(stype)) {
                        row = new StepRow("saw");
                    } else if ("create:filling".equals(stype)) {
                        row = new StepRow("spout");
                        row.extra = extractFluidId(so);
                        row.amount = extractFluidAmount(so);
                    } else {
                        continue;
                    }
                    steps.add(row);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private String extractSecondIngredientId(JsonObject stepObj) {
        try {
            JsonArray ings = stepObj.getAsJsonArray("ingredients");
            if (ings != null && ings.size() >= 2) {
                JsonObject tool = ings.get(1).getAsJsonObject();
                if (tool.has("item")) return tool.get("item").getAsString();
                if (tool.has("tag")) return "#" + tool.get("tag").getAsString();
            }
        } catch (Exception ignored) {}
        return "";
    }

    private String extractFluidId(JsonObject stepObj) {
        try {
            JsonArray ings = stepObj.getAsJsonArray("ingredients");
            if (ings != null && ings.size() >= 2) {
                JsonObject fluid = ings.get(1).getAsJsonObject();
                if (fluid.has("fluid")) return fluid.get("fluid").getAsString();
                if (fluid.has("fluidTag")) return "#" + fluid.get("fluidTag").getAsString();
            }
        } catch (Exception ignored) {}
        return "";
    }

    private int extractFluidAmount(JsonObject stepObj) {
        try {
            JsonArray ings = stepObj.getAsJsonArray("ingredients");
            if (ings != null && ings.size() >= 2) {
                JsonObject fluid = ings.get(1).getAsJsonObject();
                if (fluid.has("amount")) return fluid.get("amount").getAsInt();
            }
        } catch (Exception ignored) {}
        return 250;
    }

    @Override
    protected void init() {
        L = (width - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        clearWidgets();
        setFocused(null);

        int y = T + 22;

        EditBox transBox = new KveScaledEditBox(font, L + 110, y, 100, 12, Component.literal(""));
        transBox.setMaxLength(120);
        transBox.setValue(transitionalItem);
        transBox.setResponder(s -> transitionalItem = s);
        addRenderableWidget(transBox);

        EditBox loopsBox = new KveScaledEditBox(font, L + 260, y, 40, 12, Component.literal(""));
        loopsBox.setMaxLength(3);
        loopsBox.setValue(String.valueOf(loops));
        loopsBox.setResponder(s -> loops = parseIntSafe(s, loops));
        addRenderableWidget(loopsBox);

        y += ROW_H;

        EditBox outBox = new KveScaledEditBox(font, L + 110, y, 100, 12, Component.literal(""));
        outBox.setMaxLength(120);
        outBox.setValue(primaryOutput);
        outBox.setResponder(s -> primaryOutput = s);
        addRenderableWidget(outBox);

        EditBox countBox = new KveScaledEditBox(font, L + 220, y, 30, 12, Component.literal(""));
        countBox.setMaxLength(2);
        countBox.setValue(String.valueOf(primaryCount));
        countBox.setResponder(s -> primaryCount = parseIntSafe(s, primaryCount));
        addRenderableWidget(countBox);

        EditBox chanceBox = new KveScaledEditBox(font, L + 260, y, 40, 12, Component.literal(""));
        chanceBox.setMaxLength(6);
        chanceBox.setValue(fmtPercent(primaryChancePercent));
        chanceBox.setResponder(s -> primaryChancePercent = parseDoubleSafe(s, primaryChancePercent));
        addRenderableWidget(chanceBox);

        y += ROW_H + 6;
        int stepsHeaderY = y;
        y += 10;

        for (int i = 0; i < steps.size(); i++) {
            StepRow row = steps.get(i);
            int ry = y + i * ROW_H;
            boolean needsExtra = row.type.equals("deploy") || row.type.equals("spout");
            if (needsExtra) {
                EditBox extraBox = new KveScaledEditBox(font, L + 70, ry, 130, 12, Component.literal(""));
                extraBox.setMaxLength(120);
                extraBox.setValue(row.extra);
                extraBox.setResponder(s -> row.extra = s);
                addRenderableWidget(extraBox);
            }
            if (row.type.equals("spout")) {
                EditBox amtBox = new KveScaledEditBox(font, L + 206, ry, 40, 12, Component.literal(""));
                amtBox.setMaxLength(5);
                amtBox.setValue(String.valueOf(row.amount));
                amtBox.setResponder(s -> row.amount = parseIntSafe(s, row.amount));
                addRenderableWidget(amtBox);
            }
            final int idx = i;
            addRenderableWidget(new KvePlateButton(L + GUI_W - 24, ry - 1, 18, 14, Component.literal("X"),
                    b -> { steps.remove(idx); init(); }));
        }

        int addRowY = y + MAX_STEPS * ROW_H + 4;
        int bw = (GUI_W - 12 - 3 * 4) / 4;
        addRenderableWidget(new KvePlateButton(L + 6, addRowY, bw, 14, Lang.c("gui.kve.sequenced.add_deploy"),
                b -> addStep("deploy")));
        addRenderableWidget(new KvePlateButton(L + 6 + (bw + 4), addRowY, bw, 14, Lang.c("gui.kve.sequenced.add_press"),
                b -> addStep("press")));
        addRenderableWidget(new KvePlateButton(L + 6 + (bw + 4) * 2, addRowY, bw, 14, Lang.c("gui.kve.sequenced.add_saw"),
                b -> addStep("saw")));
        addRenderableWidget(new KvePlateButton(L + 6 + (bw + 4) * 3, addRowY, bw, 14, Lang.c("gui.kve.sequenced.add_fill"),
                b -> addStep("spout")));

        int salvageHeaderY = addRowY + 20;
        int salvageRowsY = salvageHeaderY + 10;

        for (int i = 0; i < salvage.size(); i++) {
            SalvageRow row = salvage.get(i);
            int ry = salvageRowsY + i * ROW_H;
            EditBox itemBox = new KveScaledEditBox(font, L + 70, ry, 160, 12, Component.literal(""));
            itemBox.setMaxLength(120);
            itemBox.setValue(row.item);
            itemBox.setResponder(s -> row.item = s);
            addRenderableWidget(itemBox);

            EditBox chBox = new KveScaledEditBox(font, L + 236, ry, 40, 12, Component.literal(""));
            chBox.setMaxLength(6);
            chBox.setValue(fmtPercent(row.chancePercent));
            chBox.setResponder(s -> row.chancePercent = parseDoubleSafe(s, row.chancePercent));
            addRenderableWidget(chBox);

            final int idx = i;
            addRenderableWidget(new KvePlateButton(L + GUI_W - 24, ry - 1, 18, 14, Component.literal("X"),
                    b -> { salvage.remove(idx); init(); }));
        }

        int addSalvageY = salvageRowsY + MAX_SALVAGE * ROW_H + 4;
        addRenderableWidget(new KvePlateButton(L + 6, addSalvageY, 100, 14, Lang.c("gui.kve.sequenced.add_salvage"),
                b -> {
                    if (salvage.size() < MAX_SALVAGE) { salvage.add(new SalvageRow()); init(); }
                }));

        addRenderableWidget(new KvePlateButton(L + 6, T + GUI_H - 20, 80, 14, Lang.c("gui.kve.btn.save"),
                b -> { save(); minecraft.setScreen(parent); }).withPrimary(true));
        addRenderableWidget(new KvePlateButton(L + GUI_W - 86, T + GUI_H - 20, 80, 14, Lang.c("gui.kve.btn.back"),
                b -> minecraft.setScreen(parent)));

        this.stepsHeaderY = stepsHeaderY;
        this.stepsRowsY = y;
        this.salvageHeaderYCache = salvageHeaderY;
        this.salvageRowsYCache = salvageRowsY;
    }

    private int stepsHeaderY, stepsRowsY, salvageHeaderYCache, salvageRowsYCache;

    private void addStep(String type) {
        if (steps.size() < MAX_STEPS) {
            steps.add(new StepRow(type));
            init();
        }
    }

    private static int parseIntSafe(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return fallback; }
    }

    private static double parseDoubleSafe(String s, double fallback) {
        try { return Double.parseDouble(s.trim().replace("%", "")); } catch (Exception e) { return fallback; }
    }

    private static String fmtPercent(double pct) {
        return pct == Math.floor(pct) ? String.valueOf((int) pct) : String.valueOf(pct);
    }

    private static JsonObject ingredientOf(String raw) {
        JsonObject o = new JsonObject();
        if (raw != null && raw.startsWith("#")) {
            o.addProperty("tag", raw.substring(1));
        } else {
            o.addProperty("item", raw == null ? "" : raw);
        }
        return o;
    }

    /**
     * Serializa a la misma forma que {@code customJsonCreateSequencedAssembly}
     * espera leer del JSON extra: {@code transitionalItem}/{@code loops}/
     * {@code sequence}/{@code results}. Cada paso de {@code sequence} usa el
     * item transicional como unico/primer ingrediente Y como resultado
     * placeholder (Create lo ignora y lo pisa por el real al cargar - ver
     * class doc de esta pantalla), asi que un paso nunca queda invalido por
     * "faltar" un resultado propio.
     */
    private String buildJson() {
        JsonObject root = new JsonObject();

        JsonObject trans = new JsonObject();
        trans.addProperty("item", transitionalItem);
        root.add("transitionalItem", trans);
        root.addProperty("loops", Math.max(1, loops));

        JsonObject transIngredient = ingredientOf(transitionalItem);
        JsonObject transResult = new JsonObject();
        transResult.addProperty("item", transitionalItem);

        JsonArray sequence = new JsonArray();
        for (StepRow row : steps) {
            JsonObject stepObj = new JsonObject();
            JsonArray ings = new JsonArray();
            ings.add(transIngredient);
            switch (row.type) {
                case "deploy" -> {
                    stepObj.addProperty("type", "create:deploying");
                    ings.add(ingredientOf(row.extra));
                    stepObj.add("ingredients", ings);
                    JsonArray results = new JsonArray();
                    results.add(transResult);
                    stepObj.add("results", results);
                }
                case "press" -> {
                    stepObj.addProperty("type", "create:pressing");
                    stepObj.add("ingredients", ings);
                    JsonArray results = new JsonArray();
                    results.add(transResult);
                    stepObj.add("results", results);
                }
                case "saw" -> {
                    stepObj.addProperty("type", "create:cutting");
                    stepObj.add("ingredients", ings);
                    JsonArray results = new JsonArray();
                    results.add(transResult);
                    stepObj.add("results", results);
                }
                case "spout" -> {
                    stepObj.addProperty("type", "create:filling");
                    JsonObject fluid = new JsonObject();
                    if (row.extra != null && row.extra.startsWith("#")) {
                        fluid.addProperty("fluidTag", row.extra.substring(1));
                    } else {
                        fluid.addProperty("fluid", row.extra == null ? "minecraft:water" : row.extra);
                    }
                    fluid.addProperty("amount", Math.max(1, row.amount));
                    ings.add(fluid);
                    stepObj.add("ingredients", ings);
                    JsonArray results = new JsonArray();
                    results.add(transResult);
                    stepObj.add("results", results);
                }
                default -> { continue; }
            }
            sequence.add(stepObj);
        }
        root.add("sequence", sequence);

        JsonArray results = new JsonArray();
        JsonObject primary = new JsonObject();
        primary.addProperty("item", primaryOutput);
        if (primaryCount > 1) primary.addProperty("count", primaryCount);
        if (primaryChancePercent < 100.0) primary.addProperty("chance", Math.max(0.0, primaryChancePercent / 100.0));
        results.add(primary);
        for (SalvageRow sr : salvage) {
            if (sr.item == null || sr.item.isBlank()) continue;
            JsonObject so = new JsonObject();
            so.addProperty("item", sr.item);
            so.addProperty("count", 1);
            so.addProperty("chance", Math.max(0.0, sr.chancePercent / 100.0));
            results.add(so);
        }
        root.add("results", results);

        return root.toString();
    }

    private void save() {
        onSave.accept(buildJson());
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, Lang.get("gui.kve.sequenced.title"));

        int y = T + 22;
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.sequenced.transitional"), L + 6, y + 2, 100, KveGuiStyle.th(0xFF99AACC));
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.sequenced.loops"), L + 214, y + 2, 44, KveGuiStyle.th(0xFF99AACC));
        y += ROW_H;
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.sequenced.primary_output"), L + 6, y + 2, 100, KveGuiStyle.th(0xFF99AACC));
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.sequenced.count"), L + 224, y + 2, 20, KveGuiStyle.th(0xFF99AACC));
        KveGuiStyle.drawTextSafe(g, "%", L + 302, y + 2, 12, KveGuiStyle.th(0xFF99AACC));

        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.sequenced.steps", steps.size(), MAX_STEPS), L + 6, stepsHeaderY, GUI_W - 12, 0xFFCCAA55);
        for (int i = 0; i < steps.size(); i++) {
            StepRow row = steps.get(i);
            int ry = stepsRowsY + i * ROW_H;
            ItemStack stationStack = resolveStationItem(row.type);
            if (!stationStack.isEmpty()) {
                try { g.renderItem(stationStack, L + 6, ry - 1); } catch (Throwable ignored) {}
            }
            KveGuiStyle.drawTextSafe(g, stepLabel(row.type), L + 24, ry + 2, 44, 0xFFDDDDDD);
            if (row.type.equals("spout")) {
                KveGuiStyle.drawTextSafe(g, "mB", L + 248, ry + 2, 16, KveGuiStyle.th(0xFF99AACC));
            }
        }

        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.sequenced.salvage"), L + 6, salvageHeaderYCache, GUI_W - 12, 0xFFCCAA55);
        for (int i = 0; i < salvage.size(); i++) {
            int ry = salvageRowsYCache + i * ROW_H;
            KveGuiStyle.drawTextSafe(g, "-", L + 6, ry + 2, 60, 0xFFDDDDDD);
            KveGuiStyle.drawTextSafe(g, "%", L + 278, ry + 2, 12, KveGuiStyle.th(0xFF99AACC));
        }

        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
    }

    private static ItemStack resolveStationItem(String type) {
        String id = switch (type) {
            case "deploy" -> "create:deployer";
            case "press" -> "create:mechanical_press";
            case "saw" -> "create:mechanical_saw";
            case "spout" -> "create:spout";
            default -> "";
        };
        if (id.isEmpty()) return ItemStack.EMPTY;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return ItemStack.EMPTY;
        var item = BuiltInRegistries.ITEM.get(rl);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static String stepLabel(String type) {
        return switch (type) {
            case "deploy" -> "Deploy:";
            case "press" -> "Press";
            case "saw" -> "Saw";
            case "spout" -> "Fill:";
            default -> type;
        };
    }
}
