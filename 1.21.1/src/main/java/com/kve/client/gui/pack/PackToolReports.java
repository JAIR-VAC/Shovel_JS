package com.kve.client.gui.pack;

import com.kve.client.gui.KveGuiStyle;
import com.kve.common.Lang;
import com.kve.common.packtools.BalanceCheck;
import com.kve.common.packtools.PackCheck;
import com.kve.common.packtools.PackGuide;
import com.kve.common.persist.CraftEditorState;
import com.kve.common.persist.PackSnapshotManager;
import com.kve.common.util.SafeFiles;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class PackToolReports {

    private PackToolReports() {}

    public static Screen packCheck(Screen parent) {
        List<PackReportScreen.Action> actions = List.of(new PackReportScreen.Action(Lang.get("gui.kve.check.reload_and_check"), s -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) { s.flash(Lang.get("gui.kve.check.no_world"), KveGuiStyle.TEXT_WARN); return; }
            mc.player.connection.sendCommand("reload");
            s.rerunAfter(6000, Lang.get("gui.kve.check.reloading"));
        }));
        return new PackReportScreen(parent, Lang.get("gui.kve.check.title"), () -> {
            CraftEditorState.loadIfNeeded();
            List<PackCheck.Issue> issues = PackCheck.runAll(PackSnapshotManager.gameDir());
            List<PackReportScreen.Row> rows = new ArrayList<>();
            int errors = 0;
            for (PackCheck.Issue i : issues) {
                boolean err = i.severity() == PackCheck.Severity.ERROR;
                if (err) errors++;
                rows.add(new PackReportScreen.Row("[" + Lang.get("gui.kve.check.area." + i.area()) + "] " + i.where() + " - "
                        + Lang.get(i.key(), i.args().toArray()), err ? KveGuiStyle.TEXT_ERROR : KveGuiStyle.TEXT_WARN));
            }
            String summary = issues.isEmpty() ? Lang.get("gui.kve.check.ok")
                    : Lang.get("gui.kve.check.summary", errors, issues.size() - errors);
            return new PackReportScreen.Report(summary, issues.isEmpty() ? KveGuiStyle.TEXT_OK : errors > 0 ? KveGuiStyle.TEXT_ERROR : KveGuiStyle.TEXT_WARN, rows);
        }, true, actions);
    }

    public static Screen balance(Screen parent) {
        return new PackReportScreen(parent, Lang.get("gui.kve.balance.title"), () -> {
            CraftEditorState.loadIfNeeded();
            List<BalanceCheck.Finding> found = BalanceCheck.run(gameConversions());
            List<PackReportScreen.Row> rows = new ArrayList<>();
            int errors = 0;
            for (BalanceCheck.Finding f : found) {
                boolean err = f.severity() == BalanceCheck.Severity.ERROR;
                if (err) errors++;
                rows.add(new PackReportScreen.Row(f.where() + " - " + Lang.get(f.key(), f.args().toArray()),
                        err ? KveGuiStyle.TEXT_ERROR : KveGuiStyle.TEXT_WARN));
            }
            String summary = found.isEmpty() ? Lang.get("gui.kve.balance.ok") : Lang.get("gui.kve.check.summary", errors, found.size() - errors);
            return new PackReportScreen.Report(summary, found.isEmpty() ? KveGuiStyle.TEXT_OK : errors > 0 ? KveGuiStyle.TEXT_ERROR : KveGuiStyle.TEXT_WARN, rows);
        }, true, List.of());
    }

    static List<BalanceCheck.Conversion> gameConversions() {
        List<BalanceCheck.Conversion> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return out;
        for (RecipeHolder<?> h : mc.level.getRecipeManager().getRecipes()) {
            if (h.id().getNamespace().equals(com.kve.CraftEditorMod.NS)) continue;
            ItemStack result;
            try { result = h.value().getResultItem(mc.level.registryAccess()); } catch (RuntimeException e) { continue; }
            if (result == null || result.isEmpty()) continue;
            String in = null;
            int count = 0;
            boolean simple = true;
            for (Ingredient ing : h.value().getIngredients()) {
                if (ing.isEmpty()) continue;
                ItemStack[] items = ing.getItems();
                if (items.length != 1) { simple = false; break; }
                String id = BuiltInRegistries.ITEM.getKey(items[0].getItem()).toString();
                if (in == null) in = id;
                else if (!in.equals(id)) { simple = false; break; }
                count++;
            }
            if (!simple || in == null || count == 0) continue;
            out.add(new BalanceCheck.Conversion(in, count, BuiltInRegistries.ITEM.getKey(result.getItem()).toString(), result.getCount(), h.id().toString()));
        }
        return out;
    }

    public static Screen guide(Screen parent) {
        Path gameDir = PackSnapshotManager.gameDir();
        String packName = gameDir.getFileName() == null ? "" : gameDir.getFileName().toString();
        List<PackReportScreen.Action> actions = List.of(
                new PackReportScreen.Action(Lang.get("gui.kve.guide.save"), s -> {
                    try {
                        StringBuilder md = new StringBuilder();
                        for (PackReportScreen.Row r : s.rows()) md.append(r.text()).append('\n');
                        SafeFiles.writeAtomic(PackGuide.file(gameDir), md.toString());
                        s.flash(Lang.get("gui.kve.guide.saved", PackGuide.file(gameDir).getFileName()), KveGuiStyle.TEXT_OK);
                    } catch (Exception e) {
                        s.flash(Lang.get("gui.kve.report.failed", e.getMessage()), KveGuiStyle.TEXT_ERROR);
                    }
                }),
                new PackReportScreen.Action(Lang.get("gui.kve.guide.open_folder"), s -> Util.getPlatform().openFile(gameDir.toFile())));
        return new PackReportScreen(parent, Lang.get("gui.kve.guide.title"), () -> {
            CraftEditorState.loadIfNeeded();
            String md = PackGuide.build(packName);
            List<PackReportScreen.Row> rows = new ArrayList<>();
            for (String line : md.split("\n", -1)) {
                int color = line.startsWith("#") ? KveGuiStyle.ACCENT_GOLD : line.startsWith("|---") ? KveGuiStyle.TEXT_MUTED : KveGuiStyle.TEXT_PRIMARY;
                rows.add(new PackReportScreen.Row(line, color));
            }
            return new PackReportScreen.Report(Lang.get("gui.kve.guide.summary"), KveGuiStyle.TEXT_LABEL, rows);
        }, true, actions);
    }
}
