package com.kve.common.trade;

import com.kve.CraftEditorMod;
import com.kve.common.kubejs.TradeExporter;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.bus.api.SubscribeEvent;

public final class TradeBootApplier {

    private TradeBootApplier() {}

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        try {
            TradeBackendRegistry.registerDefaults();
            int applied = TradeExporter.reapplyRuntimeFromState();
            if (applied > 0) {
                CraftEditorMod.LOGGER.info("[Shovel JS] Trades: {} entrada(s) re-aplicada(s) desde shovel_js_trades_state.json al iniciar.", applied);
            }
        } catch (Throwable t) {
            // Nunca tumbar el arranque del servidor por esto.
            CraftEditorMod.LOGGER.error("[Shovel JS] Trades: fallo re-aplicando trades al iniciar: {}", t.toString());
        }
    }
}
