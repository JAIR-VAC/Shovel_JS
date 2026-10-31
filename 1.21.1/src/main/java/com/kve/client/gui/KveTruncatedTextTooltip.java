package com.kve.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * cualquier texto que {@link KveGuiStyle#drawTextSafe} recorta
 * con "..." (nombres largos en listas, ids, titulos) muestra el texto completo
 * como tooltip al dejar el mouse encima medio segundo. Global: no hace falta
 * tocar ninguna pantalla.
 */
@OnlyIn(Dist.CLIENT)
public final class KveTruncatedTextTooltip {
    private KveTruncatedTextTooltip() {}

    private static final long DWELL_MS = 450;
    private static String lastText = null;
    private static long since = 0L;

    @SubscribeEvent
    public static void onRenderPre(ScreenEvent.Render.Pre event) {
        KveGuiStyle.hoveredTruncatedText = null;
    }

    @SubscribeEvent
    public static void onRenderPost(ScreenEvent.Render.Post event) {
        String text = KveGuiStyle.hoveredTruncatedText;
        long now = System.currentTimeMillis();
        if (text == null) { lastText = null; return; }
        if (!text.equals(lastText)) { lastText = text; since = now; return; }
        if (now - since < DWELL_MS) return;
        var g = event.getGuiGraphics();
        g.pose().pushPose();
        g.pose().translate(0, 0, 500);
        g.renderTooltip(Minecraft.getInstance().font, Component.literal(text), event.getMouseX(), event.getMouseY());
        g.pose().popPose();
    }
}
