package com.kve.client.gui;

import com.kve.common.Lang;
import com.kve.common.event.EventEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Modal editor for a single {@link EventEntry.Action} - one of
 * {@code EventEditorScreen}'s "Acciones" list rows Acciones: lista ordenada + agregar/quitar/subir/bajar"). Same
 * shape as {@link EventConditionEditScreen}; the extra piece here is the
 * item/mob pickers for GIVE_ITEM/SPAWN_ENTITY, reusing {@link ItemPickerScreen} and
 * {@link MobPickerScreen} exactly like {@code TagEditorScreen#addPickerFor}
 * does.
 */
@OnlyIn(Dist.CLIENT)
public class EventActionEditScreen extends Screen {

    private static final int GUI_W = 280;

    private static final int GUI_H_MIN = 88;
    private int GUI_H = GUI_H_MIN;

    private final Screen parent;
    private final Consumer<EventEntry.Action> onConfirm;

    private EventEntry.ActionType draftType = EventEntry.ActionType.MESSAGE;
    private String draftText = "";
    private String draftTitle = "";
    private String draftSubtitle = "";
    private String draftItemId = "";
    private int draftItemCount = 1;
    private String draftNbt = "";
    private String draftCommand = "";
    private String draftSoundId = "";
    private float draftVolume = 1.0f;
    private float draftPitch = 1.0f;
    private String draftSpawnEntityId = "";
    private int draftSpawnCount = 1;
    private double draftSpawnRadius = 2.0;
    private int draftXpLevels = 1;
    private String draftEffectId = "";
    private int draftEffectDurationTicks = 200;
    private int draftEffectAmplifier = 0;
    private String draftTeleportDim = "";
    private double draftTeleportX = 0;
    private double draftTeleportY = 64;
    private double draftTeleportZ = 0;
    private float draftTeleportYaw = 0;
    private float draftTeleportPitch = 0;
    private int draftXpPoints = 1;
    private int draftSetXpLevel = 0;
    private String draftStageId = "";
    private boolean draftHealFull = true;
    private float draftHealAmount = 1.0f;
    private int draftSetFoodLevel = 20;
    private float draftSetFoodSaturation = 0.0f;
    private boolean draftSetFoodSaturationEnabled = false;
    private float draftSetHealthAmount = 20.0f;
    private String draftBossbarText = "";
    private EventEntry.BossbarColor draftBossbarColor = EventEntry.BossbarColor.PURPLE;
    private int draftBossbarSeconds = 10;
    private String draftParticleId = "";
    private int draftParticleCount = 10;
    private double draftParticleSpread = 0.5;
    private EventEntry.SetTimeMode draftSetTimeMode = EventEntry.SetTimeMode.DAY;
    private long draftSetTimeValue = 1000L;
    private EventEntry.WeatherState draftSetWeatherMode = EventEntry.WeatherState.CLEAR;
    private int draftSetWeatherDurationTicks = 0;
    private float draftExplosionPower = 2.0f;
    private String draftCommandAsPlayer = "";
    private double draftMoneyAmount = 1.0;
    private String draftCurrencyKey = "";
    private String draftCustomEventId = "";
    private String draftGrantAdvancementId = "";
    private String draftRemoveEffectId = "";
    private String draftSetSpawnDim = "";
    private double draftSetSpawnX = 0;
    private double draftSetSpawnY = 64;
    private double draftSetSpawnZ = 0;
    private String draftLootTableId = "";

    private int L, T;
    private int fieldRowY;

    public EventActionEditScreen(Screen parent, Consumer<EventEntry.Action> onConfirm) {
        super(Lang.c("gui.kve.event.action.title"));
        this.parent = parent;
        this.onConfirm = onConfirm;
    }

    /** Preloads an existing action's values for the "Edit" flow. */
    public EventActionEditScreen withExisting(EventEntry.Action a) {
        if (a != null) {
            draftType = a.type;
            draftText = nn(a.text);
            draftTitle = nn(a.title);
            draftSubtitle = nn(a.subtitle);
            draftItemId = nn(a.itemId);
            draftItemCount = a.itemCount;
            draftNbt = nn(a.nbtString);
            draftCommand = nn(a.command);
            draftSoundId = nn(a.soundId);
            draftVolume = a.volume;
            draftPitch = a.pitch;
            draftSpawnEntityId = nn(a.spawnEntityId);
            draftSpawnCount = a.spawnCount;
            draftSpawnRadius = a.spawnRadius;
            draftXpLevels = a.xpLevels;
            draftEffectId = nn(a.effectId);
            draftEffectDurationTicks = a.effectDurationTicks;
            draftEffectAmplifier = a.effectAmplifier;
            draftTeleportDim = nn(a.teleportDim);
            draftTeleportX = a.teleportX;
            draftTeleportY = a.teleportY;
            draftTeleportZ = a.teleportZ;
            draftTeleportYaw = a.teleportYaw;
            draftTeleportPitch = a.teleportPitch;
            draftXpPoints = a.xpPoints;
            draftSetXpLevel = a.setXpLevel;
            draftStageId = nn(a.stageId);
            draftHealFull = a.healFull;
            draftHealAmount = a.healAmount;
            draftSetFoodLevel = a.setFoodLevel;
            draftSetFoodSaturation = a.setFoodSaturation;
            draftSetFoodSaturationEnabled = a.setFoodSaturationEnabled;
            draftSetHealthAmount = a.setHealthAmount;
            draftBossbarText = nn(a.bossbarText);
            draftBossbarColor = a.bossbarColor;
            draftBossbarSeconds = a.bossbarSeconds;
            draftParticleId = nn(a.particleId);
            draftParticleCount = a.particleCount;
            draftParticleSpread = a.particleSpread;
            draftSetTimeMode = a.setTimeMode;
            draftSetTimeValue = a.setTimeValue;
            draftSetWeatherMode = a.setWeatherMode;
            draftSetWeatherDurationTicks = a.setWeatherDurationTicks;
            draftExplosionPower = a.explosionPower;
            draftCommandAsPlayer = nn(a.commandAsPlayer);
            draftMoneyAmount = a.moneyAmount;
            draftCurrencyKey = nn(a.currencyKey);
            draftCustomEventId = nn(a.customEventId);
            draftGrantAdvancementId = nn(a.grantAdvancementId);
            draftRemoveEffectId = nn(a.removeEffectId);
            draftSetSpawnDim = nn(a.setSpawnDim);
            draftSetSpawnX = a.setSpawnX;
            draftSetSpawnY = a.setSpawnY;
            draftSetSpawnZ = a.setSpawnZ;
            draftLootTableId = nn(a.lootTableId);
        }
        return this;
    }

    private static String nn(String s) { return s == null ? "" : s; }

    @Override
    protected void init() {
        GUI_H = computeGuiH();
        L = (width - GUI_W) / 2;
        T = (height - GUI_H) / 2;
        clearWidgets();
        setFocused(null);

        addRenderableWidget(btn(Lang.get("gui.kve.event.action.type_btn", typeLabel(draftType)), L + 10, T + 20, 0, this::openTypePicker));

        fieldRowY = T + 42;
        int fy = fieldRowY;
        switch (draftType) {
            case MESSAGE -> {
                EditBox f = field(L + 10, fy, GUI_W - 20, draftText, Lang.c("gui.kve.event.action.text"));
                f.setResponder(s -> draftText = s);
                addRenderableWidget(f);
                setInitialFocus(f);
            }
            case TITLE -> {
                EditBox f1 = field(L + 10, fy, GUI_W - 20, draftTitle, Lang.c("gui.kve.event.action.title_field"));
                f1.setResponder(s -> draftTitle = s);
                addRenderableWidget(f1);
                setInitialFocus(f1);
                EditBox f2 = field(L + 10, fy + 20, GUI_W - 20, draftSubtitle, Lang.c("gui.kve.event.action.subtitle"));
                f2.setResponder(s -> draftSubtitle = s);
                addRenderableWidget(f2);
            }
            case GIVE_ITEM -> {
                EditBox f1 = field(L + 10, fy, 150, draftItemId, Lang.c("gui.kve.event.action.item_id"));
                f1.setHint(Lang.c("gui.kve.tag.id_hint"));
                f1.setResponder(s -> draftItemId = s);
                addRenderableWidget(f1);
                setInitialFocus(f1);
                addRenderableWidget(btn(Lang.get("gui.kve.event.action.pick_item"), L + 164, fy, 0, this::pickItem));

                EditBox f2 = field(L + 10, fy + 20, 60, String.valueOf(draftItemCount), Lang.c("gui.kve.event.action.count"));
                f2.setResponder(s -> draftItemCount = parseInt(s, draftItemCount));
                addRenderableWidget(f2);

                EditBox f3 = field(L + 74, fy + 20, GUI_W - 84, draftNbt, Lang.c("gui.kve.event.action.nbt"));
                f3.setHint(Lang.c("gui.kve.event.action.nbt_hint"));
                f3.setResponder(s -> draftNbt = s);
                addRenderableWidget(f3);
            }
            case RUN_COMMAND -> {
                EditBox f = field(L + 10, fy, GUI_W - 20, draftCommand, Lang.c("gui.kve.event.action.command"));
                f.setMaxLength(256);
                f.setHint(Lang.c("gui.kve.event.action.command_hint"));
                f.setResponder(s -> draftCommand = s);
                addRenderableWidget(f);
                setInitialFocus(f);
            }
            case PLAY_SOUND -> {
                EditBox f1 = field(L + 10, fy, GUI_W - 20, draftSoundId, Lang.c("gui.kve.event.action.sound_id"));
                f1.setHint(Lang.c("gui.kve.tag.id_hint"));
                f1.setResponder(s -> draftSoundId = s);
                addRenderableWidget(f1);
                setInitialFocus(f1);

                EditBox f2 = field(L + 10, fy + 20, 90, fmt(draftVolume), Lang.c("gui.kve.event.action.volume"));
                f2.setResponder(s -> draftVolume = parseFloat(s, draftVolume));
                addRenderableWidget(f2);

                EditBox f3 = field(L + 104, fy + 20, 90, fmt(draftPitch), Lang.c("gui.kve.event.action.pitch"));
                f3.setResponder(s -> draftPitch = parseFloat(s, draftPitch));
                addRenderableWidget(f3);
            }
            case SPAWN_ENTITY -> {
                EditBox f1 = field(L + 10, fy, 150, draftSpawnEntityId, Lang.c("gui.kve.event.action.entity_id"));
                f1.setHint(Lang.c("gui.kve.tag.id_hint"));
                f1.setResponder(s -> draftSpawnEntityId = s);
                addRenderableWidget(f1);
                setInitialFocus(f1);
                addRenderableWidget(btn(Lang.get("gui.kve.event.action.pick_mob"), L + 164, fy, 0, this::pickMob));

                EditBox f2 = field(L + 10, fy + 20, 90, String.valueOf(draftSpawnCount), Lang.c("gui.kve.event.action.count"));
                f2.setResponder(s -> draftSpawnCount = parseInt(s, draftSpawnCount));
                addRenderableWidget(f2);

                EditBox f3 = field(L + 104, fy + 20, 90, fmt(draftSpawnRadius), Lang.c("gui.kve.event.action.radius"));
                f3.setResponder(s -> draftSpawnRadius = parseDouble(s, draftSpawnRadius));
                addRenderableWidget(f3);
            }
            case ACTIONBAR -> {
                EditBox f = field(L + 10, fy, GUI_W - 20, draftText, Lang.c("gui.kve.event.action.text"));
                f.setResponder(s -> draftText = s);
                addRenderableWidget(f);
                setInitialFocus(f);
            }
            case TAKE_ITEM -> {
                EditBox f1 = field(L + 10, fy, 150, draftItemId, Lang.c("gui.kve.event.action.item_id"));
                f1.setHint(Lang.c("gui.kve.tag.id_hint"));
                f1.setResponder(s -> draftItemId = s);
                addRenderableWidget(f1);
                setInitialFocus(f1);
                addRenderableWidget(btn(Lang.get("gui.kve.event.action.pick_item"), L + 164, fy, 0, this::pickItem));

                EditBox f2 = field(L + 10, fy + 20, 60, String.valueOf(draftItemCount), Lang.c("gui.kve.event.action.count"));
                f2.setResponder(s -> draftItemCount = parseInt(s, draftItemCount));
                addRenderableWidget(f2);
            }
            case GIVE_XP_LEVELS -> {
                EditBox f = field(L + 10, fy, GUI_W - 20, String.valueOf(draftXpLevels), Lang.c("gui.kve.event.action.xp_levels"));
                f.setResponder(s -> draftXpLevels = parseInt(s, draftXpLevels));
                addRenderableWidget(f);
                setInitialFocus(f);
            }
            case ADD_EFFECT -> {
                EditBox f1 = field(L + 10, fy, GUI_W - 20 - 40, draftEffectId, Lang.c("gui.kve.event.action.effect_id"));
                f1.setHint(Lang.c("gui.kve.tag.id_hint"));
                f1.setResponder(s -> draftEffectId = s);
                addRenderableWidget(f1);
                setInitialFocus(f1);
                addRenderableWidget(btn("...", L + 10 + GUI_W - 20 - 38, fy, 0, this::pickAddEffect));

                EditBox f2 = field(L + 10, fy + 20, 130, String.valueOf(draftEffectDurationTicks), Lang.c("gui.kve.event.action.effect_duration_ticks"));
                f2.setResponder(s -> draftEffectDurationTicks = parseInt(s, draftEffectDurationTicks));
                addRenderableWidget(f2);

                EditBox f3 = field(L + 144, fy + 20, GUI_W - 154, String.valueOf(draftEffectAmplifier), Lang.c("gui.kve.event.action.effect_amplifier"));
                f3.setResponder(s -> draftEffectAmplifier = parseInt(s, draftEffectAmplifier));
                addRenderableWidget(f3);
            }
            case TELEPORT -> {
                EditBox f0 = field(L + 10, fy, GUI_W - 20, draftTeleportDim, Lang.c("gui.kve.event.action.teleport_dim"));
                f0.setHint(Lang.c("gui.kve.event.action.teleport_dim_hint"));
                f0.setResponder(s -> draftTeleportDim = s);
                addRenderableWidget(f0);
                setInitialFocus(f0);

                int colW = (GUI_W - 20 - 4) / 3;
                EditBox fx = field(L + 10, fy + 20, colW, fmt(draftTeleportX), Lang.c("gui.kve.event.action.teleport_x"));
                fx.setResponder(s -> draftTeleportX = parseDouble(s, draftTeleportX));
                addRenderableWidget(fx);
                EditBox fy2 = field(L + 10 + colW + 2, fy + 20, colW, fmt(draftTeleportY), Lang.c("gui.kve.event.action.teleport_y"));
                fy2.setResponder(s -> draftTeleportY = parseDouble(s, draftTeleportY));
                addRenderableWidget(fy2);
                EditBox fz = field(L + 10 + 2 * (colW + 2), fy + 20, colW, fmt(draftTeleportZ), Lang.c("gui.kve.event.action.teleport_z"));
                fz.setResponder(s -> draftTeleportZ = parseDouble(s, draftTeleportZ));
                addRenderableWidget(fz);

                EditBox fyaw = field(L + 10, fy + 40, 90, fmt(draftTeleportYaw), Lang.c("gui.kve.event.action.teleport_yaw"));
                fyaw.setResponder(s -> draftTeleportYaw = parseFloat(s, draftTeleportYaw));
                addRenderableWidget(fyaw);
                EditBox fpitch = field(L + 104, fy + 40, 90, fmt(draftTeleportPitch), Lang.c("gui.kve.event.action.teleport_pitch"));
                fpitch.setResponder(s -> draftTeleportPitch = parseFloat(s, draftTeleportPitch));
                addRenderableWidget(fpitch);
            }
            case TELEPORT_SPAWN -> { }
            case CLEAR_ITEM -> {
                EditBox f1 = field(L + 10, fy, GUI_W - 20, draftItemId, Lang.c("gui.kve.event.action.item_id"));
                f1.setHint(Lang.c("gui.kve.tag.id_hint"));
                f1.setResponder(s -> draftItemId = s);
                addRenderableWidget(f1);
                setInitialFocus(f1);
                addRenderableWidget(btn(Lang.get("gui.kve.event.action.pick_item"), L + 10, fy + 20, 0, this::pickItem));
            }
            case GIVE_XP_POINTS -> {
                EditBox f = field(L + 10, fy, GUI_W - 20, String.valueOf(draftXpPoints), Lang.c("gui.kve.event.action.xp_points"));
                f.setResponder(s -> draftXpPoints = parseInt(s, draftXpPoints));
                addRenderableWidget(f);
                setInitialFocus(f);
            }
            case SET_XP_LEVEL -> {
                EditBox f = field(L + 10, fy, GUI_W - 20, String.valueOf(draftSetXpLevel), Lang.c("gui.kve.event.action.set_xp_level"));
                f.setResponder(s -> draftSetXpLevel = parseInt(s, draftSetXpLevel));
                addRenderableWidget(f);
                setInitialFocus(f);
            }
            case ADD_STAGE, REMOVE_STAGE -> {
                EditBox f = field(L + 10, fy, GUI_W - 20, draftStageId, Lang.c("gui.kve.event.action.stage_id"));
                f.setResponder(s -> draftStageId = s);
                addRenderableWidget(f);
                setInitialFocus(f);
            }
            case HEAL -> {
                addRenderableWidget(btn(draftHealFull ? Lang.get("gui.kve.event.action.heal_full_on") : Lang.get("gui.kve.event.action.heal_full_off"),
                        L + 10, fy, 0, this::toggleHealFull));
                if (!draftHealFull) {
                    EditBox f = field(L + 10, fy + 20, GUI_W - 20, fmt(draftHealAmount), Lang.c("gui.kve.event.action.heal_amount"));
                    f.setResponder(s -> draftHealAmount = parseFloat(s, draftHealAmount));
                    addRenderableWidget(f);
                    setInitialFocus(f);
                }
            }
            case SET_FOOD -> {
                EditBox f1 = field(L + 10, fy, 90, String.valueOf(draftSetFoodLevel), Lang.c("gui.kve.event.action.set_food_level"));
                f1.setResponder(s -> draftSetFoodLevel = parseInt(s, draftSetFoodLevel));
                addRenderableWidget(f1);
                setInitialFocus(f1);
                addRenderableWidget(btn(draftSetFoodSaturationEnabled ? Lang.get("gui.kve.event.action.set_food_saturation_on") : Lang.get("gui.kve.event.action.set_food_saturation_off"),
                        L + 104, fy, 0, this::toggleSetFoodSaturationEnabled));
                if (draftSetFoodSaturationEnabled) {
                    EditBox f2 = field(L + 10, fy + 20, GUI_W - 20, fmt(draftSetFoodSaturation), Lang.c("gui.kve.event.action.set_food_saturation"));
                    f2.setResponder(s -> draftSetFoodSaturation = parseFloat(s, draftSetFoodSaturation));
                    addRenderableWidget(f2);
                }
            }
            case SET_HEALTH -> {
                EditBox f = field(L + 10, fy, GUI_W - 20, fmt(draftSetHealthAmount), Lang.c("gui.kve.event.action.set_health_amount"));
                f.setResponder(s -> draftSetHealthAmount = parseFloat(s, draftSetHealthAmount));
                addRenderableWidget(f);
                setInitialFocus(f);
            }
            case CLEAR_EFFECTS -> { }
            case BOSSBAR_SHOW -> {
                EditBox f1 = field(L + 10, fy, GUI_W - 20, draftBossbarText, Lang.c("gui.kve.event.action.bossbar_text"));
                f1.setResponder(s -> draftBossbarText = s);
                addRenderableWidget(f1);
                setInitialFocus(f1);
                addRenderableWidget(btn(bossbarColorLabel(draftBossbarColor), L + 10, fy + 20, 0, this::openBossbarColorPicker));
                EditBox f2 = field(L + 144, fy + 20, GUI_W - 154, String.valueOf(draftBossbarSeconds), Lang.c("gui.kve.event.action.bossbar_seconds"));
                f2.setResponder(s -> draftBossbarSeconds = parseInt(s, draftBossbarSeconds));
                addRenderableWidget(f2);
            }
            case BOSSBAR_CLEAR -> { }
            case PARTICLES -> {
                EditBox f1 = field(L + 10, fy, GUI_W - 20, draftParticleId, Lang.c("gui.kve.event.action.particle_id"));
                f1.setHint(Lang.c("gui.kve.tag.id_hint"));
                f1.setResponder(s -> draftParticleId = s);
                addRenderableWidget(f1);
                setInitialFocus(f1);
                EditBox f2 = field(L + 10, fy + 20, 90, String.valueOf(draftParticleCount), Lang.c("gui.kve.event.action.particle_count"));
                f2.setResponder(s -> draftParticleCount = parseInt(s, draftParticleCount));
                addRenderableWidget(f2);
                EditBox f3 = field(L + 104, fy + 20, 90, fmt(draftParticleSpread), Lang.c("gui.kve.event.action.particle_spread"));
                f3.setResponder(s -> draftParticleSpread = parseDouble(s, draftParticleSpread));
                addRenderableWidget(f3);
            }
            case SET_TIME -> {
                addRenderableWidget(btn(setTimeModeLabel(draftSetTimeMode), L + 10, fy, 0, this::openSetTimeModePicker));
                if (draftSetTimeMode == EventEntry.SetTimeMode.VALUE) {
                    EditBox f = field(L + 10, fy + 20, GUI_W - 20, String.valueOf(draftSetTimeValue), Lang.c("gui.kve.event.action.set_time_value"));
                    f.setResponder(s -> draftSetTimeValue = parseInt(s, (int) draftSetTimeValue));
                    addRenderableWidget(f);
                }
            }
            case SET_WEATHER -> {
                addRenderableWidget(btn(weatherLabel(draftSetWeatherMode), L + 10, fy, 0, this::openSetWeatherPicker));
                EditBox f = field(L + 10, fy + 20, GUI_W - 20, String.valueOf(draftSetWeatherDurationTicks), Lang.c("gui.kve.event.action.set_weather_duration"));
                f.setHint(Lang.c("gui.kve.event.action.set_weather_duration_hint"));
                f.setResponder(s -> draftSetWeatherDurationTicks = parseInt(s, draftSetWeatherDurationTicks));
                addRenderableWidget(f);
            }
            case EXPLOSION_HARMLESS -> {
                EditBox f = field(L + 10, fy, GUI_W - 20, fmt(draftExplosionPower), Lang.c("gui.kve.event.action.explosion_power"));
                f.setResponder(s -> draftExplosionPower = parseFloat(s, draftExplosionPower));
                addRenderableWidget(f);
                setInitialFocus(f);
            }
            case RUN_COMMAND_AS_PLAYER -> {
                EditBox f = field(L + 10, fy, GUI_W - 20, draftCommandAsPlayer, Lang.c("gui.kve.event.action.command_as_player"));
                f.setMaxLength(256);
                f.setHint(Lang.c("gui.kve.event.action.command_hint"));
                f.setResponder(s -> draftCommandAsPlayer = s);
                addRenderableWidget(f);
                setInitialFocus(f);
            }
            case GIVE_MONEY, TAKE_MONEY -> {
                EditBox f1 = field(L + 10, fy, 90, fmt(draftMoneyAmount), Lang.c("gui.kve.event.action.money_amount"));
                f1.setResponder(s -> draftMoneyAmount = parseDouble(s, draftMoneyAmount));
                addRenderableWidget(f1);
                setInitialFocus(f1);
                EditBox f2 = field(L + 104, fy, GUI_W - 114, draftCurrencyKey, Lang.c("gui.kve.event.action.currency_key"));
                f2.setHint(Lang.c("gui.kve.event.action.currency_key_hint"));
                f2.setResponder(s -> draftCurrencyKey = s);
                addRenderableWidget(f2);
            }
            case FIRE_CUSTOM_EVENT -> {
                EditBox f = field(L + 10, fy, GUI_W - 20, draftCustomEventId, Lang.c("gui.kve.event.action.custom_event_id"));
                f.setResponder(s -> draftCustomEventId = s);
                addRenderableWidget(f);
                setInitialFocus(f);
            }
            case CLEAR_TITLE -> { }
            case GRANT_ADVANCEMENT -> {
                EditBox f = field(L + 10, fy, GUI_W - 20, draftGrantAdvancementId, Lang.c("gui.kve.event.action.grant_advancement_id"));
                f.setMaxLength(64);
                f.setHint(Lang.c("gui.kve.tag.id_hint"));
                f.setResponder(s -> draftGrantAdvancementId = s);
                addRenderableWidget(f);
                setInitialFocus(f);
            }
            case REMOVE_EFFECT -> {
                EditBox f = field(L + 10, fy, GUI_W - 20 - 40, draftRemoveEffectId, Lang.c("gui.kve.event.action.remove_effect_id"));
                f.setMaxLength(64);
                f.setHint(Lang.c("gui.kve.tag.id_hint"));
                f.setResponder(s -> draftRemoveEffectId = s);
                addRenderableWidget(f);
                setInitialFocus(f);
                addRenderableWidget(btn("...", L + 10 + GUI_W - 20 - 38, fy, 0, this::pickRemoveEffect));
            }
            case SET_SPAWN -> {
                EditBox f0 = field(L + 10, fy, GUI_W - 20, draftSetSpawnDim, Lang.c("gui.kve.event.action.set_spawn_dim"));
                f0.setHint(Lang.c("gui.kve.event.action.teleport_dim_hint"));
                f0.setResponder(s -> draftSetSpawnDim = s);
                addRenderableWidget(f0);
                setInitialFocus(f0);

                int colW = (GUI_W - 20 - 4) / 3;
                EditBox fx = field(L + 10, fy + 20, colW, fmt(draftSetSpawnX), Lang.c("gui.kve.event.action.teleport_x"));
                fx.setResponder(s -> draftSetSpawnX = parseDouble(s, draftSetSpawnX));
                addRenderableWidget(fx);
                EditBox fy2 = field(L + 10 + colW + 2, fy + 20, colW, fmt(draftSetSpawnY), Lang.c("gui.kve.event.action.teleport_y"));
                fy2.setResponder(s -> draftSetSpawnY = parseDouble(s, draftSetSpawnY));
                addRenderableWidget(fy2);
                EditBox fz = field(L + 10 + 2 * (colW + 2), fy + 20, colW, fmt(draftSetSpawnZ), Lang.c("gui.kve.event.action.teleport_z"));
                fz.setResponder(s -> draftSetSpawnZ = parseDouble(s, draftSetSpawnZ));
                addRenderableWidget(fz);
            }
            case LIGHTNING -> { }
            case APPLY_LOOT_TABLE -> {
                EditBox f1 = field(L + 10, fy, GUI_W - 20 - 40, draftLootTableId, Lang.c("gui.kve.event.action.loot_table_id"));
                f1.setHint(Lang.c("gui.kve.tag.id_hint"));
                f1.setResponder(s -> draftLootTableId = s);
                addRenderableWidget(f1);
                setInitialFocus(f1);
                addRenderableWidget(btn("...", L + 10 + GUI_W - 20 - 38, fy, 0, this::pickLootTable));
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

    private int computeGuiH() {
        return GUI_H_MIN + extraRowOffsetFor(draftType);
    }

    private int extraRowOffsetFor(EventEntry.ActionType t) {
        return switch (t) {
            case TITLE, GIVE_ITEM, PLAY_SOUND, SPAWN_ENTITY, TAKE_ITEM, ADD_EFFECT,
                 CLEAR_ITEM, BOSSBAR_SHOW, PARTICLES, SET_WEATHER, SET_SPAWN -> 20;
            case TELEPORT -> 40;
            case HEAL -> draftHealFull ? 0 : 20;
            case SET_FOOD -> draftSetFoodSaturationEnabled ? 20 : 0;
            case SET_TIME -> draftSetTimeMode == EventEntry.SetTimeMode.VALUE ? 20 : 0;
            default -> 0;
        };
    }

    private EditBox field(int x, int y, int w, String value, Component narration) {
        EditBox f = new KveScaledEditBox(font, x, y, w, 14, narration);
        f.setMaxLength(128);
        f.setValue(value);
        return f;
    }

    private void pickItem() {
        minecraft.setScreen(new ItemPickerScreen(this, (ItemStack stack) -> {
            String id = idOf(stack);
            if (id != null) draftItemId = id;
        }));
    }

    private void pickMob() {
        minecraft.setScreen(new MobPickerScreen(this, et -> {
            ResourceLocation k = BuiltInRegistries.ENTITY_TYPE.getKey(et);
            if (k != null) draftSpawnEntityId = k.toString();
        }));
    }

    private void pickAddEffect() {
        minecraft.setScreen(new EffectPickerScreen(this, id -> draftEffectId = id));
    }

    private void pickRemoveEffect() {
        minecraft.setScreen(new EffectPickerScreen(this, id -> draftRemoveEffectId = id));
    }

    private void pickLootTable() {
        minecraft.setScreen(new LootTablePickerScreen(this,
                com.kve.common.kubejs.LootTableExporter.listAllLootTableIds(),
                id -> draftLootTableId = id));
    }

    private void toggleHealFull() { draftHealFull = !draftHealFull; init(); }
    private void toggleSetFoodSaturationEnabled() { draftSetFoodSaturationEnabled = !draftSetFoodSaturationEnabled; init(); }

    private void openBossbarColorPicker() {
        List<TypeOptionPickerScreen.Option> opts = new ArrayList<>();
        for (EventEntry.BossbarColor c : EventEntry.BossbarColor.values()) {
            opts.add(new TypeOptionPickerScreen.Option(bossbarColorLabel(c), "", () -> {
                draftBossbarColor = c;
                minecraft.setScreen(this);
                init();
            }));
        }
        minecraft.setScreen(new TypeOptionPickerScreen(this, Lang.get("gui.kve.event.action.bossbar_color"), opts, draftBossbarColor.ordinal()));
    }

    private void openSetTimeModePicker() {
        List<TypeOptionPickerScreen.Option> opts = new ArrayList<>();
        for (EventEntry.SetTimeMode m : EventEntry.SetTimeMode.values()) {
            opts.add(new TypeOptionPickerScreen.Option(setTimeModeLabel(m), "", () -> {
                draftSetTimeMode = m;
                minecraft.setScreen(this);
                init();
            }));
        }
        minecraft.setScreen(new TypeOptionPickerScreen(this, Lang.get("gui.kve.event.action.set_time_mode"), opts, draftSetTimeMode.ordinal()));
    }

    private void openSetWeatherPicker() {
        List<TypeOptionPickerScreen.Option> opts = new ArrayList<>();
        for (EventEntry.WeatherState w : EventEntry.WeatherState.values()) {
            opts.add(new TypeOptionPickerScreen.Option(weatherLabel(w), "", () -> {
                draftSetWeatherMode = w;
                minecraft.setScreen(this);
                init();
            }));
        }
        minecraft.setScreen(new TypeOptionPickerScreen(this, Lang.get("gui.kve.event.action.set_weather_mode"), opts, draftSetWeatherMode.ordinal()));
    }

    private String bossbarColorLabel(EventEntry.BossbarColor c) {
        return switch (c) {
            case PINK -> Lang.get("gui.kve.event.bossbarcolor.pink");
            case BLUE -> Lang.get("gui.kve.event.bossbarcolor.blue");
            case RED -> Lang.get("gui.kve.event.bossbarcolor.red");
            case GREEN -> Lang.get("gui.kve.event.bossbarcolor.green");
            case YELLOW -> Lang.get("gui.kve.event.bossbarcolor.yellow");
            case PURPLE -> Lang.get("gui.kve.event.bossbarcolor.purple");
            case WHITE -> Lang.get("gui.kve.event.bossbarcolor.white");
        };
    }

    private String setTimeModeLabel(EventEntry.SetTimeMode m) {
        return switch (m) {
            case VALUE -> Lang.get("gui.kve.event.settimemode.value");
            case DAY -> Lang.get("gui.kve.event.settimemode.day");
            case NIGHT -> Lang.get("gui.kve.event.settimemode.night");
        };
    }

    private String weatherLabel(EventEntry.WeatherState w) {
        return switch (w) {
            case CLEAR -> Lang.get("gui.kve.event.weather.clear");
            case RAIN -> Lang.get("gui.kve.event.weather.rain");
            case THUNDER -> Lang.get("gui.kve.event.weather.thunder");
        };
    }

    private String idOf(ItemStack stack) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key != null ? key.toString() : null;
    }

    private void openTypePicker() {
        List<TypeOptionPickerScreen.Option> opts = new ArrayList<>();
        EventEntry.ActionType[] values = EventEntry.ActionType.values();
        for (EventEntry.ActionType t : values) {
            opts.add(new TypeOptionPickerScreen.Option(typeLabel(t), typeDesc(t), () -> {
                draftType = t;
                minecraft.setScreen(this);
                init();
            }));
        }
        minecraft.setScreen(new TypeOptionPickerScreen(this, Lang.get("gui.kve.event.action.pick_title"), opts, draftType.ordinal()));
    }

    private String typeLabel(EventEntry.ActionType t) {
        return switch (t) {
            case MESSAGE -> Lang.get("gui.kve.event.action.type.message");
            case TITLE -> Lang.get("gui.kve.event.action.type.title");
            case GIVE_ITEM -> Lang.get("gui.kve.event.action.type.give_item");
            case RUN_COMMAND -> Lang.get("gui.kve.event.action.type.run_command_advanced");
            case PLAY_SOUND -> Lang.get("gui.kve.event.action.type.play_sound");
            case SPAWN_ENTITY -> Lang.get("gui.kve.event.action.type.spawn_entity");
            case ACTIONBAR -> Lang.get("gui.kve.event.action.type.actionbar");
            case TAKE_ITEM -> Lang.get("gui.kve.event.action.type.take_item");
            case GIVE_XP_LEVELS -> Lang.get("gui.kve.event.action.type.give_xp_levels");
            case ADD_EFFECT -> Lang.get("gui.kve.event.action.type.add_effect");
            case TELEPORT -> Lang.get("gui.kve.event.action.type.teleport");
            case TELEPORT_SPAWN -> Lang.get("gui.kve.event.action.type.teleport_spawn");
            case CLEAR_ITEM -> Lang.get("gui.kve.event.action.type.clear_item");
            case GIVE_XP_POINTS -> Lang.get("gui.kve.event.action.type.give_xp_points");
            case SET_XP_LEVEL -> Lang.get("gui.kve.event.action.type.set_xp_level");
            case ADD_STAGE -> Lang.get("gui.kve.event.action.type.add_stage");
            case REMOVE_STAGE -> Lang.get("gui.kve.event.action.type.remove_stage");
            case HEAL -> Lang.get("gui.kve.event.action.type.heal");
            case SET_FOOD -> Lang.get("gui.kve.event.action.type.set_food");
            case SET_HEALTH -> Lang.get("gui.kve.event.action.type.set_health");
            case CLEAR_EFFECTS -> Lang.get("gui.kve.event.action.type.clear_effects");
            case BOSSBAR_SHOW -> Lang.get("gui.kve.event.action.type.bossbar_show");
            case BOSSBAR_CLEAR -> Lang.get("gui.kve.event.action.type.bossbar_clear");
            case PARTICLES -> Lang.get("gui.kve.event.action.type.particles");
            case SET_TIME -> Lang.get("gui.kve.event.action.type.set_time");
            case SET_WEATHER -> Lang.get("gui.kve.event.action.type.set_weather");
            case EXPLOSION_HARMLESS -> Lang.get("gui.kve.event.action.type.explosion_harmless");
            case RUN_COMMAND_AS_PLAYER -> Lang.get("gui.kve.event.action.type.run_command_as_player_advanced");
            case GIVE_MONEY -> Lang.get("gui.kve.event.action.type.give_money");
            case TAKE_MONEY -> Lang.get("gui.kve.event.action.type.take_money");
            case FIRE_CUSTOM_EVENT -> Lang.get("gui.kve.event.action.type.fire_custom_event");
            case CLEAR_TITLE -> Lang.get("gui.kve.event.action.type.clear_title");
            case GRANT_ADVANCEMENT -> Lang.get("gui.kve.event.action.type.grant_advancement");
            case REMOVE_EFFECT -> Lang.get("gui.kve.event.action.type.remove_effect");
            case SET_SPAWN -> Lang.get("gui.kve.event.action.type.set_spawn");
            case LIGHTNING -> Lang.get("gui.kve.event.action.type.lightning");
            case APPLY_LOOT_TABLE -> Lang.get("gui.kve.event.action.type.apply_loot_table");
        };
    }

    private String typeDesc(EventEntry.ActionType t) {
        return switch (t) {
            case MESSAGE -> Lang.get("gui.kve.event.action.desc.message");
            case TITLE -> Lang.get("gui.kve.event.action.desc.title");
            case GIVE_ITEM -> Lang.get("gui.kve.event.action.desc.give_item");
            case RUN_COMMAND -> Lang.get("gui.kve.event.action.desc.run_command");
            case PLAY_SOUND -> Lang.get("gui.kve.event.action.desc.play_sound");
            case SPAWN_ENTITY -> Lang.get("gui.kve.event.action.desc.spawn_entity");
            case ACTIONBAR -> Lang.get("gui.kve.event.action.desc.actionbar");
            case TAKE_ITEM -> Lang.get("gui.kve.event.action.desc.take_item");
            case GIVE_XP_LEVELS -> Lang.get("gui.kve.event.action.desc.give_xp_levels");
            case ADD_EFFECT -> Lang.get("gui.kve.event.action.desc.add_effect");
            case TELEPORT -> Lang.get("gui.kve.event.action.desc.teleport");
            case TELEPORT_SPAWN -> Lang.get("gui.kve.event.action.desc.teleport_spawn");
            case CLEAR_ITEM -> Lang.get("gui.kve.event.action.desc.clear_item");
            case GIVE_XP_POINTS -> Lang.get("gui.kve.event.action.desc.give_xp_points");
            case SET_XP_LEVEL -> Lang.get("gui.kve.event.action.desc.set_xp_level");
            case ADD_STAGE -> Lang.get("gui.kve.event.action.desc.add_stage");
            case REMOVE_STAGE -> Lang.get("gui.kve.event.action.desc.remove_stage");
            case HEAL -> Lang.get("gui.kve.event.action.desc.heal");
            case SET_FOOD -> Lang.get("gui.kve.event.action.desc.set_food");
            case SET_HEALTH -> Lang.get("gui.kve.event.action.desc.set_health");
            case CLEAR_EFFECTS -> Lang.get("gui.kve.event.action.desc.clear_effects");
            case BOSSBAR_SHOW -> Lang.get("gui.kve.event.action.desc.bossbar_show");
            case BOSSBAR_CLEAR -> Lang.get("gui.kve.event.action.desc.bossbar_clear");
            case PARTICLES -> Lang.get("gui.kve.event.action.desc.particles");
            case SET_TIME -> Lang.get("gui.kve.event.action.desc.set_time");
            case SET_WEATHER -> Lang.get("gui.kve.event.action.desc.set_weather");
            case EXPLOSION_HARMLESS -> Lang.get("gui.kve.event.action.desc.explosion_harmless");
            case RUN_COMMAND_AS_PLAYER -> Lang.get("gui.kve.event.action.desc.run_command_as_player_warning");
            case GIVE_MONEY -> Lang.get("gui.kve.event.action.desc.give_money");
            case TAKE_MONEY -> Lang.get("gui.kve.event.action.desc.take_money");
            case FIRE_CUSTOM_EVENT -> Lang.get("gui.kve.event.action.desc.fire_custom_event");
            case CLEAR_TITLE -> Lang.get("gui.kve.event.action.desc.clear_title");
            case GRANT_ADVANCEMENT -> Lang.get("gui.kve.event.action.desc.grant_advancement");
            case REMOVE_EFFECT -> Lang.get("gui.kve.event.action.desc.remove_effect");
            case SET_SPAWN -> Lang.get("gui.kve.event.action.desc.set_spawn");
            case LIGHTNING -> Lang.get("gui.kve.event.action.desc.lightning");
            case APPLY_LOOT_TABLE -> Lang.get("gui.kve.event.action.desc.apply_loot_table");
        };
    }

    private void confirm() {
        EventEntry.Action a = new EventEntry.Action();
        a.type = draftType;
        a.text = draftText.trim();
        a.title = draftTitle.trim();
        a.subtitle = draftSubtitle.trim();
        a.itemId = draftItemId.trim();
        a.itemCount = draftItemCount;
        a.nbtString = draftNbt.trim();
        a.command = draftCommand.trim();
        a.soundId = draftSoundId.trim();
        a.volume = draftVolume;
        a.pitch = draftPitch;
        a.spawnEntityId = draftSpawnEntityId.trim();
        a.spawnCount = draftSpawnCount;
        a.spawnRadius = draftSpawnRadius;
        a.xpLevels = draftXpLevels;
        a.effectId = draftEffectId.trim();
        a.effectDurationTicks = draftEffectDurationTicks;
        a.effectAmplifier = draftEffectAmplifier;
        a.teleportDim = draftTeleportDim.trim();
        a.teleportX = draftTeleportX;
        a.teleportY = draftTeleportY;
        a.teleportZ = draftTeleportZ;
        a.teleportYaw = draftTeleportYaw;
        a.teleportPitch = draftTeleportPitch;
        a.xpPoints = draftXpPoints;
        a.setXpLevel = draftSetXpLevel;
        a.stageId = draftStageId.trim();
        a.healFull = draftHealFull;
        a.healAmount = draftHealAmount;
        a.setFoodLevel = draftSetFoodLevel;
        a.setFoodSaturation = draftSetFoodSaturation;
        a.setFoodSaturationEnabled = draftSetFoodSaturationEnabled;
        a.setHealthAmount = draftSetHealthAmount;
        a.bossbarText = draftBossbarText.trim();
        a.bossbarColor = draftBossbarColor;
        a.bossbarSeconds = draftBossbarSeconds;
        a.particleId = draftParticleId.trim();
        a.particleCount = draftParticleCount;
        a.particleSpread = draftParticleSpread;
        a.setTimeMode = draftSetTimeMode;
        a.setTimeValue = draftSetTimeValue;
        a.setWeatherMode = draftSetWeatherMode;
        a.setWeatherDurationTicks = draftSetWeatherDurationTicks;
        a.explosionPower = draftExplosionPower;
        a.commandAsPlayer = draftCommandAsPlayer.trim();
        a.moneyAmount = draftMoneyAmount;
        a.currencyKey = draftCurrencyKey.trim();
        a.customEventId = draftCustomEventId.trim();
        a.grantAdvancementId = draftGrantAdvancementId.trim();
        a.removeEffectId = draftRemoveEffectId.trim();
        a.setSpawnDim = draftSetSpawnDim.trim();
        a.setSpawnX = draftSetSpawnX;
        a.setSpawnY = draftSetSpawnY;
        a.setSpawnZ = draftSetSpawnZ;
        a.lootTableId = draftLootTableId.trim();
        onConfirm.accept(a);
        minecraft.setScreen(parent);
    }

    private int parseInt(String s, int def) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; } }
    private float parseFloat(String s, float def) { try { return Float.parseFloat(s.trim()); } catch (Exception e) { return def; } }
    private double parseDouble(String s, double def) { try { return Double.parseDouble(s.trim()); } catch (Exception e) { return def; } }
    private String fmt(double v) { return (v == Math.floor(v)) ? String.valueOf((long) v) : String.valueOf(v); }

    private Button btn(String label, int x, int y, int w, Runnable a) {
        return KveGuiStyle.smartBtn(x, y, w, label, b -> a.run());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, Lang.get("gui.kve.event.action.title"));
        super.render(g, mx, my, pt);
        drawFieldHelpIcons(g, mx, my);
    }

    private void drawFieldHelpIcons(GuiGraphics g, int mx, int my) {
        int fy = fieldRowY;
        switch (draftType) {
            case MESSAGE -> KveGuiStyle.helpIcon(g, L + GUI_W - 19, fy, mx, my, Lang.c("gui.kve.event.action.desc.message"));
            case TITLE -> {
                KveGuiStyle.helpIcon(g, L + GUI_W - 19, fy, mx, my, Lang.c("gui.kve.event.action.desc.title"));
                KveGuiStyle.helpIcon(g, L + GUI_W - 19, fy + 20, mx, my, Lang.c("gui.kve.event.action.desc.subtitle"));
            }
            case GIVE_ITEM -> {
                KveGuiStyle.helpIcon(g, L + 10 + 150 - 9, fy, mx, my, Lang.c("gui.kve.event.action.desc.give_item"));
                KveGuiStyle.helpIcon(g, L + GUI_W - 19, fy + 20, mx, my, Lang.c("gui.kve.event.action.nbt_hint"));
            }
            case RUN_COMMAND -> KveGuiStyle.helpIcon(g, L + GUI_W - 19, fy, mx, my, Lang.c("gui.kve.event.action.desc.run_command_warning"));
            case PLAY_SOUND -> KveGuiStyle.helpIcon(g, L + GUI_W - 19, fy, mx, my, Lang.c("gui.kve.event.action.desc.play_sound"));
            case SPAWN_ENTITY -> {
                KveGuiStyle.helpIcon(g, L + 10 + 150 - 9, fy, mx, my, Lang.c("gui.kve.event.action.desc.spawn_entity"));
                KveGuiStyle.helpIcon(g, L + 104 + 90 - 9, fy + 20, mx, my, Lang.c("gui.kve.event.action.radius"));
            }
            case ACTIONBAR -> KveGuiStyle.helpIcon(g, L + GUI_W - 19, fy, mx, my, Lang.c("gui.kve.event.action.desc.actionbar"));
            case TAKE_ITEM -> KveGuiStyle.helpIcon(g, L + 10 + 150 - 9, fy, mx, my, Lang.c("gui.kve.event.action.desc.take_item"));
            case GIVE_XP_LEVELS -> KveGuiStyle.helpIcon(g, L + GUI_W - 19, fy, mx, my, Lang.c("gui.kve.event.action.desc.give_xp_levels"));
            case ADD_EFFECT -> KveGuiStyle.helpIcon(g, L + 10 + (GUI_W - 20 - 40) - 9, fy, mx, my, Lang.c("gui.kve.event.action.desc.add_effect"));
            case TELEPORT -> KveGuiStyle.helpIcon(g, L + GUI_W - 19, fy, mx, my, Lang.c("gui.kve.event.action.teleport_dim_hint"));
            case TELEPORT_SPAWN -> KveGuiStyle.helpIcon(g, L + GUI_W - 19, fy, mx, my, Lang.c("gui.kve.event.action.desc.teleport_spawn"));
            case CLEAR_ITEM -> KveGuiStyle.helpIcon(g, L + GUI_W - 19, fy, mx, my, Lang.c("gui.kve.event.action.desc.clear_item"));
            case GIVE_XP_POINTS -> KveGuiStyle.helpIcon(g, L + GUI_W - 19, fy, mx, my, Lang.c("gui.kve.event.action.desc.give_xp_points"));
            case SET_XP_LEVEL -> KveGuiStyle.helpIcon(g, L + GUI_W - 19, fy, mx, my, Lang.c("gui.kve.event.action.desc.set_xp_level"));
            case ADD_STAGE -> KveGuiStyle.helpIcon(g, L + GUI_W - 19, fy, mx, my, Lang.c("gui.kve.event.action.desc.add_stage"));
            case REMOVE_STAGE -> KveGuiStyle.helpIcon(g, L + GUI_W - 19, fy, mx, my, Lang.c("gui.kve.event.action.desc.remove_stage"));
            case HEAL -> KveGuiStyle.helpIcon(g, L + GUI_W - 19, fy, mx, my, Lang.c("gui.kve.event.action.desc.heal"));
            case SET_FOOD -> KveGuiStyle.helpIcon(g, L + 104 + (GUI_W - 114) - 9, fy, mx, my, Lang.c("gui.kve.event.action.desc.set_food"));
            case SET_HEALTH -> KveGuiStyle.helpIcon(g, L + GUI_W - 19, fy, mx, my, Lang.c("gui.kve.event.action.desc.set_health"));
            case CLEAR_EFFECTS -> KveGuiStyle.helpIcon(g, L + GUI_W - 19, fy, mx, my, Lang.c("gui.kve.event.action.desc.clear_effects"));
            case BOSSBAR_SHOW -> KveGuiStyle.helpIcon(g, L + GUI_W - 19, fy, mx, my, Lang.c("gui.kve.event.action.desc.bossbar_show"));
            case BOSSBAR_CLEAR -> KveGuiStyle.helpIcon(g, L + GUI_W - 19, fy, mx, my, Lang.c("gui.kve.event.action.desc.bossbar_clear"));
            case PARTICLES -> KveGuiStyle.helpIcon(g, L + GUI_W - 19, fy, mx, my, Lang.c("gui.kve.event.action.desc.particles"));
            case SET_TIME -> KveGuiStyle.helpIcon(g, L + GUI_W - 19, fy, mx, my, Lang.c("gui.kve.event.action.desc.set_time"));
            case SET_WEATHER -> KveGuiStyle.helpIcon(g, L + GUI_W - 19, fy, mx, my, Lang.c("gui.kve.event.action.set_weather_duration_hint"));
            case EXPLOSION_HARMLESS -> KveGuiStyle.helpIcon(g, L + GUI_W - 19, fy, mx, my, Lang.c("gui.kve.event.action.desc.explosion_harmless"));
            case RUN_COMMAND_AS_PLAYER -> KveGuiStyle.helpIcon(g, L + GUI_W - 19, fy, mx, my, Lang.c("gui.kve.event.action.desc.run_command_as_player_warning"));
            case GIVE_MONEY, TAKE_MONEY -> KveGuiStyle.helpIcon(g, L + GUI_W - 19, fy, mx, my, Lang.c("gui.kve.event.action.economy_hint"));
            case FIRE_CUSTOM_EVENT -> KveGuiStyle.helpIcon(g, L + GUI_W - 19, fy, mx, my, Lang.c("gui.kve.event.action.desc.fire_custom_event"));
            case CLEAR_TITLE -> KveGuiStyle.helpIcon(g, L + GUI_W - 19, fy, mx, my, Lang.c("gui.kve.event.action.desc.clear_title"));
            case GRANT_ADVANCEMENT -> KveGuiStyle.helpIcon(g, L + GUI_W - 19, fy, mx, my, Lang.c("gui.kve.event.action.desc.grant_advancement"));
            case REMOVE_EFFECT -> KveGuiStyle.helpIcon(g, L + 10 + (GUI_W - 20 - 40) - 9, fy, mx, my, Lang.c("gui.kve.event.action.desc.remove_effect"));
            case SET_SPAWN -> KveGuiStyle.helpIcon(g, L + GUI_W - 19, fy, mx, my, Lang.c("gui.kve.event.action.desc.set_spawn"));
            case LIGHTNING -> KveGuiStyle.helpIcon(g, L + GUI_W - 19, fy, mx, my, Lang.c("gui.kve.event.action.desc.lightning"));
            case APPLY_LOOT_TABLE -> KveGuiStyle.helpIcon(g, L + 10 + (GUI_W - 20 - 40) - 9, fy, mx, my, Lang.c("gui.kve.event.action.desc.apply_loot_table"));
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