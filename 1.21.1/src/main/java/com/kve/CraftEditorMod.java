package com.kve;

import com.kve.common.config.KVEConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(CraftEditorMod.MODID)
public class CraftEditorMod {

    /** Mod id de Shovel API. */
    public static final String MODID   = "shovel_api";
    /**
     * Namespace de recursos y de ids propios: assets/kve, data/kve, lang
     * gui.kve.*, y los items/bloques que crea el jugador (kve:magic_sword).
     * Se queda en "kve" aunque el mod id cambie: si cambiara, los items ya
     * creados en un mundo o modpack desaparecerian.
     */
    public static final String NS      = "shovel_js";
    public static final String NAME    = "Shovel JS";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER  = LogManager.getLogger(MODID);

    public CraftEditorMod(IEventBus modBus, ModContainer container) {

        KVEConfig.load();

        modBus.addListener(this::commonSetup);
        com.kve.common.lootrules.HideFromLootModifier.SERIALIZERS.register(modBus);

        if (FMLLoader.getDist() == Dist.CLIENT) {
            NeoForge.EVENT_BUS.register(com.kve.common.itemrules.ItemTooltipEnforcer.class);
        }

        NeoForge.EVENT_BUS.register(com.kve.common.blockrules.ExactToolEnforcer.class);
        NeoForge.EVENT_BUS.register(com.kve.common.blockrules.NetheriteTierEnforcer.class);

        NeoForge.EVENT_BUS.register(com.kve.common.mobrules.AttackEffectEnforcer.class);

        NeoForge.EVENT_BUS.register(com.kve.common.mobrules.MobAttributeEnforcer.class);

        NeoForge.EVENT_BUS.register(com.kve.common.mobrules.AllyEnforcer.class);

        NeoForge.EVENT_BUS.register(com.kve.common.mobrules.EnemyEnforcer.class);

        NeoForge.EVENT_BUS.register(com.kve.common.mobrules.MobBehaviorFlagsEnforcer.class);
        NeoForge.EVENT_BUS.register(com.kve.common.mobrules.MobCombatEnforcer.class);
        NeoForge.EVENT_BUS.register(com.kve.common.itemrules.ItemAttributeEnforcer.class);
        com.kve.common.itemrules.CurioAttributeEnforcer.register();
        NeoForge.EVENT_BUS.register(com.kve.common.itemrules.ItemCombatEnforcer.class);

        NeoForge.EVENT_BUS.register(com.kve.common.lootrules.InteractLootEnforcer.class);

        NeoForge.EVENT_BUS.register(com.kve.common.structure.StructureSpawnCapRuntime.class);

        NeoForge.EVENT_BUS.register(com.kve.common.trade.TradeBootApplier.class);
        NeoForge.EVENT_BUS.register(com.kve.common.trade.MerchantOfferInjector.class);
        NeoForge.EVENT_BUS.register(com.kve.common.packrules.PackRulesRuntime.class);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(com.kve.common.kubejs.KubeJSMobExporter::deleteLegacyMobScriptOnBoot);
        event.enqueueWork(com.kve.common.persist.CraftEditorState::loadIfNeeded);
        // Same class of leftover-file issue, checked across every other
        // editor after the mob one turned up: only the mob script used a
        // top-level `const` (hard crash), but trades.js/kve_events.js/
        // crafteditor_add.js are the other three flat pre-reorg files
        // named in KveScriptPaths' class doc, and each would still run
        // twice - double trades, double event actions, double recipes -
        // if never cleaned up. TagExporter/KubeJSBlockAttrExporter/
        // KubeJSItemAttrExporter/LootTableExporter's scripts didn't
        // predate the per-subfolder reorg the same way, so they're not
        // included here.
        event.enqueueWork(com.kve.common.kubejs.TradeExporter::deleteLegacyTradesScriptOnBoot);
        event.enqueueWork(com.kve.common.kubejs.EventExporter::deleteLegacyEventsScriptOnBoot);
        event.enqueueWork(com.kve.common.kubejs.KubeJSExporter::deleteLegacyAddScriptOnBoot);
        event.enqueueWork(com.kve.common.kubejs.KveScriptPaths::deleteLegacyLooseScripts);

        event.enqueueWork(com.kve.common.trade.TradeBackendRegistry::registerDefaults);
    }
}
