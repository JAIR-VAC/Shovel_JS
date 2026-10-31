package com.kve.client.gui;

import com.kve.common.event.EventEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class EventTemplates {
    private EventTemplates() {}

    /** One offered template: a name/description (as lang keys, resolved by the picker) plus a factory for a fresh draft. */
    public static final class Template {
        public final String nameKey;
        public final String descKey;
        private final Supplier<EventEntry> factory;

        public Template(String nameKey, String descKey, Supplier<EventEntry> factory) {
            this.nameKey = nameKey;
            this.descKey = descKey;
            this.factory = factory;
        }

        /** Fresh, independent {@link EventEntry} draft - see class doc on why this isn't a cached singleton. */
        public EventEntry create() { return factory.get(); }
    }

    /** All templates, in the display order lists them. */
    public static List<Template> all() {
        List<Template> list = new ArrayList<>();
        list.add(new Template("gui.kve.event.template.welcome_kit.name", "gui.kve.event.template.welcome_kit.desc", EventTemplates::welcomeKit));
        list.add(new Template("gui.kve.event.template.playtime_reward.name", "gui.kve.event.template.playtime_reward.desc", EventTemplates::playtimeReward));
        list.add(new Template("gui.kve.event.template.biome_warning.name", "gui.kve.event.template.biome_warning.desc", EventTemplates::biomeWarning));
        list.add(new Template("gui.kve.event.template.hunt_achievement.name", "gui.kve.event.template.hunt_achievement.desc", EventTemplates::huntAchievement));
        list.add(new Template("gui.kve.event.template.staff_manual.name", "gui.kve.event.template.staff_manual.desc", EventTemplates::staffManual));
        // Nuevas plantillas: cada una cubre a proposito un Trigger/Condition/
        // Action que ninguna plantilla anterior ejercitaba (ver tabla en el
        // comentario de cada metodo), asi que entre todas dejan los 6
        // TriggerType, los 4 ConditionType y los 6 ActionType con al menos
        // un ejemplo elegible desde "+ Nuevo evento" -> plantillas.
        list.add(new Template("gui.kve.event.template.returning_player.name", "gui.kve.event.template.returning_player.desc", EventTemplates::returningPlayer));
        list.add(new Template("gui.kve.event.template.night_ambush.name", "gui.kve.event.template.night_ambush.desc", EventTemplates::nightAmbush));
        list.add(new Template("gui.kve.event.template.server_announcement.name", "gui.kve.event.template.server_announcement.desc", EventTemplates::serverAnnouncement));
        list.add(new Template("gui.kve.event.template.nether_toll.name", "gui.kve.event.template.nether_toll.desc", EventTemplates::netherToll));
        list.add(new Template("gui.kve.event.template.creative_builder_greeting.name", "gui.kve.event.template.creative_builder_greeting.desc", EventTemplates::creativeBuilderGreeting));
        list.add(new Template("gui.kve.event.template.staff_login_notice.name", "gui.kve.event.template.staff_login_notice.desc", EventTemplates::staffLoginNotice));

        // 15 plantillas nuevas, cada una
        // elegida para ejercitar al menos una pieza de X1 (trigger, condition
        // o action) que ninguna plantilla anterior usaba - ver comentario de
        // cada metodo. ITEM_PICKUP/ITEM_CRAFT quedan afuera de las plantillas
        // a proposito: todavia son un stub sin
        // export real, asi que una plantilla con ese trigger nunca dispararia
        // en juego.
        list.add(new Template("gui.kve.event.template.welcome_rules.name", "gui.kve.event.template.welcome_rules.desc", EventTemplates::welcomeRules));
        list.add(new Template("gui.kve.event.template.first_night_torch.name", "gui.kve.event.template.first_night_torch.desc", EventTemplates::firstNightTorch));
        list.add(new Template("gui.kve.event.template.first_death_help.name", "gui.kve.event.template.first_death_help.desc", EventTemplates::firstDeathHelp));
        list.add(new Template("gui.kve.event.template.respawn_nudge.name", "gui.kve.event.template.respawn_nudge.desc", EventTemplates::respawnNudge));
        list.add(new Template("gui.kve.event.template.rough_landing_care.name", "gui.kve.event.template.rough_landing_care.desc", EventTemplates::roughLandingCare));
        list.add(new Template("gui.kve.event.template.nether_welcome.name", "gui.kve.event.template.nether_welcome.desc", EventTemplates::netherWelcome));
        list.add(new Template("gui.kve.event.template.end_welcome.name", "gui.kve.event.template.end_welcome.desc", EventTemplates::endWelcome));
        list.add(new Template("gui.kve.event.template.high_altitude_hint.name", "gui.kve.event.template.high_altitude_hint.desc", EventTemplates::highAltitudeHint));
        list.add(new Template("gui.kve.event.template.deep_cave_warning.name", "gui.kve.event.template.deep_cave_warning.desc", EventTemplates::deepCaveWarning));
        list.add(new Template("gui.kve.event.template.jungle_explorer_badge.name", "gui.kve.event.template.jungle_explorer_badge.desc", EventTemplates::jungleExplorerBadge));
        list.add(new Template("gui.kve.event.template.veteran_teleport_home.name", "gui.kve.event.template.veteran_teleport_home.desc", EventTemplates::veteranTeleportHome));
        list.add(new Template("gui.kve.event.template.quarry_starter_kit.name", "gui.kve.event.template.quarry_starter_kit.desc", EventTemplates::quarryStarterKit));
        list.add(new Template("gui.kve.event.template.builder_foundation_hint.name", "gui.kve.event.template.builder_foundation_hint.desc", EventTemplates::builderFoundationHint));
        list.add(new Template("gui.kve.event.template.staff_badge_check.name", "gui.kve.event.template.staff_badge_check.desc", EventTemplates::staffBadgeCheck));
        list.add(new Template("gui.kve.event.template.lost_and_found_recall.name", "gui.kve.event.template.lost_and_found_recall.desc", EventTemplates::lostAndFoundRecall));

        list.add(new Template("gui.kve.event.template.hunt_ten_skeletons.name", "gui.kve.event.template.hunt_ten_skeletons.desc", EventTemplates::huntTenSkeletons));
        list.add(new Template("gui.kve.event.template.first_blood.name", "gui.kve.event.template.first_blood.desc", EventTemplates::firstBlood));
        list.add(new Template("gui.kve.event.template.xp_ten_reward.name", "gui.kve.event.template.xp_ten_reward.desc", EventTemplates::xpTenReward));
        list.add(new Template("gui.kve.event.template.xp_thirty_reward.name", "gui.kve.event.template.xp_thirty_reward.desc", EventTemplates::xpThirtyReward));
        list.add(new Template("gui.kve.event.template.advancement_kill_dragon.name", "gui.kve.event.template.advancement_kill_dragon.desc", EventTemplates::advancementKillDragon));
        list.add(new Template("gui.kve.event.template.raid_hero_celebrate.name", "gui.kve.event.template.raid_hero_celebrate.desc", EventTemplates::raidHeroCelebrate));
        list.add(new Template("gui.kve.event.template.starter_quest_woodcutter.name", "gui.kve.event.template.starter_quest_woodcutter.desc", EventTemplates::starterQuestWoodcutter));
        list.add(new Template("gui.kve.event.template.low_health_warning.name", "gui.kve.event.template.low_health_warning.desc", EventTemplates::lowHealthWarning));
        list.add(new Template("gui.kve.event.template.well_fed_bonus.name", "gui.kve.event.template.well_fed_bonus.desc", EventTemplates::wellFedBonus));
        list.add(new Template("gui.kve.event.template.torch_lighter_hint.name", "gui.kve.event.template.torch_lighter_hint.desc", EventTemplates::torchLighterHint));

        list.add(new Template("gui.kve.event.template.maintenance_warning.name", "gui.kve.event.template.maintenance_warning.desc", EventTemplates::maintenanceWarning));
        list.add(new Template("gui.kve.event.template.storm_warning.name", "gui.kve.event.template.storm_warning.desc", EventTemplates::stormWarning));
        list.add(new Template("gui.kve.event.template.food_low_warning.name", "gui.kve.event.template.food_low_warning.desc", EventTemplates::foodLowWarning));
        list.add(new Template("gui.kve.event.template.tool_check_hint.name", "gui.kve.event.template.tool_check_hint.desc", EventTemplates::toolCheckHint));
        list.add(new Template("gui.kve.event.template.afk_nudge.name", "gui.kve.event.template.afk_nudge.desc", EventTemplates::afkNudge));
        list.add(new Template("gui.kve.event.template.vote_reward_stub.name", "gui.kve.event.template.vote_reward_stub.desc", EventTemplates::voteRewardStub));
        list.add(new Template("gui.kve.event.template.softcore_keep_kit.name", "gui.kve.event.template.softcore_keep_kit.desc", EventTemplates::softcoreKeepKit));
        list.add(new Template("gui.kve.event.template.hunted_beast_bounty.name", "gui.kve.event.template.hunted_beast_bounty.desc", EventTemplates::huntedBeastBounty));
        list.add(new Template("gui.kve.event.template.villager_rapport.name", "gui.kve.event.template.villager_rapport.desc", EventTemplates::villagerRapport));
        list.add(new Template("gui.kve.event.template.first_tame_companion.name", "gui.kve.event.template.first_tame_companion.desc", EventTemplates::firstTameCompanion));
        list.add(new Template("gui.kve.event.template.weekend_farmer_bonus.name", "gui.kve.event.template.weekend_farmer_bonus.desc", EventTemplates::weekendFarmerBonus));
        list.add(new Template("gui.kve.event.template.companion_mount.name", "gui.kve.event.template.companion_mount.desc", EventTemplates::companionMount));
        list.add(new Template("gui.kve.event.template.farewell_broadcast.name", "gui.kve.event.template.farewell_broadcast.desc", EventTemplates::farewellBroadcast));
        list.add(new Template("gui.kve.event.template.seasonal_server_boot.name", "gui.kve.event.template.seasonal_server_boot.desc", EventTemplates::seasonalServerBoot));
        list.add(new Template("gui.kve.event.template.full_moon_bucket_ritual.name", "gui.kve.event.template.full_moon_bucket_ritual.desc", EventTemplates::fullMoonBucketRitual));
        list.add(new Template("gui.kve.event.template.staff_weather_toolkit.name", "gui.kve.event.template.staff_weather_toolkit.desc", EventTemplates::staffWeatherToolkit));
        list.add(new Template("gui.kve.event.template.toll_and_signal.name", "gui.kve.event.template.toll_and_signal.desc", EventTemplates::tollAndSignal));
        return list;
    }

    private static EventEntry welcomeKit() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:welcome_kit";
        e.displayName = "Kit de bienvenida";
        e.category = "starter";
        e.trigger.type = EventEntry.TriggerType.FIRST_JOIN;

        EventEntry.Action msg = new EventEntry.Action();
        msg.type = EventEntry.ActionType.MESSAGE;
        msg.text = "\u00a1Bienvenido al servidor! Esperamos que la pases genial.";
        e.actions.add(msg);

        EventEntry.Action give = new EventEntry.Action();
        give.type = EventEntry.ActionType.GIVE_ITEM;
        give.itemId = "minecraft:bread";
        give.itemCount = 1;
        e.actions.add(give);

        return e;
    }

    private static EventEntry playtimeReward() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:playtime_reward";
        e.displayName = "Recompensa por tiempo jugado";
        e.category = "recompensas";
        e.trigger.type = EventEntry.TriggerType.INTERVAL;
        e.trigger.intervalSeconds = 3600;

        EventEntry.Action msg = new EventEntry.Action();
        msg.type = EventEntry.ActionType.MESSAGE;
        msg.text = "Gracias por seguir jugando! Aca tenes una recompensa.";
        e.actions.add(msg);

        EventEntry.Action give = new EventEntry.Action();
        give.type = EventEntry.ActionType.GIVE_ITEM;
        give.itemId = "minecraft:golden_apple";
        give.itemCount = 1;
        e.actions.add(give);

        return e;
    }

    private static EventEntry biomeWarning() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:danger_biome_warning";
        e.displayName = "Aviso de bioma peligroso";
        e.category = "aviso";
        e.trigger.type = EventEntry.TriggerType.ENTER_BIOME;
        e.trigger.biomeId = "minecraft:nether_wastes";

        EventEntry.Action title = new EventEntry.Action();
        title.type = EventEntry.ActionType.TITLE;
        title.title = "Cuidado!";
        title.subtitle = "Estas entrando a una zona peligrosa";
        e.actions.add(title);

        return e;
    }

    private static EventEntry huntAchievement() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:hunt_achievement";
        e.displayName = "Logro de caceria";
        e.category = "logros";
        e.trigger.type = EventEntry.TriggerType.PLAYER_KILL;
        e.trigger.entityId = "minecraft:zombie";
        e.trigger.count = 10;

        EventEntry.Action msg = new EventEntry.Action();
        msg.type = EventEntry.ActionType.MESSAGE;
        msg.text = "Completaste el logro de caceria de zombies!";
        e.actions.add(msg);

        EventEntry.Action give = new EventEntry.Action();
        give.type = EventEntry.ActionType.GIVE_ITEM;
        give.itemId = "minecraft:diamond";
        give.itemCount = 1;
        e.actions.add(give);

        return e;
    }

    private static EventEntry staffManual() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:staff_manual_event";
        e.displayName = "Evento manual de staff";
        e.category = "staff";
        e.trigger.type = EventEntry.TriggerType.COMMAND;
        e.trigger.commandName = "staffevent";

        EventEntry.Action title = new EventEntry.Action();
        title.type = EventEntry.ActionType.TITLE;
        title.title = "Evento especial!";
        e.actions.add(title);

        EventEntry.Action sound = new EventEntry.Action();
        sound.type = EventEntry.ActionType.PLAY_SOUND;
        sound.soundId = "minecraft:entity.player.levelup";
        e.actions.add(sound);

        return e;
    }

    private static EventEntry returningPlayer() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:returning_player";
        e.displayName = "Bienvenida de regreso";
        e.category = "starter";
        e.trigger.type = EventEntry.TriggerType.PLAYER_LOGIN;

        EventEntry.Action msg = new EventEntry.Action();
        msg.type = EventEntry.ActionType.MESSAGE;
        msg.text = "\u00a1Que bueno tenerte de vuelta!";
        e.actions.add(msg);

        return e;
    }

    private static EventEntry nightAmbush() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:night_ambush";
        e.displayName = "Emboscada nocturna";
        e.category = "aviso";
        e.trigger.type = EventEntry.TriggerType.INTERVAL;
        e.trigger.intervalSeconds = 1800;

        EventEntry.Condition night = new EventEntry.Condition();
        night.type = EventEntry.ConditionType.TIME_OF_DAY;
        night.timeOfDay = EventEntry.TimeOfDay.NIGHT;
        e.conditions.add(night);

        EventEntry.Action title = new EventEntry.Action();
        title.type = EventEntry.ActionType.TITLE;
        title.title = "La noche esta llena de peligros...";
        e.actions.add(title);

        EventEntry.Action spawn = new EventEntry.Action();
        spawn.type = EventEntry.ActionType.SPAWN_ENTITY;
        spawn.spawnEntityId = "minecraft:zombie";
        spawn.spawnCount = 2;
        spawn.spawnRadius = 8.0;
        e.actions.add(spawn);

        return e;
    }

    private static EventEntry serverAnnouncement() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:server_announcement";
        e.displayName = "Anuncio del servidor";
        e.category = "staff";
        e.trigger.type = EventEntry.TriggerType.COMMAND;
        e.trigger.commandName = "announce";

        EventEntry.Action msg = new EventEntry.Action();
        msg.type = EventEntry.ActionType.MESSAGE;
        msg.text = "\u00a1Atencion! Anuncio del servidor.";
        e.actions.add(msg);

        EventEntry.Action cmd = new EventEntry.Action();
        cmd.type = EventEntry.ActionType.RUN_COMMAND;
        cmd.command = "say Anuncio importante del servidor";
        e.actions.add(cmd);

        return e;
    }

    private static EventEntry netherToll() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:nether_toll";
        e.displayName = "Peaje del Nether";
        e.category = "aviso";
        e.trigger.type = EventEntry.TriggerType.INTERVAL;
        e.trigger.intervalSeconds = 300;

        EventEntry.Condition inNether = new EventEntry.Condition();
        inNether.type = EventEntry.ConditionType.DIMENSION;
        inNether.dimensionId = "minecraft:the_nether";
        e.conditions.add(inNether);

        EventEntry.Action msg = new EventEntry.Action();
        msg.type = EventEntry.ActionType.MESSAGE;
        msg.text = "Segui con cuidado, todavia estas en el Nether.";
        e.actions.add(msg);

        EventEntry.Action sound = new EventEntry.Action();
        sound.type = EventEntry.ActionType.PLAY_SOUND;
        sound.soundId = "minecraft:block.fire.ambient";
        e.actions.add(sound);

        return e;
    }

    private static EventEntry creativeBuilderGreeting() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:creative_builder_greeting";
        e.displayName = "Saludo a constructores";
        e.category = "starter";
        e.trigger.type = EventEntry.TriggerType.FIRST_JOIN;

        EventEntry.Condition creative = new EventEntry.Condition();
        creative.type = EventEntry.ConditionType.GAMEMODE;
        creative.gamemode = EventEntry.GameMode.CREATIVE;
        e.conditions.add(creative);

        EventEntry.Action msg = new EventEntry.Action();
        msg.type = EventEntry.ActionType.MESSAGE;
        msg.text = "\u00a1Bienvenido al modo creativo! A construir se ha dicho.";
        e.actions.add(msg);

        return e;
    }

    private static EventEntry staffLoginNotice() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:staff_login_notice";
        e.displayName = "Aviso de ingreso de staff";
        e.category = "staff";
        e.trigger.type = EventEntry.TriggerType.PLAYER_LOGIN;

        EventEntry.Condition isOp = new EventEntry.Condition();
        isOp.type = EventEntry.ConditionType.PERMISSION;
        isOp.permissionNode = "op";
        e.conditions.add(isOp);

        EventEntry.Action msg = new EventEntry.Action();
        msg.type = EventEntry.ActionType.MESSAGE;
        msg.text = "Sesion de staff iniciada.";
        e.actions.add(msg);

        return e;
    }

    // =====================================================================
    // - ver comentario de
    // EventTemplates#all sobre por que estas 15 y qué pieza nueva ejercita
    // cada una.
    // =====================================================================

    private static EventEntry welcomeRules() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:welcome_rules";
        e.displayName = "Reglas de bienvenida";
        e.category = "starter";
        e.trigger.type = EventEntry.TriggerType.FIRST_JOIN;

        EventEntry.Action msg1 = new EventEntry.Action();
        msg1.type = EventEntry.ActionType.MESSAGE;
        msg1.text = "Antes de arrancar: no griefees, no hagas spam, y pasala bien.";
        e.actions.add(msg1);

        EventEntry.Action msg2 = new EventEntry.Action();
        msg2.type = EventEntry.ActionType.MESSAGE;
        msg2.text = "Cualquier duda, preguntale al staff. Usa /shovel reset si algo se traba.";
        e.actions.add(msg2);

        return e;
    }

    private static EventEntry firstNightTorch() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:first_night_torch";
        e.displayName = "Antorcha de la primera noche (chequeo cada 60s)";
        e.category = "starter";
        e.trigger.type = EventEntry.TriggerType.INTERVAL;
        e.trigger.intervalSeconds = 60;
        e.limits.oncePerPlayer = true;

        EventEntry.Condition night = new EventEntry.Condition();
        night.type = EventEntry.ConditionType.TIME_OF_DAY;
        night.timeOfDay = EventEntry.TimeOfDay.NIGHT;
        e.conditions.add(night);

        EventEntry.Action give = new EventEntry.Action();
        give.type = EventEntry.ActionType.GIVE_ITEM;
        give.itemId = "minecraft:torch";
        give.itemCount = 8;
        e.actions.add(give);

        return e;
    }

    private static EventEntry firstDeathHelp() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:first_death_help";
        e.displayName = "Ayuda en la primera muerte";
        e.category = "starter";
        e.trigger.type = EventEntry.TriggerType.PLAYER_DEATH;
        e.limits.oncePerPlayer = true;

        EventEntry.Action msg = new EventEntry.Action();
        msg.type = EventEntry.ActionType.MESSAGE;
        msg.text = "Morir es parte del juego. Aca tenes algo para arrancar de nuevo.";
        e.actions.add(msg);

        EventEntry.Action give = new EventEntry.Action();
        give.type = EventEntry.ActionType.GIVE_ITEM;
        give.itemId = "minecraft:bread";
        give.itemCount = 4;
        e.actions.add(give);

        return e;
    }

    private static EventEntry respawnNudge() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:respawn_nudge";
        e.displayName = "Aviso de reaparicion";
        e.category = "starter";
        e.trigger.type = EventEntry.TriggerType.PLAYER_RESPAWN;
        e.limits.cooldownSeconds = 60;

        EventEntry.Action bar = new EventEntry.Action();
        bar.type = EventEntry.ActionType.ACTIONBAR;
        bar.text = "Volviste al mundo. Cuidado con tu inventario.";
        e.actions.add(bar);

        return e;
    }

    private static EventEntry roughLandingCare() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:rough_landing_care";
        e.displayName = "Primeros golpes";
        e.category = "starter";
        e.trigger.type = EventEntry.TriggerType.PLAYER_HURT;
        e.trigger.minDamage = 6.0f;
        e.limits.oncePerPlayer = true;

        EventEntry.Action msg = new EventEntry.Action();
        msg.type = EventEntry.ActionType.MESSAGE;
        msg.text = "Eso dolio. Cuidate mas ahi afuera.";
        e.actions.add(msg);

        return e;
    }

    private static EventEntry netherWelcome() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:nether_welcome";
        e.displayName = "Bienvenida al Nether";
        e.category = "aviso";
        e.trigger.type = EventEntry.TriggerType.DIMENSION_CHANGE;
        e.trigger.toDimension = "minecraft:the_nether";

        EventEntry.Action title = new EventEntry.Action();
        title.type = EventEntry.ActionType.TITLE;
        title.title = "Entraste al Nether";
        title.subtitle = "Cuidado con la lava y los piglins";
        e.actions.add(title);

        EventEntry.Action effect = new EventEntry.Action();
        effect.type = EventEntry.ActionType.ADD_EFFECT;
        effect.effectId = "minecraft:fire_resistance";
        effect.effectDurationTicks = 200;
        effect.effectAmplifier = 0;
        e.actions.add(effect);

        return e;
    }

    private static EventEntry endWelcome() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:end_welcome";
        e.displayName = "Bienvenida al End";
        e.category = "aviso";
        e.trigger.type = EventEntry.TriggerType.DIMENSION_CHANGE;
        e.trigger.toDimension = "minecraft:the_end";

        EventEntry.Action title = new EventEntry.Action();
        title.type = EventEntry.ActionType.TITLE;
        title.title = "Llegaste al End";
        e.actions.add(title);

        return e;
    }

    private static EventEntry highAltitudeHint() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:high_altitude_hint";
        e.displayName = "Aviso de gran altura";
        e.category = "aviso";
        e.trigger.type = EventEntry.TriggerType.INTERVAL;
        e.trigger.intervalSeconds = 60;
        e.limits.oncePerPlayer = true;

        EventEntry.Condition high = new EventEntry.Condition();
        high.type = EventEntry.ConditionType.Y_LEVEL_MIN;
        high.yMin = 200;
        e.conditions.add(high);

        EventEntry.Action msg = new EventEntry.Action();
        msg.type = EventEntry.ActionType.MESSAGE;
        msg.text = "Estas muy alto. Si vas a planear con elytra, cuidado con la caida.";
        e.actions.add(msg);

        return e;
    }

    private static EventEntry deepCaveWarning() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:deep_cave_warning";
        e.displayName = "Aviso de cueva profunda";
        e.category = "aviso";
        e.trigger.type = EventEntry.TriggerType.INTERVAL;
        e.trigger.intervalSeconds = 60;
        e.limits.oncePerPlayer = true;

        EventEntry.Condition deep = new EventEntry.Condition();
        deep.type = EventEntry.ConditionType.Y_LEVEL_MAX;
        deep.yMax = -40;
        e.conditions.add(deep);

        EventEntry.Action title = new EventEntry.Action();
        title.type = EventEntry.ActionType.TITLE;
        title.title = "Zona profunda";
        title.subtitle = "Ten cuidado con lo que hay ahi abajo";
        e.actions.add(title);

        return e;
    }

    private static EventEntry jungleExplorerBadge() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:jungle_explorer_badge";
        e.displayName = "Insignia de explorador de la jungla";
        e.category = "logros";
        e.trigger.type = EventEntry.TriggerType.INTERVAL;
        e.trigger.intervalSeconds = 60;
        e.limits.oncePerPlayer = true;

        EventEntry.Condition jungle = new EventEntry.Condition();
        jungle.type = EventEntry.ConditionType.BIOME;
        jungle.biomeId = "minecraft:jungle";
        e.conditions.add(jungle);

        EventEntry.Action give = new EventEntry.Action();
        give.type = EventEntry.ActionType.GIVE_ITEM;
        give.itemId = "minecraft:cocoa_beans";
        give.itemCount = 3;
        e.actions.add(give);

        return e;
    }

    private static EventEntry veteranTeleportHome() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:veteran_teleport_home";
        e.displayName = "Teletransporte de veterano al spawn";
        e.category = "recompensas";
        e.trigger.type = EventEntry.TriggerType.INTERVAL;
        e.trigger.intervalSeconds = 60;
        e.limits.oncePerPlayer = true;

        EventEntry.Condition xp = new EventEntry.Condition();
        xp.type = EventEntry.ConditionType.XP_LEVEL_MIN;
        xp.xpLevelMin = 30;
        e.conditions.add(xp);

        EventEntry.Action msg = new EventEntry.Action();
        msg.type = EventEntry.ActionType.MESSAGE;
        msg.text = "Llegaste a nivel 30! Te llevamos de vuelta al spawn como regalo.";
        e.actions.add(msg);

        EventEntry.Action tp = new EventEntry.Action();
        tp.type = EventEntry.ActionType.TELEPORT_SPAWN;
        e.actions.add(tp);

        return e;
    }

    private static EventEntry quarryStarterKit() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:quarry_starter_kit";
        e.displayName = "Bono de cantera";
        e.category = "recompensas";
        e.trigger.type = EventEntry.TriggerType.BLOCK_BREAK;
        e.trigger.blockId = "minecraft:diamond_ore";
        e.trigger.count = 1;
        e.limits.cooldownSeconds = 600;

        EventEntry.Condition chance = new EventEntry.Condition();
        chance.type = EventEntry.ConditionType.RANDOM_CHANCE;
        chance.randomChance = 0.25;
        e.conditions.add(chance);

        EventEntry.Action xp = new EventEntry.Action();
        xp.type = EventEntry.ActionType.GIVE_XP_LEVELS;
        xp.xpLevels = 2;
        e.actions.add(xp);

        return e;
    }

    private static EventEntry builderFoundationHint() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:builder_foundation_hint";
        e.displayName = "Aviso de cimientos";
        e.category = "starter";
        e.trigger.type = EventEntry.TriggerType.BLOCK_PLACE;
        e.trigger.blockId = "minecraft:scaffolding";
        e.trigger.count = 10;
        e.limits.oncePerPlayer = true;

        EventEntry.Action msg = new EventEntry.Action();
        msg.type = EventEntry.ActionType.MESSAGE;
        msg.text = "Buen uso del andamiaje! Es mas rapido que escaleras para construir alto.";
        e.actions.add(msg);

        return e;
    }

    private static EventEntry staffBadgeCheck() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:staff_badge_check";
        e.displayName = "Chequeo de insignia de staff";
        e.category = "staff";
        e.trigger.type = EventEntry.TriggerType.COMMAND;
        e.trigger.commandName = "staffcheck";

        EventEntry.Condition hasBadge = new EventEntry.Condition();
        hasBadge.type = EventEntry.ConditionType.HAS_ITEM;
        hasBadge.hasItemId = "minecraft:nether_star";
        hasBadge.hasItemMinCount = 1;
        e.conditions.add(hasBadge);

        EventEntry.Action title = new EventEntry.Action();
        title.type = EventEntry.ActionType.TITLE;
        title.title = "Insignia verificada";
        e.actions.add(title);

        return e;
    }

    private static EventEntry lostAndFoundRecall() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:lost_and_found_recall";
        e.displayName = "Recuperar objeto perdido";
        e.category = "staff";
        e.trigger.type = EventEntry.TriggerType.COMMAND;
        e.trigger.commandName = "recall";

        EventEntry.Action tp = new EventEntry.Action();
        tp.type = EventEntry.ActionType.TELEPORT;
        tp.teleportDim = "minecraft:overworld";
        tp.teleportX = 0;
        tp.teleportY = 100;
        tp.teleportZ = 0;
        e.actions.add(tp);

        EventEntry.Action msg = new EventEntry.Action();
        msg.type = EventEntry.ActionType.MESSAGE;
        msg.text = "Te trajimos de vuelta a un punto seguro.";
        e.actions.add(msg);

        return e;
    }

    private static EventEntry huntTenSkeletons() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:hunt_ten_skeletons";
        e.displayName = "Cazar 10 esqueletos";
        e.category = "combat";
        e.trigger.type = EventEntry.TriggerType.PLAYER_KILL;
        e.trigger.entityId = "minecraft:skeleton";
        e.trigger.count = 10;
        e.limits.oncePerPlayer = true;

        EventEntry.Action give = new EventEntry.Action();
        give.type = EventEntry.ActionType.GIVE_ITEM;
        give.itemId = "minecraft:arrow";
        give.itemCount = 16;
        e.actions.add(give);

        return e;
    }

    private static EventEntry firstBlood() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:first_blood";
        e.displayName = "Primer golpe recibido";
        e.category = "combat";
        e.trigger.type = EventEntry.TriggerType.PLAYER_HURT;
        e.trigger.minDamage = 1.0f;
        e.limits.oncePerPlayer = true;

        EventEntry.Action msg = new EventEntry.Action();
        msg.type = EventEntry.ActionType.MESSAGE;
        msg.text = "Recibiste tu primer golpe. Revisa tu vida en la barra de corazones.";
        e.actions.add(msg);

        return e;
    }

    private static EventEntry xpTenReward() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:xp_ten_reward";
        e.displayName = "Recompensa de nivel 10";
        e.category = "combat";
        e.trigger.type = EventEntry.TriggerType.XP_LEVEL_REACH;
        e.trigger.xpLevel = 10;
        e.limits.oncePerPlayer = true;

        EventEntry.Action give = new EventEntry.Action();
        give.type = EventEntry.ActionType.GIVE_ITEM;
        give.itemId = "minecraft:golden_apple";
        give.itemCount = 1;
        e.actions.add(give);

        return e;
    }

    private static EventEntry xpThirtyReward() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:xp_thirty_reward";
        e.displayName = "Recompensa de nivel 30";
        e.category = "combat";
        e.trigger.type = EventEntry.TriggerType.XP_LEVEL_REACH;
        e.trigger.xpLevel = 30;
        e.limits.oncePerPlayer = true;

        EventEntry.Action stage = new EventEntry.Action();
        stage.type = EventEntry.ActionType.ADD_STAGE;
        stage.stageId = "shovel_js_veteran";
        e.actions.add(stage);

        EventEntry.Action give = new EventEntry.Action();
        give.type = EventEntry.ActionType.GIVE_ITEM;
        give.itemId = "minecraft:diamond";
        give.itemCount = 2;
        e.actions.add(give);

        return e;
    }

    private static EventEntry advancementKillDragon() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:advancement_kill_dragon";
        e.displayName = "Derrotaste al Ender Dragon";
        e.category = "combat";
        e.trigger.type = EventEntry.TriggerType.ADVANCEMENT_EARNED;
        e.trigger.advancementId = "minecraft:end/kill_dragon";
        e.limits.oncePerPlayer = true;

        EventEntry.Action title = new EventEntry.Action();
        title.type = EventEntry.ActionType.TITLE;
        title.title = "Felicitaciones";
        title.subtitle = "Derrotaste al Ender Dragon";
        e.actions.add(title);

        EventEntry.Action give = new EventEntry.Action();
        give.type = EventEntry.ActionType.GIVE_ITEM;
        give.itemId = "minecraft:nether_star";
        give.itemCount = 1;
        e.actions.add(give);

        return e;
    }

    private static EventEntry raidHeroCelebrate() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:raid_hero_celebrate";
        e.displayName = "Celebracion de heroe de asalto";
        e.category = "combat";
        e.trigger.type = EventEntry.TriggerType.PLAYER_KILL;
        e.trigger.entityId = "minecraft:ravager";
        e.trigger.count = 1;
        e.limits.oncePerPlayer = true;

        EventEntry.Action title = new EventEntry.Action();
        title.type = EventEntry.ActionType.TITLE;
        title.title = "Asalto repelido";
        e.actions.add(title);

        EventEntry.Action sound = new EventEntry.Action();
        sound.type = EventEntry.ActionType.PLAY_SOUND;
        sound.soundId = "minecraft:entity.villager.celebrate";
        e.actions.add(sound);

        EventEntry.Action give = new EventEntry.Action();
        give.type = EventEntry.ActionType.GIVE_ITEM;
        give.itemId = "minecraft:emerald";
        give.itemCount = 3;
        e.actions.add(give);

        return e;
    }

    private static EventEntry starterQuestWoodcutter() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:starter_quest_woodcutter";
        e.displayName = "Cadena inicial - talar";
        e.category = "starter";
        e.trigger.type = EventEntry.TriggerType.BLOCK_BREAK;
        e.trigger.blockId = "#minecraft:logs";
        e.trigger.count = 5;
        e.limits.oncePerPlayer = true;

        EventEntry.Action msg = new EventEntry.Action();
        msg.type = EventEntry.ActionType.MESSAGE;
        msg.text = "Buen trabajo talando madera. Ya podes armar tus primeras herramientas.";
        e.actions.add(msg);

        return e;
    }

    private static EventEntry lowHealthWarning() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:low_health_warning";
        e.displayName = "Aviso de vida baja";
        e.category = "aviso";
        e.trigger.type = EventEntry.TriggerType.INTERVAL;
        e.trigger.intervalSeconds = 300;

        EventEntry.Condition lowHp = new EventEntry.Condition();
        lowHp.type = EventEntry.ConditionType.HEALTH_MAX;
        lowHp.healthMax = 6.0f;
        e.conditions.add(lowHp);

        EventEntry.Action bar = new EventEntry.Action();
        bar.type = EventEntry.ActionType.ACTIONBAR;
        bar.text = "Tu vida esta baja. Considera curarte o retirarte.";
        e.actions.add(bar);

        return e;
    }

    private static EventEntry wellFedBonus() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:well_fed_bonus";
        e.displayName = "Bono por buena alimentacion";
        e.category = "server";
        e.trigger.type = EventEntry.TriggerType.PLAYER_LOGIN;
        e.limits.cooldownSeconds = 86400;

        EventEntry.Condition wellFed = new EventEntry.Condition();
        wellFed.type = EventEntry.ConditionType.FOOD_LEVEL_MIN;
        wellFed.foodLevelMin = 18;
        e.conditions.add(wellFed);

        EventEntry.Action xp = new EventEntry.Action();
        xp.type = EventEntry.ActionType.GIVE_XP_POINTS;
        xp.xpPoints = 20;
        e.actions.add(xp);

        return e;
    }

    private static EventEntry torchLighterHint() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:torch_lighter_hint";
        e.displayName = "Aviso de encendedor";
        e.category = "starter";
        e.trigger.type = EventEntry.TriggerType.ITEM_USE;
        e.trigger.itemId = "minecraft:flint_and_steel";
        e.limits.oncePerPlayer = true;

        EventEntry.Action msg = new EventEntry.Action();
        msg.type = EventEntry.ActionType.MESSAGE;
        msg.text = "Con el encendedor podes prender fuego, portales del Nether y mas.";
        e.actions.add(msg);

        return e;
    }

    private static EventEntry maintenanceWarning() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:maintenance_warning";
        e.displayName = "Aviso de mantenimiento";
        e.category = "server";
        e.trigger.type = EventEntry.TriggerType.COMMAND;
        e.trigger.commandName = "maintenance";

        EventEntry.Action bar = new EventEntry.Action();
        bar.type = EventEntry.ActionType.BOSSBAR_SHOW;
        bar.bossbarText = "Mantenimiento programado en breve";
        bar.bossbarColor = EventEntry.BossbarColor.RED;
        bar.bossbarSeconds = 20;
        e.actions.add(bar);

        EventEntry.Action msg = new EventEntry.Action();
        msg.type = EventEntry.ActionType.MESSAGE;
        msg.text = "El servidor entrara en mantenimiento pronto. Guarda tu progreso.";
        e.actions.add(msg);

        return e;
    }

    private static EventEntry stormWarning() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:storm_warning";
        e.displayName = "Aviso de tormenta";
        e.category = "world";
        e.trigger.type = EventEntry.TriggerType.INTERVAL;
        e.trigger.intervalSeconds = 300;
        e.limits.cooldownSeconds = 600;

        EventEntry.Condition thunder = new EventEntry.Condition();
        thunder.type = EventEntry.ConditionType.WEATHER;
        thunder.weatherState = EventEntry.WeatherState.THUNDER;
        e.conditions.add(thunder);

        EventEntry.Action title = new EventEntry.Action();
        title.type = EventEntry.ActionType.TITLE;
        title.title = "Tormenta electrica";
        title.subtitle = "Cuidado con los rayos";
        e.actions.add(title);

        return e;
    }

    private static EventEntry foodLowWarning() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:food_low_warning";
        e.displayName = "Recordatorio de comida";
        e.category = "aviso";
        e.trigger.type = EventEntry.TriggerType.INTERVAL;
        e.trigger.intervalSeconds = 600;

        EventEntry.Action bar = new EventEntry.Action();
        bar.type = EventEntry.ActionType.ACTIONBAR;
        bar.text = "Recorda revisar tu nivel de comida.";
        e.actions.add(bar);

        return e;
    }

    private static EventEntry toolCheckHint() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:tool_check_hint";
        e.displayName = "Aviso de revisar equipo";
        e.category = "starter";
        e.trigger.type = EventEntry.TriggerType.PLAYER_HURT;
        e.trigger.minDamage = 6.0f;
        e.limits.oncePerPlayer = true;

        EventEntry.Action msg = new EventEntry.Action();
        msg.type = EventEntry.ActionType.MESSAGE;
        msg.text = "Ese golpe dolio. Revisa la durabilidad de tu armadura y herramientas.";
        e.actions.add(msg);

        return e;
    }

    private static EventEntry afkNudge() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:afk_nudge";
        e.displayName = "Aviso de inactividad";
        e.category = "server";
        e.trigger.type = EventEntry.TriggerType.INTERVAL;
        e.trigger.intervalSeconds = 900;

        EventEntry.Action bar = new EventEntry.Action();
        bar.type = EventEntry.ActionType.ACTIONBAR;
        bar.text = "Segui explorando - todavia queda mucho por hacer.";
        e.actions.add(bar);

        return e;
    }

    private static EventEntry voteRewardStub() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:vote_reward_stub";
        e.displayName = "Recompensa por voto";
        e.category = "server";
        e.trigger.type = EventEntry.TriggerType.COMMAND;
        e.trigger.commandName = "vote";
        e.limits.cooldownSeconds = 86400;

        EventEntry.Action give = new EventEntry.Action();
        give.type = EventEntry.ActionType.GIVE_ITEM;
        give.itemId = "minecraft:emerald";
        give.itemCount = 1;
        e.actions.add(give);

        return e;
    }

    private static EventEntry softcoreKeepKit() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:softcore_keep_kit";
        e.displayName = "Kit softcore tras morir";
        e.category = "server";
        e.trigger.type = EventEntry.TriggerType.PLAYER_DEATH;
        e.limits.cooldownSeconds = 3600;

        EventEntry.Action bread = new EventEntry.Action();
        bread.type = EventEntry.ActionType.GIVE_ITEM;
        bread.itemId = "minecraft:bread";
        bread.itemCount = 8;
        e.actions.add(bread);

        EventEntry.Action torch = new EventEntry.Action();
        torch.type = EventEntry.ActionType.GIVE_ITEM;
        torch.itemId = "minecraft:torch";
        torch.itemCount = 8;
        e.actions.add(torch);

        return e;
    }

    private static EventEntry huntedBeastBounty() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:hunted_beast_bounty";
        e.displayName = "Recompensa de caceria";
        e.category = "combat";
        e.trigger.type = EventEntry.TriggerType.ENTITY_KILL_ANY;
        e.trigger.count = 5;
        e.limits.oncePerPlayer = true;

        EventEntry.Action give = new EventEntry.Action();
        give.type = EventEntry.ActionType.GIVE_ITEM;
        give.itemId = "minecraft:arrow";
        give.itemCount = 16;
        e.actions.add(give);

        EventEntry.Action particles = new EventEntry.Action();
        particles.type = EventEntry.ActionType.PARTICLES;
        particles.particleId = "minecraft:totem_of_undying";
        particles.particleCount = 20;
        particles.particleSpread = 0.5;
        e.actions.add(particles);

        return e;
    }

    private static EventEntry villagerRapport() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:villager_rapport";
        e.displayName = "Buena relacion con aldeanos";
        e.category = "flavor";
        e.trigger.type = EventEntry.TriggerType.ENTITY_INTERACT;
        e.trigger.entityTypeId = "minecraft:villager";
        e.limits.cooldownSeconds = 300;

        EventEntry.Action bar = new EventEntry.Action();
        bar.type = EventEntry.ActionType.ACTIONBAR;
        bar.text = "El aldeano parece contento de verte.";
        e.actions.add(bar);

        return e;
    }

    private static EventEntry firstTameCompanion() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:first_tame_companion";
        e.displayName = "Primer companero domesticado";
        e.category = "flavor";
        e.trigger.type = EventEntry.TriggerType.TAMING;
        e.trigger.entityTypeId = "";
        e.limits.oncePerPlayer = true;

        EventEntry.Action msg = new EventEntry.Action();
        msg.type = EventEntry.ActionType.MESSAGE;
        msg.text = "Ahora tenes un companero domesticado.";
        e.actions.add(msg);

        EventEntry.Action xp = new EventEntry.Action();
        xp.type = EventEntry.ActionType.GIVE_XP_POINTS;
        xp.xpPoints = 10;
        e.actions.add(xp);

        return e;
    }

    private static EventEntry weekendFarmerBonus() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:weekend_farmer_bonus";
        e.displayName = "Bono de granjero del fin de semana";
        e.category = "flavor";
        e.trigger.type = EventEntry.TriggerType.BREEDING;
        e.trigger.entityTypeId = "";
        e.limits.cooldownSeconds = 3600;

        EventEntry.Condition saturday = new EventEntry.Condition();
        saturday.type = EventEntry.ConditionType.DAY_OF_WEEK;
        saturday.dayOfWeek = EventEntry.WeekDay.SAT;
        e.conditions.add(saturday);

        EventEntry.Action xp = new EventEntry.Action();
        xp.type = EventEntry.ActionType.GIVE_XP_POINTS;
        xp.xpPoints = 15;
        e.actions.add(xp);

        return e;
    }

    private static EventEntry companionMount() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:companion_mount";
        e.displayName = "Paseo a caballo";
        e.category = "flavor";
        e.trigger.type = EventEntry.TriggerType.MOUNT;
        e.trigger.entityTypeId = "minecraft:horse";
        e.limits.oncePerPlayer = true;

        EventEntry.Condition daytime = new EventEntry.Condition();
        daytime.type = EventEntry.ConditionType.HOUR_BETWEEN;
        daytime.hourStart = 6;
        daytime.hourEnd = 18;
        e.conditions.add(daytime);

        EventEntry.Action particles = new EventEntry.Action();
        particles.type = EventEntry.ActionType.PARTICLES;
        particles.particleId = "minecraft:happy_villager";
        particles.particleCount = 12;
        particles.particleSpread = 0.4;
        e.actions.add(particles);

        EventEntry.Action xp = new EventEntry.Action();
        xp.type = EventEntry.ActionType.GIVE_XP_POINTS;
        xp.xpPoints = 5;
        e.actions.add(xp);

        return e;
    }

    private static EventEntry farewellBroadcast() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:farewell_broadcast";
        e.displayName = "Despedida al desconectarse";
        e.category = "server";
        e.trigger.type = EventEntry.TriggerType.PLAYER_DISCONNECT;

        EventEntry.Action broadcast = new EventEntry.Action();
        broadcast.type = EventEntry.ActionType.RUN_COMMAND;
        broadcast.command = "tellraw @a {\"text\":\"Un jugador se desconecto. Hasta la proxima.\"}";
        e.actions.add(broadcast);

        EventEntry.Action clearBar = new EventEntry.Action();
        clearBar.type = EventEntry.ActionType.BOSSBAR_CLEAR;
        e.actions.add(clearBar);

        return e;
    }

    private static EventEntry seasonalServerBoot() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:seasonal_server_boot";
        e.displayName = "Arranque de temporada";
        e.category = "world";
        e.trigger.type = EventEntry.TriggerType.SERVER_STARTUP;

        EventEntry.Condition winter = new EventEntry.Condition();
        winter.type = EventEntry.ConditionType.DATE_BETWEEN;
        winter.dateStartMd = "12-01";
        winter.dateEndMd = "12-31";
        e.conditions.add(winter);

        EventEntry.Action msg = new EventEntry.Action();
        msg.type = EventEntry.ActionType.MESSAGE;
        msg.text = "Bienvenido a la temporada de invierno del servidor.";
        e.actions.add(msg);

        EventEntry.Action time = new EventEntry.Action();
        time.type = EventEntry.ActionType.SET_TIME;
        time.setTimeMode = EventEntry.SetTimeMode.DAY;
        e.actions.add(time);

        return e;
    }

    private static EventEntry fullMoonBucketRitual() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:full_moon_bucket_ritual";
        e.displayName = "Ritual de luna llena";
        e.category = "world";
        e.trigger.type = EventEntry.TriggerType.FILL_BUCKET;
        e.trigger.fluidId = "minecraft:water";
        e.limits.oncePerPlayer = true;

        EventEntry.Condition fullMoon = new EventEntry.Condition();
        fullMoon.type = EventEntry.ConditionType.MOON_PHASE;
        fullMoon.moonPhase = "FULL";
        e.conditions.add(fullMoon);

        EventEntry.Action title = new EventEntry.Action();
        title.type = EventEntry.ActionType.TITLE;
        title.title = "Luna llena";
        title.subtitle = "El agua brilla de forma extrania";
        e.actions.add(title);

        EventEntry.Action explosion = new EventEntry.Action();
        explosion.type = EventEntry.ActionType.EXPLOSION_HARMLESS;
        explosion.explosionPower = 1.5f;
        e.actions.add(explosion);

        EventEntry.Action particles = new EventEntry.Action();
        particles.type = EventEntry.ActionType.PARTICLES;
        particles.particleId = "minecraft:end_rod";
        particles.particleCount = 15;
        particles.particleSpread = 0.6;
        e.actions.add(particles);

        return e;
    }

    private static EventEntry staffWeatherToolkit() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:staff_weather_toolkit";
        e.displayName = "Kit de clima para staff";
        e.category = "server";
        e.trigger.type = EventEntry.TriggerType.COMMAND;
        e.trigger.commandName = "staffkit";

        EventEntry.Condition op = new EventEntry.Condition();
        op.type = EventEntry.ConditionType.PERMISSION;
        op.permissionNode = "op";
        e.conditions.add(op);

        EventEntry.Action weather = new EventEntry.Action();
        weather.type = EventEntry.ActionType.SET_WEATHER;
        weather.setWeatherMode = EventEntry.WeatherState.CLEAR;
        e.actions.add(weather);

        EventEntry.Action tp = new EventEntry.Action();
        tp.type = EventEntry.ActionType.RUN_COMMAND_AS_PLAYER;
        tp.commandAsPlayer = "spawn";
        e.actions.add(tp);

        EventEntry.Action money = new EventEntry.Action();
        money.type = EventEntry.ActionType.GIVE_MONEY;
        money.moneyAmount = 50.0;
        e.actions.add(money);

        return e;
    }

    private static EventEntry tollAndSignal() {
        EventEntry e = new EventEntry();
        e.id = "shovel_js:toll_and_signal";
        e.displayName = "Peaje y senal";
        e.category = "server";
        e.trigger.type = EventEntry.TriggerType.COMMAND;
        e.trigger.commandName = "toll";

        EventEntry.Action take = new EventEntry.Action();
        take.type = EventEntry.ActionType.TAKE_MONEY;
        take.moneyAmount = 10.0;
        e.actions.add(take);

        EventEntry.Action fire = new EventEntry.Action();
        fire.type = EventEntry.ActionType.FIRE_CUSTOM_EVENT;
        fire.customEventId = "shovel_js:toll_paid";
        e.actions.add(fire);

        return e;
    }
}