package com.kve.client.gui.event;

import com.kve.client.gui.worldgen.BiomePickerScreen;
import com.kve.client.gui.picker.ItemPickerScreen;
import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KveScaledEditBox;
import com.kve.client.gui.picker.TypeOptionPickerScreen;

import com.kve.common.Lang;
import com.kve.common.event.EventEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class EventConditionEditScreen extends Screen {

    private static final int GUI_W = 260;
    private static final int GUI_H_MIN = 88;
    private int GUI_H = GUI_H_MIN;

    private final Screen parent;
    private final Consumer<EventEntry.Condition> onConfirm;

    private EventEntry.ConditionType draftType = EventEntry.ConditionType.DIMENSION;
    private String draftDimensionId = "";
    private String draftPermissionNode = "";
    private EventEntry.GameMode draftGamemode = EventEntry.GameMode.SURVIVAL;
    private EventEntry.TimeOfDay draftTimeOfDay = EventEntry.TimeOfDay.DAY;
    private int draftYMin = 0;
    private int draftYMax = 320;
    private String draftBiomeId = "";
    private int draftXpLevelMin = 0;
    private String draftHasItemId = "";
    private int draftHasItemMinCount = 1;
    private double draftRandomChance = 1.0;
    private float draftHealthMax = 20.0f;
    private float draftHealthMin = 0.0f;
    private int draftFoodLevelMin = 0;
    private String draftHasEffectId = "";
    private String draftEquippedItemId = "";
    private EventEntry.EquipSlot draftEquippedSlot = EventEntry.EquipSlot.MAINHAND;
    private String draftAdvancementId = "";
    private String draftEntityEquippedItemId = "";
    private EventEntry.WeatherState draftWeatherState = EventEntry.WeatherState.CLEAR;
    private String draftMoonPhase = "0";
    private EventEntry.WeekDay draftDayOfWeek = EventEntry.WeekDay.MON;
    private String draftDateStartMd = "";
    private String draftDateEndMd = "";
    private int draftHourStart = 0;
    private int draftHourEnd = 23;
    private String draftStageId = "";
    private String draftFlagId = "";
    private int draftPlaytimeMinSeconds = 0;

    private int L, T;
    private int fieldRowY;

    public EventConditionEditScreen(Screen parent, Consumer<EventEntry.Condition> onConfirm) {
        super(Lang.c("gui.kve.event.cond.title"));
        this.parent = parent;
        this.onConfirm = onConfirm;
    }

    /** Preloads an existing condition's values for the "Edit" flow. */
    public EventConditionEditScreen withExisting(EventEntry.Condition c) {
        if (c != null) {
            draftType = c.type;
            draftDimensionId = c.dimensionId == null ? "" : c.dimensionId;
            draftPermissionNode = c.permissionNode == null ? "" : c.permissionNode;
            draftGamemode = c.gamemode;
            draftTimeOfDay = c.timeOfDay;
            draftYMin = c.yMin;
            draftYMax = c.yMax;
            draftBiomeId = c.biomeId == null ? "" : c.biomeId;
            draftXpLevelMin = c.xpLevelMin;
            draftHasItemId = c.hasItemId == null ? "" : c.hasItemId;
            draftHasItemMinCount = c.hasItemMinCount;
            draftRandomChance = c.randomChance;
            draftHealthMax = c.healthMax;
            draftHealthMin = c.healthMin;
            draftFoodLevelMin = c.foodLevelMin;
            draftHasEffectId = c.hasEffectId == null ? "" : c.hasEffectId;
            draftEquippedItemId = c.equippedItemId == null ? "" : c.equippedItemId;
            draftEquippedSlot = c.equippedSlot;
            draftAdvancementId = c.advancementId == null ? "" : c.advancementId;
            draftEntityEquippedItemId = c.entityEquippedItemId == null ? "" : c.entityEquippedItemId;
            draftWeatherState = c.weatherState;
            draftMoonPhase = c.moonPhase == null ? "0" : c.moonPhase;
            draftDayOfWeek = c.dayOfWeek;
            draftDateStartMd = c.dateStartMd == null ? "" : c.dateStartMd;
            draftDateEndMd = c.dateEndMd == null ? "" : c.dateEndMd;
            draftHourStart = c.hourStart;
            draftHourEnd = c.hourEnd;
            draftStageId = c.stageId == null ? "" : c.stageId;
            draftFlagId = c.flagId == null ? "" : c.flagId;
            draftPlaytimeMinSeconds = c.playtimeMinSeconds;
        }
        return this;
    }

    @Override
    protected void init() {
        GUI_H = computeGuiH();
        L = (width - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        clearWidgets();
        setFocused(null);

        addRenderableWidget(btn(Lang.get("gui.kve.event.cond.type_btn", typeLabel(draftType)), L + 10, T + 20, 0, this::openTypePicker));

        fieldRowY = T + 42;
        int fy = fieldRowY;
        switch (draftType) {
            case DIMENSION -> {
                EditBox f = new KveScaledEditBox(font, L + 10, fy, GUI_W - 20, 14, Lang.c("gui.kve.event.cond.dimension"));
                f.setMaxLength(64);
                f.setValue(draftDimensionId);
                f.setHint(Lang.c("gui.kve.tag.id_hint"));
                f.setResponder(s -> draftDimensionId = s);
                addRenderableWidget(f);
                setInitialFocus(f);
            }
            case PERMISSION -> {
                EditBox f = new KveScaledEditBox(font, L + 10, fy, GUI_W - 20, 14, Lang.c("gui.kve.event.cond.permission"));
                f.setMaxLength(64);
                f.setValue(draftPermissionNode);
                f.setHint(Lang.c("gui.kve.event.cond.permission"));
                f.setResponder(s -> draftPermissionNode = s);
                addRenderableWidget(f);
                setInitialFocus(f);
            }
            case GAMEMODE -> addRenderableWidget(btn(gamemodeLabel(draftGamemode), L + 10, fy, 0, this::openGamemodePicker));
            case TIME_OF_DAY -> addRenderableWidget(btn(timeOfDayLabel(draftTimeOfDay), L + 10, fy, 0, this::openTimeOfDayPicker));
            case Y_LEVEL_MIN -> {
                EditBox f = new KveScaledEditBox(font, L + 10, fy, GUI_W - 20, 14, Lang.c("gui.kve.event.cond.y_min"));
                f.setValue(String.valueOf(draftYMin));
                f.setResponder(s -> draftYMin = parseInt(s, draftYMin));
                addRenderableWidget(f);
                setInitialFocus(f);
            }
            case Y_LEVEL_MAX -> {
                EditBox f = new KveScaledEditBox(font, L + 10, fy, GUI_W - 20, 14, Lang.c("gui.kve.event.cond.y_max"));
                f.setValue(String.valueOf(draftYMax));
                f.setResponder(s -> draftYMax = parseInt(s, draftYMax));
                addRenderableWidget(f);
                setInitialFocus(f);
            }
            case BIOME -> {
                EditBox f = new KveScaledEditBox(font, L + 10, fy, GUI_W - 20 - 40, 14, Lang.c("gui.kve.event.cond.biome_id"));
                f.setMaxLength(64);
                f.setValue(draftBiomeId);
                f.setHint(Lang.c("gui.kve.tag.id_hint"));
                f.setResponder(s -> draftBiomeId = s);
                addRenderableWidget(f);
                setInitialFocus(f);
                addRenderableWidget(btn("...", L + 10 + GUI_W - 20 - 38, fy, 0, this::pickConditionBiome));
            }
            case XP_LEVEL_MIN -> {
                EditBox f = new KveScaledEditBox(font, L + 10, fy, GUI_W - 20, 14, Lang.c("gui.kve.event.cond.xp_level_min"));
                f.setValue(String.valueOf(draftXpLevelMin));
                f.setResponder(s -> draftXpLevelMin = parseInt(s, draftXpLevelMin));
                addRenderableWidget(f);
                setInitialFocus(f);
            }
            case HAS_ITEM -> {
                EditBox f1 = new KveScaledEditBox(font, L + 10, fy, GUI_W - 20, 14, Lang.c("gui.kve.event.cond.has_item_id"));
                f1.setMaxLength(64);
                f1.setValue(draftHasItemId);
                f1.setHint(Lang.c("gui.kve.tag.id_hint"));
                f1.setResponder(s -> draftHasItemId = s);
                addRenderableWidget(f1);
                setInitialFocus(f1);
                EditBox f2 = new KveScaledEditBox(font, L + 10, fy + 20, GUI_W - 20, 14, Lang.c("gui.kve.event.cond.has_item_min_count"));
                f2.setValue(String.valueOf(draftHasItemMinCount));
                f2.setResponder(s -> draftHasItemMinCount = parseInt(s, draftHasItemMinCount));
                addRenderableWidget(f2);
            }
            case RANDOM_CHANCE -> {
                EditBox f = new KveScaledEditBox(font, L + 10, fy, GUI_W - 20, 14, Lang.c("gui.kve.event.cond.random_chance"));
                f.setValue(fmt(draftRandomChance));
                f.setHint(Lang.c("gui.kve.event.cond.random_chance_hint"));
                f.setResponder(s -> draftRandomChance = parseDouble(s, draftRandomChance));
                addRenderableWidget(f);
                setInitialFocus(f);
            }
            case HEALTH_MAX -> {
                EditBox f = new KveScaledEditBox(font, L + 10, fy, GUI_W - 20, 14, Lang.c("gui.kve.event.cond.health_max"));
                f.setValue(fmt(draftHealthMax));
                f.setResponder(s -> draftHealthMax = (float) parseDouble(s, draftHealthMax));
                addRenderableWidget(f);
                setInitialFocus(f);
            }
            case HEALTH_MIN -> {
                EditBox f = new KveScaledEditBox(font, L + 10, fy, GUI_W - 20, 14, Lang.c("gui.kve.event.cond.health_min"));
                f.setValue(fmt(draftHealthMin));
                f.setResponder(s -> draftHealthMin = (float) parseDouble(s, draftHealthMin));
                addRenderableWidget(f);
                setInitialFocus(f);
            }
            case FOOD_LEVEL_MIN -> {
                EditBox f = new KveScaledEditBox(font, L + 10, fy, GUI_W - 20, 14, Lang.c("gui.kve.event.cond.food_level_min"));
                f.setValue(String.valueOf(draftFoodLevelMin));
                f.setResponder(s -> draftFoodLevelMin = parseInt(s, draftFoodLevelMin));
                addRenderableWidget(f);
                setInitialFocus(f);
            }
            case HAS_EFFECT -> {
                EditBox f = new KveScaledEditBox(font, L + 10, fy, GUI_W - 20, 14, Lang.c("gui.kve.event.cond.has_effect_id"));
                f.setMaxLength(64);
                f.setValue(draftHasEffectId);
                f.setHint(Lang.c("gui.kve.tag.id_hint"));
                f.setResponder(s -> draftHasEffectId = s);
                addRenderableWidget(f);
                setInitialFocus(f);
            }
            case HAS_ITEM_EQUIPPED -> {
                EditBox f1 = new KveScaledEditBox(font, L + 10, fy, GUI_W - 20, 14, Lang.c("gui.kve.event.cond.equipped_item_id"));
                f1.setMaxLength(64);
                f1.setValue(draftEquippedItemId);
                f1.setHint(Lang.c("gui.kve.tag.id_hint"));
                f1.setResponder(s -> draftEquippedItemId = s);
                addRenderableWidget(f1);
                setInitialFocus(f1);
                addRenderableWidget(btn(equipSlotLabel(draftEquippedSlot), L + 10, fy + 20, 0, this::openEquipSlotPicker));
            }
            case ENTITY_HAS_ITEM_EQUIPPED -> {
                EditBox f1 = new KveScaledEditBox(font, L + 10, fy, GUI_W - 20 - 40, 14, Lang.c("gui.kve.event.cond.entity_equipped_item_id"));
                f1.setMaxLength(64);
                f1.setValue(draftEntityEquippedItemId);
                f1.setHint(Lang.c("gui.kve.tag.id_hint"));
                f1.setResponder(s -> draftEntityEquippedItemId = s);
                addRenderableWidget(f1);
                setInitialFocus(f1);
                addRenderableWidget(btn("...", L + 10 + GUI_W - 20 - 38, fy, 0, this::pickEntityEquippedItem));
                addRenderableWidget(btn(equipSlotLabel(draftEquippedSlot), L + 10, fy + 20, 0, this::openEquipSlotPicker));
            }
            case ADVANCEMENT_DONE, ADVANCEMENT_MISSING -> {
                EditBox f = new KveScaledEditBox(font, L + 10, fy, GUI_W - 20, 14, Lang.c("gui.kve.event.cond.advancement_id"));
                f.setMaxLength(64);
                f.setValue(draftAdvancementId);
                f.setHint(Lang.c("gui.kve.tag.id_hint"));
                f.setResponder(s -> draftAdvancementId = s);
                addRenderableWidget(f);
                setInitialFocus(f);
            }
            case WEATHER -> addRenderableWidget(btn(weatherLabel(draftWeatherState), L + 10, fy, 0, this::openWeatherPicker));
            case MOON_PHASE -> {
                EditBox f = new KveScaledEditBox(font, L + 10, fy, GUI_W - 20, 14, Lang.c("gui.kve.event.cond.moon_phase"));
                f.setMaxLength(8);
                f.setValue(draftMoonPhase);
                f.setHint(Lang.c("gui.kve.event.cond.moon_phase_hint"));
                f.setResponder(s -> draftMoonPhase = s);
                addRenderableWidget(f);
                setInitialFocus(f);
            }
            case DAY_OF_WEEK -> addRenderableWidget(btn(dayOfWeekLabel(draftDayOfWeek), L + 10, fy, 0, this::openDayOfWeekPicker));
            case DATE_BETWEEN -> {
                EditBox f1 = new KveScaledEditBox(font, L + 10, fy, (GUI_W - 24) / 2, 14, Lang.c("gui.kve.event.cond.date_start_md"));
                f1.setMaxLength(5);
                f1.setValue(draftDateStartMd);
                f1.setHint(Lang.c("gui.kve.event.cond.date_md_hint"));
                f1.setResponder(s -> draftDateStartMd = s);
                addRenderableWidget(f1);
                setInitialFocus(f1);
                EditBox f2 = new KveScaledEditBox(font, L + 14 + (GUI_W - 24) / 2, fy, (GUI_W - 24) / 2, 14, Lang.c("gui.kve.event.cond.date_end_md"));
                f2.setMaxLength(5);
                f2.setValue(draftDateEndMd);
                f2.setHint(Lang.c("gui.kve.event.cond.date_md_hint"));
                f2.setResponder(s -> draftDateEndMd = s);
                addRenderableWidget(f2);
            }
            case HOUR_BETWEEN -> {
                EditBox f1 = new KveScaledEditBox(font, L + 10, fy, (GUI_W - 24) / 2, 14, Lang.c("gui.kve.event.cond.hour_start"));
                f1.setValue(String.valueOf(draftHourStart));
                f1.setResponder(s -> draftHourStart = parseInt(s, draftHourStart));
                addRenderableWidget(f1);
                setInitialFocus(f1);
                EditBox f2 = new KveScaledEditBox(font, L + 14 + (GUI_W - 24) / 2, fy, (GUI_W - 24) / 2, 14, Lang.c("gui.kve.event.cond.hour_end"));
                f2.setValue(String.valueOf(draftHourEnd));
                f2.setResponder(s -> draftHourEnd = parseInt(s, draftHourEnd));
                addRenderableWidget(f2);
            }
            case HAS_TAG_STAGE -> {
                EditBox f = new KveScaledEditBox(font, L + 10, fy, GUI_W - 20, 14, Lang.c("gui.kve.event.cond.stage_id"));
                f.setMaxLength(64);
                f.setValue(draftStageId);
                f.setHint(Lang.c("gui.kve.tag.id_hint"));
                f.setResponder(s -> draftStageId = s);
                addRenderableWidget(f);
                setInitialFocus(f);
            }
            case NOT_HAS_FLAG -> {
                EditBox f = new KveScaledEditBox(font, L + 10, fy, GUI_W - 20, 14, Lang.c("gui.kve.event.cond.flag_id"));
                f.setMaxLength(64);
                f.setValue(draftFlagId);
                f.setHint(Lang.c("gui.kve.tag.id_hint"));
                f.setResponder(s -> draftFlagId = s);
                addRenderableWidget(f);
                setInitialFocus(f);
            }
            case PLAYTIME_MIN_SECONDS -> {
                EditBox f = new KveScaledEditBox(font, L + 10, fy, GUI_W - 20, 14, Lang.c("gui.kve.event.cond.playtime_min_seconds"));
                f.setValue(String.valueOf(draftPlaytimeMinSeconds));
                f.setResponder(s -> draftPlaytimeMinSeconds = parseInt(s, draftPlaytimeMinSeconds));
                addRenderableWidget(f);
                setInitialFocus(f);
            }
        }

        {
            String confirmLabel = Lang.get("gui.kve.btn.confirm");
            String cancelLabel = Lang.get("gui.kve.btn.cancel");
            int confirmW = KveGuiStyle.btnWidth(confirmLabel);
            int cancelW = KveGuiStyle.btnWidth(cancelLabel);
            int pairX = L + GUI_W / 2 - (confirmW + KveGuiStyle.BTN_GAP + cancelW) / 2;
            KveGuiStyle.ButtonRow confirmRow = new KveGuiStyle.ButtonRow(pairX, T + GUI_H - 18, L + GUI_W - 6);
            addRenderableWidget(confirmRow.add(0, confirmLabel, b -> confirm()).withPrimary(true));
            addRenderableWidget(confirmRow.add(0, cancelLabel, b -> onClose()));
        }
    }

    private void openTypePicker() {
        List<TypeOptionPickerScreen.Option> opts = new ArrayList<>();
        EventEntry.ConditionType[] values = EventEntry.ConditionType.values();
        for (EventEntry.ConditionType t : values) {
            opts.add(new TypeOptionPickerScreen.Option(typeLabel(t), typeDesc(t), () -> {
                draftType = t;
                minecraft.setScreen(this);
                init();
            }));
        }
        minecraft.setScreen(new TypeOptionPickerScreen(this, Lang.get("gui.kve.event.cond.pick_title"), opts, draftType.ordinal()));
    }

    private void openGamemodePicker() {
        List<TypeOptionPickerScreen.Option> opts = new ArrayList<>();
        EventEntry.GameMode[] values = EventEntry.GameMode.values();
        for (EventEntry.GameMode m : values) {
            opts.add(new TypeOptionPickerScreen.Option(gamemodeLabel(m), "", () -> {
                draftGamemode = m;
                minecraft.setScreen(this);
                init();
            }));
        }
        minecraft.setScreen(new TypeOptionPickerScreen(this, Lang.get("gui.kve.event.cond.type.gamemode"), opts, draftGamemode.ordinal()));
    }

    private void openTimeOfDayPicker() {
        List<TypeOptionPickerScreen.Option> opts = new ArrayList<>();
        EventEntry.TimeOfDay[] values = EventEntry.TimeOfDay.values();
        for (EventEntry.TimeOfDay t : values) {
            opts.add(new TypeOptionPickerScreen.Option(timeOfDayLabel(t), "", () -> {
                draftTimeOfDay = t;
                minecraft.setScreen(this);
                init();
            }));
        }
        minecraft.setScreen(new TypeOptionPickerScreen(this, Lang.get("gui.kve.event.cond.type.time_of_day"), opts, draftTimeOfDay.ordinal()));
    }

    private void pickEntityEquippedItem() {
        minecraft.setScreen(new ItemPickerScreen(this, (net.minecraft.world.item.ItemStack stack) -> {
            net.minecraft.resources.ResourceLocation k = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (k != null) draftEntityEquippedItemId = k.toString();
        }));
    }

    private void openEquipSlotPicker() {
        List<TypeOptionPickerScreen.Option> opts = new ArrayList<>();
        EventEntry.EquipSlot[] values = EventEntry.EquipSlot.values();
        for (EventEntry.EquipSlot s : values) {
            opts.add(new TypeOptionPickerScreen.Option(equipSlotLabel(s), "", () -> {
                draftEquippedSlot = s;
                minecraft.setScreen(this);
                init();
            }));
        }
        minecraft.setScreen(new TypeOptionPickerScreen(this, Lang.get("gui.kve.event.cond.equipped_item_id"), opts, draftEquippedSlot.ordinal()));
    }

    private void openWeatherPicker() {
        List<TypeOptionPickerScreen.Option> opts = new ArrayList<>();
        EventEntry.WeatherState[] values = EventEntry.WeatherState.values();
        for (EventEntry.WeatherState w : values) {
            opts.add(new TypeOptionPickerScreen.Option(weatherLabel(w), "", () -> {
                draftWeatherState = w;
                minecraft.setScreen(this);
                init();
            }));
        }
        minecraft.setScreen(new TypeOptionPickerScreen(this, Lang.get("gui.kve.event.cond.type.weather"), opts, draftWeatherState.ordinal()));
    }

    private void openDayOfWeekPicker() {
        List<TypeOptionPickerScreen.Option> opts = new ArrayList<>();
        EventEntry.WeekDay[] values = EventEntry.WeekDay.values();
        for (EventEntry.WeekDay d : values) {
            opts.add(new TypeOptionPickerScreen.Option(dayOfWeekLabel(d), "", () -> {
                draftDayOfWeek = d;
                minecraft.setScreen(this);
                init();
            }));
        }
        minecraft.setScreen(new TypeOptionPickerScreen(this, Lang.get("gui.kve.event.cond.type.day_of_week"), opts, draftDayOfWeek.ordinal()));
    }

    private String weatherLabel(EventEntry.WeatherState w) {
        return switch (w) {
            case CLEAR -> Lang.get("gui.kve.event.weather.clear");
            case RAIN -> Lang.get("gui.kve.event.weather.rain");
            case THUNDER -> Lang.get("gui.kve.event.weather.thunder");
        };
    }

    private String dayOfWeekLabel(EventEntry.WeekDay d) {
        return switch (d) {
            case MON -> Lang.get("gui.kve.event.weekday.mon");
            case TUE -> Lang.get("gui.kve.event.weekday.tue");
            case WED -> Lang.get("gui.kve.event.weekday.wed");
            case THU -> Lang.get("gui.kve.event.weekday.thu");
            case FRI -> Lang.get("gui.kve.event.weekday.fri");
            case SAT -> Lang.get("gui.kve.event.weekday.sat");
            case SUN -> Lang.get("gui.kve.event.weekday.sun");
        };
    }

    private String equipSlotLabel(EventEntry.EquipSlot s) {
        return switch (s) {
            case MAINHAND -> Lang.get("gui.kve.event.equipslot.mainhand");
            case OFFHAND -> Lang.get("gui.kve.event.equipslot.offhand");
            case HEAD -> Lang.get("gui.kve.event.equipslot.head");
            case CHEST -> Lang.get("gui.kve.event.equipslot.chest");
            case LEGS -> Lang.get("gui.kve.event.equipslot.legs");
            case FEET -> Lang.get("gui.kve.event.equipslot.feet");
        };
    }

    private String typeDesc(EventEntry.ConditionType t) {
        return switch (t) {
            case DIMENSION -> Lang.get("gui.kve.event.cond.desc.dimension");
            case PERMISSION -> Lang.get("gui.kve.event.cond.desc.permission");
            case GAMEMODE -> Lang.get("gui.kve.event.cond.desc.gamemode");
            case TIME_OF_DAY -> Lang.get("gui.kve.event.cond.desc.time_of_day");
            case Y_LEVEL_MIN -> Lang.get("gui.kve.event.cond.desc.y_level_min");
            case Y_LEVEL_MAX -> Lang.get("gui.kve.event.cond.desc.y_level_max");
            case BIOME -> Lang.get("gui.kve.event.cond.desc.biome");
            case XP_LEVEL_MIN -> Lang.get("gui.kve.event.cond.desc.xp_level_min");
            case HAS_ITEM -> Lang.get("gui.kve.event.cond.desc.has_item");
            case RANDOM_CHANCE -> Lang.get("gui.kve.event.cond.desc.random_chance");
            case HEALTH_MAX -> Lang.get("gui.kve.event.cond.desc.health_max");
            case HEALTH_MIN -> Lang.get("gui.kve.event.cond.desc.health_min");
            case FOOD_LEVEL_MIN -> Lang.get("gui.kve.event.cond.desc.food_level_min");
            case HAS_EFFECT -> Lang.get("gui.kve.event.cond.desc.has_effect");
            case HAS_ITEM_EQUIPPED -> Lang.get("gui.kve.event.cond.desc.has_item_equipped");
            case ADVANCEMENT_DONE -> Lang.get("gui.kve.event.cond.desc.advancement_done");
            case ADVANCEMENT_MISSING -> Lang.get("gui.kve.event.cond.desc.advancement_missing");
            case WEATHER -> Lang.get("gui.kve.event.cond.desc.weather");
            case MOON_PHASE -> Lang.get("gui.kve.event.cond.desc.moon_phase");
            case DAY_OF_WEEK -> Lang.get("gui.kve.event.cond.desc.day_of_week");
            case DATE_BETWEEN -> Lang.get("gui.kve.event.cond.desc.date_between");
            case HOUR_BETWEEN -> Lang.get("gui.kve.event.cond.desc.hour_between");
            case HAS_TAG_STAGE -> Lang.get("gui.kve.event.cond.desc.has_tag_stage");
            case NOT_HAS_FLAG -> Lang.get("gui.kve.event.cond.desc.not_has_flag");
            case PLAYTIME_MIN_SECONDS -> Lang.get("gui.kve.event.cond.desc.playtime_min_seconds");
            case ENTITY_HAS_ITEM_EQUIPPED -> Lang.get("gui.kve.event.cond.desc.entity_has_item_equipped");
        };
    }

    private String typeLabel(EventEntry.ConditionType t) {
        return switch (t) {
            case DIMENSION -> Lang.get("gui.kve.event.cond.type.dimension");
            case PERMISSION -> Lang.get("gui.kve.event.cond.type.permission");
            case GAMEMODE -> Lang.get("gui.kve.event.cond.type.gamemode");
            case TIME_OF_DAY -> Lang.get("gui.kve.event.cond.type.time_of_day");
            case Y_LEVEL_MIN -> Lang.get("gui.kve.event.cond.type.y_level_min");
            case Y_LEVEL_MAX -> Lang.get("gui.kve.event.cond.type.y_level_max");
            case BIOME -> Lang.get("gui.kve.event.cond.type.biome");
            case XP_LEVEL_MIN -> Lang.get("gui.kve.event.cond.type.xp_level_min");
            case HAS_ITEM -> Lang.get("gui.kve.event.cond.type.has_item");
            case RANDOM_CHANCE -> Lang.get("gui.kve.event.cond.type.random_chance");
            case HEALTH_MAX -> Lang.get("gui.kve.event.cond.type.health_max");
            case HEALTH_MIN -> Lang.get("gui.kve.event.cond.type.health_min");
            case FOOD_LEVEL_MIN -> Lang.get("gui.kve.event.cond.type.food_level_min");
            case HAS_EFFECT -> Lang.get("gui.kve.event.cond.type.has_effect");
            case HAS_ITEM_EQUIPPED -> Lang.get("gui.kve.event.cond.type.has_item_equipped");
            case ADVANCEMENT_DONE -> Lang.get("gui.kve.event.cond.type.advancement_done");
            case ADVANCEMENT_MISSING -> Lang.get("gui.kve.event.cond.type.advancement_missing");
            case WEATHER -> Lang.get("gui.kve.event.cond.type.weather");
            case MOON_PHASE -> Lang.get("gui.kve.event.cond.type.moon_phase");
            case DAY_OF_WEEK -> Lang.get("gui.kve.event.cond.type.day_of_week");
            case DATE_BETWEEN -> Lang.get("gui.kve.event.cond.type.date_between");
            case HOUR_BETWEEN -> Lang.get("gui.kve.event.cond.type.hour_between");
            case HAS_TAG_STAGE -> Lang.get("gui.kve.event.cond.type.has_tag_stage");
            case NOT_HAS_FLAG -> Lang.get("gui.kve.event.cond.type.not_has_flag");
            case PLAYTIME_MIN_SECONDS -> Lang.get("gui.kve.event.cond.type.playtime_min_seconds");
            case ENTITY_HAS_ITEM_EQUIPPED -> Lang.get("gui.kve.event.cond.type.entity_has_item_equipped");
        };
    }

    private String gamemodeLabel(EventEntry.GameMode m) {
        return switch (m) {
            case SURVIVAL -> Lang.get("gui.kve.event.gamemode.survival");
            case CREATIVE -> Lang.get("gui.kve.event.gamemode.creative");
            case ADVENTURE -> Lang.get("gui.kve.event.gamemode.adventure");
            case SPECTATOR -> Lang.get("gui.kve.event.gamemode.spectator");
        };
    }

    private String timeOfDayLabel(EventEntry.TimeOfDay t) {
        return switch (t) {
            case DAY -> Lang.get("gui.kve.event.timeofday.day");
            case NIGHT -> Lang.get("gui.kve.event.timeofday.night");
        };
    }

    private void confirm() {
        EventEntry.Condition c = new EventEntry.Condition();
        c.type = draftType;
        c.dimensionId = draftDimensionId.trim();
        c.permissionNode = draftPermissionNode.trim();
        c.gamemode = draftGamemode;
        c.timeOfDay = draftTimeOfDay;
        c.yMin = draftYMin;
        c.yMax = draftYMax;
        c.biomeId = draftBiomeId.trim();
        c.xpLevelMin = draftXpLevelMin;
        c.hasItemId = draftHasItemId.trim();
        c.hasItemMinCount = draftHasItemMinCount;
        c.randomChance = draftRandomChance;
        c.healthMax = draftHealthMax;
        c.healthMin = draftHealthMin;
        c.foodLevelMin = draftFoodLevelMin;
        c.hasEffectId = draftHasEffectId.trim();
        c.equippedItemId = draftEquippedItemId.trim();
        c.equippedSlot = draftEquippedSlot;
        c.advancementId = draftAdvancementId.trim();
        c.entityEquippedItemId = draftEntityEquippedItemId.trim();
        c.weatherState = draftWeatherState;
        c.moonPhase = draftMoonPhase.trim();
        c.dayOfWeek = draftDayOfWeek;
        c.dateStartMd = draftDateStartMd.trim();
        c.dateEndMd = draftDateEndMd.trim();
        c.hourStart = draftHourStart;
        c.hourEnd = draftHourEnd;
        c.stageId = draftStageId.trim();
        c.flagId = draftFlagId.trim();
        c.playtimeMinSeconds = draftPlaytimeMinSeconds;
        onConfirm.accept(c);
        minecraft.setScreen(parent);
    }

    private int computeGuiH() {
        return GUI_H_MIN + extraRowOffsetFor(draftType);
    }

    private int extraRowOffsetFor(EventEntry.ConditionType t) {
        return switch (t) {
            case HAS_ITEM, HAS_ITEM_EQUIPPED, ENTITY_HAS_ITEM_EQUIPPED -> 20;
            default -> 0;
        };
    }

    private int parseInt(String s, int def) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; } }
    private double parseDouble(String s, double def) { try { return Double.parseDouble(s.trim()); } catch (Exception e) { return def; } }
    private String fmt(double v) { return (v == Math.floor(v)) ? String.valueOf((long) v) : String.valueOf(v); }

    private void pickConditionBiome() {
        minecraft.setScreen(new BiomePickerScreen(this,
                draftBiomeId.isEmpty() ? java.util.List.of() : java.util.List.of(draftBiomeId), true,
                picked -> { if (!picked.isEmpty()) draftBiomeId = picked.get(0); }));
    }

    private Button btn(String label, int x, int y, int w, Runnable a) {
        return KveGuiStyle.smartBtn(x, y, w, label, b -> a.run());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, Lang.get("gui.kve.event.cond.title"));
        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
        if (draftType == EventEntry.ConditionType.DIMENSION) {
            KveGuiStyle.helpIcon(g, L + GUI_W - 19, fieldRowY, mx, my, Lang.c("gui.kve.event.cond.desc.dimension"));
        } else if (draftType == EventEntry.ConditionType.PERMISSION) {
            KveGuiStyle.helpIcon(g, L + GUI_W - 19, fieldRowY, mx, my, Lang.c("gui.kve.event.cond.desc.permission"));
        } else if (draftType == EventEntry.ConditionType.RANDOM_CHANCE) {
            KveGuiStyle.helpIcon(g, L + GUI_W - 19, fieldRowY, mx, my, Lang.c("gui.kve.event.cond.random_chance_hint"));
        }
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override public boolean isPauseScreen() { return false; }
}