package com.kve.client.gui.mob;

import com.kve.client.gui.properties.ItemAttributeScreen;
import com.kve.client.gui.picker.ItemPickerScreen;
import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.KveScaledEditBox;
import com.kve.client.gui.picker.NbtEditorScreen;

import com.kve.common.Lang;

import com.kve.common.kubejs.KubeJSMobExporter.EquipmentBuild;
import com.kve.common.kubejs.KubeJSMobExporter.EquipmentItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.function.Consumer;

/**
 * Edits a single {@link EquipmentBuild}: its display name, relative weight
 * (used for weighted-random selection among a mob's builds at spawn time -
 * see KubeJSMobExporter#writeEquipment), and the same 6 equipment slots
 * MobEditorScreen's simple-mode equipment tab has, reusing ItemPickerScreen
 * for item selection exactly like that tab does - including its
 * allowNbtEdit hotspot and a dedicated per-slot enchant button, so a
 * build's items can carry enchantments the same way the simple-mode tab's
 * items can. Same self-contained dialog pattern as {@link MobModValueScreen}
 * (one object in, a Consumer callback out, "this" is the parent to return
 * to on close).
 */
@OnlyIn(Dist.CLIENT)
public class MobEquipmentBuildScreen extends Screen {

    private static final int GUI_W = 300;
    private static final int GUI_H = 190;
    private static final String[] EQUIP_SLOTS = {"MAINHAND","OFFHAND","HEAD","CHEST","LEGS","FEET"};

    private final Screen parent;
    private final Consumer<EquipmentBuild> onConfirm;

    private String initialName = "";
    private double initialWeight = 1.0;
    private final String[] initialItem   = new String[EQUIP_SLOTS.length];
    private final double[] initialChance = new double[EQUIP_SLOTS.length];
    private final String[] initialNbt    = new String[EQUIP_SLOTS.length];
    private final double[] initialSpawn  = new double[EQUIP_SLOTS.length];
    { java.util.Arrays.fill(initialSpawn, 1.0); }

    private EditBox nameBox;
    private EditBox weightBox;
    private int nameBoxY, weightBoxY;
    private final String[] draftItem   = new String[EQUIP_SLOTS.length];
    private final double[] draftChance = new double[EQUIP_SLOTS.length];
    private final String[] draftNbt    = new String[EQUIP_SLOTS.length];
    private final EditBox[] fChance    = new EditBox[EQUIP_SLOTS.length];
    private final double[] draftSpawn  = new double[EQUIP_SLOTS.length];
    private final EditBox[] fSpawn     = new EditBox[EQUIP_SLOTS.length];
    private boolean draftLoaded = false;

    // - hover tooltip de cada boton de item (mismo hueco que
    // quedaba abierto en LootEntryEditScreen): a diferencia de las
    // grillas de slots reales de CraftEditor/MachineRecipeEdit/Loot, acá
    // el item nunca se ve (el boton solo muestra el id como texto) y no
    // tenia forma de pasar el mouse por encima y ver el tooltip real del
    // item (encantamientos/lore) antes de decidir si re-elegirlo. Guarda
    // una referencia por slot (se recrean en cada init()) para poder
    // hit-testear el mouse en render() - mismo patron ya usado por
    // CraftEditorScreen#drawTooltips (super.render() primero, tooltips
    // custom despues).
    private final Button[] itemBtn = new Button[EQUIP_SLOTS.length];

    private int L, T;

    public MobEquipmentBuildScreen(Screen parent, Consumer<EquipmentBuild> onConfirm) {
        super(Lang.c("gui.kve.mob.build_title"));
        this.parent = parent;
        this.onConfirm = onConfirm;
    }

    /** Pre-fills the dialog with an existing build's data (edit mode). Without a call to this, the dialog starts empty (add mode). */
    public MobEquipmentBuildScreen withExisting(EquipmentBuild existing) {
        if (existing != null) {
            this.initialName   = existing.name == null ? "" : existing.name;
            this.initialWeight = existing.weight;
            for (EquipmentItem eq : existing.equipment) {
                int i = indexOf(eq.slot);
                if (i < 0) continue;
                initialItem[i]   = eq.itemId;
                initialChance[i] = eq.dropChance;
                initialNbt[i]    = eq.nbt;
                initialSpawn[i]  = eq.spawnChance;
            }
        }
        return this;
    }

    private static int indexOf(String slot) {
        if (slot == null) return -1;
        for (int i = 0; i < EQUIP_SLOTS.length; i++) if (EQUIP_SLOTS[i].equals(slot)) return i;
        return -1;
    }

    @Override
    protected void init() {
        L = (width  - GUI_W) / 2;
        T = (height - GUI_H) / 2;

        String snapName   = nameBox   != null ? nameBox.getValue()   : null;
        String snapWeight = weightBox != null ? weightBox.getValue() : null;
        String[] snapChance = new String[EQUIP_SLOTS.length];
        String[] snapSpawn = new String[EQUIP_SLOTS.length];
        for (int i = 0; i < EQUIP_SLOTS.length; i++) {
            snapChance[i] = fChance[i] != null ? fChance[i].getValue() : null;
            snapSpawn[i]  = fSpawn[i]  != null ? fSpawn[i].getValue()  : null;
        }

        if (!draftLoaded) {
            System.arraycopy(initialItem, 0, draftItem, 0, EQUIP_SLOTS.length);
            System.arraycopy(initialChance, 0, draftChance, 0, EQUIP_SLOTS.length);
            System.arraycopy(initialNbt, 0, draftNbt, 0, EQUIP_SLOTS.length);
            System.arraycopy(initialSpawn, 0, draftSpawn, 0, EQUIP_SLOTS.length);
            draftLoaded = true;
        }

        clearWidgets();
        setFocused(null);

        KveGuiStyle.Layout ly = new KveGuiStyle.Layout(L + 10, T + 26, 16);

        nameBoxY = ly.row(14);
        nameBox = new KveScaledEditBox(font, ly.x(), nameBoxY, GUI_W - 20, 14, Component.literal("nombre"));
        nameBox.setMaxLength(32);
        nameBox.setValue(snapName != null ? snapName : initialName);
        nameBox.setHint(Component.literal(Lang.get("gui.kve.mob.build_name_hint")));
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);

        weightBoxY = ly.row(14);
        weightBox = new KveScaledEditBox(font, ly.x(), weightBoxY, GUI_W - 20, 14, Component.literal("weight"));
        weightBox.setMaxLength(8);
        weightBox.setValue(snapWeight != null ? snapWeight : fmt(initialWeight));
        addRenderableWidget(weightBox);
        ly.gap(4);

        int spawnW = 28;
        int chanceW = 30;
        int smallW = 14;
        int itemW = (GUI_W - 20) - spawnW - chanceW - smallW * 3 - 12;
        for (int i = 0; i < EQUIP_SLOTS.length; i++) {
            int idx = i;
            int ry = ly.row(14);
            boolean hasItem = draftItem[i] != null && !draftItem[i].isEmpty();
            String label = !hasItem
                    ? "(" + Lang.get("gui.kve.mob.equip." + EQUIP_SLOTS[i].toLowerCase()) + ")"
                    : draftItem[i];
            Button itemButton = new KvePlateButton(ly.x(), ry, itemW, 12, Component.literal(KveGuiStyle.truncate(font, label, itemW - 6)), b -> openPicker(idx));
            addRenderableWidget(itemButton);
            itemBtn[i] = itemButton;

            EditBox spawn = new KveScaledEditBox(font, ly.x() + itemW + 2, ry, spawnW, 12, Component.literal("prob"));
            spawn.setMaxLength(6);
            spawn.setHint(Component.literal("1"));
            spawn.setValue(snapSpawn[i] != null ? snapSpawn[i]
                    : (!hasItem ? "" : fmt(draftSpawn[i])));
            spawn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(Lang.get("gui.kve.mob.equip.spawn_chance_tip"))));
            addRenderableWidget(spawn);
            fSpawn[i] = spawn;

            EditBox chance = new KveScaledEditBox(font, ly.x() + itemW + spawnW + 4, ry, chanceW, 12, Component.literal("%"));
            chance.setMaxLength(6);
            chance.setHint(Component.literal("drop%"));
            chance.setValue(snapChance[i] != null ? snapChance[i]
                    : (!hasItem ? "" : fmt(draftChance[i] * 100)));
            chance.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(Lang.get("gui.kve.mob.equip.drop_chance_tip"))));
            addRenderableWidget(chance);
            fChance[i] = chance;
            int bx = ly.x() + itemW + spawnW + 2;

            boolean hasEnchant = draftNbt[i] != null && !draftNbt[i].isBlank();
            Button enchBtn = new KvePlateButton(bx + chanceW + 4, ry, smallW, 12, Component.literal(hasEnchant ? "E*" : "E"), b -> openEnchant(idx));
            enchBtn.active = hasItem;
            addRenderableWidget(enchBtn);

            Button attrBtn = new KvePlateButton(bx + chanceW + smallW + 6, ry, smallW, 12, Component.literal("Atr"), b -> openItemAttributes(idx));
            attrBtn.active = hasItem;
            addRenderableWidget(attrBtn);

            addRenderableWidget(new KvePlateButton(bx + chanceW + smallW * 2 + 8, ry, smallW, 12, Component.literal("X"), b -> clearSlot(idx)));
        }

        addRenderableWidget(new KvePlateButton(L + GUI_W / 2 - 70, T + GUI_H - 18, 70, 14, Component.literal(Lang.get("gui.kve.btn.confirm")), b -> confirm()).withPrimary(true));
        addRenderableWidget(new KvePlateButton(L + GUI_W / 2 + 2, T + GUI_H - 18, 70, 14, Component.literal(Lang.get("gui.kve.btn.cancel")), b -> onClose()));
    }

    private void openPicker(int idx) {
        minecraft.setScreen(new ItemPickerScreen(this, stack -> {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            draftItem[idx] = id != null ? id.toString() : "";
            draftNbt[idx]  = com.kve.common.util.ItemExtraDataCodec.encodeOrEmpty(stack);
            if (draftChance[idx] <= 0 && (fChance[idx] == null || fChance[idx].getValue().trim().isEmpty()))
                draftChance[idx] = 0.085;
            if (fSpawn[idx] == null || fSpawn[idx].getValue().trim().isEmpty())
                draftSpawn[idx] = 1.0;
            minecraft.setScreen(this);
            init();
        }, true));
    }

    private void openEnchant(int idx) {
        if (draftItem[idx] == null || draftItem[idx].isEmpty()) return;
        ItemStack stack = stackFor(draftItem[idx], draftNbt[idx]);
        minecraft.setScreen(new NbtEditorScreen(this, stack, result -> {
            draftNbt[idx] = com.kve.common.util.ItemExtraDataCodec.encodeOrEmpty(result);
            minecraft.setScreen(this);
            init();
        }));
    }

    private void openItemAttributes(int idx) {
        if (draftItem[idx] == null || draftItem[idx].isEmpty()) return;
        if (parent instanceof MobEditorScreen mob) {
            String itemId = draftItem[idx];
            confirm();
            if (mob.openItemInShell(itemId)) return;
            minecraft.setScreen(new ItemAttributeScreen(itemId).withReturnScreen(parent));
            return;
        }
        minecraft.setScreen(new ItemAttributeScreen(draftItem[idx]).withReturnScreen(this));
    }

    private ItemStack stackFor(String itemId, String nbt) {
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        Item item = rl != null ? BuiltInRegistries.ITEM.get(rl) : Items.AIR;
        ItemStack stack = new ItemStack(item);
        if (nbt != null && !nbt.isBlank()) {
            com.kve.common.util.ItemExtraDataCodec.applyFromSnbt(stack, nbt);
        }
        return stack;
    }

    private void clearSlot(int idx) {
        draftItem[idx] = "";
        draftChance[idx] = 0.0;
        draftSpawn[idx] = 1.0;
        draftNbt[idx] = "";
        init();
    }

    private void confirm() {
        EquipmentBuild b = new EquipmentBuild();
        b.name = nameBox.getValue().trim();
        double w;
        try { w = Double.parseDouble(weightBox.getValue().trim().replace(',', '.')); } catch (Exception e) { w = 1.0; }
        b.weight = Math.max(0.0, w);
        for (int i = 0; i < EQUIP_SLOTS.length; i++) {
            if (draftItem[i] == null || draftItem[i].isEmpty()) continue;
            double pct;
            try { pct = Double.parseDouble(fChance[i].getValue().trim().replace(',', '.').replace("%", "")); } catch (Exception e) { pct = draftChance[i] * 100; }
            double chance = Math.max(0, Math.min(100, pct)) / 100.0;
            double spawnP = com.kve.common.kubejs.KubeJSMobExporter.parseProbability(fSpawn[i].getValue());
            if (Double.isNaN(spawnP)) spawnP = draftSpawn[i];
            b.equipment.add(new EquipmentItem(EQUIP_SLOTS[i], draftItem[i], chance, draftNbt[i], spawnP));
        }
        onConfirm.accept(b);
    }

    private String fmt(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return new java.math.BigDecimal(String.format(java.util.Locale.ROOT, "%.4f", v)).stripTrailingZeros().toPlainString();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, Lang.get("gui.kve.mob.build_title"));

        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.mob.build_name"),   L + 10, nameBoxY - 9,   GUI_W - 20, KveGuiStyle.TEXT_LABEL);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.mob.build_weight"), L + 10, weightBoxY - 9, GUI_W - 20, KveGuiStyle.TEXT_LABEL);

        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
        drawItemBtnTooltip(g, mx, my);
    }

    private void drawItemBtnTooltip(GuiGraphics g, int mx, int my) {
        for (int i = 0; i < EQUIP_SLOTS.length; i++) {
            Button b = itemBtn[i];
            if (b == null || mx < b.getX() || mx >= b.getX() + b.getWidth()
                    || my < b.getY() || my >= b.getY() + b.getHeight()) continue;
            boolean hasItem = draftItem[i] != null && !draftItem[i].isEmpty();
            java.util.List<Component> lines = new java.util.ArrayList<>();
            if (hasItem) {
                lines.addAll(getTooltipFromItem(minecraft, stackFor(draftItem[i], draftNbt[i])));
                lines.add(Component.empty());
            }
            lines.add(KveGuiStyle.slotHint(KveGuiStyle.HINT_CLICK_CHANGE));
            g.renderComponentTooltip(font, lines, mx, my);
            return;
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override public boolean isPauseScreen() { return false; }
}
