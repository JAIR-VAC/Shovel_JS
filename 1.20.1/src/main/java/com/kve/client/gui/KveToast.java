package com.kve.client.gui;

import com.kve.client.gui.workspace.pending.ApplyFeedback;
import com.kve.client.gui.workspace.pending.PendingChangeManager;
import com.kve.common.Lang;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * notificacion corta en la esquina (el sistema de toasts de
 * vanilla) con la paleta del mod - para avisos que no deben depender de que
 * el jugador este mirando una franja concreta de la pantalla (cambios
 * aplicados, id copiado, favorito marcado...). Se ven encima de cualquier
 * pantalla abierta y se van solos. El texto sigue el tamanio de letra de
 * Ajustes y el mensaje se reparte en varias lineas en vez de cortarse.
 */
@OnlyIn(Dist.CLIENT)
public final class KveToast implements Toast {

    public enum Kind { OK, INFO, WARN }

    private static final long DURATION_MS = 3000L;
    /** Los avisos de Aplicar traen mas texto: se quedan mas tiempo. */
    private static final long APPLIED_MS = 6000L;
    private static final int BASE_W = 170, APPLIED_W = 220, PAD_X = 8, MAX_LINES = 5;

    private final Kind kind;
    private final String title;
    private final String message;
    private final long duration;
    private final int w;
    private List<String> lines;
    private float linesScale = -1f;

    private KveToast(Kind kind, String title, String message, long duration, int baseW) {
        this.kind = kind;
        this.title = title == null ? "" : title;
        this.message = message == null ? "" : message;
        this.duration = duration;
        this.w = Math.round(baseW * Math.max(1f, scale()));
    }

    /** Muestra un aviso; seguro de llamar desde cualquier pantalla del cliente. */
    public static void show(Kind kind, String title, String message) {
        add(new KveToast(kind, title, message, DURATION_MS, BASE_W));
    }

    public static void ok(String title, String message)   { show(Kind.OK, title, message); }
    public static void info(String title, String message) { show(Kind.INFO, title, message); }
    public static void warn(String title, String message) { show(Kind.WARN, title, message); }

    /**
     * Resultado de un Aplicar: que se aplico y si hace falta /reload o reiniciar. Manda el /reload
     * solo si esta activado en Ajustes. {@code warnings} son las entradas que no se pudieron escribir.
     */
    public static ApplyFeedback.Feedback applied(PendingChangeManager.ApplyResult result, List<String> warnings) {
        ApplyFeedback.Feedback fb = ApplyFeedback.handle(result);
        String msg = fb.message();
        if (warnings != null && !warnings.isEmpty()) {
            msg += " " + Lang.get("gui.kve.apply_result.skipped", warnings.size()) + ": " + warnings.get(0);
        }
        Kind kind = fb.level() == ApplyFeedback.Level.WARN || (warnings != null && !warnings.isEmpty()) ? Kind.WARN : Kind.OK;
        add(new KveToast(kind, Lang.get("gui.kve.toast.applied_title"), msg, APPLIED_MS, APPLIED_W));
        return fb;
    }

    public static ApplyFeedback.Feedback applied(PendingChangeManager.ApplyResult result) {
        return applied(result, List.of());
    }

    private static void add(KveToast t) {
        try {
            Minecraft.getInstance().getToasts().addToast(t);
        } catch (Throwable ignored) {
        }
    }

    private static float scale() { return Math.max(0.5f, KveGuiStyle.uiTextScale()); }

    private int lineH() { return Math.round(Minecraft.getInstance().font.lineHeight * scale()) + 1; }

    /** Lineas del mensaje ya cortadas al ancho; se rearman si cambia la letra en Ajustes. */
    private List<String> lines() {
        float s = scale();
        if (lines == null || s != linesScale) {
            linesScale = s;
            Font font = Minecraft.getInstance().font;
            int wrapW = Math.max(20, Math.round((w - PAD_X - 4) / s));
            lines = new ArrayList<>();
            for (var seq : font.split(Component.literal(message), wrapW)) {
                StringBuilder sb = new StringBuilder();
                seq.accept((idx, style, cp) -> { sb.appendCodePoint(cp); return true; });
                lines.add(sb.toString());
                if (lines.size() == MAX_LINES) break;
            }
        }
        return lines;
    }

    @Override
    public int width() { return w; }

    @Override
    public int height() { return 9 + lineH() * (1 + Math.max(1, lines().size())) + 5; }

    @Override
    public Visibility render(GuiGraphics g, ToastComponent toasts, long timeSinceVisible) {
        Font font = Minecraft.getInstance().font;
        int accent = switch (kind) {
            case OK -> KveGuiStyle.TEXT_OK;
            case WARN -> KveGuiStyle.TEXT_WARN;
            case INFO -> KveGuiStyle.ACCENT_GOLD;
        };
        int h = height();
        g.fill(0, 0, w, h, KveGuiStyle.BG_PANEL);
        g.fill(0, 0, 3, h, accent);
        g.renderOutline(0, 0, w, h, KveGuiStyle.BORDER_OUTER);
        float s = scale();
        KveGuiStyle.drawText(g, KveGuiStyle.truncate(font, title, Math.round((w - PAD_X - 4) / s)), PAD_X, 6, accent);
        int y = 6 + lineH() + 1;
        for (String line : lines()) {
            KveGuiStyle.drawText(g, line, PAD_X, y, KveGuiStyle.TEXT_PRIMARY);
            y += lineH();
        }
        float left = 1f - Math.min(1f, timeSinceVisible / (float) duration);
        g.fill(3, h - 2, 3 + (int) ((w - 4) * left), h - 1, accent);
        return timeSinceVisible >= duration ? Visibility.HIDE : Visibility.SHOW;
    }
}
