package com.kve.editor;

import com.kve.CraftEditorMod;
import com.kve.common.config.KVEConfig;
import com.kve.common.network.KVENetwork;
import com.kve.common.network.ServerPermissionSync;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.common.NeoForge;

@Mod(KveEditorMod.MODID)
public class KveEditorMod {

    public static final String MODID = "shovel_js";

    public KveEditorMod(IEventBus modBus, ModContainer container) {
        modBus.addListener(this::clientSetup);
        modBus.register(KVENetwork.class);

        boolean client = FMLLoader.getDist() == Dist.CLIENT;
        if (client) {
            com.kve.client.KveClientSetup.registerModBus(modBus);
            modBus.addListener(com.kve.common.kubejs.CreativeTabIndex::onBuildContents);
            KVEConfig.clientUiHook = com.kve.client.KveClientSetup::applyUiConfig;
            com.kve.client.KveClientSetup.applyUiConfig();
            com.kve.client.KveClientSetup.installPermissionHooks();
        }

        NeoForge.EVENT_BUS.register(ServerPermissionSync.class);
        CraftEditorMod.LOGGER.info("[Shovel JS] Editor loaded");
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        com.kve.client.KveClientSetup.run();
    }
}
