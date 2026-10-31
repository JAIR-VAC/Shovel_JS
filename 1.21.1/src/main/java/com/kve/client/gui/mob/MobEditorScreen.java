package com.kve.client.gui.mob;

import com.kve.client.gui.DiffPreviewScreen;
import com.kve.client.gui.EditorKeys;
import com.kve.client.gui.picker.EffectPickerScreen;
import com.kve.client.gui.picker.EffectValueScreen;
import com.kve.client.gui.pack.IdUsageScreen;
import com.kve.client.gui.properties.ItemAttributeScreen;
import com.kve.client.gui.picker.ItemPickerScreen;
import com.kve.client.gui.KveConfirmScreen;
import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KveEffectInfo;
import com.kve.client.gui.ListKeyNav;
import com.kve.client.gui.KvePlateButton;
import com.kve.client.gui.KvePropertyFormScreen;
import com.kve.client.gui.KveScaledEditBox;
import com.kve.client.gui.loot.LootTableEditorScreen;
import com.kve.client.gui.picker.NbtEditorScreen;
import com.kve.client.gui.recipe.RecipePreviewScreen;
import com.kve.client.gui.RegistryIndex;
import com.kve.client.gui.ResizeHandle;
import com.kve.client.gui.ScrollBar;
import com.kve.client.gui.SearchFilter;

import org.lwjgl.glfw.GLFW;
import com.kve.common.Lang;
import com.kve.client.permission.ClientPermissionState;

import com.kve.client.gui.workspace.pending.PendingDomain;
import com.kve.client.gui.workspace.pending.PendingChange;
import com.kve.client.gui.workspace.pending.PendingChangeManager;
import com.kve.client.gui.studio.KveStudioShell;
import com.kve.client.gui.studio.KveStudioTab;
import com.kve.client.gui.history.KveChangeLog;
import com.kve.client.gui.history.KveChangeHistoryScreen;
import com.kve.common.kubejs.ExternalScriptScanner;
import com.kve.common.kubejs.KubeJSItemAttrExporter;
import com.kve.common.kubejs.KubeJSMobExporter;
import com.kve.common.persist.CraftEditorState;
import com.kve.common.persist.MobTemplateStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Comparator;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class MobEditorScreen extends Screen implements KveStudioTab {

    private static final int GUI_W_MIN = 500;
    private int GUI_W = GUI_W_MIN;
    private static final int GUI_H_MAX = 382;
    // 1.4 "Paneles redimensionables con layout
    // persistente": misma familia "columna vecina se achica" que
    // Item/BlockAttributeScreen (GUI_W=500 fijo, RENDER_W a la derecha
    // tambien fijo - ver ResizeHandle/PanelLayoutStore). El rango es mas
    // angosto todavia que el de esas dos (140-190) porque el campo mas
    // ancho de esta pantalla (fCustomName / los botones "toggle" en la
    // columna de stats) mide FIELD_W+60=132px y arranca en
    // ax = L+LIST_W+105: LIST_W+105+132 tiene que quedar por debajo de
    // GUI_W-RENDER_W=390, o sea LIST_W <= 153 (con margen, se dejo el
    // limite en 145). listScrollBar en esta pantalla se reconstruye entero
    // en cada init() (no se preserva entre resizes como en
    // StructurePickerScreen), asi que no hace falta ScrollBar#setPosition
    // aca - mismo motivo por el que TagEditorScreen tampoco lo necesita.
    private static final int LIST_W_MIN     = 100;
    private static final int LIST_W_MAX     = 145;
    private static final int LIST_W_DEFAULT = 135;
    private static final int RENDER_W = 110;
    private static final int PREVIEW_BOX_TOP_OFFSET = 15;
    private static final int PREVIEW_BOX_H = 130;
    private static final float PREVIEW_SCALE_MIN = 8f;
    private static final float PREVIEW_SCALE_MAX = 90f;
    private static final float PREVIEW_SCALE_STEP = 3f;
    private static final int ROWS    = 18;
    private static final int ROW_H_MAX   = 16;
    private static final int FIELD_W = 72;
    private static final int GAP_MAX     = 18;
    private int MOD_ROWS_VISIBLE = 5;

    private int modContentRows() {
        return draftMods.size() > MOD_ROWS_VISIBLE ? Math.max(1, MOD_ROWS_VISIBLE - 1) : MOD_ROWS_VISIBLE;
    }
    private static final int MOD_ROW_H_MAX = 14;
    private static final int TAB_CONTENT_Y = 34;

    private int GUI_H, ROW_H, GAP, MOD_ROW_H, BUILDS_ROW_H, SAVED_ROW_H;

    private int fitH(int availableH) {
        return hosted ? Math.max(160, availableH) : Math.min(GUI_H_MAX, Math.max(160, availableH - 4));
    }

    private void resizeToFit(int availableH) {
        GUI_H = fitH(availableH);
        float scale = Math.min(1f, GUI_H / (float) GUI_H_MAX);
        ROW_H        = Math.max(11, Math.round(ROW_H_MAX        * scale));
        GAP          = Math.max(12, Math.round(GAP_MAX          * scale));
        MOD_ROW_H    = Math.max(10, Math.round(MOD_ROW_H_MAX    * scale));
        BUILDS_ROW_H = Math.max(10, Math.round(BUILDS_ROW_H_MAX * scale));
        SAVED_ROW_H  = Math.max(9,  Math.round(SAVED_ROW_H_MAX  * scale));
        EFFECT_ROW_H = Math.max(10, Math.round(EFFECT_ROW_H_MAX * scale));
        REL_ROW_H    = Math.max(10, Math.round(REL_ROW_H_MAX    * scale));

        int topOffset = TAB_CONTENT_Y + 12 * GAP + 22;
        int trailingBelowList = showMoreActions
                ? 6 + 3 * (KveGuiStyle.BTN_H + KveGuiStyle.BTN_GAP)
                : 6;
        int availableForList = (GUI_H - 26) - topOffset - trailingBelowList;
        MOD_ROWS_VISIBLE = Math.max(1, Math.min(5, availableForList / MOD_ROW_H));

        int savedListTop = PREVIEW_BOX_TOP_OFFSET + PREVIEW_BOX_H + 4 + 11;
        int availableForSaved = (GUI_H - 26) - savedListTop - 4;
        SAVED_ROWS_VISIBLE = Math.max(6, availableForSaved / SAVED_ROW_H);
    }

    private enum Tab { STATS, EQUIPMENT, EFFECTS, RELATIONS, BEHAVIOR }
    private Tab activeTab = Tab.STATS;

    private int L, T, LIST_W;
    private ResizeHandle listResize;

    private final List<EntityType<?>> allMobs   = new ArrayList<>();
    private final List<EntityType<?>> filtered  = new ArrayList<>();
    private ScrollBar listScrollBar;
    private EditBox   searchBox;
    private String    searchFilter = "";

    private EntityType<?> selectedType  = null;
    private LivingEntity  previewEntity = null;
    private float         entityScale   = 30f;

    private EditBox fHealth, fArmor, fArmorTough, fAtkDmg, fAtkSpd, fMoveSpd, fKnockRes, fFollowRange;

    private double vHealth = Double.NaN, vArmor = Double.NaN, vArmorTough = Double.NaN,
                   vAtkDmg = Double.NaN, vAtkSpd = Double.NaN, vMoveSpd = Double.NaN,
                   vKnockRes = Double.NaN, vFollowRange = Double.NaN;

    private EditBox fCustomName;
    private boolean draftNameVisible = false;
    private boolean draftEnabled     = true;
    private String  draftCategory    = "";
    private boolean readOnlyMode     = false;
    private boolean showMoreActions  = false;
    private String  currentMobId     = "";

    private final List<KubeJSMobExporter.AttrMod> draftMods = new ArrayList<>();
    private int modsScroll = 0;
    private final List<Button> modRowWidgets = new ArrayList<>();
    private int modsListAx, modsListTop;

    private String flashMsg = "";
    private long   flashAt  = 0;

    private String externalSourceNote = "";

    private List<KubeJSMobExporter.MobEntry> savedList = new ArrayList<>();
    private int savedScroll = 0;
    private int SAVED_ROWS_VISIBLE = 6;
    private static final int SAVED_ROW_H_MAX = 12;

    private String orphanedSelectedId = null;

    private static final String[] EQUIP_SLOTS = KubeJSMobExporter.EQUIP_SLOTS;
    private static final int EQ_MAINHAND = 0, EQ_OFFHAND = 1, EQ_HEAD = 2, EQ_CHEST = 3, EQ_LEGS = 4, EQ_FEET = 5;
    private static final int[] EQ_GRID_SLOT = { -1, EQ_HEAD, -1, EQ_MAINHAND, EQ_CHEST, EQ_OFFHAND, -1, EQ_LEGS, -1 };
    private final String[] draftEquipItem   = new String[EQUIP_SLOTS.length];
    private final double[] draftEquipChance = new double[EQUIP_SLOTS.length];
    private final String[] draftEquipNbt    = new String[EQUIP_SLOTS.length];
    private final EditBox[] fEquipChance    = new EditBox[EQUIP_SLOTS.length];
    private final double[] draftEquipSpawn  = new double[EQUIP_SLOTS.length];
    private final EditBox[] fEquipSpawn     = new EditBox[EQUIP_SLOTS.length];
    { java.util.Arrays.fill(draftEquipSpawn, 1.0); }
    private int selectedEquipSlot = EQ_MAINHAND;
    private int equipDetailHeaderX, equipDetailHeaderY;

    private static final int BUILDS_ROWS_VISIBLE = 5;
    private static final int BUILDS_ROW_H_MAX = 14;
    private final List<KubeJSMobExporter.EquipmentBuild> draftBuilds = new ArrayList<>();
    private int buildsScroll = 0;
    private final List<Button> buildRowWidgets = new ArrayList<>();
    private int buildsListAx, buildsListTop;

    private enum EffectsSubTab { ATTACK, MOB }
    private EffectsSubTab activeEffectsSubTab = EffectsSubTab.ATTACK;
    private static final int EFFECT_ROW_H_MAX = 14;
    private int EFFECT_ROW_H;
    private int effectRows = 5;
    private final List<KubeJSItemAttrExporter.FoodEffect> draftAttackEffects = new ArrayList<>();
    private final List<KubeJSItemAttrExporter.FoodEffect> draftMobEffects = new ArrayList<>();
    private int effectsScroll = 0;
    private final List<Button> effectRowWidgets = new ArrayList<>();
    private int effectsListAx, effectsListTop;

    private List<KubeJSItemAttrExporter.FoodEffect> currentEffectsList() {
        return activeEffectsSubTab == EffectsSubTab.ATTACK ? draftAttackEffects : draftMobEffects;
    }

    private enum RelSubTab { ALLIES, ENEMIES }
    private RelSubTab activeRelSubTab = RelSubTab.ALLIES;
    private static final int REL_ROW_H_MAX = 14;
    private int REL_ROW_H;
    private int relRows = 5;
    private final List<String> draftAllies = new ArrayList<>();
    private final List<String> draftEnemies = new ArrayList<>();
    private int relScroll = 0;
    private final List<Button> relRowWidgets = new ArrayList<>();
    private int relListAx, relListTop, relQuickY;
    private EditBox fRelManual;
    private static final String[] REL_QUICK = {"minecraft:player", "minecraft:villager", "minecraft:iron_golem", "#minecraft:raiders", "#minecraft:skeletons"};
    private static final String[] REL_QUICK_KEYS = {"player", "villager", "golem", "raiders", "skeletons"};

    private List<String> currentRelList() { return activeRelSubTab == RelSubTab.ALLIES ? draftAllies : draftEnemies; }
    private List<String> otherRelList()   { return activeRelSubTab == RelSubTab.ALLIES ? draftEnemies : draftAllies; }

    private enum BehaviorSubTab { ATTITUDE, IMMUNITY, COMBAT }
    private BehaviorSubTab activeBehaviorSubTab = BehaviorSubTab.ATTITUDE;
    private final java.util.Set<String> draftBehaviorFlags = new java.util.LinkedHashSet<>();
    private int draftXpDrop = -1;
    private double draftRegenPerSecond = Double.NaN, draftDamageTakenMult = Double.NaN, draftDamageDealtMult = Double.NaN;
    private static final String[][] FLAG_GROUPS_ATTITUDE = {
            {"gui.kve.mob.behavior.group.attitude", "HOSTILE", "NEUTRAL", "PASSIVE", "FLEES_PLAYER", "RANGED_ATTACK", "GRIEFS"},
            {"gui.kve.mob.behavior.group.ai", "NO_AI", "FLIES", "CAN_PICKUP_LOOT", "BABY", "LEFT_HANDED", "PERSISTENT"},
            {"gui.kve.mob.behavior.group.looks", "SILENT", "INVISIBLE", "GLOWING"},
    };
    private static final String[][] FLAG_GROUPS_IMMUNITY = {
            {"gui.kve.mob.behavior.group.immunity", "INVULNERABLE", "FIRE_IMMUNE", "FALL_IMMUNE", "DROWN_IMMUNE",
                    "EXPLOSION_IMMUNE", "PROJECTILE_IMMUNE", "NO_KNOCKBACK"},
    };
    private record FlagCell(String flag, int x, int y, int w, int h) {}
    private record SectionHeader(String label, int x, int y, int w) {}
    private final List<FlagCell> flagCells = new ArrayList<>();
    private final List<SectionHeader> sectionHeaders = new ArrayList<>();
    private EditBox fXp, fRegen, fTaken, fDealt;
    private int combatTop, combatRowH;

    private String draftScriptOverride = "";

    private boolean hasScriptOverride() {
        return draftScriptOverride != null && !draftScriptOverride.isBlank();
    }

    private Screen returnScreen = null;

    private boolean hosted = false;
    private int hostedX, hostedY, hostedW, hostedH;

    public MobEditorScreen() {
        super(Lang.c("gui.kve.mob.title"));
    }

    public MobEditorScreen withReturnScreen(Screen s) {
        this.returnScreen = s;
        return this;
    }

    @Override
    protected void init() {
        // "retorno al shell" (regla 5.1, ver Javadoc de
        // KveStudioShell#reclaimIfHosted()): si esto se esta reabriendo
        // porque un picker hijo (MobPickerScreen, MobAttributePickerScreen,
        // ItemPickerScreen, EffectPickerScreen/EffectValueScreen,
        // MobEquipmentBuildScreen, etc.) hizo minecraft.setScreen(this)
        // para "volver" y esta tab sigue siendo la tab activa de un
        // KveStudioShell vivo, reabrir el shell en vez de reconstruirse
        // standalone/centrada.
        if (KveStudioShell.reclaimIfHosted(this, returnScreen)) return;
        hosted = false;
        resizeToFit(height); // must run before any layout math below - see field doc on GUI_H

        GUI_W = GUI_W_MIN;
        L = Math.max(2, Math.min((width  - GUI_W) / 2, width  - GUI_W  - 2));
        T = Math.max(2, Math.min((height - GUI_H) / 2, height - GUI_H - 2));

        buildWidgets();
    }

    @Override
    public void init(int x0, int y0, int w, int h) {
        if (this.minecraft == null) {
            this.minecraft = Minecraft.getInstance();
            this.font = this.minecraft.font;
        }
        hosted = true;
        hostedX = x0; hostedY = y0; hostedW = w; hostedH = h;
        resizeToFit(h);
        // Mismo criterio que Tags/Loot: el shell puede ofrecer mas ancho
        // que el minimo historico de esta pantalla (GUI_W crece), nunca
        // menos (evita solapar los campos fijos de buildWidgets(), que
        // siguen asumiendo el ancho de columna actual via LIST_W/RENDER_W).
        GUI_W = Math.max(w, GUI_W_MIN);
        L = x0;
        T = y0;
        buildWidgets();
    }

    private void refresh() {
        if (hosted) {
            resizeToFit(hostedH);
            GUI_W = Math.max(hostedW, GUI_W_MIN);
            L = hostedX;
            T = hostedY;
            buildWidgets();
        } else {
            init();
        }
    }

    private void buildWidgets() {
        if (listResize == null) {
            listResize = new ResizeHandle("mob_editor.list", LIST_W_MIN, LIST_W_MAX, LIST_W_DEFAULT);
        }
        LIST_W = listResize.width();
        listResize.reposition(L, T + 15, T + GUI_H);

        boolean hadFieldsBefore = fHealth != null;
        String snapHealth      = hadFieldsBefore ? fHealth.getValue()      : null;
        String snapArmor       = hadFieldsBefore ? fArmor.getValue()       : null;
        String snapArmorTough  = hadFieldsBefore ? fArmorTough.getValue()  : null;
        String snapAtkDmg      = hadFieldsBefore ? fAtkDmg.getValue()      : null;
        String snapAtkSpd      = hadFieldsBefore ? fAtkSpd.getValue()      : null;
        String snapMoveSpd     = hadFieldsBefore ? fMoveSpd.getValue()     : null;
        String snapKnockRes    = hadFieldsBefore ? fKnockRes.getValue()    : null;
        String snapFollowRange = hadFieldsBefore ? fFollowRange.getValue() : null;
        String snapCustomName  = fCustomName != null ? fCustomName.getValue() : null;
        String[] snapEquipChance = new String[EQUIP_SLOTS.length];
        String[] snapEquipSpawn = new String[EQUIP_SLOTS.length];
        for (int i = 0; i < EQUIP_SLOTS.length; i++) {
            snapEquipChance[i] = fEquipChance[i] != null ? fEquipChance[i].getValue() : null;
            snapEquipSpawn[i]  = fEquipSpawn[i]  != null ? fEquipSpawn[i].getValue()  : null;
        }

        clearWidgets();
        setFocused(null);

        searchBox = new KveScaledEditBox(font, L + 2, T + 16, LIST_W - 4, 11,
                Component.literal(Lang.get("gui.kve.search.mob")));
        searchBox.setHint(Component.literal(Lang.get("gui.kve.search.mob")));
        searchBox.setMaxLength(48);
        searchBox.setValue(searchFilter);
        searchBox.setResponder(s -> {
            searchFilter = s;
            mobNav.reset();
            rebuildFilter();
            listScrollBar.setTotals(filtered.size(), ROWS);
        });
        addRenderableWidget(searchBox);

        int prevListScroll = listScrollBar != null ? listScrollBar.getScroll() : 0;
        listScrollBar = new ScrollBar(L + LIST_W - 10, T + 30, ROWS * ROW_H, 0, ROWS);

        int ax = L + LIST_W + 105;
        int tabX0 = L + LIST_W + 2;
        tabRow = new KveGuiStyle.GridTabRow(
                tabX0, L + GUI_W - RENDER_W - 12, T + 16, 2,
                new String[] {
                        Lang.get("gui.kve.mob.tab.stats"), Lang.get("gui.kve.mob.tab.equipment"),
                        Lang.get("gui.kve.mob.tab.effects"), Lang.get("gui.kve.mob.tab.relations"),
                        Lang.get("gui.kve.mob.tab.behavior")
                },
                new Runnable[] {
                        () -> switchTab(Tab.STATS), () -> switchTab(Tab.EQUIPMENT), () -> switchTab(Tab.EFFECTS),
                        () -> switchTab(Tab.RELATIONS), () -> switchTab(Tab.BEHAVIOR)
                },
                activeTab.ordinal());
        tabRow.addAllTo(this::addRenderableWidget);
        for (int i = 0; i < Tab.values().length; i++) {
            tabRow.get(i).setTooltip(Tooltip.create(Component.literal(
                    Lang.get("gui.kve.mob.tab." + TAB_KEYS[i] + ".tip") + "  (Ctrl+" + (i + 1) + ")")));
        }

        int tabX = L + LIST_W + 6;
        if (activeTab == Tab.STATS) {
            KveGuiStyle.Layout ly = new KveGuiStyle.Layout(ax, T + TAB_CONTENT_Y, GAP);

            fHealth     = field(ly.x(), ly.row());
            fArmor      = field(ly.x(), ly.row());
            fArmorTough = field(ly.x(), ly.row());
            fAtkDmg     = field(ly.x(), ly.row());
            fAtkSpd     = field(ly.x(), ly.row());
            fMoveSpd    = field(ly.x(), ly.row());
            fKnockRes   = field(ly.x(), ly.row());
            fFollowRange= field(ly.x(), ly.row());
            ly.gap(4);

            fCustomName = new KveScaledEditBox(font, ly.x(), ly.row(), FIELD_W + 60, 11, Component.literal(Lang.get("gui.kve.mob.custom_name")));
            fCustomName.setMaxLength(64);
            fCustomName.setHint(Component.literal(Lang.get("gui.kve.mob.name_hint")));
            addRenderableWidget(fCustomName);

            ly.row(9);
            addRenderableWidget(btn(Lang.get(draftNameVisible ? "gui.kve.mob.visible_always" : "gui.kve.mob.visible_hover"),
                    ly.x(), ly.row(11), 0, this::toggleNameVisible));
            // el gap(4)+gap(6)+gap(4)
            // de aca abajo (14px en total) dejaba "Atributos extra" mas
            // lejos del bloque de arriba (stats fijos + nombre/hint + boton
            // Visible) de lo necesario. Reducido a gap(2)+gap(3)+gap(2)
            // (7px), acerca la seccion sin pisar el boton Visible. Espejado
            // 1:1 en drawStatsLabels() - ver comentario largo de esa
            // funcion sobre por que ambos ly tienen que coincidir siempre.
            ly.gap(2);

            ly.gap(3);
            addRenderableWidget(btn("+ " + Lang.get("gui.kve.attr.title"), ly.x(), ly.y(), 0, this::openAddModification));
            ly.gap(KveGuiStyle.BTN_H);
            modsListAx = ly.x();
            modsListTop = ly.y();
            modRowWidgets.clear();
            addModRowWidgets();

        } else if (activeTab == Tab.EQUIPMENT) {
            buildEquipmentTab(tabX, T + TAB_CONTENT_Y);
        } else if (activeTab == Tab.EFFECTS) {
            buildEffectsTab(tabX, T + TAB_CONTENT_Y);
        } else if (activeTab == Tab.RELATIONS) {
            buildRelationsTab(tabX, T + TAB_CONTENT_Y);
        } else {
            buildBehaviorTab(tabX, T + TAB_CONTENT_Y);
        }

        int by = T + GUI_H - 26;
        int bx = L + LIST_W + 6;
        boolean canUndo = !currentMobId.isEmpty() && KubeJSMobExporter.canUndo(currentMobId);
        String saveL = Lang.get("gui.kve.btn.save"), deleteL = Lang.get("gui.kve.btn.delete"),
                dupL = Lang.get("gui.kve.btn.duplicate_short"), resetL = Lang.get("gui.kve.btn.reset_short"),
                previewL = Lang.get("gui.kve.btn.preview_short"), undoL = Lang.get("gui.kve.btn.undo_short");
        int rowMaxX = L + GUI_W - RENDER_W - 4;
        KveGuiStyle.ButtonRow actionRow = new KveGuiStyle.ButtonRow(bx, by, rowMaxX, true);
        KvePlateButton saveBtn = actionRow.add(0, saveL, b -> saveMob());
        saveBtn.withPrimary(true);
        saveBtn.setTooltip(Tooltip.create(Component.literal(Lang.get("gui.kve.mob.save_tip"))));
        addRenderableWidget(saveBtn);

        // Guardar con texto; el resto son simbolos con tooltip, agrupados: revisar, reutilizar,
        // navegar y, separado al final, Eliminar.
        // misma fila en todos los editores: frecuentes con texto, lo poco usado en "Mas", Eliminar al final
        KveGuiStyle.ActionBar bar = new KveGuiStyle.ActionBar(actionRow, this::addRenderableWidget);
        bar.button(Lang.get("gui.kve.btn.preview"), b -> previewMob());
        bar.button(Lang.get("gui.kve.btn.duplicate"), b -> duplicateMob());
        bar.enabled(draftEnabled, b -> toggleEnabled());
        bar.history(Lang.get("gui.kve.btn.undo"), () -> undoMob(), canUndo)
                .history(Lang.get("gui.kve.btn.reset"), () -> resetFields())
                .more(Lang.get("gui.kve.mob.template.save_title"), () -> saveAsTemplate())
                .more(Lang.get("gui.kve.mob.template.pick_title"), () -> openTemplatePicker())
                .more(Lang.get("gui.kve.mob.view_drops"), () -> openLootTable(), !currentMobId.isEmpty())
                .state(Lang.get(readOnlyMode ? "gui.kve.mob.readonly_on" : "gui.kve.mob.readonly_off"), () -> toggleReadOnly());
        bar.end(() -> deleteMob());

        int pendCount = PendingChangeManager.size();
        int closeX = L + GUI_W - 17;
        int pendBtnX;
        if (!hosted) {
            String applyL = pendCount > 0 ? Lang.get("gui.kve.pending.apply") + " (" + pendCount + ")" : Lang.get("gui.kve.pending.apply");
            String discardL = pendCount > 0 ? Lang.get("gui.kve.pending.discard") + " (" + pendCount + ")" : Lang.get("gui.kve.pending.discard");
            int discardW = KveGuiStyle.btnWidth(discardL), applyW = KveGuiStyle.btnWidth(applyL);
            int discardX = closeX - KveGuiStyle.BTN_GAP - discardW;
            int applyX = discardX - KveGuiStyle.BTN_GAP - applyW;
            Button applyPendBtn = addRenderableWidget(btn(applyL, applyX, T + 2, 0, this::onApplyPending));
            applyPendBtn.active = pendCount > 0 && ClientPermissionState.canApply();
            Button discardPendBtn = addRenderableWidget(btn(discardL, discardX, T + 2, 0, this::onDiscardPending));
            discardPendBtn.active = pendCount > 0;
            pendBtnX = applyX - KveGuiStyle.BTN_GAP - 110;
        } else {
            pendBtnX = closeX - KveGuiStyle.BTN_GAP - 110;
        }
        if (!hosted) {
            addRenderableWidget(KveGuiStyle.pendingChangesButton(pendBtnX, T + 2, 110, 11, this));
        }

        if (!currentMobId.isEmpty()) {
            addRenderableWidget(btn(
                    CraftEditorState.isFavoriteMob(currentMobId) ? "\u2605" : "\u2606",
                    closeX, T + 16, 0, this::toggleFavoriteCurrentMob));

            Button idUsageBtn = btn("?", closeX - 12, T + 16, 0, this::openIdUsageForCurrentMob);
            idUsageBtn.setTooltip(Tooltip.create(Component.literal(Lang.get("gui.kve.idusage.title"))));
            addRenderableWidget(idUsageBtn);
        }

        buildMobList();
        listScrollBar.setTotals(allMobs.size(), ROWS);
        rebuildFilter();
        listScrollBar.setScroll(prevListScroll);
        refreshSaved();

        if (hadFieldsBefore) {
            if (snapHealth      != null) fHealth.setValue(snapHealth);
            if (snapArmor       != null) fArmor.setValue(snapArmor);
            if (snapArmorTough  != null) fArmorTough.setValue(snapArmorTough);
            if (snapAtkDmg      != null) fAtkDmg.setValue(snapAtkDmg);
            if (snapAtkSpd      != null) fAtkSpd.setValue(snapAtkSpd);
            if (snapMoveSpd     != null) fMoveSpd.setValue(snapMoveSpd);
            if (snapKnockRes    != null) fKnockRes.setValue(snapKnockRes);
            if (snapFollowRange != null) fFollowRange.setValue(snapFollowRange);
            if (snapCustomName  != null && fCustomName != null) fCustomName.setValue(snapCustomName);
            for (int i = 0; i < EQUIP_SLOTS.length; i++) {
                if (snapEquipChance[i] != null && fEquipChance[i] != null) fEquipChance[i].setValue(snapEquipChance[i]);
                if (snapEquipSpawn[i]  != null && fEquipSpawn[i]  != null) fEquipSpawn[i].setValue(snapEquipSpawn[i]);
            }
        } else if (selectedType != null) {
            populateFields();
            markClean();
        }
    }

    private EditBox field(int x, int y) {
        EditBox box = new KveScaledEditBox(font, x, y, FIELD_W, 11, Component.empty());
        box.setMaxLength(12);
        box.setHint(Component.literal(Lang.get("gui.kve.mob.no_change")));
        box.setEditable(true);
        addRenderableWidget(box);
        return box;
    }

    private void switchTab(Tab t) {
        if (activeTab == t) return;
        activeTab = t;
        refresh();
    }

    private String equipCellLabel(int slotIdx, int maxTextW) {
        boolean hasItem = draftEquipItem[slotIdx] != null && !draftEquipItem[slotIdx].isEmpty();
        String base = hasItem ? draftEquipItem[slotIdx]
                : "(" + Lang.get("gui.kve.mob.equip." + EQUIP_SLOTS[slotIdx].toLowerCase()) + ")";
        return KveGuiStyle.truncate(font, base, maxTextW);
    }

    private void buildEquipmentTab(int ax, int ay) {
        if (draftBuilds.isEmpty()) {
            buildSimpleEquipmentTab(ax, ay);
        } else {
            buildEquipmentBuildsTab(ax, ay);
        }
    }

    private void buildSimpleEquipmentTab(int ax, int ay) {
        int w = contentRight() - ax;
        KveGuiStyle.Layout ly = new KveGuiStyle.Layout(ax, ay, GAP);

        int gridCell = (w - 4) / 3;
        int gridTop = ly.y();
        for (int cell = 0; cell < 9; cell++) {
            int slot = EQ_GRID_SLOT[cell];
            if (slot == -1) continue;
            int col = cell % 3, row = cell / 3;
            int cx = ax + col * (gridCell + 2);
            int cy = gridTop + row * GAP;
            addRenderableWidget(equipCellButton(slot, cx, cy, gridCell));
        }
        ly.gap(GAP * 3);
        addRenderableWidget(equipCellButton(EQ_FEET, ax + gridCell + 2, ly.row(), gridCell));
        ly.gap(2);

        buildEquipDetailRow(ax, ly, w);

        addRenderableWidget(btn(Lang.get("gui.kve.mob.equip.use_builds"), ax, ly.row(), 0, this::enableBuildsMode));
    }

    private Button equipCellButton(int slot, int x, int y, int size) {
        String label = equipCellLabel(slot, size - 4);
        boolean selected = slot == selectedEquipSlot;
        KvePlateButton b = new KvePlateButton(x, y, size, 12, Component.literal(label), btn -> onEquipCellClick(slot)) {
            @Override
            public void renderWidget(GuiGraphics g, int mx, int my, float partialTick) {
                KveGuiStyle.ButtonState state = !this.active ? KveGuiStyle.ButtonState.DISABLED
                        : selected ? KveGuiStyle.ButtonState.ACTIVE
                        : this.isHovered() ? KveGuiStyle.ButtonState.HOVER : KveGuiStyle.ButtonState.NORMAL;
                KveGuiStyle.button(g, getX(), getY(), getWidth(), getHeight(), getMessage().getString(), state);
            }
        };
        String full = equipCellLabel(slot, Integer.MAX_VALUE);
        if (font.width(full) > size - 4) {
            b.setTooltip(Tooltip.create(Component.literal(full)));
        }
        return b;
    }

    private void onEquipCellClick(int slot) {
        commitEquipChanceDraft();
        selectedEquipSlot = slot;
        boolean hasItem = draftEquipItem[slot] != null && !draftEquipItem[slot].isEmpty();
        if (hasItem) {
            refresh();
        } else {
            openEquipPicker(slot);
        }
    }

    private void commitEquipChanceDraft() {
        EditBox f = fEquipChance[selectedEquipSlot];
        if (f != null) {
            double pct = parseD(f);
            if (pct >= 0) draftEquipChance[selectedEquipSlot] = Math.max(0, Math.min(100, pct)) / 100.0;
        }
        EditBox sp = fEquipSpawn[selectedEquipSlot];
        if (sp != null) {
            double v = KubeJSMobExporter.parseProbability(sp.getValue());
            if (!Double.isNaN(v)) draftEquipSpawn[selectedEquipSlot] = v;
        }
    }

    private void buildEquipDetailRow(int ax, KveGuiStyle.Layout ly, int w) {
        java.util.Arrays.fill(fEquipChance, null);
        java.util.Arrays.fill(fEquipSpawn, null);

        equipDetailHeaderX = ax;
        equipDetailHeaderY = ly.row(9);

        int rowY = ly.row();
        boolean hasItem = draftEquipItem[selectedEquipSlot] != null && !draftEquipItem[selectedEquipSlot].isEmpty();

        boolean hasEnchant = draftEquipNbt[selectedEquipSlot] != null && !draftEquipNbt[selectedEquipSlot].isBlank();
        String enchLabel = hasEnchant ? "E*" : "E";
        int spawnW = 30;
        int chanceW = 30;
        int gap = KveGuiStyle.BTN_GAP;
        int enchW = 14;
        int attrW = KveGuiStyle.btnWidth("Atr");
        int removeW = 14;
        int itemW = w - spawnW - chanceW - enchW - attrW - removeW - gap * 5;
        int spawnX = ax + itemW + gap;
        int chanceX = spawnX + spawnW + gap;
        int enchX = chanceX + chanceW + gap;
        int attrX = enchX + enchW + gap;
        int removeX = attrX + attrW + gap;

        String itemLabel = !hasItem
                ? "(" + Lang.get("gui.kve.mob.equip." + EQUIP_SLOTS[selectedEquipSlot].toLowerCase()) + ")"
                : draftEquipItem[selectedEquipSlot];
        // sec.7.2: draftEquipItem es un id
        // de item (puede ser largo, ej. de mods) - truncado ANTES de pasarlo
        // a btn() para que btnWidth(label) (font.width+BTN_PAD_X*2) del
        // texto ya truncado no supere itemW y se coma la EditBox de chance/
        // los botones E/X que vienen despues a posicion fija (ax+itemW+...).
        // El limite de truncate() resta BTN_PAD_X*2, no un numero suelto,
        // para que quede exacto contra la formula real de btnWidth().
        Button itemBtn = btn(KveGuiStyle.truncate(font, itemLabel, Math.max(4, itemW - KveGuiStyle.BTN_PAD_X * 2)), ax, rowY, 0, () -> openEquipPicker(selectedEquipSlot));
        if (font.width(itemLabel) > Math.max(4, itemW - KveGuiStyle.BTN_PAD_X * 2)) {
            itemBtn.setTooltip(Tooltip.create(Component.literal(itemLabel)));
        }
        addRenderableWidget(itemBtn);

        EditBox spawn = new KveScaledEditBox(font, spawnX, rowY, spawnW, 11, Component.literal("prob"));
        spawn.setMaxLength(6);
        spawn.setHint(Component.literal("1"));
        spawn.setValue(!hasItem ? "" : fmtShort(draftEquipSpawn[selectedEquipSlot]));
        spawn.setTooltip(Tooltip.create(Component.literal(Lang.get("gui.kve.mob.equip.spawn_chance_tip"))));
        addRenderableWidget(spawn);
        fEquipSpawn[selectedEquipSlot] = spawn;

        EditBox chance = new KveScaledEditBox(font, chanceX, rowY, chanceW, 11, Component.literal("%"));
        chance.setMaxLength(6);
        chance.setHint(Component.literal("drop%"));
        chance.setValue(!hasItem ? "" : fmtShort(draftEquipChance[selectedEquipSlot] * 100));
        chance.setTooltip(Tooltip.create(Component.literal(Lang.get("gui.kve.mob.equip.drop_chance_tip"))));
        addRenderableWidget(chance);
        fEquipChance[selectedEquipSlot] = chance;

        Button enchBtn = btn(enchLabel, enchX, rowY, enchW, () -> openEquipEnchant(selectedEquipSlot));
        enchBtn.active = hasItem;
        addRenderableWidget(enchBtn);

        Button attrBtn = btn("Atr", attrX, rowY, attrW, () -> openEquipAttributes(selectedEquipSlot));
        attrBtn.active = hasItem;
        addRenderableWidget(attrBtn);

        addRenderableWidget(btn("X", removeX, rowY, removeW, () -> clearEquip(selectedEquipSlot)));
    }

    private void openEquipAttributes(int slot) {
        if (draftEquipItem[slot] == null || draftEquipItem[slot].isEmpty()) return;
        if (openItemInShell(draftEquipItem[slot])) return;
        minecraft.setScreen(new ItemAttributeScreen(draftEquipItem[slot]).withReturnScreen(this));
    }

    boolean openItemInShell(String itemId) {
        if (!hosted || !(returnScreen instanceof KveStudioShell shell)) return false;
        if (minecraft.screen != shell) minecraft.setScreen(shell);
        return shell.jumpToTab("gui.kve.studio.tab.properties", itemId);
    }

    private void buildEquipmentBuildsTab(int ax, int ay) {
        int w = contentRight() - ax;
        KveGuiStyle.Layout ly = new KveGuiStyle.Layout(ax, ay, GAP);

        addRenderableWidget(btn(Lang.get("gui.kve.mob.build.add"), ly.x(), ly.row(), 0, this::openAddBuild));
        ly.row();

        buildsListAx = ly.x();
        buildsListTop = ly.y();
        buildRowWidgets.clear();
        addBuildRowWidgets();

        int extraY = buildsListTop + BUILDS_ROWS_VISIBLE * BUILDS_ROW_H + 4;
        addRenderableWidget(btn(Lang.get("gui.kve.mob.build.disable"), ax, extraY, 0, this::disableBuildsMode));
    }

    private static final String[] TAB_KEYS = {"stats", "equipment", "effects", "relations", "behavior"};
    private KveGuiStyle.GridTabRow tabRow;
    private static final int DESC_H = 21;
    private int sectionDescY;

    private KvePlateButton segBtn(String label, int x, int y, int w, boolean selected, Runnable action) {
        KvePlateButton b = new KvePlateButton(x, y, w, KveGuiStyle.BTN_H,
                Component.literal(KveGuiStyle.truncate(font, label, Math.max(0, w - 4))), btn -> action.run());
        b.withSelected(selected);
        if (font.width(label) > w - 4) b.setTooltip(Tooltip.create(Component.literal(label)));
        return b;
    }

    private int listRowsFor(int listTop, int rowH) {
        int bottom = T + GUI_H - 26 - 12
                - (showMoreActions ? 3 * (KveGuiStyle.BTN_H + KveGuiStyle.BTN_GAP) : 0);
        return Math.max(3, Math.min(14, (bottom - listTop) / Math.max(1, rowH)));
    }

    private void buildEffectsTab(int ax, int ay) {
        int w = contentRight() - ax;
        int halfW = (w - 4) / 2;
        addRenderableWidget(segBtn(Lang.get("gui.kve.mob.effect.tab.attack_long") + " (" + draftAttackEffects.size() + ")",
                ax, ay, halfW, activeEffectsSubTab == EffectsSubTab.ATTACK, () -> switchEffectsSubTab(EffectsSubTab.ATTACK)));
        addRenderableWidget(segBtn(Lang.get("gui.kve.mob.effect.tab.mob_long") + " (" + draftMobEffects.size() + ")",
                ax + halfW + 4, ay, w - halfW - 4, activeEffectsSubTab == EffectsSubTab.MOB, () -> switchEffectsSubTab(EffectsSubTab.MOB)));
        sectionDescY = ay + KveGuiStyle.BTN_H + 4;
        int headerY = sectionDescY + DESC_H;
        String addL = Lang.get("gui.kve.mob.effect.add");
        addRenderableWidget(btn(addL, rightBtnX(addL), headerY, 0, this::openAddEffect));
        effectsListAx = ax;
        effectsListTop = headerY + KveGuiStyle.BTN_H + 4;
        effectRows = listRowsFor(effectsListTop, EFFECT_ROW_H);
        effectRowWidgets.clear();
        addEffectRowWidgets();
    }

    private void switchEffectsSubTab(EffectsSubTab sub) {
        if (activeEffectsSubTab == sub) return;
        activeEffectsSubTab = sub;
        effectsScroll = 0;
        refresh();
    }

    private void openAddEffect() {
        minecraft.setScreen(new EffectPickerScreen(this, effectId -> {
            minecraft.setScreen(new EffectValueScreen(this, effectId, KveEffectInfo.name(effectId), fx -> {
                List<KubeJSItemAttrExporter.FoodEffect> list = currentEffectsList();
                list.add(fx);
                effectsScroll = Math.max(0, list.size() - effectRows);
                minecraft.setScreen(this);
                refresh();
            }));
        }));
    }

    private void openEditEffect(int idx) {
        List<KubeJSItemAttrExporter.FoodEffect> list = currentEffectsList();
        if (idx < 0 || idx >= list.size()) return;
        KubeJSItemAttrExporter.FoodEffect existing = list.get(idx);
        minecraft.setScreen(new EffectValueScreen(this, existing.effectId, KveEffectInfo.name(existing.effectId), fx -> {
            list.set(idx, fx);
            minecraft.setScreen(this);
            refresh();
        }).withExisting(existing));
    }

    private void removeEffect(int idx) {
        List<KubeJSItemAttrExporter.FoodEffect> list = currentEffectsList();
        if (idx < 0 || idx >= list.size()) return;
        list.remove(idx);
        if (effectsScroll > 0 && effectsScroll >= list.size()) effectsScroll--;
        refresh();
    }

    private int effectEditX() { return contentRight() - 14 - KveGuiStyle.BTN_GAP - KveGuiStyle.btnWidth(Lang.get("gui.kve.btn.edit")); }

    private void addEffectRowWidgets() {
        List<KubeJSItemAttrExporter.FoodEffect> list = currentEffectsList();
        int maxScroll = Math.max(0, list.size() - effectRows);
        effectsScroll = Math.max(0, Math.min(maxScroll, effectsScroll));
        for (int i = 0; i < effectRows; i++) {
            int idx = i + effectsScroll;
            if (idx >= list.size()) break;
            int ry = effectsListTop + i * EFFECT_ROW_H;
            int finalIdx = idx;
            effectRowWidgets.add(addRenderableWidget(btn(Lang.get("gui.kve.btn.edit"), effectEditX(), ry, 0, () -> openEditEffect(finalIdx))));
            effectRowWidgets.add(addRenderableWidget(btn("X", contentRight() - 14, ry, 14, () -> removeEffect(finalIdx))));
        }
    }

    private void rebuildEffectRows() {
        for (Button w : effectRowWidgets) removeWidget(w);
        effectRowWidgets.clear();
        addEffectRowWidgets();
    }

    private void buildRelationsTab(int ax, int ay) {
        int w = contentRight() - ax;
        int halfW = (w - 4) / 2;
        addRenderableWidget(segBtn(Lang.get("gui.kve.mob.tab.allies") + " (" + draftAllies.size() + ")",
                ax, ay, halfW, activeRelSubTab == RelSubTab.ALLIES, () -> switchRelSubTab(RelSubTab.ALLIES)));
        addRenderableWidget(segBtn(Lang.get("gui.kve.mob.tab.enemies") + " (" + draftEnemies.size() + ")",
                ax + halfW + 4, ay, w - halfW - 4, activeRelSubTab == RelSubTab.ENEMIES, () -> switchRelSubTab(RelSubTab.ENEMIES)));
        sectionDescY = ay + KveGuiStyle.BTN_H + 4;

        int inputY = sectionDescY + DESC_H;
        String pickL = Lang.get("gui.kve.mob.rel.pick");
        int pickW = KveGuiStyle.btnWidth(pickL);
        int fieldW = Math.max(40, w - 14 - pickW - 2 * KveGuiStyle.BTN_GAP);
        String keep = fRelManual != null ? fRelManual.getValue() : "";
        fRelManual = new KveScaledEditBox(font, ax, inputY + 1, fieldW, 12, Component.literal(Lang.get("gui.kve.mob.rel.manual_hint")));
        fRelManual.setMaxLength(96);
        fRelManual.setHint(Component.literal(Lang.get("gui.kve.mob.rel.manual_hint")));
        fRelManual.setValue(keep);
        addRenderableWidget(fRelManual);
        Button addBtn = btn("+", ax + fieldW + KveGuiStyle.BTN_GAP, inputY, 14, this::addRelFromField);
        addBtn.setTooltip(Tooltip.create(Component.literal(Lang.get("gui.kve.mob.rel.add_tip"))));
        addRenderableWidget(addBtn);
        addRenderableWidget(btn(pickL, contentRight() - pickW, inputY, 0, this::openRelPicker));

        relQuickY = inputY + KveGuiStyle.BTN_H + 4;
        int qx = ax;
        List<String> cur = currentRelList();
        for (int i = 0; i < REL_QUICK.length; i++) {
            String label = Lang.get("gui.kve.mob.rel.quick." + REL_QUICK_KEYS[i]);
            int bw = font.width(label) + 10;
            if (qx + bw > contentRight()) break;
            String value = REL_QUICK[i];
            KvePlateButton chip = new KvePlateButton(qx, relQuickY, bw, KveGuiStyle.BTN_H, Component.literal(label), b -> addRelValue(value));
            chip.setTooltip(Tooltip.create(Component.literal(Lang.get("gui.kve.mob.rel.quick_tip", value))));
            chip.active = !cur.contains(value);
            addRenderableWidget(chip);
            qx += bw + KveGuiStyle.BTN_GAP;
        }

        relListAx = ax;
        relListTop = relQuickY + KveGuiStyle.BTN_H + 4 + 12;
        relRows = listRowsFor(relListTop, REL_ROW_H);
        relRowWidgets.clear();
        addRelRowWidgets();
    }

    private void switchRelSubTab(RelSubTab sub) {
        if (activeRelSubTab == sub) return;
        activeRelSubTab = sub;
        relScroll = 0;
        refresh();
    }

    private void addRelFromField() {
        if (fRelManual == null) return;
        String raw = fRelManual.getValue().trim().toLowerCase(Locale.ROOT);
        if (raw.isEmpty()) return;
        boolean tag = raw.startsWith("#");
        String body = tag ? raw.substring(1) : raw;
        if (!body.contains(":")) body = "minecraft:" + body;
        fRelManual.setValue("");
        addRelValue(tag ? "#" + body : body);
    }

    private void addRelValue(String value) {
        List<String> cur = currentRelList();
        String bare = value.startsWith("#") ? value.substring(1) : value;
        if (ResourceLocation.tryParse(bare) == null) { flashWarn(Lang.get("gui.kve.mob.rel.invalid", value)); refresh(); return; }
        if (cur.contains(value)) { flashWarn(Lang.get("gui.kve.mob.rel.duplicate", value)); refresh(); return; }
        cur.add(value);
        if (otherRelList().contains(value)) flashWarn(Lang.get("gui.kve.mob.rel.conflict_flash", value));
        relScroll = Math.max(0, cur.size() - relRows);
        refresh();
    }

    private void openRelPicker() {
        minecraft.setScreen(new MobPickerScreen(this, type -> {
            ResourceLocation k = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            minecraft.setScreen(this);
            if (k != null) addRelValue(k.toString());
            else refresh();
        }));
    }

    private void removeRel(int idx) {
        List<String> list = currentRelList();
        if (idx < 0 || idx >= list.size()) return;
        list.remove(idx);
        if (relScroll > 0 && relScroll >= list.size()) relScroll--;
        refresh();
    }

    private void moveRel(int idx) {
        List<String> list = currentRelList();
        if (idx < 0 || idx >= list.size()) return;
        String v = list.remove(idx);
        List<String> other = otherRelList();
        if (!other.contains(v)) other.add(v);
        if (relScroll > 0 && relScroll >= list.size()) relScroll--;
        flash(Lang.get(activeRelSubTab == RelSubTab.ALLIES ? "gui.kve.mob.rel.moved_to_enemies" : "gui.kve.mob.rel.moved_to_allies", v));
        refresh();
    }

    private int relTextRight() { return contentRight() - 2 * 14 - 2 * KveGuiStyle.BTN_GAP; }

    private void addRelRowWidgets() {
        List<String> list = currentRelList();
        int maxScroll = Math.max(0, list.size() - relRows);
        relScroll = Math.max(0, Math.min(maxScroll, relScroll));
        String moveTip = Lang.get(activeRelSubTab == RelSubTab.ALLIES ? "gui.kve.mob.rel.move_to_enemies" : "gui.kve.mob.rel.move_to_allies");
        for (int i = 0; i < relRows; i++) {
            int idx = i + relScroll;
            if (idx >= list.size()) break;
            int ry = relListTop + i * REL_ROW_H;
            int finalIdx = idx;
            Button mv = btn("⇄", contentRight() - 14 - KveGuiStyle.BTN_GAP - 14, ry, 14, () -> moveRel(finalIdx));
            mv.setTooltip(Tooltip.create(Component.literal(moveTip)));
            relRowWidgets.add(addRenderableWidget(mv));
            relRowWidgets.add(addRenderableWidget(btn("X", contentRight() - 14, ry, 14, () -> removeRel(finalIdx))));
        }
    }

    private void rebuildRelRows() {
        for (Button w : relRowWidgets) removeWidget(w);
        relRowWidgets.clear();
        addRelRowWidgets();
    }

    private int flagCount(String[][] groups) {
        int n = 0;
        for (String[] g : groups) for (int i = 1; i < g.length; i++) if (draftBehaviorFlags.contains(g[i])) n++;
        return n;
    }

    private int combatFieldCount() {
        int n = 0;
        if (draftXpDrop >= 0) n++;
        if (!Double.isNaN(draftRegenPerSecond)) n++;
        if (!Double.isNaN(draftDamageTakenMult)) n++;
        if (!Double.isNaN(draftDamageDealtMult)) n++;
        return n;
    }

    /** Los grupos de la sub-pestaña + "Otros" con cualquier flag de BEHAVIOR_FLAGS que no este agrupado (un flag nuevo nunca queda inalcanzable). */
    private String[][] behaviorGroupsFor(BehaviorSubTab sub) {
        String[][] base = sub == BehaviorSubTab.IMMUNITY ? FLAG_GROUPS_IMMUNITY : FLAG_GROUPS_ATTITUDE;
        if (sub != BehaviorSubTab.ATTITUDE) return base;
        java.util.Set<String> grouped = new java.util.HashSet<>();
        for (String[][] gs : new String[][][]{FLAG_GROUPS_ATTITUDE, FLAG_GROUPS_IMMUNITY})
            for (String[] g : gs) for (int i = 1; i < g.length; i++) grouped.add(g[i]);
        List<String> rest = new ArrayList<>();
        rest.add("gui.kve.mob.behavior.group.other");
        for (String f : KubeJSMobExporter.BEHAVIOR_FLAGS) if (!grouped.contains(f)) rest.add(f);
        if (rest.size() == 1) return base;
        String[][] out = java.util.Arrays.copyOf(base, base.length + 1);
        out[base.length] = rest.toArray(new String[0]);
        return out;
    }

    private void buildBehaviorTab(int ax, int ay) {
        int w = contentRight() - ax;
        int thirdW = (w - 8) / 3;
        addRenderableWidget(segBtn(Lang.get("gui.kve.mob.behavior.sub.attitude") + " (" + flagCount(behaviorGroupsFor(BehaviorSubTab.ATTITUDE)) + ")",
                ax, ay, thirdW, activeBehaviorSubTab == BehaviorSubTab.ATTITUDE, () -> switchBehaviorSubTab(BehaviorSubTab.ATTITUDE)));
        addRenderableWidget(segBtn(Lang.get("gui.kve.mob.behavior.sub.immunity") + " (" + flagCount(FLAG_GROUPS_IMMUNITY) + ")",
                ax + thirdW + 4, ay, thirdW, activeBehaviorSubTab == BehaviorSubTab.IMMUNITY, () -> switchBehaviorSubTab(BehaviorSubTab.IMMUNITY)));
        addRenderableWidget(segBtn(Lang.get("gui.kve.mob.behavior.sub.combat") + " (" + combatFieldCount() + ")",
                ax + 2 * (thirdW + 4), ay, w - 2 * (thirdW + 4), activeBehaviorSubTab == BehaviorSubTab.COMBAT, () -> switchBehaviorSubTab(BehaviorSubTab.COMBAT)));
        sectionDescY = ay + KveGuiStyle.BTN_H + 4;
        int y = sectionDescY + DESC_H;
        flagCells.clear();
        sectionHeaders.clear();

        if (activeBehaviorSubTab == BehaviorSubTab.COMBAT) {
            buildCombatFields(ax, y, w);
            return;
        }

        String[][] groups = behaviorGroupsFor(activeBehaviorSubTab);
        int rowsTotal = 0;
        for (String[] g : groups) rowsTotal += (g.length - 1 + 1) / 2;
        int avail = (T + GUI_H - 26 - 8 - (showMoreActions ? 3 * (KveGuiStyle.BTN_H + KveGuiStyle.BTN_GAP) : 0)) - y - groups.length * 14;
        int cellH = Math.max(10, Math.min(13, avail / Math.max(1, rowsTotal)));
        int colW = (w - 4) / 2;
        for (String[] g : groups) {
            sectionHeaders.add(new SectionHeader(Lang.get(g[0]), ax, y, w));
            y += 12;
            for (int i = 1; i < g.length; i++) {
                int col = (i - 1) % 2, row = (i - 1) / 2;
                flagCells.add(new FlagCell(g[i], ax + col * (colW + 4), y + row * cellH, colW, cellH - 1));
            }
            y += ((g.length - 1 + 1) / 2) * cellH + 2;
        }
        String clearL = Lang.get("gui.kve.mob.behavior.clear_group");
        Button clearBtn = btn(clearL, rightBtnX(clearL), sectionHeaders.get(0).y() - 2, 0, () -> clearBehaviorGroup(groups));
        clearBtn.active = flagCount(groups) > 0;
        addRenderableWidget(clearBtn);
    }

    private void switchBehaviorSubTab(BehaviorSubTab sub) {
        if (activeBehaviorSubTab == sub) return;
        activeBehaviorSubTab = sub;
        refresh();
    }

    private void clearBehaviorGroup(String[][] groups) {
        for (String[] g : groups) for (int i = 1; i < g.length; i++) draftBehaviorFlags.remove(g[i]);
        refresh();
    }

    private void toggleFlag(String flag) {
        if (!draftBehaviorFlags.add(flag)) {
            draftBehaviorFlags.remove(flag);
        } else if (KubeJSMobExporter.EXCLUSIVE_ATTITUDE_FLAGS.contains(flag)) {
            for (String other : KubeJSMobExporter.EXCLUSIVE_ATTITUDE_FLAGS) if (!other.equals(flag)) draftBehaviorFlags.remove(other);
        }
        refresh();
    }

    private static String flagLabel(String flag) {
        return Lang.get("gui.kve.mob.behavior." + flag.toLowerCase(Locale.ROOT));
    }

    private static String flagDesc(String flag) {
        String key = "gui.kve.mob.behavior." + flag.toLowerCase(Locale.ROOT) + ".desc";
        String v = Lang.get(key);
        return key.equals(v) ? "" : v;
    }

    private void buildCombatFields(int ax, int y, int w) {
        combatTop = y;
        combatRowH = Math.max(22, Math.min(28, (T + GUI_H - 26 - 30 - y) / 4));
        int fieldW = 64, fx = contentRight() - fieldW;
        fXp    = combatField(fx, y + 1,                  fieldW, draftXpDrop >= 0 ? String.valueOf(draftXpDrop) : "", "xp");
        fRegen = combatField(fx, y + 1 + combatRowH,     fieldW, fmtOrEmpty(draftRegenPerSecond), "regen");
        fTaken = combatField(fx, y + 1 + combatRowH * 2, fieldW, fmtOrEmpty(draftDamageTakenMult), "taken");
        fDealt = combatField(fx, y + 1 + combatRowH * 3, fieldW, fmtOrEmpty(draftDamageDealtMult), "dealt");
        String clearL = Lang.get("gui.kve.mob.combat.clear");
        Button clearBtn = btn(clearL, rightBtnX(clearL), y + combatRowH * 4 + 2, 0, () -> {
            draftXpDrop = -1;
            draftRegenPerSecond = draftDamageTakenMult = draftDamageDealtMult = Double.NaN;
            refresh();
        });
        clearBtn.active = draftHasCombatTweaks();
        addRenderableWidget(clearBtn);
    }

    private String fmtOrEmpty(double v) { return Double.isNaN(v) ? "" : fmtShort(v); }

    private EditBox combatField(int x, int y, int w, String value, String key) {
        EditBox box = new KveScaledEditBox(font, x, y, w, 12, Component.literal(Lang.get("gui.kve.mob.combat." + key)));
        box.setMaxLength(10);
        box.setHint(Component.literal(Lang.get("gui.kve.mob.no_change")));
        box.setValue(value);
        box.setResponder(txt -> onCombatTyped(box, key, txt));
        addRenderableWidget(box);
        return box;
    }

    private void onCombatTyped(EditBox box, String key, String txt) {
        String t = txt == null ? "" : txt.trim().replace(',', '.');
        boolean ok = true;
        if (key.equals("xp")) {
            if (t.isEmpty()) draftXpDrop = -1;
            else {
                try { int v = Integer.parseInt(t); ok = v >= 0 && v <= 100000; if (ok) draftXpDrop = v; }
                catch (NumberFormatException ex) { ok = false; }
            }
        } else {
            double max = key.equals("regen") ? 1000 : 100;
            double v = t.isEmpty() ? Double.NaN : parseOrNaN(t);
            ok = t.isEmpty() || (!Double.isNaN(v) && v >= 0 && v <= max);
            if (ok) {
                switch (key) {
                    case "regen" -> draftRegenPerSecond = v;
                    case "taken" -> draftDamageTakenMult = v;
                    default -> draftDamageDealtMult = v;
                }
            }
        }
        box.setTextColor(ok ? 0xFFE0E0E0 : 0xFFFF6B6B);
    }

    private boolean draftHasCombatTweaks() {
        return draftXpDrop >= 0 || !Double.isNaN(draftRegenPerSecond) || !Double.isNaN(draftDamageTakenMult) || !Double.isNaN(draftDamageDealtMult);
    }

    private static double parseOrNaN(String s) {
        if (s == null || s.isBlank()) return Double.NaN;
        try { return Double.parseDouble(s.trim().replace(',', '.')); } catch (NumberFormatException ex) { return Double.NaN; }
    }

    private void enableBuildsMode() {
        commitEquipChanceDraft();
        KubeJSMobExporter.EquipmentBuild first = new KubeJSMobExporter.EquipmentBuild();
        first.name = Lang.get("gui.kve.mob.build.default_name");
        for (int i = 0; i < EQUIP_SLOTS.length; i++) {
            if (draftEquipItem[i] == null || draftEquipItem[i].isEmpty()) continue;
            first.equipment.add(new KubeJSMobExporter.EquipmentItem(EQUIP_SLOTS[i], draftEquipItem[i], draftEquipChance[i], draftEquipNbt[i], draftEquipSpawn[i]));
        }
        draftBuilds.add(first);
        buildsScroll = 0;
        refresh();
    }

    private void disableBuildsMode() {
        if (draftBuilds.size() > 1) {
            int lostCount = draftBuilds.size() - 1;
            minecraft.setScreen(new KveConfirmScreen(
                    confirmed -> {
                        if (confirmed) {
                            doDisableBuildsMode();
                        } else {
                            minecraft.setScreen(this);
                        }
                    },
                    Component.literal(Lang.get("gui.kve.mob.build.disable_confirm_title")),
                    Component.literal(Lang.get("gui.kve.mob.build.disable_confirm_msg", lostCount))));
            return;
        }
        doDisableBuildsMode();
    }

    private void doDisableBuildsMode() {
        for (int i = 0; i < EQUIP_SLOTS.length; i++) {
            draftEquipItem[i] = "";
            draftEquipChance[i] = 0.0;
            draftEquipSpawn[i] = 1.0;
            draftEquipNbt[i] = "";
        }
        if (!draftBuilds.isEmpty()) {
            for (KubeJSMobExporter.EquipmentItem eq : draftBuilds.get(0).equipment) {
                int i = KubeJSMobExporter.indexOfSlot(eq.slot);
                if (i < 0) continue;
                draftEquipItem[i] = eq.itemId;
                draftEquipChance[i] = eq.dropChance;
                draftEquipSpawn[i] = eq.spawnChance;
                draftEquipNbt[i] = eq.nbt;
            }
        }
        draftBuilds.clear();
        buildsScroll = 0;
        refresh();
    }

    private void openAddBuild() {
        minecraft.setScreen(new MobEquipmentBuildScreen(this, build -> {
            draftBuilds.add(build);
            minecraft.setScreen(this);
            refresh();
        }));
    }

    private void openEditBuild(int idx) {
        if (idx < 0 || idx >= draftBuilds.size()) return;
        KubeJSMobExporter.EquipmentBuild existing = draftBuilds.get(idx);
        MobEquipmentBuildScreen editScreen = new MobEquipmentBuildScreen(this, build -> {
            draftBuilds.set(idx, build);
            minecraft.setScreen(this);
            refresh();
        }).withExisting(existing);
        minecraft.setScreen(editScreen);
    }

    private void toggleBuild(int idx) {
        if (idx < 0 || idx >= draftBuilds.size()) return;
        KubeJSMobExporter.EquipmentBuild b = draftBuilds.get(idx);
        b.enabled = !b.enabled;
        refresh();
    }

    private void duplicateBuild(int idx) {
        if (idx < 0 || idx >= draftBuilds.size()) return;
        KubeJSMobExporter.EquipmentBuild c = draftBuilds.get(idx).copy();
        c.name = (c.name == null || c.name.isBlank() ? Lang.get("gui.kve.mob.build.unnamed") : c.name) + " (2)";
        draftBuilds.add(idx + 1, c);
        refresh();
    }

    private void removeBuild(int idx) {
        if (idx < 0 || idx >= draftBuilds.size()) return;
        draftBuilds.remove(idx);
        if (buildsScroll > 0 && buildsScroll >= draftBuilds.size()) buildsScroll--;
        refresh();
    }

    private void addBuildRowWidgets() {
        int maxScroll = Math.max(0, draftBuilds.size() - BUILDS_ROWS_VISIBLE);
        buildsScroll = Math.max(0, Math.min(maxScroll, buildsScroll));
        int editW = KveGuiStyle.btnWidth(Lang.get("gui.kve.btn.edit"));
        int rowRight = (L + GUI_W - RENDER_W) - 4;
        int xX = rowRight - 14;
        int editX = xX - KveGuiStyle.BTN_GAP - editW;
        int dupX = editX - KveGuiStyle.BTN_GAP - 14;
        int toggleX = dupX - KveGuiStyle.BTN_GAP - 14;
        for (int i = 0; i < BUILDS_ROWS_VISIBLE; i++) {
            int idx = i + buildsScroll;
            if (idx >= draftBuilds.size()) break;
            int ry = buildsListTop + i * BUILDS_ROW_H;
            int finalIdx = idx;
            boolean on = draftBuilds.get(idx).enabled;
            Button toggle = btn(on ? "\u2714" : "\u2716", toggleX, ry, 14, () -> toggleBuild(finalIdx));
            toggle.setTooltip(Tooltip.create(Component.literal(Lang.get(on ? "gui.kve.mob.build.toggle_off" : "gui.kve.mob.build.toggle_on"))));
            buildRowWidgets.add(addRenderableWidget(toggle));
            Button dup = btn("D", dupX, ry, 14, () -> duplicateBuild(finalIdx));
            dup.setTooltip(Tooltip.create(Component.literal(Lang.get("gui.kve.mob.build.duplicate"))));
            buildRowWidgets.add(addRenderableWidget(dup));
            buildRowWidgets.add(addRenderableWidget(btn(Lang.get("gui.kve.btn.edit"), editX, ry, 0, () -> openEditBuild(finalIdx))));
            buildRowWidgets.add(addRenderableWidget(btn("X", xX, ry, 14, () -> removeBuild(finalIdx))));
        }
    }

    private void rebuildBuildRows() {
        for (Button w : buildRowWidgets) removeWidget(w);
        buildRowWidgets.clear();
        addBuildRowWidgets();
    }

    private void openEquipPicker(int idx) {
        minecraft.setScreen(new ItemPickerScreen(this, stack -> {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            draftEquipItem[idx] = id != null ? id.toString() : "";
            draftEquipNbt[idx]  = com.kve.common.util.ItemExtraDataCodec.encodeOrEmpty(stack);
            if (draftEquipChance[idx] <= 0 && (fEquipChance[idx] == null || fEquipChance[idx].getValue().trim().isEmpty()))
                draftEquipChance[idx] = 0.085;
            if (fEquipSpawn[idx] == null || fEquipSpawn[idx].getValue().trim().isEmpty())
                draftEquipSpawn[idx] = 1.0;
            minecraft.setScreen(this);
            refresh();
        }, true));
    }

    private void openEquipEnchant(int idx) {
        if (draftEquipItem[idx] == null || draftEquipItem[idx].isEmpty()) return;
        net.minecraft.world.item.ItemStack stack = stackFor(draftEquipItem[idx], draftEquipNbt[idx]);
        minecraft.setScreen(new NbtEditorScreen(this, stack, result -> {
            draftEquipNbt[idx] = com.kve.common.util.ItemExtraDataCodec.encodeOrEmpty(result);
            minecraft.setScreen(this);
            refresh();
        }));
    }

    private net.minecraft.world.item.ItemStack stackFor(String itemId, String nbt) {
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        net.minecraft.world.item.Item item = rl != null ? BuiltInRegistries.ITEM.get(rl) : net.minecraft.world.item.Items.AIR;
        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item);
        if (nbt != null && !nbt.isBlank()) {
            com.kve.common.util.ItemExtraDataCodec.applyFromSnbt(stack, nbt);
        }
        return stack;
    }

    private void clearEquip(int idx) {
        draftEquipItem[idx] = "";
        draftEquipChance[idx] = 0.0;
        draftEquipSpawn[idx] = 1.0;
        draftEquipNbt[idx] = "";
        if (fEquipChance[idx] != null) fEquipChance[idx].setValue("");
        if (fEquipSpawn[idx] != null) fEquipSpawn[idx].setValue("");
        refresh();
    }

    private Button btn(String label, int x, int y, int w, Runnable action) {
        return KveGuiStyle.smartBtn(x, y, w, label, b -> action.run());
    }

    private int actionBtnW(String langKey) {
        return Math.max(24, font.width(Lang.get(langKey)) + 10);
    }

    private int placeCompactActionBtn(int x, int y, String langKey, Runnable action) {
        int w = actionBtnW(langKey + "_short");
        Button b = btn(Lang.get(langKey + "_short"), x, y, 0, action);
        b.setTooltip(Tooltip.create(Component.literal(Lang.get(langKey))));
        addRenderableWidget(b);
        return w;
    }

    private boolean mobListBuilt = false;

    private void buildMobList() {
        if (mobListBuilt) return;
        mobListBuilt = true;
        allMobs.clear();
        List<EntityType<?>> vanilla = new ArrayList<>(), modded = new ArrayList<>();
        for (EntityType<?> et : RegistryIndex.editableMobTypes()) {
            (RegistryIndex.id(et).startsWith("minecraft:") ? vanilla : modded).add(et);
        }
        vanilla.sort(Comparator.comparing((EntityType<?> e) -> RegistryIndex.name(e)));
        modded.sort(Comparator.comparing((EntityType<?> e) -> RegistryIndex.name(e)));
        allMobs.addAll(vanilla);
        allMobs.addAll(modded);
    }

    private boolean isEditableMob(EntityType<?> et) {
        return MobEditability.isEditableMob(minecraft.level, et);
    }

    private String filteredForQuery = null;
    private int filteredForFavEpoch = -1;

    private void rebuildFilter() {
        if (searchFilter.equals(filteredForQuery)
                && filteredForFavEpoch == CraftEditorState.favoritesEpoch()) {
            if (listScrollBar != null) listScrollBar.setTotals(filtered.size(), ROWS);
            return;
        }
        filteredForQuery = searchFilter;
        filteredForFavEpoch = CraftEditorState.favoritesEpoch();

        filtered.clear();
        SearchFilter.Query q = SearchFilter.compile(searchFilter);
        for (EntityType<?> et : allMobs) {
            if (q.matches(RegistryIndex.id(et), RegistryIndex.name(et)))
                filtered.add(et);
        }
        filtered.sort((a, b) -> {
            boolean fa = isFavoriteMobType(a), fb = isFavoriteMobType(b);
            if (fa != fb) return fa ? -1 : 1;
            return 0;
        });
        if (listScrollBar != null) listScrollBar.setTotals(filtered.size(), ROWS);
    }

    private boolean isFavoriteMobType(EntityType<?> et) {
        String id = RegistryIndex.id(et);
        return !id.isEmpty() && CraftEditorState.isFavoriteMob(id);
    }

    private void toggleFavoriteCurrentMob() {
        if (currentMobId.isEmpty()) return;
        CraftEditorState.toggleFavoriteMob(currentMobId);
        flash(CraftEditorState.isFavoriteMob(currentMobId)
                ? Lang.get("gui.kve.mob.favorite_add")
                : Lang.get("gui.kve.mob.favorite_remove"));
        refresh();
    }

    private void openIdUsageForCurrentMob() {
        if (currentMobId.isEmpty()) return;
        minecraft.setScreen(new IdUsageScreen(this, currentMobId));
    }

    private void duplicateMob() {
        if (readOnlyMode) { flashWarn(Lang.get("gui.kve.mob.readonly")); return; }
        if (!ClientPermissionState.canEdit()) { flashWarn(Lang.get("gui.kve.perm.denied")); return; }
        if (selectedType == null) { flashWarn(Lang.get("gui.kve.mob.select_first")); return; }
        minecraft.setScreen(new MobPickerScreen(this, type -> {
            selectedType  = type;
            previewEntity = tryCreatePreview(type);
            entityScale   = calcScale(type);
            externalSourceNote = "";
            selectedEquipSlot = EQ_MAINHAND;
            computeVanillaBaseline();
            ResourceLocation k = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            String id = k != null ? k.toString() : "";
            currentMobId = id;
            minecraft.setScreen(this);
            flash(KubeJSMobExporter.getEntry(id) != null
                    ? Lang.get("gui.kve.mob.duplicate_target_conflict")
                    : Lang.get("gui.kve.mob.duplicate_hint"));
            refresh();
        }));
    }

    private void saveAsTemplate() {
        if (readOnlyMode) { flashWarn(Lang.get("gui.kve.mob.readonly")); return; }
        if (!ClientPermissionState.canEdit()) { flashWarn(Lang.get("gui.kve.perm.denied")); return; }
        KubeJSMobExporter.MobEntry draft = buildDraftMobEntry();
        if (draft == null) { flashWarn(Lang.get("gui.kve.mob.select_first")); return; }
        minecraft.setScreen(new MobTemplateNameScreen(this, name -> {
            MobTemplateStore.saveOrReplace(name, draft);
            minecraft.setScreen(this);
            flash(Lang.get("gui.kve.mob.template.saved"));
            refresh();
        }));
    }

    /**
     * Aplica una plantilla guardada al draft actual - reusa
     * {@link #loadEntry(KubeJSMobExporter.MobEntry)} tal cual (mismo metodo
     * que ya aplica un MobEntry existente al elegir un mob con entrada
     * previa), que nunca toca entityId/selectedType, asi que sirve igual de
     * bien sobre el mismo mob que la guardo o sobre uno distinto.
     */
    private void openTemplatePicker() {
        if (readOnlyMode) { flashWarn(Lang.get("gui.kve.mob.readonly")); return; }
        if (!ClientPermissionState.canEdit()) { flashWarn(Lang.get("gui.kve.perm.denied")); return; }
        if (selectedType == null) { flashWarn(Lang.get("gui.kve.mob.select_first")); return; }
        minecraft.setScreen(new MobTemplateListScreen(this, tpl -> {
            boolean keepEnabled = draftEnabled;
            String keepCategory = draftCategory;
            loadEntry(tpl.data);
            draftEnabled = keepEnabled;
            draftCategory = keepCategory;
            flash(Lang.get("gui.kve.mob.template.applied"));
            refresh();
        }));
    }

    private void selectMob(EntityType<?> et) {
        selectedType  = et;
        previewEntity = tryCreatePreview(et);
        entityScale   = calcScale(et);
        externalSourceNote = "";
        selectedEquipSlot = EQ_MAINHAND;
        computeVanillaBaseline();

        ResourceLocation k = BuiltInRegistries.ENTITY_TYPE.getKey(et);
        String id = k != null ? k.toString() : "";
        currentMobId = id;
        var entry = KubeJSMobExporter.getEntry(id);
        if (entry != null) {
            loadEntry(entry);
        } else {
            clearFields();
            draftEnabled = true;
            draftCategory = "";
            if (previewEntity != null) loadFromVanilla();
            overlayExternalMobStats(id);
        }
        refresh();
        markClean();
    }

    private boolean overlayExternalMobStats(String mobId) {
        ExternalScriptScanner.ExternalMobStats ext = ExternalScriptScanner.getMobStats(mobId);
        if (ext == null) return false;

        if (!Double.isNaN(ext.health))          setF(fHealth,     ext.health);
        if (!Double.isNaN(ext.armor))           setF(fArmor,      ext.armor);
        if (!Double.isNaN(ext.armorToughness))  setF(fArmorTough, ext.armorToughness);
        if (!Double.isNaN(ext.attackDamage))    setF(fAtkDmg,     ext.attackDamage);
        if (!Double.isNaN(ext.attackSpeed))     setF(fAtkSpd,     ext.attackSpeed);
        if (!Double.isNaN(ext.movementSpeed))   setF(fMoveSpd,    ext.movementSpeed);
        if (!Double.isNaN(ext.knockbackResist)) setF(fKnockRes,   ext.knockbackResist);
        if (!Double.isNaN(ext.followRange))     setF(fFollowRange,ext.followRange);

        if (ext.customNameSet && fCustomName != null) {
            fCustomName.setValue(ext.customName);
            draftNameVisible = ext.customNameVisible;
        }

        draftMods.clear();
        draftMods.addAll(ext.attributeMods);
        modsScroll = 0;

        externalSourceNote = ext.sourceFile;
        return true;
    }

    private void loadEntry(KubeJSMobExporter.MobEntry e) {
        setF(fHealth,     !Double.isNaN(e.health)          ? e.health          : vHealth);
        setF(fArmor,      !Double.isNaN(e.armor)           ? e.armor           : vArmor);
        setF(fArmorTough, !Double.isNaN(e.armorToughness)  ? e.armorToughness  : vArmorTough);
        setF(fAtkDmg,     !Double.isNaN(e.attackDamage)    ? e.attackDamage    : vAtkDmg);
        setF(fAtkSpd,     !Double.isNaN(e.attackSpeed)     ? e.attackSpeed     : vAtkSpd);
        setF(fMoveSpd,    !Double.isNaN(e.movementSpeed)   ? e.movementSpeed   : vMoveSpd);
        setF(fKnockRes,   !Double.isNaN(e.knockbackResist) ? e.knockbackResist : vKnockRes);
        setF(fFollowRange,!Double.isNaN(e.followRange)     ? e.followRange     : vFollowRange);

        if (fCustomName != null) fCustomName.setValue(e.customNameSet ? e.customName : "");
        draftNameVisible = e.customNameVisible;
        draftEnabled     = e.enabled;
        draftCategory    = e.category != null ? e.category : "";

        draftMods.clear();
        for (KubeJSMobExporter.AttrMod m : e.attributeMods)
            draftMods.add(new KubeJSMobExporter.AttrMod(m.attrId, m.value, m.dimension, m.probability));
        modsScroll = 0;

        for (int i = 0; i < EQUIP_SLOTS.length; i++) {
            draftEquipItem[i] = "";
            draftEquipChance[i] = 0.0;
            draftEquipSpawn[i] = 1.0;
            draftEquipNbt[i] = "";
            if (fEquipChance[i] != null) fEquipChance[i].setValue("");
            if (fEquipSpawn[i] != null) fEquipSpawn[i].setValue("");
        }
        for (KubeJSMobExporter.EquipmentItem eq : e.equipment) {
            int i = KubeJSMobExporter.indexOfSlot(eq.slot);
            if (i < 0) continue;
            draftEquipItem[i] = eq.itemId;
            draftEquipChance[i] = eq.dropChance;
            draftEquipSpawn[i] = eq.spawnChance;
            draftEquipNbt[i] = eq.nbt;
            if (fEquipChance[i] != null) fEquipChance[i].setValue(fmtShort(eq.dropChance * 100));
            if (fEquipSpawn[i] != null) fEquipSpawn[i].setValue(fmtShort(eq.spawnChance));
        }

        draftBuilds.clear();
        for (KubeJSMobExporter.EquipmentBuild b : e.equipmentBuilds) draftBuilds.add(b.copy());
        buildsScroll = 0;

        draftAttackEffects.clear();
        for (KubeJSItemAttrExporter.FoodEffect fx : e.attackEffects) draftAttackEffects.add(fx.copy());
        draftMobEffects.clear();
        for (KubeJSItemAttrExporter.FoodEffect fx : e.mobEffects) draftMobEffects.add(fx.copy());
        effectsScroll = 0;
        activeEffectsSubTab = EffectsSubTab.ATTACK;

        draftAllies.clear();
        draftAllies.addAll(e.allies);
        draftEnemies.clear();
        draftEnemies.addAll(e.enemies);
        relScroll = 0;

        draftBehaviorFlags.clear();
        draftBehaviorFlags.addAll(e.behaviorFlags);
        draftXpDrop = e.xpDrop; draftRegenPerSecond = e.regenPerSecond;
        draftDamageTakenMult = e.damageTakenMult; draftDamageDealtMult = e.damageDealtMult;

        draftScriptOverride = e.scriptOverride != null ? e.scriptOverride : "";
    }

    private void loadFromVanilla() {
        if (previewEntity == null) return;
        AttributeMap attrs = previewEntity.getAttributes();
        setFIfHas(fHealth,     attrs, Attributes.MAX_HEALTH);
        setFIfHas(fArmor,      attrs, Attributes.ARMOR);
        setFIfHas(fArmorTough, attrs, Attributes.ARMOR_TOUGHNESS);
        setFIfHas(fAtkDmg,     attrs, Attributes.ATTACK_DAMAGE);
        setFIfHas(fAtkSpd,     attrs, Attributes.ATTACK_SPEED);
        setFIfHas(fMoveSpd,    attrs, Attributes.MOVEMENT_SPEED);
        setFIfHas(fKnockRes,   attrs, Attributes.KNOCKBACK_RESISTANCE);
        setFIfHas(fFollowRange,attrs, Attributes.FOLLOW_RANGE);
    }

    private void computeVanillaBaseline() {
        if (previewEntity == null) {
            vHealth = vArmor = vArmorTough = vAtkDmg = vAtkSpd = vMoveSpd = vKnockRes = vFollowRange = Double.NaN;
            return;
        }
        AttributeMap attrs = previewEntity.getAttributes();
        vHealth      = baselineIfHas(attrs, Attributes.MAX_HEALTH);
        vArmor       = baselineIfHas(attrs, Attributes.ARMOR);
        vArmorTough  = baselineIfHas(attrs, Attributes.ARMOR_TOUGHNESS);
        vAtkDmg      = baselineIfHas(attrs, Attributes.ATTACK_DAMAGE);
        vAtkSpd      = baselineIfHas(attrs, Attributes.ATTACK_SPEED);
        vMoveSpd     = baselineIfHas(attrs, Attributes.MOVEMENT_SPEED);
        vKnockRes    = baselineIfHas(attrs, Attributes.KNOCKBACK_RESISTANCE);
        vFollowRange = baselineIfHas(attrs, Attributes.FOLLOW_RANGE);
    }

    private double baselineIfHas(AttributeMap m, net.minecraft.core.Holder<Attribute> a) {
        if (!m.hasAttribute(a)) return Double.NaN;
        try { return Double.parseDouble(fmt(m.getValue(a))); } catch (Exception ex) { return Double.NaN; }
    }

    private static boolean eqD(double a, double b) { return (Double.isNaN(a) && Double.isNaN(b)) || a == b; }

    private void populateFields() {
        ResourceLocation k = BuiltInRegistries.ENTITY_TYPE.getKey(selectedType);
        String id = k != null ? k.toString() : "";
        var e = KubeJSMobExporter.getEntry(id);
        if (e != null) {
            loadEntry(e);
        } else {
            if (previewEntity != null) loadFromVanilla();
            overlayExternalMobStats(id);
        }
    }

    private void setF(EditBox box, double v)              { if (box != null) box.setValue(Double.isNaN(v) ? "" : fmt(v)); }
    private void setFIfHas(EditBox box, AttributeMap m, net.minecraft.core.Holder<Attribute> a) {
        if (box != null && m.hasAttribute(a)) box.setValue(fmt(m.getValue(a)));
    }
    private String fmtShort(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return new java.math.BigDecimal(String.format(java.util.Locale.ROOT, "%.4f", v)).stripTrailingZeros().toPlainString();
    }

    private String fmt(double v) { return (v == Math.floor(v)) ? String.valueOf((long) v) : String.format(java.util.Locale.ROOT, "%.4f", v); }

    private void clearFields() {
        for (EditBox b : new EditBox[]{fHealth,fArmor,fArmorTough,fAtkDmg,fAtkSpd,fMoveSpd,fKnockRes,fFollowRange})
            if (b != null) b.setValue("");
        if (fCustomName != null) fCustomName.setValue("");
        draftNameVisible = false;
        draftEnabled     = true;
        draftCategory    = "";
        draftMods.clear();
        modsScroll = 0;

        for (int i = 0; i < EQUIP_SLOTS.length; i++) {
            draftEquipItem[i] = "";
            draftEquipChance[i] = 0.0;
            draftEquipSpawn[i] = 1.0;
            draftEquipNbt[i] = "";
            if (fEquipChance[i] != null) fEquipChance[i].setValue("");
            if (fEquipSpawn[i] != null) fEquipSpawn[i].setValue("");
        }
        draftBuilds.clear();
        buildsScroll = 0;
        draftAttackEffects.clear();
        draftMobEffects.clear();
        effectsScroll = 0;
        activeEffectsSubTab = EffectsSubTab.ATTACK;
        draftAllies.clear();
        draftEnemies.clear();
        relScroll = 0;
        draftBehaviorFlags.clear();
        draftXpDrop = -1; draftRegenPerSecond = Double.NaN; draftDamageTakenMult = Double.NaN; draftDamageDealtMult = Double.NaN;
        draftScriptOverride = "";
    }

    private KubeJSMobExporter.MobEntry buildDraftMobEntry() {
        if (selectedType == null) return null;
        ResourceLocation k = BuiltInRegistries.ENTITY_TYPE.getKey(selectedType);
        if (k == null) return null;

        KubeJSMobExporter.MobEntry e = new KubeJSMobExporter.MobEntry();
        e.entityId        = k.toString();
        e.enabled          = draftEnabled;
        e.category         = draftCategory;
        double dHealth = parseD(fHealth), dArmor = parseD(fArmor), dArmorTough = parseD(fArmorTough),
               dAtkDmg = parseD(fAtkDmg), dAtkSpd = parseD(fAtkSpd), dMoveSpd = parseD(fMoveSpd),
               dKnockRes = parseD(fKnockRes), dFollowRange = parseD(fFollowRange);
        e.health          = eqD(dHealth, vHealth)          ? Double.NaN : dHealth;
        e.armor           = eqD(dArmor, vArmor)            ? Double.NaN : dArmor;
        e.armorToughness  = eqD(dArmorTough, vArmorTough)  ? Double.NaN : dArmorTough;
        e.attackDamage    = eqD(dAtkDmg, vAtkDmg)          ? Double.NaN : dAtkDmg;
        e.attackSpeed     = eqD(dAtkSpd, vAtkSpd)          ? Double.NaN : dAtkSpd;
        e.movementSpeed   = eqD(dMoveSpd, vMoveSpd)        ? Double.NaN : dMoveSpd;
        e.knockbackResist = eqD(dKnockRes, vKnockRes)      ? Double.NaN : dKnockRes;
        e.followRange     = eqD(dFollowRange, vFollowRange)? Double.NaN : dFollowRange;

        String typedName = fCustomName != null ? fCustomName.getValue() : "";
        e.customName        = typedName;

        e.customNameSet     = !typedName.isEmpty() || draftNameVisible;
        e.customNameVisible = draftNameVisible;

        e.attributeMods.clear();
        for (KubeJSMobExporter.AttrMod m : draftMods)
            e.attributeMods.add(new KubeJSMobExporter.AttrMod(m.attrId, m.value, m.dimension, m.probability));

        e.equipment.clear();
        if (draftBuilds.isEmpty()) {
            for (int i = 0; i < EQUIP_SLOTS.length; i++) {
                if (draftEquipItem[i] == null || draftEquipItem[i].isEmpty()) continue;
                double chancePct = fEquipChance[i] != null ? parseD(fEquipChance[i]) : -1;
                double chance = chancePct >= 0 ? Math.max(0, Math.min(100, chancePct)) / 100.0 : draftEquipChance[i];
                double spawnP = fEquipSpawn[i] != null ? KubeJSMobExporter.parseProbability(fEquipSpawn[i].getValue()) : Double.NaN;
                if (Double.isNaN(spawnP)) spawnP = draftEquipSpawn[i];
                e.equipment.add(new KubeJSMobExporter.EquipmentItem(EQUIP_SLOTS[i], draftEquipItem[i], chance, draftEquipNbt[i], spawnP));
            }
        }

        e.equipmentBuilds.clear();
        for (KubeJSMobExporter.EquipmentBuild b : draftBuilds) e.equipmentBuilds.add(b.copy());

        e.attackEffects.clear();
        for (KubeJSItemAttrExporter.FoodEffect fx : draftAttackEffects) e.attackEffects.add(fx.copy());
        e.mobEffects.clear();
        for (KubeJSItemAttrExporter.FoodEffect fx : draftMobEffects) e.mobEffects.add(fx.copy());

        e.allies.clear();
        e.allies.addAll(draftAllies);

        e.enemies.clear();
        e.enemies.addAll(draftEnemies);

        e.behaviorFlags.clear();
        e.behaviorFlags.addAll(draftBehaviorFlags);
        e.xpDrop = draftXpDrop; e.regenPerSecond = draftRegenPerSecond;
        e.damageTakenMult = draftDamageTakenMult; e.damageDealtMult = draftDamageDealtMult;

        e.scriptOverride = draftScriptOverride;
        return e;
    }

    private void saveMob() {
        if (readOnlyMode) { flashWarn(Lang.get("gui.kve.mob.readonly")); return; }
        if (!ClientPermissionState.canEdit()) { flashWarn(Lang.get("gui.kve.perm.denied")); return; }
        if (selectedType == null) { flashWarn(Lang.get("gui.kve.mob.select_first")); return; }

        KubeJSMobExporter.MobEntry e = buildDraftMobEntry();
        if (e == null) { flashWarn(Lang.get("gui.kve.mob.select_first")); return; }

        boolean conflict = ExternalScriptScanner.getMobStats(e.entityId) != null;

        KubeJSMobExporter.MobEntry previous = KubeJSMobExporter.getEntry(e.entityId);
        PendingChange.Action action = previous == null ? PendingChange.Action.CREATE : PendingChange.Action.EDIT;
        KubeJSMobExporter.stageMob(e);
        PendingChangeManager.queue(PendingChange.of(PendingDomain.CONTENT, e.entityId, action, false,
                () -> {
                    KubeJSMobExporter.commitMobs();
                    KveChangeLog.record(KveChangeLog.Log.MOBS, action, e.entityId);
                },
                () -> {
                    KubeJSMobExporter.discardStagedMob(e.entityId, previous);
                    refreshSaved();
                })
                .withDiff(previous == null ? null : () -> KubeJSMobExporter.previewMobJS(previous),
                        () -> KubeJSMobExporter.previewMobJS(e)));

        currentMobId = e.entityId;
        flash(conflict
                ? Lang.get("gui.kve.mob.conflict")
                : Lang.get("gui.kve.pending.queued_hint_reload"));
        refreshSaved();
        refresh();
        markClean();
    }

    private void onApplyPending() {
        if (!ClientPermissionState.canApply()) { flashWarn(Lang.get("gui.kve.perm.apply_denied")); return; }
        if (PendingChangeManager.isEmpty()) return;
        if (PendingChangeManager.domainHasDiff(PendingDomain.CONTENT)) {
            minecraft.setScreen(new DiffPreviewScreen(this,
                    PendingChangeManager.entriesForDomain(PendingDomain.CONTENT),
                    this::proceedApply));
            return;
        }
        proceedApply();
    }

    private void proceedApply() {
        java.util.Set<PendingDomain> domains = PendingChangeManager.domainsPresent();
        if (domains.size() > 1) {
            int otherCount = PendingChangeManager.size() - PendingChangeManager.sizeForDomain(PendingDomain.CONTENT);
            minecraft.setScreen(new KveConfirmScreen(
                    confirmed -> {
                        minecraft.setScreen(this);
                        applyPendingResult(confirmed ? PendingChangeManager.applyAll() : PendingChangeManager.applyDomain(PendingDomain.CONTENT));
                    },
                    Component.literal(Lang.get("gui.kve.pending.other_domains_title")),
                    Component.literal(Lang.get("gui.kve.pending.other_domains_msg", otherCount))));
            return;
        }
        applyPendingResult(PendingChangeManager.applyDomain(PendingDomain.CONTENT));
    }

    private void applyPendingResult(PendingChangeManager.ApplyResult result) {
                com.kve.client.gui.KveToast.applied(result, com.kve.common.kubejs.KubeJSMobExporter.getLastWriteWarnings());
        refreshSaved();
        refresh();
    }

    private void onDiscardPending() {
        if (PendingChangeManager.isEmpty()) return;
        int count = PendingChangeManager.sizeForDomain(PendingDomain.CONTENT);
        if (count == 0) return;
        PendingChangeManager.discardDomain(PendingDomain.CONTENT);
        flash(Lang.get("gui.kve.pending.discarded", count));
        refreshSaved();
        if (selectedType != null) {
            String msg = flashMsg;
            selectMob(selectedType);
            flash(msg);
            return;
        }
        refresh();
    }

    private void previewMob() {
        KubeJSMobExporter.MobEntry e = buildDraftMobEntry();
        if (e == null) { flashWarn(Lang.get("gui.kve.mob.select_first")); return; }
        String generated = KubeJSMobExporter.previewGeneratedMobBody(e);
        String current = (draftScriptOverride != null && !draftScriptOverride.isBlank())
                ? draftScriptOverride : generated;
        minecraft.setScreen(new RecipePreviewScreen(this, e.entityId, current, generated,
                text -> {
                    draftScriptOverride = text;
                    flash(Lang.get("gui.kve.mob.script_override.saved_hint"));
                }));
    }

    private void undoMob() {
        if (readOnlyMode) { flashWarn(Lang.get("gui.kve.mob.readonly")); return; }
        if (!ClientPermissionState.canEdit()) { flashWarn(Lang.get("gui.kve.perm.denied")); return; }
        if (currentMobId.isEmpty()) return;
        String id = currentMobId;
        KubeJSMobExporter.MobEntry beforeUndo = KubeJSMobExporter.getEntry(id);
        KubeJSMobExporter.MobEntry restored = KubeJSMobExporter.stageUndo(id);
        if (restored != null) {
            loadEntry(restored);
            PendingChangeManager.queue(PendingChange.of(PendingDomain.CONTENT, id, PendingChange.Action.EDIT, false,
                    () -> {
                        KubeJSMobExporter.commitMobs();
                        KveChangeLog.record(KveChangeLog.Log.MOBS, PendingChange.Action.EDIT, id);
                    },
                    () -> {
                        KubeJSMobExporter.discardStagedUndo(id, restored, beforeUndo);
                        refreshSaved();
                    })
                    .withDiff(() -> KubeJSMobExporter.previewMobJS(beforeUndo == null ? restored : beforeUndo),
                            () -> KubeJSMobExporter.previewMobJS(restored)));
            flash(Lang.get("gui.kve.pending.queued_hint_reload"));
            refreshSaved();
        }
        refresh();
        markClean();
    }

    private void deleteMob() {
        if (readOnlyMode) { flashWarn(Lang.get("gui.kve.mob.readonly")); return; }
        if (!ClientPermissionState.canEdit()) { flashWarn(Lang.get("gui.kve.perm.denied")); return; }

        String confirmId = null;
        if (selectedType != null) {
            ResourceLocation k = BuiltInRegistries.ENTITY_TYPE.getKey(selectedType);
            if (k != null) confirmId = k.toString();
        }
        if (confirmId == null && orphanedSelectedId != null) confirmId = orphanedSelectedId;
        if (confirmId == null) { flashWarn(Lang.get("gui.kve.mob.select_first")); return; }

        minecraft.setScreen(new KveConfirmScreen(
                confirmed -> {
                    minecraft.setScreen(this);
                    if (confirmed) doDeleteMob();
                },
                Component.literal(Lang.get("gui.kve.mob.delete_confirm_title")),
                Component.literal(Lang.get("gui.kve.mob.delete_confirm_msg", confirmId))));
    }

    private void doDeleteMob() {
        if (selectedType != null) {
            ResourceLocation k = BuiltInRegistries.ENTITY_TYPE.getKey(selectedType);
            if (k != null) {
                String id = k.toString();
                KubeJSMobExporter.MobEntry previous = KubeJSMobExporter.getEntry(id);
                KubeJSMobExporter.stageDeleteMob(id);
                PendingChangeManager.queue(PendingChange.of(PendingDomain.CONTENT, id, PendingChange.Action.DELETE, false,
                        () -> {
                            KubeJSMobExporter.commitMobs();
                            KveChangeLog.record(KveChangeLog.Log.MOBS, PendingChange.Action.DELETE, id);
                        },
                        () -> {
                            if (previous != null) KubeJSMobExporter.stageMob(previous);
                            refreshSaved();
                        })
                        .withDiff(previous == null ? null : () -> KubeJSMobExporter.previewMobJS(previous), null));
                clearFields();
                if (previewEntity != null) loadFromVanilla();
                flash(Lang.get("gui.kve.pending.queued_hint_reload"));
                refreshSaved();
                refresh();
                markClean();
                return;
            }
        }

        if (orphanedSelectedId != null) {
            String id = orphanedSelectedId;
            KubeJSMobExporter.MobEntry previous = KubeJSMobExporter.getEntry(id);
            KubeJSMobExporter.stageDeleteMob(id);
            PendingChangeManager.queue(PendingChange.of(PendingDomain.CONTENT, id, PendingChange.Action.DELETE, false,
                    () -> {
                        KubeJSMobExporter.commitMobs();
                        KveChangeLog.record(KveChangeLog.Log.MOBS, PendingChange.Action.DELETE, id);
                    },
                    () -> {
                        if (previous != null) KubeJSMobExporter.stageMob(previous);
                        refreshSaved();
                    })
                    .withDiff(previous == null ? null : () -> KubeJSMobExporter.previewMobJS(previous), null));
            orphanedSelectedId = null;
            clearFields();
            flash(Lang.get("gui.kve.pending.queued_hint_reload"));
            refreshSaved();
            refresh();
            return;
        }
        flashWarn(Lang.get("gui.kve.mob.select_first"));
    }

    private void resetFields() {
        if (readOnlyMode) { flashWarn(Lang.get("gui.kve.mob.readonly")); return; }
        if (!ClientPermissionState.canEdit()) { flashWarn(Lang.get("gui.kve.perm.denied")); return; }
        computeVanillaBaseline();
        clearFields();
        draftEnabled = true;
        draftCategory = "";
        if (previewEntity != null) loadFromVanilla();
        overlayExternalMobStats(currentMobId);
        refresh();
    }

    private void doOpenChangeHistory() {
        minecraft.setScreen(new KveChangeHistoryScreen(this, KveChangeLog.Log.MOBS,
                Lang.c("gui.kve.change_history.title", Lang.get("gui.kve.change_history.domain.mobs")),
                id -> {
                    ResourceLocation rl = ResourceLocation.tryParse(id);
                    EntityType<?> found = rl != null ? BuiltInRegistries.ENTITY_TYPE.getOptional(rl).orElse(null) : null;
                    if (found != null) selectMob(found);
                    minecraft.setScreen(this);
                    refresh();
                }));
    }

    private void goBack() {
        if (KveStudioShell.exitIfHosted(this, returnScreen)) return;
        minecraft.setScreen(returnScreen != null ? returnScreen : new KveStudioShell());
    }

    private void openLootTable() {
        if (currentMobId.isEmpty()) return;
        if (hosted && returnScreen instanceof KveStudioShell shell
                && shell.jumpToTab("gui.kve.studio.tab.loot", currentMobId)) {
            return;
        }
        minecraft.setScreen(new LootTableEditorScreen().withInitialTarget(currentMobId).withReturnScreen(this));
    }

    /** ESC acts as "back to main menu" here instead of closing the whole GUI. */
    @Override
    public void onClose() { goBack(); }

    private void toggleNameVisible() {
        draftNameVisible = !draftNameVisible;
        refresh();
    }

    private void toggleEnabled() {
        if (readOnlyMode) { flashWarn(Lang.get("gui.kve.mob.readonly")); return; }
        if (!ClientPermissionState.canEdit()) { flashWarn(Lang.get("gui.kve.perm.denied")); return; }
        draftEnabled = !draftEnabled;
        String id = currentMobId;
        if (!id.isEmpty() && KubeJSMobExporter.getEntry(id) != null) {
            boolean previousEnabled = !draftEnabled;
            if (KubeJSMobExporter.stageSetEnabled(id, draftEnabled)) {
                PendingChangeManager.queue(PendingChange.of(PendingDomain.CONTENT, id, PendingChange.Action.EDIT, false,
                        () -> {
                            KubeJSMobExporter.commitMobs();
                            KveChangeLog.record(KveChangeLog.Log.MOBS, PendingChange.Action.EDIT, id);
                        },
                        () -> {
                            KubeJSMobExporter.discardStagedSetEnabled(id, previousEnabled);
                            refreshSaved();
                        }));
                flash(Lang.get("gui.kve.pending.queued_hint_reload"));
                refreshSaved();
                refresh();
                markClean();
                return;
            }
        }
        refresh();
    }

    private void toggleReadOnly() {
        readOnlyMode = !readOnlyMode;
        refresh();
    }

    private void openAddModification() {
        minecraft.setScreen(new MobAttributePickerScreen(this, attrId -> {
            MobModValueScreen valueScreen = new MobModValueScreen(this, attrId, friendlyAttrName(attrId), mod -> {
                draftMods.add(mod);
                minecraft.setScreen(this);
                refresh();
            });
            withPreviewSupportInfo(valueScreen, attrId);
            minecraft.setScreen(valueScreen);
        }));
    }

    private void openEditModification(int idx) {
        if (idx < 0 || idx >= draftMods.size()) return;
        KubeJSMobExporter.AttrMod existing = draftMods.get(idx);
        MobModValueScreen editScreen = new MobModValueScreen(this, existing.attrId, friendlyAttrName(existing.attrId), mod -> {
            draftMods.set(idx, mod);
            minecraft.setScreen(this);
            refresh();
        }).withExisting(existing);
        withPreviewSupportInfo(editScreen, existing.attrId);
        minecraft.setScreen(editScreen);
    }

    private void withPreviewSupportInfo(MobModValueScreen screen, String attrId) {
        if (previewEntity == null || attrId == null || attrId.isEmpty()) return;
        try {
            net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(attrId);
            var attr = rl == null ? null : net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.getHolder(rl).orElse(null);
            if (attr != null) screen.withSupportInfo(previewEntity.getAttributes().hasAttribute(attr));
        } catch (Exception ignored) {}
    }

    private void removeModification(int idx) {
        if (idx < 0 || idx >= draftMods.size()) return;
        draftMods.remove(idx);
        if (modsScroll > 0 && modsScroll >= draftMods.size()) modsScroll--;
        refresh();
    }

    private int contentRight() { return (L + GUI_W - RENDER_W) - 4; }

    private int rightBtnX(String label) { return contentRight() - KveGuiStyle.btnWidth(label); }

    private void drawListBox(GuiGraphics g, int top, int rows, int rowH, int filledRows, String emptyText) {
        int x1 = L + LIST_W + 6 - 3, x2 = contentRight() + 3;
        KveGuiStyle.plate(g, x1, top - 2, x2, top + rows * rowH + 2, KveGuiStyle.BG_FIELD, KveGuiStyle.BG_FIELD, KveGuiStyle.BORDER_OUTER);
        if (filledRows <= 0 && emptyText != null) {
            KveGuiStyle.drawTextSafe(g, emptyText, x1 + 5, top + 2, x2 - x1 - 8, KveGuiStyle.th(0xFF556677));
        }
        for (int i = 0; i < Math.min(rows, filledRows); i++) {
            int ry = top + (i + 1) * rowH;
            g.fill(x1 + 3, ry - 1, x2 - 3, ry, KveGuiStyle.BORDER_SUBTLE);
        }
    }

    private void addModRowWidgets() {
        int modRowsVisible = modContentRows();
        int maxModsScroll = Math.max(0, draftMods.size() - modRowsVisible);
        modsScroll = Math.max(0, Math.min(maxModsScroll, modsScroll));
        int modRowRight = (L + GUI_W - RENDER_W) - 4;
        int editW = KveGuiStyle.btnWidth(Lang.get("gui.kve.btn.edit"));
        int editX = modRowRight - 14 - KveGuiStyle.BTN_GAP - editW;
        int xX = modRowRight - 14;
        for (int i = 0; i < modRowsVisible; i++) {
            int idx = i + modsScroll;
            if (idx >= draftMods.size()) break;
            int ry = modsListTop + i * MOD_ROW_H;
            int finalIdx = idx;
            modRowWidgets.add(addRenderableWidget(btn(Lang.get("gui.kve.btn.edit"), editX, ry, 0, () -> openEditModification(finalIdx))));
            modRowWidgets.add(addRenderableWidget(btn("X", xX, ry, 14, () -> removeModification(finalIdx))));
        }
    }

    private void rebuildModRows() {
        for (Button w : modRowWidgets) removeWidget(w);
        modRowWidgets.clear();
        addModRowWidgets();
    }

    private String friendlyAttrName(String attrId) {
        try {
            net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.parse(attrId);
            var attr = net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.get(rl);
            if (attr != null) {
                String translated = Component.translatable(attr.getDescriptionId()).getString();
                if (translated != null && !translated.isEmpty() && !translated.equals(attr.getDescriptionId()))
                    return translated;
            }
        } catch (Exception ignored) {}
        String path = attrId.contains(":") ? attrId.substring(attrId.indexOf(':') + 1) : attrId;
        String last = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
        String[] parts = last.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    private void flash(String m) { flashMsg = m; flashAt = System.currentTimeMillis(); flashColor = 0xFF66FF99; }
    private void refreshSaved() {
        savedList.clear();
        savedList.addAll(KubeJSMobExporter.getEntries());
        savedList.sort((a, b) -> Boolean.compare(CraftEditorState.isFavoriteMob(b.entityId), CraftEditorState.isFavoriteMob(a.entityId)));
    }

    private double parseD(EditBox box) {
        if (box == null) return Double.NaN;
        try { return Double.parseDouble(box.getValue().trim().replace(',', '.')); } catch (Exception e) { return Double.NaN; }
    }

    private LivingEntity tryCreatePreview(EntityType<?> et) {
        try {
            var e = et.create(minecraft.level);
            if (e instanceof LivingEntity le) return le;
        } catch (Exception ignored) {}
        return null;
    }

    private float calcScale(EntityType<?> et) {
        var bb = et.getDimensions();
        float max = Math.max(bb.width(), bb.height());
        return Math.min(45f, Math.max(14f, 40f / Math.max(1f, max)));
    }

    private String hoverTip = null;
    private int flashColor = 0xFF66FF99;
    private final ListKeyNav mobNav = new ListKeyNav();

    private static final com.google.gson.Gson SIG_GSON =
            new com.google.gson.GsonBuilder().serializeSpecialFloatingPointValues().create();
    private String cleanSig = null;
    private long sigCheckedAt = 0;
    private boolean dirtyCache = false;

    private String draftSignature() {
        KubeJSMobExporter.MobEntry e = buildDraftMobEntry();
        return e == null ? "" : SIG_GSON.toJson(e);
    }

    private void markClean() {
        cleanSig = draftSignature();
        dirtyCache = false;
        sigCheckedAt = System.currentTimeMillis();
    }

    private boolean isDirty() {
        if (selectedType == null || cleanSig == null) return false;
        long now = System.currentTimeMillis();
        if (now - sigCheckedAt > 300) {
            sigCheckedAt = now;
            dirtyCache = !cleanSig.equals(draftSignature());
        }
        return dirtyCache;
    }

    private void confirmLeave(Runnable action) {
        sigCheckedAt = 0;
        if (!isDirty()) { action.run(); return; }
        String name = selectedType != null ? selectedType.getDescription().getString() : currentMobId;
        minecraft.setScreen(new KveConfirmScreen(
                ok -> {
                    minecraft.setScreen(this);
                    if (ok) action.run();
                },
                Component.literal(Lang.get("gui.kve.mob.unsaved_title")),
                Component.literal(Lang.get("gui.kve.mob.unsaved_msg", name))));
    }

    private void flashWarn(String m) { flashMsg = m; flashAt = System.currentTimeMillis(); flashColor = 0xFFFFB347; }

    private boolean tabHasContent(Tab t) {
        return switch (t) {
            case STATS -> false;
            case EQUIPMENT -> !draftBuilds.isEmpty() || java.util.Arrays.stream(draftEquipItem).anyMatch(v -> v != null && !v.isEmpty());
            case EFFECTS -> !draftAttackEffects.isEmpty() || !draftMobEffects.isEmpty();
            case RELATIONS -> !draftAllies.isEmpty() || !draftEnemies.isEmpty();
            case BEHAVIOR -> !draftBehaviorFlags.isEmpty() || draftHasCombatTweaks();
        };
    }

    private void drawTabDots(GuiGraphics g) {
        if (tabRow == null) return;
        for (Tab t : Tab.values()) {
            if (!tabHasContent(t)) continue;
            Button b = tabRow.get(t.ordinal());
            int x = b.getX() + b.getWidth() - 5, y = b.getY() + 2;
            g.fill(x, y, x + 3, y + 3, KveGuiStyle.ACCENT_GOLD);
        }
    }

    private String savedEntrySummary(KubeJSMobExporter.MobEntry e, String displayName) {
        List<String> parts = new ArrayList<>();
        int stats = 0;
        for (double v : new double[]{e.health, e.armor, e.armorToughness, e.attackDamage, e.attackSpeed, e.movementSpeed, e.knockbackResist, e.followRange})
            if (!Double.isNaN(v)) stats++;
        if (stats > 0) parts.add(Lang.get("gui.kve.mob.sum.stats", stats));
        if (!e.attributeMods.isEmpty()) parts.add(Lang.get("gui.kve.mob.sum.attrs", e.attributeMods.size()));
        int equip = e.equipmentBuilds.isEmpty() ? e.equipment.size() : e.equipmentBuilds.size();
        if (equip > 0) parts.add(Lang.get(e.equipmentBuilds.isEmpty() ? "gui.kve.mob.sum.equip" : "gui.kve.mob.sum.builds", equip));
        int fx = e.attackEffects.size() + e.mobEffects.size();
        if (fx > 0) parts.add(Lang.get("gui.kve.mob.sum.effects", fx));
        if (!e.allies.isEmpty()) parts.add(Lang.get("gui.kve.mob.sum.allies", e.allies.size()));
        if (!e.enemies.isEmpty()) parts.add(Lang.get("gui.kve.mob.sum.enemies", e.enemies.size()));
        if (!e.behaviorFlags.isEmpty()) parts.add(Lang.get("gui.kve.mob.sum.flags", e.behaviorFlags.size()));
        if (e.customNameSet && e.customName != null && !e.customName.isEmpty()) parts.add(Lang.get("gui.kve.mob.sum.name"));
        String head = displayName + (e.enabled ? "" : " (" + Lang.get("gui.kve.status.disabled") + ")");
        return parts.isEmpty() ? head : head + ": " + String.join(", ", parts);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (hosted) {
            int wantH = fitH(hostedH);
            int wantW = Math.max(hostedW, GUI_W_MIN);
            if (wantW != GUI_W || wantH != GUI_H) refresh();
        }
        renderTransparentBackground(g);
        drawFrame(g);
        hoveredFullMobLabel = null;
        hoverTip = null;
        drawMobList(g, mx, my);
        drawAttrLabels(g);
        drawRenderPanel(g, mx, my);
        listResize.render(g, mx, my);
        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
        drawTabDots(g);
        if (activeTab == Tab.BEHAVIOR && activeBehaviorSubTab != BehaviorSubTab.COMBAT) {
            for (FlagCell c : flagCells) {
                if (mx >= c.x() && mx < c.x() + c.w() && my >= c.y() && my < c.y() + c.h()) {
                    String d = flagDesc(c.flag());
                    hoverTip = flagLabel(c.flag()) + (d.isBlank() ? "" : ": " + d);
                }
            }
        }
        if (flashAt > 0 && System.currentTimeMillis() - flashAt < 5000)
            KveGuiStyle.drawCenteredTextSafe(g, flashMsg, L + GUI_W / 2, T + GUI_H - 10, flashColor);
        if (hoverTip != null && hoveredFullMobLabel == null) {
            KveGuiStyle.drawFramedTooltip(g, hoverTip, mx, my, width, height);
        }
        if (hoveredFullMobLabel != null) {
            KveGuiStyle.drawFramedTooltip(g, hoveredFullMobLabel, mx, my, width, height);
        }
    }

    private String hoveredFullMobLabel = null;

    private void drawFrame(GuiGraphics g) {
        String title = Lang.get("gui.kve.mob.title");
        if (selectedType != null) title += " — " + selectedType.getDescription().getString();
        if (isDirty()) title += "  ● " + Lang.get("gui.kve.mob.unsaved");
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, title);
        g.fill(L + LIST_W, T + 14, L + LIST_W + 1, T + GUI_H, KveGuiStyle.th(0xFF2A4A6A));
        g.fill(L + GUI_W - RENDER_W, T + 14, L + GUI_W - RENDER_W + 1, T + GUI_H, KveGuiStyle.th(0xFF2A4A6A));
        boolean flashing = flashAt > 0 && System.currentTimeMillis() - flashAt < 5000;
        if (!flashing) {
            if (hasScriptOverride()) {
                KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.mob.script_override.active_hint"),
                        L + LIST_W + 6, T + GUI_H - 10, GUI_W - LIST_W - RENDER_W - 12, KveGuiStyle.TEXT_WARN);
            } else {
                int pending = PendingChangeManager.size();
                String hint = pending > 0
                        ? Lang.get("gui.kve.mob.pending_hint", pending)
                        : Lang.get("gui.kve.mob.no_pending_hint");
                KveGuiStyle.drawTextSafe(g, hint, L + LIST_W + 6, T + GUI_H - 10, GUI_W - LIST_W - RENDER_W - 12, 0xFFB8883A);
            }
        }
    }

    private void drawMobList(GuiGraphics g, int mx, int my) {
        int lx = L + 2;
        int y0 = T + 30;
        if (filtered.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.common.empty_search"), lx + 2, y0 + 4, LIST_W - 16, KveGuiStyle.th(0xFF2A5577));
            listScrollBar.render(g);
            return;
        }
        int scroll = listScrollBar.getScroll();
        int vis    = Math.min(ROWS, filtered.size() - scroll);
        for (int i = 0; i < vis; i++) {
            EntityType<?> et = filtered.get(i + scroll);
            int ry  = y0 + i * ROW_H;
            boolean hov = mx >= lx && mx < lx + LIST_W - 12 && my >= ry && my < ry + ROW_H - 1;
            boolean sel = et == selectedType;
            g.fill(lx, ry, lx + LIST_W - 12, ry + ROW_H - 1,
                    sel ? KveGuiStyle.th(0xFF2A5E9A) : hov ? KveGuiStyle.BORDER_FIELD : KveGuiStyle.BG_FIELD);
            if (mobNav.isCursor(i + scroll)) g.renderOutline(lx, ry, LIST_W - 12, ROW_H - 1, KveGuiStyle.ACCENT_GOLD);
            String name = et.getDescription().getString();
            if (isFavoriteMobType(et)) name = "\u2605" + name;
            int maxW = LIST_W - 14;
            KubeJSMobExporter.MobEntry saved = KubeJSMobExporter.getEntry(RegistryIndex.id(et));
            if (saved != null) {
                g.fill(lx + LIST_W - 17, ry + 4, lx + LIST_W - 14, ry + 7, saved.enabled ? 0xFF66CC77 : 0xFF777777);
                maxW -= 5;
            }
            KveGuiStyle.drawTextSafe(g, name, lx + 2, ry + 4, maxW,
                    sel ? 0xFFFFFFFF : isFavoriteMobType(et) ? 0xFFFFD966 : KveGuiStyle.TEXT_PRIMARY);
            if (hov && font.width(name) > maxW) {
                ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(et);
                hoveredFullMobLabel = id == null ? name : name + " (" + id + ")";
            }
        }
        listScrollBar.render(g);
        String count = searchFilter.isBlank() ? "" : Lang.get("gui.kve.mob.search_count", filtered.size(), allMobs.size());
        String page = filtered.size() > ROWS ? (scroll + 1) + "/" + (filtered.size() - ROWS + 1) : "";
        String foot = count.isEmpty() ? page : page.isEmpty() ? count : count + " \u00B7 " + page;
        if (!foot.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, foot, lx, y0 + ROWS * ROW_H + 2, LIST_W - 4, KveGuiStyle.th(0xFF556677));
        }
    }

    private void drawAttrLabels(GuiGraphics g) {
        int lx = L + LIST_W + 6;

        if (!externalSourceNote.isEmpty()) {
            String note = Lang.get("gui.kve.mob.external_detected") + ": " + externalSourceNote;
            KveGuiStyle.drawTextSafe(g, note, lx, T + 6, (L + GUI_W - RENDER_W) - lx - 4, 0xFFE8A03A);
        }

        if (activeTab == Tab.STATS) {
            drawStatsLabels(g, lx);
        } else if (activeTab == Tab.EQUIPMENT) {
            drawEquipmentLabels(g, lx);
        } else if (activeTab == Tab.EFFECTS) {
            drawEffectsLabels(g, lx);
        } else if (activeTab == Tab.RELATIONS) {
            drawRelationsLabels(g, lx);
        } else {
            drawBehaviorLabels(g, lx);
        }
    }

    private void drawStatsLabels(GuiGraphics g, int lx) {
        KveGuiStyle.Layout ly = new KveGuiStyle.Layout(lx, T + TAB_CONTENT_Y + 2, GAP);

        int col = KveGuiStyle.th(0xFF7BC8E0);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.mob.health"),      lx, ly.row() + 2, 95, col);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.mob.armor"),       lx, ly.row() + 2, 95, col);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.mob.armor_tough"), lx, ly.row() + 2, 95, col);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.mob.atk_damage"),  lx, ly.row() + 2, 95, col);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.mob.atk_speed"),   lx, ly.row() + 2, 95, col);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.mob.move_speed"),  lx, ly.row() + 2, 95, col);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.mob.knockback"),   lx, ly.row() + 2, 95, col);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.mob.follow_range"),lx, ly.row() + 2, 95, col); ly.gap(4);

        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.mob.custom_name"), lx, ly.row() + 2, 95, 0xFFE0B07D);
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.mob.color_hint"), lx, ly.row(9) + 2, 95, KveGuiStyle.th(0xFF556677));
        ly.row(11);
        ly.gap(2);

        ly.gap(3);
        int rowX = lx;
        int modRowRight = (L + GUI_W - RENDER_W) - 4;
        int modEditW = KveGuiStyle.btnWidth(Lang.get("gui.kve.btn.edit"));
        int modBtnsX = modRowRight - 14 - KveGuiStyle.BTN_GAP - modEditW;
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.item.custom_attrs") + " (" + draftMods.size() + "):", lx, ly.y() + 2, 95 + 66, KveGuiStyle.th(0xFFE8C96A));
        ly.gap(KveGuiStyle.BTN_H);
        int ay = ly.y();
        int modBoxX1 = rowX - 3, modBoxX2 = modRowRight + 3;
        int modBoxY1 = ay - 2;
        int modBoxRows = MOD_ROWS_VISIBLE;
        int modBoxY2 = ay + modBoxRows * MOD_ROW_H + 2;
        KveGuiStyle.plate(g, modBoxX1, modBoxY1, modBoxX2, modBoxY2, KveGuiStyle.BG_FIELD, KveGuiStyle.BG_FIELD, KveGuiStyle.BORDER_OUTER);
        if (draftMods.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.item.custom_attrs_empty"), rowX + 2, ay + 2, modBoxX2 - modBoxX1 - 4, KveGuiStyle.th(0xFF556677));
        }
        int modRowsVisible = modContentRows();
        for (int i = 0; i < modRowsVisible; i++) {
            int idx = i + modsScroll;
            if (idx >= draftMods.size()) break;
            KubeJSMobExporter.AttrMod m = draftMods.get(idx);
            int ry = ay + i * MOD_ROW_H;
            String dimPart  = (m.dimension == null || m.dimension.isEmpty()) ? "" : " @" + shortDim(m.dimension);
            String probPart = m.probability < 1.0 ? " (" + (int) Math.round(m.probability * 100) + "%)" : "";
            String label = friendlyAttrName(m.attrId) + "=" + fmt(m.value) + dimPart + probPart;
            KveGuiStyle.drawTextSafe(g, label, rowX, ry + 2, modBtnsX - rowX - 4, KveGuiStyle.TEXT_LABEL);
            g.fill(rowX, ry + MOD_ROW_H - 1, modBtnsX - 4, ry + MOD_ROW_H, KveGuiStyle.BORDER_SUBTLE);
        }
        if (draftMods.size() > modRowsVisible) {
            KveGuiStyle.drawTextSafe(g, (modsScroll + 1) + "-" +
                    Math.min(draftMods.size(), modsScroll + modRowsVisible) + "/" + draftMods.size(),
                    rowX, ay + modRowsVisible * MOD_ROW_H + 2, modBtnsX - rowX - 4, KveGuiStyle.th(0xFF556677));
        }
    }

    private void drawEquipmentLabels(GuiGraphics g, int lx) {
        if (draftBuilds.isEmpty()) {
            String slotName = Lang.get("gui.kve.mob.equip." + EQUIP_SLOTS[selectedEquipSlot].toLowerCase());
            String header = Lang.get("gui.kve.mob.equip.selected_slot") + ": " + slotName;
            KveGuiStyle.drawTextSafe(g, header, equipDetailHeaderX, equipDetailHeaderY, (L + GUI_W - RENDER_W) - equipDetailHeaderX - 4, KveGuiStyle.th(0xFF7BC8E0));
            return;
        }

        double[] probs = KubeJSMobExporter.buildProbabilities(draftBuilds);
        double total = 0;
        for (double pr : probs) total += pr;
        double unarmed = Math.max(0, 1 - total);

        int rowX = buildsListAx;
        int buildRowRight = (L + GUI_W - RENDER_W) - 4;
        int buildEditW = KveGuiStyle.btnWidth(Lang.get("gui.kve.btn.edit"));
        int buildBtnsX = buildRowRight - 14 - KveGuiStyle.BTN_GAP - buildEditW - (14 + KveGuiStyle.BTN_GAP) * 2;
        boolean noneUsable = total <= 0;
        String header = Lang.get("gui.kve.mob.build.list") + " (" + draftBuilds.size() + ")"
                + (noneUsable ? " " + Lang.get("gui.kve.mob.build.none_usable_warn")
                   : unarmed > 0.0001 ? " - " + Lang.get("gui.kve.mob.build.unarmed_pct", fmtShort(Math.round(unarmed * 1000) / 10.0)) : ":");
        KveGuiStyle.drawTextSafe(g, header,
                lx, buildsListTop - GAP + 2, contentRight() - lx, noneUsable ? 0xFFE07850 : KveGuiStyle.th(0xFFE8C96A));
        drawListBox(g, buildsListTop, BUILDS_ROWS_VISIBLE, BUILDS_ROW_H, draftBuilds.size() - buildsScroll, null);
        for (int i = 0; i < BUILDS_ROWS_VISIBLE; i++) {
            int idx = i + buildsScroll;
            if (idx >= draftBuilds.size()) break;
            KubeJSMobExporter.EquipmentBuild b = draftBuilds.get(idx);
            int ry = buildsListTop + i * BUILDS_ROW_H;
            String name = b.name == null || b.name.isBlank() ? "(" + Lang.get("gui.kve.mob.build.unnamed") + ")" : b.name;
            String pct = " (" + fmtShort(Math.round(probs[idx] * 1000) / 10.0) + "%)";
            int slotCount = 0;
            for (KubeJSMobExporter.EquipmentItem eq : b.equipment) if (eq.itemId != null && !eq.itemId.isEmpty()) slotCount++;
            String label = name + pct + " - " + slotCount + " " + Lang.get("gui.kve.mob.build.slots_suffix");
            KveGuiStyle.drawTextSafe(g, label, rowX, ry + 2, buildBtnsX - rowX - 4, b.enabled ? KveGuiStyle.TEXT_LABEL : KveGuiStyle.th(0xFF667788));
        }
        if (draftBuilds.size() > BUILDS_ROWS_VISIBLE) {
            KveGuiStyle.drawTextSafe(g, (buildsScroll + 1) + "-" +
                    Math.min(draftBuilds.size(), buildsScroll + BUILDS_ROWS_VISIBLE) + "/" + draftBuilds.size(),
                    rowX, buildsListTop + BUILDS_ROWS_VISIBLE * BUILDS_ROW_H + 2, buildBtnsX - rowX - 4, KveGuiStyle.th(0xFF556677));
        }
    }

    private void drawSectionDesc(GuiGraphics g, String text) {
        int x = L + LIST_W + 6, maxW = contentRight() - x;
        List<String> lines = KveGuiStyle.wrapTextSafe(font, text, maxW);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            KveGuiStyle.drawTextSafe(g, lines.get(i), x, sectionDescY + i * 10, maxW, KveGuiStyle.th(0xFF8899AA));
        }
    }

    private void drawPageIndicator(GuiGraphics g, int x, int top, int rows, int rowH, int scroll, int size) {
        if (size <= rows) return;
        KveGuiStyle.drawTextSafe(g, (scroll + 1) + "-" + Math.min(size, scroll + rows) + "/" + size,
                x, top + rows * rowH + 2, contentRight() - x, KveGuiStyle.th(0xFF556677));
    }

    private void drawEffectsLabels(GuiGraphics g, int lx) {
        List<KubeJSItemAttrExporter.FoodEffect> list = currentEffectsList();
        boolean attack = activeEffectsSubTab == EffectsSubTab.ATTACK;
        drawSectionDesc(g, Lang.get(attack ? "gui.kve.mob.effect.desc.attack" : "gui.kve.mob.effect.desc.mob"));
        KveGuiStyle.drawTextSafe(g, Lang.get(attack ? "gui.kve.mob.effect.tab.attack_long" : "gui.kve.mob.effect.tab.mob_long") + " (" + list.size() + "):",
                lx, effectsListTop - KveGuiStyle.BTN_H - 1, effectEditX() - lx, KveGuiStyle.th(0xFFE8C96A));
        drawListBox(g, effectsListTop, effectRows, EFFECT_ROW_H, list.size() - effectsScroll, Lang.get("gui.kve.mob.effect.empty"));
        int textRight = effectEditX() - 4;
        for (int i = 0; i < effectRows; i++) {
            int idx = i + effectsScroll;
            if (idx >= list.size()) break;
            KubeJSItemAttrExporter.FoodEffect fx = list.get(idx);
            int ry = effectsListTop + i * EFFECT_ROW_H;
            int x = effectsListAx;
            boolean known = KveEffectInfo.exists(fx.effectId);
            if (known) {
                KveEffectInfo.drawIcon(g, fx.effectId, x, ry + 1, 9);
                x += 12;
            }
            String name = (known ? "" : "[!] ") + KveEffectInfo.name(fx.effectId) + " " + KveEffectInfo.level(fx.amplifier);
            String detail = KveEffectInfo.duration(fx.durationTicks) + " · " + KveEffectInfo.percent(fx.chance);
            int detailW = font.width(detail);
            int nameColor = known ? KveEffectInfo.categoryColor(fx.effectId) : KveGuiStyle.TEXT_WARN;
            KveGuiStyle.drawTextSafe(g, name, x, ry + 2, Math.max(10, textRight - x - detailW - 6), nameColor);
            KveGuiStyle.drawTextSafe(g, detail, textRight - detailW, ry + 2, detailW + 1, KveGuiStyle.th(0xFF8899AA));
        }
        drawPageIndicator(g, effectsListAx, effectsListTop, effectRows, EFFECT_ROW_H, effectsScroll, list.size());
    }

    private boolean isBrokenAllyOrEnemyId(String id) {
        if (id == null || id.isBlank() || id.startsWith("#")) return false;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null || BuiltInRegistries.ENTITY_TYPE.getOptional(rl).isEmpty();
    }

    private String relDisplayName(String id) {
        if (id == null || id.startsWith("#")) return null;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        EntityType<?> et = rl == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(rl).orElse(null);
        return et == null ? null : et.getDescription().getString();
    }

    private void drawRelationsLabels(GuiGraphics g, int lx) {
        boolean allies = activeRelSubTab == RelSubTab.ALLIES;
        List<String> list = currentRelList(), other = otherRelList();
        drawSectionDesc(g, Lang.get(allies ? "gui.kve.mob.rel.desc.allies" : "gui.kve.mob.rel.desc.enemies"));
        KveGuiStyle.drawTextSafe(g, Lang.get(allies ? "gui.kve.mob.ally.list" : "gui.kve.mob.enemy.list") + " (" + list.size() + "):",
                lx, relListTop - 12, contentRight() - lx, KveGuiStyle.th(0xFFE8C96A));
        drawListBox(g, relListTop, relRows, REL_ROW_H, list.size() - relScroll, Lang.get("gui.kve.mob.rel.empty"));
        int textRight = relTextRight() - 4;
        String conflictL = " " + Lang.get(allies ? "gui.kve.mob.rel.also_enemy" : "gui.kve.mob.rel.also_ally");
        for (int i = 0; i < relRows; i++) {
            int idx = i + relScroll;
            if (idx >= list.size()) break;
            String id = list.get(idx);
            int ry = relListTop + i * REL_ROW_H;
            boolean broken = isBrokenAllyOrEnemyId(id);
            boolean conflict = other.contains(id);
            String name = relDisplayName(id);
            String main = (broken ? "[!] " : "") + (name != null ? name : id)
                    + (id.startsWith("#") ? " " + Lang.get("gui.kve.mob.rel.tag_suffix") : "");
            int color = broken || conflict ? KveGuiStyle.TEXT_WARN : KveGuiStyle.TEXT_LABEL;
            int x = relListAx;
            String suffix = conflict ? conflictL : "";
            int suffixW = suffix.isEmpty() ? 0 : font.width(suffix);
            int mainW = Math.min(font.width(main), Math.max(20, textRight - x - suffixW));
            KveGuiStyle.drawTextSafe(g, main, x, ry + 2, mainW + 1, color);
            x += mainW + 1;
            if (name != null && x + 12 < textRight - suffixW) {
                KveGuiStyle.drawTextSafe(g, " " + id, x, ry + 2, textRight - suffixW - x, KveGuiStyle.th(0xFF667788));
                x = Math.min(textRight - suffixW, x + font.width(" " + id));
            }
            if (conflict) KveGuiStyle.drawTextSafe(g, suffix, x, ry + 2, textRight - x, KveGuiStyle.TEXT_WARN);
        }
        drawPageIndicator(g, relListAx, relListTop, relRows, REL_ROW_H, relScroll, list.size());
    }

    private void drawBehaviorLabels(GuiGraphics g, int lx) {
        String descKey = switch (activeBehaviorSubTab) {
            case ATTITUDE -> "gui.kve.mob.behavior.desc.attitude";
            case IMMUNITY -> "gui.kve.mob.behavior.desc.immunity";
            case COMBAT -> "gui.kve.mob.combat.hint_inline";
        };
        drawSectionDesc(g, Lang.get(descKey));
        if (activeBehaviorSubTab == BehaviorSubTab.COMBAT) {
            String[] keys = {"xp", "regen", "taken", "dealt"};
            int labelW = contentRight() - 64 - 6 - lx;
            for (int i = 0; i < keys.length; i++) {
                int ry = combatTop + i * combatRowH;
                KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.mob.combat." + keys[i]), lx, ry + 3, labelW, KveGuiStyle.TEXT_LABEL);
                KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.mob.combat." + keys[i] + ".desc"), lx, ry + 13, contentRight() - lx, KveGuiStyle.th(0xFF667788));
            }
            return;
        }
        for (SectionHeader h : sectionHeaders) {
            KveGuiStyle.drawTextSafe(g, h.label(), h.x(), h.y() + 1, h.w() - 80, KveGuiStyle.th(0xFFE8C96A));
            g.fill(h.x(), h.y() + 10, h.x() + h.w(), h.y() + 11, KveGuiStyle.BORDER_SUBTLE);
        }
        double mx = KveGuiStyle.mouseX(), my = KveGuiStyle.mouseY();
        for (FlagCell c : flagCells) {
            boolean sel = draftBehaviorFlags.contains(c.flag());
            boolean hov = mx >= c.x() && mx < c.x() + c.w() && my >= c.y() && my < c.y() + c.h();
            g.fill(c.x(), c.y(), c.x() + c.w(), c.y() + c.h(), sel ? KveGuiStyle.BORDER_FIELD : (hov ? 0xFF2A3440 : KveGuiStyle.BG_FIELD));
            int ty = c.y() + Math.max(1, (c.h() - 8) / 2);
            KveGuiStyle.drawTextSafe(g, sel ? "☑" : "☐", c.x() + 2, ty, 10, sel ? 0xFF88CC88 : KveGuiStyle.th(0xFF556677));
            KveGuiStyle.drawTextSafe(g, flagLabel(c.flag()), c.x() + 13, ty, c.w() - 15, sel ? 0xFFAADDAA : KveGuiStyle.TEXT_LABEL);
        }
    }

    private String shortDim(String dim) {
        String s = dim.contains(":") ? dim.substring(dim.indexOf(':') + 1) : dim;
        return KveGuiStyle.truncate(font, s, 60);
    }

    private void drawRenderPanel(GuiGraphics g, int mx, int my) {
        int rx = L + GUI_W - RENDER_W + 2;
        int ry = T + PREVIEW_BOX_TOP_OFFSET;
        int rw = RENDER_W - 4;
        int rh = PREVIEW_BOX_H;
        g.fill(rx, ry, rx + rw, ry + rh, 0xFF0A0A18);

        if (previewEntity == null) {
            KveGuiStyle.drawCenteredTextSafe(g, Lang.get("gui.kve.mob.no_preview"), rx + rw / 2, ry + rh / 2 - 4, KveGuiStyle.th(0xFF2A5577));
        } else {
            int cx = rx + rw / 2;
            int cy = ry + rh - 20;
            try {
                float offX = (float)(cx - mx);
                float offY = (float)(cy - 50 - my);
                KveGuiStyle.renderEntityAt(g, cx, cy, (int) entityScale, offX, offY, previewEntity);
            } catch (Exception e) {
                KveGuiStyle.drawCenteredTextSafe(g, Lang.get("gui.kve.mob.render_error"), cx, ry + rh / 2, 0xFF664444);
            }

            if (selectedType != null) {
                String name = KveGuiStyle.truncate(font, selectedType.getDescription().getString(), rw - 4);
                KveGuiStyle.drawCenteredTextSafe(g, name, cx, ry + rh - 10, KveGuiStyle.th(0xFF8899BB));
                KveGuiStyle.drawTextSafeScaled(g, KveGuiStyle.truncate(font, currentMobId, (int) ((rw - 6) / 0.75f)),
                        rx + 3, ry + 3, rw - 6, KveGuiStyle.th(0xFF556677), 0.75f);
            }
        }

        drawModifiedMobsList(g, mx, my, rx, ry + rh + 4, rw, GUI_H - (rh + 4) - 20);
    }

    private void drawModifiedMobsList(GuiGraphics g, int mx, int my, int rx, int ry, int rw, int rh) {
        g.hLine(rx, rx + rw - 1, ry - 2, KveGuiStyle.th(0xFF2A4A6A));
        KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.mob.changed_list") + " (" + savedList.size() + "):", rx, ry, rw, KveGuiStyle.th(0xFFE8C96A));

        int listTop = ry + 11;
        int maxScroll = Math.max(0, savedList.size() - SAVED_ROWS_VISIBLE);
        savedScroll = Math.max(0, Math.min(maxScroll, savedScroll));

        if (savedList.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.common.none"), rx, listTop, rw, KveGuiStyle.th(0xFF2A5577));
            return;
        }

        for (int i = 0; i < SAVED_ROWS_VISIBLE; i++) {
            int idx = i + savedScroll;
            if (idx >= savedList.size()) break;
            KubeJSMobExporter.MobEntry entry = savedList.get(idx);
            int rowY = listTop + i * SAVED_ROW_H;
            boolean hov = mx >= rx && mx < rx + rw && my >= rowY && my < rowY + SAVED_ROW_H - 1;

            boolean exists = false;
            String displayName = entry.entityId;
            try {
                ResourceLocation rl = ResourceLocation.parse(entry.entityId);
                EntityType<?> et = BuiltInRegistries.ENTITY_TYPE.getOptional(rl).orElse(null);
                if (et != null) { exists = true; displayName = et.getDescription().getString(); }
            } catch (Exception ignored) {}
            if (CraftEditorState.isFavoriteMob(entry.entityId)) displayName = "★" + displayName;

            boolean isCurrent = selectedType != null && entry.entityId.equals(idStr(selectedType))
                    || (selectedType == null && entry.entityId.equals(orphanedSelectedId));

            int color = isCurrent ? 0xFFFFFFFF
                      : !entry.enabled ? KveGuiStyle.th(0xFF667788)
                      : !exists   ? 0xFFFF8844
                      : hov       ? KveGuiStyle.TEXT_PRIMARY
                      : KveGuiStyle.TEXT_LABEL;

            if (hov) {
                g.fill(rx, rowY, rx + rw, rowY + SAVED_ROW_H - 1, KveGuiStyle.BORDER_FIELD);
                hoverTip = savedEntrySummary(entry, displayName);
            }

            KveGuiStyle.drawTextSafe(g, displayName, rx, rowY + 2, rw - 12, color);

            if (!exists) KveGuiStyle.drawTextSafe(g, "!", rx + rw - 8, rowY + 2, 8, 0xFFFF8844);
            else if (!entry.enabled) KveGuiStyle.drawTextSafe(g, "\u26A0", rx + rw - 8, rowY + 2, 8, KveGuiStyle.th(0xFF667788));
        }
        if (savedList.size() > SAVED_ROWS_VISIBLE) {
            KveGuiStyle.drawTextSafe(g, (savedScroll + 1) + "-" +
                    Math.min(savedList.size(), savedScroll + SAVED_ROWS_VISIBLE) + "/" + savedList.size(),
                    rx, listTop + SAVED_ROWS_VISIBLE * SAVED_ROW_H + 1, rw, KveGuiStyle.th(0xFF556677));
        }
    }

    private String idStr(EntityType<?> et) {
        ResourceLocation k = BuiltInRegistries.ENTITY_TYPE.getKey(et);
        return k != null ? k.toString() : "";
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // un menu "Mas" abierto recibe el clic antes que lo que tenga debajo
        if (com.kve.client.gui.KveMenuButton.consumeClick(mx, my, btn)) return true;
        if (listResize.mouseClicked(mx, my)) return true;
        if (listScrollBar.mouseClicked(mx, my, btn)) return true;

        int lx = L + 2; int y0 = T + 30;
        int scroll = listScrollBar.getScroll();
        int vis    = Math.min(ROWS, filtered.size() - scroll);
        for (int i = 0; i < vis; i++) {
            int ry = y0 + i * ROW_H;
            if (mx >= lx && mx < lx + LIST_W - 12 && my >= ry && my < ry + ROW_H - 1) {
                EntityType<?> picked = filtered.get(i + scroll);
                if (btn == 1) {
                    String id = RegistryIndex.id(picked);
                    if (!id.isEmpty()) {
                        CraftEditorState.toggleFavoriteMob(id);
                        flash(Lang.get(CraftEditorState.isFavoriteMob(id) ? "gui.kve.mob.favorite_add" : "gui.kve.mob.favorite_remove"));
                        refresh();
                    }
                    return true;
                }
                mobNav.setCursor(i + scroll, filtered.size(), 1, ROWS, listScrollBar);
                if (picked == selectedType) return true;
                confirmLeave(() -> {
                    orphanedSelectedId = null;
                    selectMob(picked);
                });
                return true;
            }
        }

        int rx = L + GUI_W - RENDER_W + 2;
        int rw = RENDER_W - 4;
        int listTop = T + PREVIEW_BOX_TOP_OFFSET + PREVIEW_BOX_H + 4 + 11;
        for (int i = 0; i < SAVED_ROWS_VISIBLE; i++) {
            int idx = i + savedScroll;
            if (idx >= savedList.size()) break;
            int rowY = listTop + i * SAVED_ROW_H;
            if (mx >= rx && mx < rx + rw && my >= rowY && my < rowY + SAVED_ROW_H - 1) {
                KubeJSMobExporter.MobEntry entry = savedList.get(idx);
                confirmLeave(() -> openSavedEntry(entry));
                return true;
            }
        }

        if (activeTab == Tab.RELATIONS && fRelManual != null) {
            List<String> list = currentRelList();
            int rowRight = relTextRight();
            for (int i = 0; i < relRows; i++) {
                int idx = i + relScroll;
                if (idx >= list.size()) break;
                int ry = relListTop + i * REL_ROW_H;
                if (mx >= relListAx && mx < rowRight && my >= ry && my < ry + REL_ROW_H - 1) {
                    String removed = list.remove(idx);
                    refresh();
                    fRelManual.setValue(removed);
                    setFocused(fRelManual);
                    return true;
                }
            }
        }
        if (activeTab == Tab.BEHAVIOR && btn == 0) {
            for (FlagCell c : flagCells) {
                if (mx >= c.x() && mx < c.x() + c.w() && my >= c.y() && my < c.y() + c.h()) {
                    toggleFlag(c.flag());
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    private void openSavedEntry(KubeJSMobExporter.MobEntry entry) {
        try {
            ResourceLocation rl = ResourceLocation.tryParse(entry.entityId);
            EntityType<?> et = rl == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(rl).orElse(null);
            if (et != null) {
                orphanedSelectedId = null;
                selectMob(et);
                refresh();
            } else {
                orphanedSelectedId = entry.entityId;
                selectedType = null;
                previewEntity = null;
                currentMobId = entry.entityId;
                externalSourceNote = "";
                computeVanillaBaseline();
                clearFields();
                loadEntry(entry);
                flash("§c" + entry.entityId + " §r" + Lang.get("gui.kve.mob.orphan_note"));
                refresh();
            }
        } catch (Exception ignored) {}
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (listResize.mouseDragged(mx)) { refresh(); return true; }
        if (listResize.isDragging()) return true;
        if (listScrollBar.mouseDragged(mx, my, btn, dx, dy)) return true;
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        listResize.mouseReleased();
        listScrollBar.mouseReleased(mx, my, btn);
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        int mainListBot = T + 30 + ROWS * ROW_H;
        if (mx >= L + 2 && mx < L + LIST_W && my >= T + 30 && my < mainListBot) {
            listScrollBar.mouseScrolled(delta);
            return true;
        }
        if (my >= T + 16 && my < T + 16 + KveGuiStyle.BTN_H && mx >= L + LIST_W && mx < L + GUI_W - RENDER_W) {
            int n = Tab.values().length;
            switchTab(Tab.values()[Math.floorMod(activeTab.ordinal() - (int) Math.signum(delta), n)]);
            return true;
        }
        int modsX = L + LIST_W + 6;
        int modsListBot = modsListTop + modContentRows() * MOD_ROW_H;
        if (activeTab == Tab.STATS && mx >= modsX && mx < L + GUI_W - RENDER_W
                && my >= modsListTop && my < modsListBot) {
            int maxScroll = Math.max(0, draftMods.size() - modContentRows());
            modsScroll = Math.max(0, Math.min(maxScroll, modsScroll - (int) Math.signum(delta)));
            rebuildModRows();
            return true;
        }
        int buildsListBot = buildsListTop + BUILDS_ROWS_VISIBLE * BUILDS_ROW_H;
        if (activeTab == Tab.EQUIPMENT && !draftBuilds.isEmpty() && mx >= modsX && mx < L + GUI_W - RENDER_W
                && my >= buildsListTop && my < buildsListBot) {
            int maxScroll = Math.max(0, draftBuilds.size() - BUILDS_ROWS_VISIBLE);
            buildsScroll = Math.max(0, Math.min(maxScroll, buildsScroll - (int) Math.signum(delta)));
            rebuildBuildRows();
            return true;
        }
        int effectsListBot = effectsListTop + effectRows * EFFECT_ROW_H;
        if (activeTab == Tab.EFFECTS && mx >= modsX && mx < L + GUI_W - RENDER_W
                && my >= effectsListTop && my < effectsListBot) {
            int maxScroll = Math.max(0, currentEffectsList().size() - effectRows);
            effectsScroll = Math.max(0, Math.min(maxScroll, effectsScroll - (int) Math.signum(delta)));
            rebuildEffectRows();
            return true;
        }
        int relListBot = relListTop + relRows * REL_ROW_H;
        if (activeTab == Tab.RELATIONS && mx >= modsX && mx < L + GUI_W - RENDER_W
                && my >= relListTop && my < relListBot) {
            int maxScroll = Math.max(0, currentRelList().size() - relRows);
            relScroll = Math.max(0, Math.min(maxScroll, relScroll - (int) Math.signum(delta)));
            rebuildRelRows();
            return true;
        }
        if (mx >= L + GUI_W - RENDER_W) {
            int prx = L + GUI_W - RENDER_W + 2;
            int pry = T + PREVIEW_BOX_TOP_OFFSET;
            int prw = RENDER_W - 4;
            if (previewEntity != null && mx >= prx && mx < prx + prw && my >= pry && my < pry + PREVIEW_BOX_H) {
                entityScale = Math.max(PREVIEW_SCALE_MIN, Math.min(PREVIEW_SCALE_MAX,
                        entityScale + (float) Math.signum(delta) * PREVIEW_SCALE_STEP));
                return true;
            }
            int maxScroll = Math.max(0, savedList.size() - SAVED_ROWS_VISIBLE);
            savedScroll = Math.max(0, Math.min(maxScroll, savedScroll - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrl && key == GLFW.GLFW_KEY_D) { duplicateMob(); return true; }
        if (ctrl && key == GLFW.GLFW_KEY_F && searchBox != null) { setFocused(searchBox); return true; }
        if (ctrl && key >= GLFW.GLFW_KEY_1 && key < GLFW.GLFW_KEY_1 + Tab.values().length) {
            switchTab(Tab.values()[key - GLFW.GLFW_KEY_1]);
            return true;
        }
        if (fRelManual != null && getFocused() == fRelManual && ListKeyNav.isConfirm(key)) { addRelFromField(); return true; }
        if (getFocused() == null || getFocused() == searchBox) {
            if (ListKeyNav.isConfirm(key) && mobNav.active() && mobNav.cursor() < filtered.size()) {
                EntityType<?> picked = filtered.get(mobNav.cursor());
                if (picked != selectedType) confirmLeave(() -> { orphanedSelectedId = null; selectMob(picked); });
                return true;
            }
            if (mobNav.keyPressed(key, filtered.size(), 1, ROWS, listScrollBar)) return true;
        }
        if (getFocused() != null && getFocused().keyPressed(key, scan, mods)) return true;
        if (EditorKeys.isSave(key, mods)) { saveMob(); return true; }
        if (EditorKeys.isFocusTab(key, mods)) { super.keyPressed(key, scan, mods); return true; }
        return EditorKeys.swallow(mods);
    }

    @Override public boolean isPauseScreen() { return false; }
}
