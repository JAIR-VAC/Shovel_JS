package com.kve.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;

@OnlyIn(Dist.CLIENT)
public final class KveEffectInfo {

    private KveEffectInfo() {}

    public static final int COLOR_BENEFICIAL = 0xFF7DE0B0;
    public static final int COLOR_HARMFUL    = 0xFFE88080;
    public static final int COLOR_NEUTRAL    = 0xFFB8C4D0;

    private static MobEffect get(String id) {
        ResourceLocation rl = id == null ? null : ResourceLocation.tryParse(id);
        return rl == null ? null : ForgeRegistries.MOB_EFFECTS.getValue(rl);
    }

    /** True si el id existe en el registro. */
    public static boolean exists(String id) { return get(id) != null; }

    public static String name(String id) {
        MobEffect e = get(id);
        if (e == null) return id == null ? "" : id;
        String t = Component.translatable(e.getDescriptionId()).getString();
        return t == null || t.isEmpty() || t.equals(e.getDescriptionId()) ? id : t;
    }

    /** Verde = beneficioso, rojo = perjudicial, gris = neutral o desconocido. */
    public static int categoryColor(String id) {
        MobEffect e = get(id);
        if (e == null) return COLOR_NEUTRAL;
        MobEffectCategory c = e.getCategory();
        return c == MobEffectCategory.BENEFICIAL ? COLOR_BENEFICIAL
                : c == MobEffectCategory.HARMFUL ? COLOR_HARMFUL : COLOR_NEUTRAL;
    }

    /** Icono del efecto escalado a {@code size}px. No dibuja nada si no se resuelve. */
    public static void drawIcon(GuiGraphics g, String id, int x, int y, int size) {
        MobEffect e = get(id);
        if (e == null) return;
        try {
            TextureAtlasSprite sprite = Minecraft.getInstance().getMobEffectTextures().get(e);
            g.blit(x, y, 0, size, size, sprite);
        } catch (Exception ignored) {}
    }

    /** Nivel visible a partir del amplifier (0 -> "I"). Romano hasta X, numero despues. */
    public static String level(int amplifier) {
        int n = amplifier + 1;
        String[] roman = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 1 && n <= roman.length ? roman[n - 1] : String.valueOf(n);
    }

    /** Ticks -> "5s", "1m 30s", "2h 5m". Negativo = infinito. */
    public static String duration(int ticks) {
        if (ticks < 0) return "∞";
        double secs = ticks / 20.0;
        if (secs < 60) {
            return (secs == Math.floor(secs) ? String.valueOf((long) secs) : String.format(Locale.ROOT, "%.1f", secs)) + "s";
        }
        long t = Math.round(secs);
        long h = t / 3600, m = (t % 3600) / 60, s = t % 60;
        if (h > 0) return h + "h" + (m > 0 ? " " + m + "m" : "");
        return m + "m" + (s > 0 ? " " + s + "s" : "");
    }

    /** Probabilidad 0..1 -> "50%" / "12.5%". */
    public static String percent(double chance) {
        double pct = Math.round(chance * 1000) / 10.0;
        return (pct == Math.floor(pct) ? String.valueOf((long) pct) : String.valueOf(pct)) + "%";
    }
}
