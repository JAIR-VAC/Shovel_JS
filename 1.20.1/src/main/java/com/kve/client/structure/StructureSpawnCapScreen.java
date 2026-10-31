package com.kve.client.structure;

import com.kve.common.Lang;
import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.MobPickerScreen;
import com.kve.common.structure.StructureSpawnCap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import com.kve.client.gui.KveScaledEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class StructureSpawnCapScreen extends Screen {

    private static final int GUI_W = 280;
    private static final int GUI_H = 258;
    private static final int ROW_H = 16;
    private static final int LABEL_X_OFF = 8;
    private static final int FIELD_X_OFF = 118;
    private static final int FIELD_W = 60;
    private static final int ENTITY_STATUS_W = 14;

    private final Screen parent;
    private final BlockPos pos;
    private final boolean editingExisting;
    private final Consumer<StructureSpawnCap> onSave;
    private final Runnable onDelete;

    private String entityId;
    private boolean blockOnPeaceful;
    private boolean blockInDaylight;

    private EditBox entityBox;
    private EditBox minDelayBox, maxDelayBox, spawnCountBox;
    private EditBox maxNearbyBox, playerRangeBox, spawnRangeBox;
    private EditBox minLightBox, maxLightBox;
    private Button peacefulBtn, daylightBtn;

    private int L, T;
    private String errorMsg = "";

    public StructureSpawnCapScreen(Screen parent, BlockPos pos, StructureSpawnCap existing,
                                    Consumer<StructureSpawnCap> onSave, Runnable onDelete) {
        super(Lang.c("gui.kve.structure.spawncap.title"));
        this.parent = parent;
        this.pos = pos.immutable();
        this.editingExisting = existing != null;
        this.onSave = onSave;
        this.onDelete = onDelete;

        StructureSpawnCap seed = existing != null ? existing : new StructureSpawnCap();
        this.entityId = seed.entityId;
        this.blockOnPeaceful = seed.blockOnPeaceful;
        this.blockInDaylight = seed.blockInDaylight;
        this.seedMinDelay = seed.minSpawnDelayTicks;
        this.seedMaxDelay = seed.maxSpawnDelayTicks;
        this.seedSpawnCount = seed.spawnCount;
        this.seedMaxNearby = seed.maxNearbyEntities;
        this.seedPlayerRange = seed.requiredPlayerRange;
        this.seedSpawnRange = seed.spawnRange;
        this.seedMinLight = seed.minLightLevel;
        this.seedMaxLight = seed.maxLightLevel;
    }

    private final int seedMinDelay, seedMaxDelay, seedSpawnCount;
    private final int seedMaxNearby, seedPlayerRange, seedSpawnRange;
    private final int seedMinLight, seedMaxLight;

    @Override
    protected void init() {
        L = (width - GUI_W) / 2;
        T = (height - GUI_H) / 2;

        String snapEntity    = entityBox     != null ? entityBox.getValue()     : entityId;
        String snapMinDelay  = minDelayBox   != null ? minDelayBox.getValue()   : String.valueOf(seedMinDelay);
        String snapMaxDelay  = maxDelayBox   != null ? maxDelayBox.getValue()   : String.valueOf(seedMaxDelay);
        String snapCount     = spawnCountBox != null ? spawnCountBox.getValue(): String.valueOf(seedSpawnCount);
        String snapMaxNearby = maxNearbyBox  != null ? maxNearbyBox.getValue()  : String.valueOf(seedMaxNearby);
        String snapPlayerRng = playerRangeBox!= null ? playerRangeBox.getValue(): String.valueOf(seedPlayerRange);
        String snapSpawnRng  = spawnRangeBox != null ? spawnRangeBox.getValue() : String.valueOf(seedSpawnRange);
        String snapMinLight  = minLightBox   != null ? minLightBox.getValue()   : String.valueOf(seedMinLight);
        String snapMaxLight  = maxLightBox   != null ? maxLightBox.getValue()   : String.valueOf(seedMaxLight);

        clearWidgets();
        setFocused(null);

        KveGuiStyle.Layout ly = new KveGuiStyle.Layout(L + LABEL_X_OFF, T + 24, ROW_H);

        int entityY = ly.row();
        entityBox = new KveScaledEditBox(font, L + FIELD_X_OFF, entityY, GUI_W - FIELD_X_OFF - 40 - ENTITY_STATUS_W, 14, Component.literal("entity"));
        entityBox.setMaxLength(96);
        entityBox.setValue(snapEntity);
        entityBox.setHint(Component.literal("minecraft:zombie"));
        addRenderableWidget(entityBox);
        setInitialFocus(entityBox);
        addRenderableWidget(new KvePlateButton(L + GUI_W - 34, entityY, 26, 14, Component.literal("..."),
                b -> minecraft.setScreen(new MobPickerScreen(this, this::onEntityPicked))));

        int minDelayY = ly.row();
        minDelayBox = intField(minDelayY, snapMinDelay);
        int maxDelayY = ly.row();
        maxDelayBox = intField(maxDelayY, snapMaxDelay);
        int countY = ly.row();
        spawnCountBox = intField(countY, snapCount);

        ly.gap(4);

        int maxNearbyY = ly.row();
        maxNearbyBox = intField(maxNearbyY, snapMaxNearby);
        int playerRangeY = ly.row();
        playerRangeBox = intField(playerRangeY, snapPlayerRng);
        int spawnRangeY = ly.row();
        spawnRangeBox = intField(spawnRangeY, snapSpawnRng);

        ly.gap(4);

        int minLightY = ly.row();
        minLightBox = intField(minLightY, snapMinLight);
        int maxLightY = ly.row();
        maxLightBox = intField(maxLightY, snapMaxLight);

        int peacefulY = ly.row();
        peacefulBtn = new KvePlateButton(L + FIELD_X_OFF, peacefulY, GUI_W - FIELD_X_OFF - 8, 14,
                Component.literal(Lang.get(blockOnPeaceful ? "gui.kve.confirm.yes" : "gui.kve.confirm.no")),
                b -> { blockOnPeaceful = !blockOnPeaceful; init(); });
        addRenderableWidget(peacefulBtn);

        int daylightY = ly.row();
        daylightBtn = new KvePlateButton(L + FIELD_X_OFF, daylightY, GUI_W - FIELD_X_OFF - 8, 14,
                Component.literal(Lang.get(blockInDaylight ? "gui.kve.confirm.yes" : "gui.kve.confirm.no")),
                b -> { blockInDaylight = !blockInDaylight; init(); });
        addRenderableWidget(daylightBtn);

        this.entityY = entityY; this.minDelayY = minDelayY; this.maxDelayY = maxDelayY; this.countY = countY;
        this.maxNearbyY = maxNearbyY; this.playerRangeY = playerRangeY; this.spawnRangeY = spawnRangeY;
        this.minLightY = minLightY; this.maxLightY = maxLightY;
        this.peacefulY = peacefulY; this.daylightY = daylightY;

        int by = T + GUI_H - 18;
        addRenderableWidget(new KvePlateButton(L + 6, by, 70, 14, Lang.c("gui.kve.btn.save"), b -> doSave()).withPrimary(true));
        if (editingExisting) {
            addRenderableWidget(new KvePlateButton(L + 80, by, 70, 14, Lang.c("gui.kve.btn.delete"), b -> doDelete()).withDanger(true));
        }
        addRenderableWidget(new KvePlateButton(L + GUI_W - 76, by, 70, 14, Lang.c("gui.kve.btn.cancel"), b -> onClose()));
    }

    private int entityY, minDelayY, maxDelayY, countY, maxNearbyY, playerRangeY, spawnRangeY, minLightY, maxLightY, peacefulY, daylightY;

    private EditBox intField(int y, String value) {
        EditBox box = new KveScaledEditBox(font, L + FIELD_X_OFF, y, FIELD_W, 14, Component.literal("n"));
        box.setMaxLength(7);
        box.setValue(value);
        addRenderableWidget(box);
        return box;
    }

    private void onEntityPicked(net.minecraft.world.entity.EntityType<?> et) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(et);
        entityBox.setValue(key != null ? key.toString() : "");
        minecraft.setScreen(this);
    }

    private void doSave() {
        String id = entityBox.getValue().trim();
        if (id.isEmpty()) {
            errorMsg = Lang.get("gui.kve.structure.spawncap.err_no_entity");
            return;
        }
        ResourceLocation entityRl = ResourceLocation.tryParse(id);
        if (entityRl == null || BuiltInRegistries.ENTITY_TYPE.getOptional(entityRl).isEmpty()) {
            errorMsg = Lang.get("gui.kve.structure.spawncap.err_invalid_entity", id);
            return;
        }
        StructureSpawnCap cap = new StructureSpawnCap();
        cap.entityId = id;
        cap.minSpawnDelayTicks = Math.max(0, parseIntOr(minDelayBox, seedMinDelay));
        cap.maxSpawnDelayTicks = Math.max(cap.minSpawnDelayTicks, parseIntOr(maxDelayBox, seedMaxDelay));
        cap.spawnCount = Math.max(1, parseIntOr(spawnCountBox, seedSpawnCount));
        cap.maxNearbyEntities = Math.max(0, parseIntOr(maxNearbyBox, seedMaxNearby));
        cap.requiredPlayerRange = Math.max(0, parseIntOr(playerRangeBox, seedPlayerRange));
        cap.spawnRange = Math.max(0, parseIntOr(spawnRangeBox, seedSpawnRange));
        cap.minLightLevel = Math.max(0, Math.min(15, parseIntOr(minLightBox, seedMinLight)));
        cap.maxLightLevel = Math.max(cap.minLightLevel, Math.min(15, parseIntOr(maxLightBox, seedMaxLight)));
        cap.blockOnPeaceful = blockOnPeaceful;
        cap.blockInDaylight = blockInDaylight;
        onSave.accept(cap);
        onClose();
    }

    private void doDelete() {
        if (onDelete != null) onDelete.run();
        onClose();
    }

    private static int parseIntOr(EditBox box, int fallback) {
        if (box == null) return fallback;
        try { return Integer.parseInt(box.getValue().trim()); } catch (Exception e) { return fallback; }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H,
                Lang.get("gui.kve.structure.spawncap.title") + " (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")");

        label(g, "gui.kve.structure.spawncap.entity", entityY);
        if (entityBox != null) {
            String typed = entityBox.getValue().trim();
            if (!typed.isEmpty()) {
                ResourceLocation rl = ResourceLocation.tryParse(typed);
                boolean valid = rl != null && BuiltInRegistries.ENTITY_TYPE.getOptional(rl).isPresent();
                int statusX = L + FIELD_X_OFF + (GUI_W - FIELD_X_OFF - 40 - ENTITY_STATUS_W);
                KveGuiStyle.drawTextSafe(g, valid ? "\u2713" : "?", statusX, entityY + 3, ENTITY_STATUS_W,
                        valid ? 0xFF88CC88 : com.kve.client.gui.KveGuiStyle.th(0xFF667788));
            }
        }
        label(g, "gui.kve.structure.spawncap.min_delay", minDelayY);
        label(g, "gui.kve.structure.spawncap.max_delay", maxDelayY);
        label(g, "gui.kve.structure.spawncap.count", countY);
        label(g, "gui.kve.structure.spawncap.max_nearby", maxNearbyY);
        label(g, "gui.kve.structure.spawncap.player_range", playerRangeY);
        label(g, "gui.kve.structure.spawncap.spawn_range", spawnRangeY);
        label(g, "gui.kve.structure.spawncap.min_light", minLightY);
        label(g, "gui.kve.structure.spawncap.max_light", maxLightY);
        label(g, "gui.kve.structure.spawncap.block_peaceful", peacefulY);
        label(g, "gui.kve.structure.spawncap.block_daylight", daylightY);

        if (!errorMsg.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, errorMsg, L + 6, T + GUI_H - 30, GUI_W - 12, 0xFFFF5555);
        }

        super.render(g, mx, my, pt);
    }

    private void label(GuiGraphics g, String key, int y) {
        KveGuiStyle.drawTextSafe(g, Lang.get(key), L + LABEL_X_OFF, y + 3, FIELD_X_OFF - LABEL_X_OFF - 4, KveGuiStyle.TEXT_LABEL);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
