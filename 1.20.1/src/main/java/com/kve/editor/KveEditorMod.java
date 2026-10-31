package com.kve.editor;

import com.kve.CraftEditorMod;
import com.kve.common.config.KVEConfig;
import com.kve.common.network.KVENetwork;
import com.kve.common.network.ServerPermissionSync;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(KveEditorMod.MODID)
public class KveEditorMod {

    public static final String MODID = "shovel_js";

    public KveEditorMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::clientSetup);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.kve.client.KveClientSetup.registerModBus(modBus));

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                modBus.addListener(com.kve.common.kubejs.CreativeTabIndex::onBuildContents));

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            KVEConfig.clientUiHook = com.kve.client.KveClientSetup::applyUiConfig;
            com.kve.client.KveClientSetup.applyUiConfig();
            com.kve.client.KveClientSetup.installPermissionHooks();
        });

        MinecraftForge.EVENT_BUS.register(ServerPermissionSync.class);
        CraftEditorMod.LOGGER.info("[Shovel JS] Editor loaded");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(KVENetwork::register);
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        com.kve.client.KveClientSetup.run();
    }
}
