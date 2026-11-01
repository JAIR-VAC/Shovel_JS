package com.kve.client.gui;

import com.kve.common.Lang;
import com.kve.client.gui.workspace.pending.PendingChangeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class KveGuiStyle {
    private KveGuiStyle() {}

    private static float uiTextScale = 1.0f;

    /** Leido por cada draw* de texto de esta clase; espejo de KVEConfig.uiTextScale(). */
    public static float uiTextScale() { return uiTextScale; }

    /** Tamanio de la casilla de seleccion multiple. */
    public static final int CHECK = 9;

    /** Casilla de seleccion multiple, la misma de "Editar varias": marco y relleno verde si esta marcada. */
    public static void drawCheck(GuiGraphics g, int x, int y, boolean on, boolean hov) {
        g.fill(x, y, x + CHECK, y + CHECK, 0xFF0A1220);
        g.renderOutline(x, y, CHECK, CHECK, hov ? ACCENT_GOLD : BORDER_FIELD);
        if (on) g.fill(x + 2, y + 2, x + CHECK - 2, y + CHECK - 2, TEXT_OK);
    }

    /** Ancho de un texto con la escala de letra de Ajustes (para menus y listas que dibujan su propio texto). */
    public static int textWidth(String text) {
        return Math.round(Minecraft.getInstance().font.width(text) * uiTextScale);
    }

    /** Alto de una fila de texto de {@code base} px a escala 1, ajustado a la escala de letra. */
    public static int rowHeight(int base) {
        return Math.max(base - 2, Math.round(base * Math.max(1f, uiTextScale)));
    }

    /** Dibuja texto con la escala de letra de Ajustes, sin recortar. */
    public static void drawText(GuiGraphics g, String text, int x, int y, int color) {
        Font font = Minecraft.getInstance().font;
        if (uiTextScale > 0.999f && uiTextScale < 1.001f) { g.drawString(font, text, x, y, color, false); return; }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(uiTextScale, uiTextScale, 1f);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    /**
     * Escala que deben usar los tooltips de vanilla mientras {@code screen}
     * esta abierta: la del mod si es una pantalla del mod (incluido el shell
     * de KveStudio), 1 en cualquier otra. Ver GuiGraphicsTooltipScaleMixin.
     */
    /** Limite de FPS de las pantallas del editor con "Ahorro de recursos" (ver MinecraftFramerateMixin). */
    public static final int EDITOR_FPS_CAP = 120;

    private static boolean powerSaver() {
        return com.kve.client.gui.KveClientSettings.getOrDefault(com.kve.client.gui.KveClientSettings.EDITOR_POWER_SAVER, true);
    }

    private static boolean fpsLimit() {
        return com.kve.client.gui.KveClientSettings.getOrDefault(com.kve.client.gui.KveClientSettings.EDITOR_FPS_LIMIT, true);
    }

    /** True si el mundo no hace falta dibujarlo: el Studio lo tapa entero (ver GameRendererEditorMixin). */
    public static boolean shouldSkipWorldRender(net.minecraft.client.gui.screens.Screen screen) {
        return (screen instanceof com.kve.client.gui.studio.KveStudioShell || screen instanceof KveOpaqueScreen) && powerSaver();
    }

    /** True si la pantalla abierta es del editor y hay que limitarla a {@link #EDITOR_FPS_CAP}. */
    public static boolean shouldCapEditorFps(net.minecraft.client.gui.screens.Screen screen) {
        return screen != null && screen.getClass().getName().startsWith("com.kve.") && fpsLimit();
    }

    public static float tooltipScaleFor(net.minecraft.client.gui.screens.Screen screen) {
        if (screen == null || !screen.getClass().getName().startsWith("com.kve.")) return 1f;
        return uiTextScale;
    }

 /** Llamado por KVEConfig al cargar/guardar y por el boton de ciclo en KveSettingsScreen. Clamp defensivo: nunca 0 ni negativo (dividiria por cero en drawTextSafeScaled()). */
    public static void setUiTextScale(float scale) {
        uiTextScale = Math.max(0.5f, Math.min(1.25f, scale));
    }

    private static final int BASE_BG_PANEL      = 0xF0080C1A;
    private static final int BASE_BG_TITLEBAR   = 0xFF0D1B2A;
    private static final int BASE_BG_FIELD      = 0xFF0A1220;
    private static final int BASE_BORDER_SUBTLE = 0xFF2A5577;
    private static final int BASE_BORDER_FIELD  = 0xFF1A3050;
    private static final int BASE_TEXT_PRIMARY  = 0xFFCCDDFF;
    private static final int BASE_TEXT_LABEL    = 0xFF99BBCC;
    private static final int BASE_TEXT_MUTED    = 0xFF667788;

    public static int BG_PANEL      = BASE_BG_PANEL;
    public static int BG_TITLEBAR   = BASE_BG_TITLEBAR;
    public static int BG_FIELD      = BASE_BG_FIELD;
    public static int BORDER_OUTER  = 0xFF4A8FBF;
    public static int BORDER_SUBTLE = BASE_BORDER_SUBTLE;
    public static int BORDER_FIELD  = BASE_BORDER_FIELD;
    public static int ACCENT_GOLD   = 0xFFE8C96A;
    public static int ACCENT_PURPLE = 0xFF7F77DD;
    public static int TEXT_PRIMARY  = BASE_TEXT_PRIMARY;
    public static int TEXT_LABEL    = BASE_TEXT_LABEL;
    public static int TEXT_MUTED    = BASE_TEXT_MUTED;
    public static final int TEXT_OK       = 0xFF66DD99;
    public static final int TEXT_WARN     = 0xFFFF8844;
    public static final int TEXT_ERROR    = 0xFFFF6644;

    public static final int SYNTAX_KEYWORD = 0xFF569CD6;
    public static final int SYNTAX_STRING  = 0xFFCE9178;
    public static final int SYNTAX_NUMBER  = 0xFFB5CEA8;
    public static final int SYNTAX_COMMENT = 0xFF6A9955;
    public static final int SYNTAX_API     = 0xFFDCDCAA;
    public static final int SYNTAX_PLAIN   = 0xFFD4D4D4;
    public static final int SYNTAX_PUNCT   = 0xFF808080;

    public enum Theme {
        CYAN(0xFF4A8FBF, 0xFFE8C96A, 0xFF7F77DD, null),
        GOLD(0xFFB8883A, 0xFFF0D080, 0xFFD9A85C, null),
        PURPLE(0xFF7F77DD, 0xFFD8C9F0, 0xFF9F8FE0, null),
        GREEN(0xFF4AA87B, 0xFFBFE0A0, 0xFF6FCC99, null),
        BRONZE(0xFFC8834F, 0xFFEFB45A, 0xFFE39A6E, new Palette(
                0xF2221611,
                0xFF2F1F17,
                0xFF140D09,
                0xFF6E4630,
                0xFF432A1D,
                0xFFF5E6D8,
                0xFFC29E84,
                0xFF8A6B57
        )),
        // Tema "elegante oscuro". Mismo
        // tratamiento que BRONZE arriba: paleta a mano completa en vez de
        // un retint HSV del navy BASE_, porque retintar un navy hacia negro
        // via HSV solo le baja el brillo (V) y termina en un gris-azulado
        // apagado, no en el negro/grafito real que "elegante oscuro"
        // implica - el mismo motivo por el que BRONZE trae su propia
        // Palette en vez de derivarse. Acento dorado clasico (mismo tono
        // que ACCENT_GOLD de CYAN, 0xFFE8C96A, pero un poco mas calido/
        // saturado, 0xFFD4AF37 - "gold" heraldico) sobre grafito casi
        // negro: look tipo "panel de vidrio esmerilado con detalle en oro"
        // en vez del navy+cyan por defecto. ACCENT_PURPLE se mantiene en la
        // misma familia violeta apagada que el resto de temas (no dorado)
        // para que los elementos "featured/highlighted" sigan
        // distinguiendose del texto/acento normal.
        DARK(0xFF6E6E78, 0xFFD4AF37, 0xFF9A8FD0, new Palette(
                0xF2131316,
                0xFF1B1B1F,
                0xFF0A0A0C,
                0xFF3A3A42,
                0xFF242429,
                0xFFEDEDF0,
                0xFFA8A8B2,
                0xFF6C6C76
        )),
        SILVER(0xFFAEAEB6, 0xFFE2E2E2, 0xFFC4C4CE, null),
        SAGE(0xFF8FA888, 0xFFCBDBB9, 0xFFA9BA9B, null),
        SLATE(0xFF7E8FA3, 0xFFC8D1DD, 0xFF9BA7B9, null),
        // Tema "blanco": SILVER ya
        // se llamaba "plata/blanco" en el comentario de arriba, pero sigue
        // retiñendo BASE_BG_PANEL (navy oscuro, 0xF0080C1A) via HSV - el
        // motor de tint solo reemplaza el HUE y escala saturacion/valor
        // proporcionalmente, así que un panel oscuro con sat~0 sigue dando
        // un GRIS OSCURO, nunca blanco real (mismo motivo por el que
        // BRONZE/DARK no usan el motor de retint: invertir V de oscuro a
        // claro no es algo que #tint pueda hacer, solo reescalarlo).
        // Mismo tratamiento que BRONZE/DARK entonces: Palette a mano
        // completa con fondos claros/blancos y texto oscuro, más sus propias
        // ramas en #applyPlateColors()/#applyScrollColors() (ver abajo) -
        // blend(BG_FIELD, negro,...) para el bisel de botones asume
        // BG_FIELD oscuro y da bordes casi invisibles sobre un panel claro.
        WHITE(0xFF5A6B8C, 0xFFB8860B, 0xFF7A6FD1, new Palette(
                0xF2F2F3F5,
                0xFFE2E5EA,
                0xFFFFFFFF,
                0xFFC7CCD4,
                0xFFB0B6C0,
                0xFF1A1D22,
                0xFF4A5060,
                0xFF8A8F99
        ));

        public final int border, gold, purple;
        /** Full hand-picked palette override; null = derive via HSV retint of the CYAN-family BASE_ values instead (see {@link #applyTheme()}). */
        public final Palette palette;

        Theme(int border, int gold, int purple, Palette palette) {
            this.border = border; this.gold = gold; this.purple = purple; this.palette = palette;
        }

        public static Theme fromId(String id) {
            if (id == null) return BRONZE;
            for (Theme t : values()) if (t.name().equalsIgnoreCase(id.trim())) return t;
            return BRONZE;
        }
    }

    /** Full BG/BORDER/TEXT override for a {@link Theme} that doesn't fit the CYAN-family retint. */
    public static final class Palette {
        public final int bgPanel, bgTitlebar, bgField, borderSubtle, borderField, textPrimary, textLabel, textMuted;
        public Palette(int bgPanel, int bgTitlebar, int bgField, int borderSubtle, int borderField,
                       int textPrimary, int textLabel, int textMuted) {
            this.bgPanel = bgPanel; this.bgTitlebar = bgTitlebar; this.bgField = bgField;
            this.borderSubtle = borderSubtle; this.borderField = borderField;
            this.textPrimary = textPrimary; this.textLabel = textLabel; this.textMuted = textMuted;
        }
    }

    private static Theme currentTheme = Theme.BRONZE;

    public static Theme currentTheme() { return currentTheme; }

    public static void applyTheme(Theme theme) {
        currentTheme = theme;
        BORDER_OUTER  = theme.border;
        ACCENT_GOLD   = theme.gold;
        ACCENT_PURPLE = theme.purple;
        if (theme.palette != null) {
            tinted = false;
            TINT_CACHE.clear();
            BG_PANEL      = theme.palette.bgPanel;
            BG_TITLEBAR   = theme.palette.bgTitlebar;
            BG_FIELD      = theme.palette.bgField;
            BORDER_SUBTLE = theme.palette.borderSubtle;
            BORDER_FIELD  = theme.palette.borderField;
            TEXT_PRIMARY  = theme.palette.textPrimary;
            TEXT_LABEL    = theme.palette.textLabel;
            TEXT_MUTED    = theme.palette.textMuted;
        } else {
            applyAccentColor((theme.gold >> 16) & 0xFF, (theme.gold >> 8) & 0xFF, theme.gold & 0xFF);
        }
        applyPlateColors(theme);
    }

    public static int PLATE_HI = 0xFF2A4258;
    public static int PLATE_LO = 0xFF13202E;
    public static int BORDER_T = 0xFF7FBFE8;
    public static int BORDER_B = 0xFF2A5577;
    public static int CORNER_MARK = 0xFF2A5577;

    public static int SCROLL_TRACK        = 0xFF0A1220;
    public static int SCROLL_TRACK_BORDER = 0xFF1A3050;
    public static int SCROLL_THUMB        = 0xFF4A8FBF;
    public static int SCROLL_THUMB_DRAG   = 0xFFE8C96A;
    public static int SCROLL_THUMB_HI     = 0xFF7FBFE8;
    public static int SCROLL_THUMB_LO     = 0xFF2A5577;

    private static void applyPlateColors(Theme theme) {
        if (theme == Theme.BRONZE) {
            PLATE_HI = 0xFF4A2F21;
            PLATE_LO = 0xFF22150F;
            BORDER_T = 0xFFE0A577;
            BORDER_B = 0xFF7E4A2C;
            CORNER_MARK = 0xFFC8834F;
        } else if (theme == Theme.DARK) {
            PLATE_HI = 0xFF2E2E33;
            PLATE_LO = 0xFF08080A;
            BORDER_T = 0xFFE8D8A0;
            BORDER_B = 0xFF8A7020;
            CORNER_MARK = 0xFF3A3A42;
        } else if (theme == Theme.WHITE) {
            PLATE_HI = 0xFFFFFFFF;
            PLATE_LO = 0xFFD5D9DF;
            BORDER_T = 0xFFFFFFFF;
            BORDER_B = 0xFF8A93A3;
            CORNER_MARK = 0xFFB0B6C0;
        } else {
            PLATE_HI = blend(BG_FIELD, 0xFFFFFFFF, 0.18f);
            PLATE_LO = blend(BG_FIELD, 0xFF000000, 0.12f);
            BORDER_T = blend(BORDER_OUTER, 0xFFFFFFFF, 0.30f);
            BORDER_B = blend(BORDER_OUTER, 0xFF000000, 0.25f);
            CORNER_MARK = BORDER_SUBTLE;
        }
        applyScrollColors(theme);
    }

    private static void applyScrollColors(Theme theme) {
        if (theme == Theme.BRONZE) {
            SCROLL_TRACK        = BG_FIELD;
            SCROLL_TRACK_BORDER = BORDER_FIELD;
            SCROLL_THUMB        = BORDER_B;
            SCROLL_THUMB_DRAG   = ACCENT_GOLD;
            SCROLL_THUMB_HI     = BORDER_T;
            SCROLL_THUMB_LO     = BORDER_FIELD;
        } else if (theme == Theme.DARK) {
            SCROLL_TRACK        = 0xFF0A0A0C;
            SCROLL_TRACK_BORDER = 0xFF242429;
            SCROLL_THUMB        = 0xFF6E6E78;
            SCROLL_THUMB_DRAG   = 0xFFD4AF37;
            SCROLL_THUMB_HI     = 0xFFE8D8A0;
            SCROLL_THUMB_LO     = 0xFF3A3A42;
        } else if (theme == Theme.WHITE) {
            SCROLL_TRACK        = 0xFFE8EAED;
            SCROLL_TRACK_BORDER = 0xFFB0B6C0;
            SCROLL_THUMB        = 0xFF5A6B8C;
            SCROLL_THUMB_DRAG   = 0xFFB8860B;
            SCROLL_THUMB_HI     = 0xFF8895AC;
            SCROLL_THUMB_LO     = 0xFF3E4A61;
        } else {
            SCROLL_TRACK        = BG_FIELD;
            SCROLL_TRACK_BORDER = BORDER_FIELD;
            SCROLL_THUMB        = BORDER_OUTER;
            SCROLL_THUMB_DRAG   = ACCENT_GOLD;
            SCROLL_THUMB_HI     = blend(BORDER_OUTER, 0xFFFFFFFF, 0.30f);
            SCROLL_THUMB_LO     = blend(BORDER_OUTER, 0xFF000000, 0.25f);
        }
    }

    // Referencia: el dorado de CYAN (0xFFE8C96A) es el color "sin tinte" -
    // cada BASE_* de arriba se eligio contra ese tono. Se calcula una vez desde
    // el propio valor para que nunca se desincronice de Theme.CYAN.
    private static final float REF_SAT;
    private static final float REF_LIGHT;
    static {
        float[] hsl = toHsl(0xE8, 0xC9, 0x6A);
        REF_SAT = hsl[1];
        REF_LIGHT = hsl[2];
    }

    private static boolean tinted;
    private static float accentHue, accentSat, accentLight;
    private static final java.util.Map<Integer, Integer> TINT_CACHE = new java.util.HashMap<>();

    /**
     * Recalcula cada campo retintable (BG_/BORDER_SUBTLE/BORDER_FIELD/TEXT_)
     * desde su valor BASE_, llevado al tono del acento en HSL: el tono se
     * reemplaza, la saturacion se escala segun cuan saturado es el acento y
     * la luminosidad del BASE_ se conserva (con un ajuste leve segun lo claro
     * que sea el acento), asi un fondo oscuro sigue oscuro y un texto claro
     * sigue claro. El dorado de CYAN (232,201,106) es "sin tema": no se
     * retiñe y el look por defecto queda identico, sin pasar por una
     * conversion de color.
     */
    public static void applyAccentColor(int r, int g, int b) {
        float[] hsl = toHsl(r, g, b);
        accentHue = hsl[0];
        accentSat = hsl[1];
        accentLight = hsl[2];
        tinted = !(r == 0xE8 && g == 0xC9 && b == 0x6A);
        TINT_CACHE.clear();
        BG_PANEL      = tint(BASE_BG_PANEL, false);
        BG_TITLEBAR   = tint(BASE_BG_TITLEBAR, false);
        BG_FIELD      = tint(BASE_BG_FIELD, false);
        BORDER_SUBTLE = tint(BASE_BORDER_SUBTLE, false);
        BORDER_FIELD  = tint(BASE_BORDER_FIELD, false);
        TEXT_PRIMARY  = tint(BASE_TEXT_PRIMARY, true);
        TEXT_LABEL    = tint(BASE_TEXT_LABEL, true);
        TEXT_MUTED    = tint(BASE_TEXT_MUTED, true);
    }

    // =========================================================
    // Custom accent color: a diferencia
    // de Theme (paletas fijas cicladas con un boton), esto deja al jugador
    // tipear CUALQUIER color como codigo hex y lo aplica directo, sin pasar
    // por ningun Theme predefinido. Reusa el mismo motor de retint HSV que
    // ya usan CYAN/GOLD/PURPLE/GREEN/SILVER/SAGE/SLATE (#applyAccentColor())
    // para los fondos/texto derivados, pero ADEMAS pisa BORDER_OUTER/
    // ACCENT_GOLD/ACCENT_PURPLE con el color EXACTO que el jugador tipeo
    // (los Theme normales los eligen a mano por separado, ver el enum
    // arriba) - asi el acento que ve es literalmente el elegido, no una
    // aproximacion derivada.
    // =========================================================

    private static String customAccentHex = "";

    /** Codigo hex activo ("RRGGBB", sin '#') o "" si no hay ninguno (tema normal). */
    public static String customAccentHex() { return customAccentHex; }

    /**
     * Parsea "RRGGBB"/"#RRGGBB" (o la forma corta "RGB"/"#RGB") a un entero
     * 0xRRGGBB. Devuelve null si {@code hex} no es un color valido (longitud
     * incorrecta o digitos no hexadecimales) - el llamador decide que hacer
     * (KveSettingsScreen muestra un flash de error y no cambia nada).
     */
    public static Integer parseHexColor(String hex) {
        if (hex == null) return null;
        String h = hex.trim();
        if (h.startsWith("#")) h = h.substring(1);
        if (h.length() == 3) {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 3; i++) { char c = h.charAt(i); sb.append(c).append(c); }
            h = sb.toString();
        }
        if (h.length() != 6) return null;
        try { return Integer.parseInt(h, 16); } catch (NumberFormatException e) { return null; }
    }

    /**
     * Aplica {@code hex} como acento personalizado - ver comentario de
     * seccion arriba. No toca nada y devuelve false si {@code hex} no
     * parsea (ver {@link #parseHexColor()}). Llamado desde KVEConfig.load
     * (sesion siguiente) y desde KveSettingsScreen (en vivo).
     */
    public static boolean applyCustomAccentColor(String hex) {
        Integer rgb = parseHexColor(hex);
        if (rgb == null) return false;
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        customAccentHex = String.format(java.util.Locale.ROOT, "%06X", rgb);
        applyAccentColor(r, g, b);
        int argb = 0xFF000000 | rgb;
        ACCENT_GOLD   = readableAccent(argb);
        BORDER_OUTER  = blend(argb, BG_PANEL | 0xFF000000, 0.35f);
        ACCENT_PURPLE = readableAccent(hueShift(argb, 30f / 360f));
        applyGenericPlateAndScrollColors();
        return true;
    }

    private static int readableAccent(int argb) {
        int out = argb;
        for (int i = 0; i < 8 && luminance(out) < 0.40f; i++) out = blend(out, 0xFFFFFFFF, 0.18f);
        return out;
    }

    private static float luminance(int argb) {
        return (0.2126f * ((argb >> 16) & 0xFF) + 0.7152f * ((argb >> 8) & 0xFF) + 0.0722f * (argb & 0xFF)) / 255f;
    }

    private static int hueShift(int argb, float delta) {
        float[] hsl = toHsl((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF);
        return (argb & 0xFF000000) | fromHsl(hsl[0] + delta, hsl[1], hsl[2]);
    }

    // =========================================================
    // Colores fijos de las pantallas: ~70 pantallas
    // dibujaban con literales de la paleta CYAN por defecto (fila
    // seleccionada 0xFF2A5E9A, divisores 0xFF2A4A6A, marcos de icono,
    // grises azulados de texto...). Con cualquier otro tema quedaban
    // manchas azules sobre un panel dorado/rosa/blanco. th traduce esos
    // literales al rol equivalente del tema activo; con CYAN sin acento
    // personalizado devuelve el mismo valor (el look por defecto no cambia).
    // Colores semanticos (verde/rojo/naranja), blanco y negro no se tocan.
    // =========================================================

    private static boolean themedLook() {
        return currentTheme != Theme.CYAN || !customAccentHex.isEmpty();
    }

    /** Fondo de la fila/elemento seleccionado, en la familia del tema. */
    public static int selectionBg() {
        return themedLook() ? blend(BG_FIELD, BORDER_OUTER, 0.45f) : 0xFF2A5E9A;
    }

    /** Traduce un color literal de la paleta por defecto al rol equivalente del tema activo. */
    public static int th(int argb) {
        if (!themedLook()) return argb;
        int a = argb & 0xFF000000;
        int rgb = switch (argb & 0xFFFFFF) {
            case 0xE8C96A -> ACCENT_GOLD;
            case 0x4A8FBF -> BORDER_OUTER;
            case 0x7F77DD -> ACCENT_PURPLE;
            case 0x0A1220, 0x080C18 -> BG_FIELD;
            case 0x14243A -> blend(BG_FIELD, BORDER_FIELD, 0.6f);
            case 0x1A3050 -> BORDER_FIELD;
            case 0x2A4A6A, 0x2A5577 -> BORDER_SUBTLE;
            case 0x2A5E9A -> selectionBg();
            case 0x334455 -> blend(BORDER_FIELD, TEXT_MUTED, 0.5f);
            case 0x445566 -> blend(BORDER_FIELD, TEXT_MUTED, 0.75f);
            case 0x556677, 0x667788, 0x778899 -> TEXT_MUTED;
            case 0x8899BB, 0x88AACC, 0x99AACC, 0x8899CC, 0x8888BB, 0x6A99BB, 0x5A8AAA -> TEXT_LABEL;
            case 0xCCCCFF, 0xCCDDFF -> TEXT_PRIMARY;
            case 0x60A8F0, 0x7BC8E0 -> blend(BORDER_OUTER, TEXT_PRIMARY, 0.30f);
            default -> argb;
        };
        return a | (rgb & 0xFFFFFF);
    }

    /** Vuelve al Theme actual (descarta el acento personalizado). */
    public static void clearCustomAccentColor() {
        customAccentHex = "";
        applyTheme(currentTheme);
    }

    /**
     * Corre la misma rama "generica" (no BRONZE/DARK/WHITE) que
     * {@link #applyPlateColors()}/{@link #applyScrollColors()} ya usan para
     * CYAN/GOLD/PURPLE/GREEN/SILVER/SAGE/SLATE - Theme.CYAN nunca matchea
     * ninguno de esos 3 casos especiales, asi que pasarlo aca equivale a
     * correr la rama generica directamente sin duplicar su logica.
     */
    private static void applyGenericPlateAndScrollColors() {
        applyPlateColors(Theme.CYAN);
    }

    private static int tint(int argb, boolean text) {
        if (!tinted) return argb;
        int key = text ? ~argb : argb;
        Integer cached = TINT_CACHE.get(key);
        if (cached != null) return cached;
        float[] hsl = toHsl((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF);
        float satRatio = REF_SAT < 1.0e-4f ? 1f : accentSat / REF_SAT;
        float sat = Math.min(text ? 0.22f : 0.95f, hsl[1] * Math.max(0.35f, Math.min(1.15f, satRatio)));
        float lightRatio = REF_LIGHT < 1.0e-4f ? 1f : accentLight / REF_LIGHT;
        float light = clamp01(hsl[2] * (0.85f + 0.15f * lightRatio));
        int out = (argb & 0xFF000000) | fromHsl(accentHue, sat, light);
        TINT_CACHE.put(key, out);
        return out;
    }

    private static float[] toHsl(int r, int g, int b) {
        float rf = r / 255f, gf = g / 255f, bf = b / 255f;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float light = (max + min) / 2f;
        float chroma = max - min;
        if (chroma < 1.0e-5f) return new float[]{0f, 0f, light};
        float sat = chroma / (1f - Math.abs(2f * light - 1f));
        float sector;
        if (max == rf)      sector = (gf - bf) / chroma + (gf < bf ? 6f : 0f);
        else if (max == gf) sector = (bf - rf) / chroma + 2f;
        else                sector = (rf - gf) / chroma + 4f;
        return new float[]{sector / 6f, Math.min(1f, sat), light};
    }

    private static int fromHsl(float hue, float sat, float light) {
        float chroma = (1f - Math.abs(2f * light - 1f)) * sat;
        float sector = (((hue % 1f) + 1f) % 1f) * 6f;
        float second = chroma * (1f - Math.abs(sector % 2f - 1f));
        float r = 0f, g = 0f, b = 0f;
        switch ((int) sector) {
            case 0 -> { r = chroma; g = second; }
            case 1 -> { r = second; g = chroma; }
            case 2 -> { g = chroma; b = second; }
            case 3 -> { g = second; b = chroma; }
            case 4 -> { r = second; b = chroma; }
            default -> { r = chroma; b = second; }
        }
        float base = light - chroma / 2f;
        return (clamp255(Math.round((r + base) * 255f)) << 16)
                | (clamp255(Math.round((g + base) * 255f)) << 8)
                | clamp255(Math.round((b + base) * 255f));
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    private static int blend(int argb, int towardRgb, float amount) {
        int a = argb >>> 24;
        int r = clamp255((int) lerp((argb >> 16) & 0xFF, (towardRgb >> 16) & 0xFF, amount));
        int g = clamp255((int) lerp((argb >> 8) & 0xFF, (towardRgb >> 8) & 0xFF, amount));
        int b = clamp255((int) lerp(argb & 0xFF, towardRgb & 0xFF, amount));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Fondo del lienzo de nodos (grafo de recetas, editor de maquina): un paso mas oscuro que los campos del tema activo. */
    public static int canvasBg() { return 0xFF000000 | (blend(BG_FIELD, 0x000000, 0.15f) & 0xFFFFFF); }

    /** Puntos de la grilla del lienzo de nodos, sobre {@link #canvasBg()}. */
    public static int canvasDot() { return 0xFF000000 | (blend(canvasBg(), TEXT_MUTED, 0.3f) & 0xFFFFFF); }

    private static float lerp(float from, float to, float t) { return from + (to - from) * t; }
    private static int clamp255(int v) { return Math.max(0, Math.min(255, v)); }

    /**
     * Caja en relieve: degrade vertical top-&gt;bot, una linea de luz arriba y
     * sombra abajo y a la derecha por dentro, y un borde de 1px sin las 4
     * esquinas (se ve levemente redondeado). x2/y2 son exclusivos (igual que
     * el resto de esta clase / GuiGraphics#fill).
     */
    public static void plate(GuiGraphics g, int x1, int y1, int x2, int y2, int top, int bot, int border) {
        if (x2 <= x1 || y2 <= y1) return;
        int ix1 = x1 + 1, iy1 = y1 + 1, ix2 = x2 - 1, iy2 = y2 - 1;
        if (ix2 > ix1 && iy2 > iy1) {
            g.fillGradient(ix1, iy1, ix2, iy2, top, bot);
            g.fill(ix1, iy1, ix2, iy1 + 1, blend(top, 0xFFFFFFFF, 0.18f));
            g.fill(ix1, iy2 - 1, ix2, iy2, blend(bot, 0xFF000000, 0.22f));
            if (iy2 - iy1 > 2) g.fill(ix2 - 1, iy1 + 1, ix2, iy2 - 1, blend(bot, 0xFF000000, 0.12f));
        }
        g.fill(x1 + 1, y1, x2 - 1, y1 + 1, border);
        g.fill(x1 + 1, y2 - 1, x2 - 1, y2, border);
        g.fill(x1, y1 + 1, x1 + 1, y2 - 1, border);
        g.fill(x2 - 1, y1 + 1, x2, y2 - 1, border);
    }

    /** Visual state for {@link #button}: one top/bot/border combination per state. */
    public enum ButtonState { NORMAL, HOVER, ACTIVE, DISABLED }

    /**
     * {@link #plate} with per-state
     * top/bot/border, plus centered text on top. This is a pure render
     * call (no click handling) so it works for both a hand-rolled hotspot
     * and a real widget wrapping it, like
     * {@code KvePlateButton} does for the Screen widget system.
     */
    public static void button(GuiGraphics g, int x, int y, int w, int h, String text, ButtonState state) {
        int top, bot, border, textColor;
        switch (state) {
            case HOVER:
                top = blend(PLATE_HI, 0xFFFFFFFF, 0.10f);
                bot = blend(PLATE_LO, PLATE_HI, 0.35f);
                border = ACCENT_GOLD;
                textColor = ACCENT_GOLD;
                break;
            case ACTIVE:
                top = blend(PLATE_LO, 0xFF000000, 0.15f);
                bot = PLATE_HI;
                border = ACCENT_GOLD;
                textColor = ACCENT_GOLD;
                break;
            case DISABLED:
                top = blend(PLATE_LO, BG_PANEL | 0xFF000000, 0.5f);
                bot = top;
                border = BORDER_FIELD;
                textColor = TEXT_MUTED;
                break;
            case NORMAL:
            default:
                top = PLATE_HI;
                bot = PLATE_LO;
                border = BORDER_SUBTLE;
                textColor = TEXT_PRIMARY;
                break;
        }
        plate(g, x, y, x + w, y + h, top, bot, border);
        if (text != null && !text.isEmpty()) {
            Font font = Minecraft.getInstance().font;
            float scale = uiTextScale;
            int availW = Math.max(0, w - 4);
            if (font.width(text) > availW) {
                float fitScale = availW > 0 ? Math.min(scale, availW / (float) font.width(text)) : scale;
                scale = Math.max(0.5f, fitScale);
            }
            if (scale < 0.999f) {
                int unscaledAvailW = (int) (availW / scale);
                String safe = truncate(font, text, unscaledAvailW);
                int tw = font.width(safe);
                int tx = x + (int) ((w - tw * scale) / 2);
                int ty = y + (int) ((h - font.lineHeight * scale) / 2) + 1;
                g.pose().pushPose();
                g.pose().translate(tx, ty, 0);
                g.pose().scale(scale, scale, 1f);
                g.drawString(font, safe, 0, 0, textColor, false);
                g.pose().popPose();
            } else {
                String safe = truncate(font, text, availW);
                int tx = x + (w - font.width(safe)) / 2;
                int ty = y + (h - font.lineHeight) / 2 + 1;
                g.drawString(font, safe, tx, ty, textColor, false);
            }
        }
    }

    /** Convenience overload for the common Screen#render case: derives {@link ButtonState} from mouse position + active flag instead of making every call site branch by hand. */
    public static void button(GuiGraphics g, int x, int y, int w, int h, String text, int mx, int my, boolean active) {
        ButtonState state = !active ? ButtonState.DISABLED
                : isHovered(mx, my, x, y, w, h) ? ButtonState.HOVER : ButtonState.NORMAL;
        button(g, x, y, w, h, text, state);
    }

    // =========================================================
    // Button layout: Raiz del problema: 20
    // pantallas tenian cada una su propio helper `btn(label,x,y,w,...)`
    // local con `w` fijo elegido a ojo, lo que truncaba labels largos
    // ("Hi...ial", "D...icar") y desalineaba alturas entre tabs (12/13/14
    // sueltos). Unico lugar que ya lo hacia bien: KveStudioShell#buildTabBar
    // (`w = Math.max(36, font.width(label) + 10)`) - este bloque generaliza
    // ese patron en vez de inventar uno nuevo.
    /** Altura unica de boton, reemplaza los 12/13/14/16/18 sueltos por pantalla. */
    public static final int BTN_H = 14;
    /** Padding horizontal interno usado por {@link #btnWidth()}. */
    public static final int BTN_PAD_X = 10;
    /** Piso de ancho, para iconos/labels de una palabra corta. */
    public static final int BTN_MIN_W = 36;
    /** Separacion horizontal fija entre botones de una misma fila. */
    public static final int BTN_GAP = 3;

    public static int btnWidth(String label) {
        return Math.max(BTN_MIN_W, Minecraft.getInstance().font.width(label) + BTN_PAD_X * 2);
    }

    /** {@link KvePlateButton} factory que calcula su ancho con {@link #btnWidth()} - ningun call site vuelve a pasar `w` a mano. */
    public static KvePlateButton plateBtn(int x, int y, Component label, Button.OnPress action) {
        int w = btnWidth(label.getString());
        return new KvePlateButton(x, y, w, BTN_H, label, action);
    }

    /** Overload de string plano, para call sites que hoy pasan labels como String en vez de Component. */
    public static KvePlateButton plateBtn(int x, int y, String label, Button.OnPress action) {
        return plateBtn(x, y, Component.literal(label), action);
    }

    public static KvePlateButton smartBtn(int x, int y, int w, String label, Button.OnPress action) {
        return new KvePlateButton(x, y, smartWidth(w, label), BTN_H, Component.literal(label), action);
    }

    private static int smartWidth(int w, String label) {
        boolean isIcon = label != null && label.length() <= 2;
        if (isIcon) return Math.max(w, 12);
        return w > 0 ? clampedWidth(w, label) : btnWidth(label);
    }

    /**
     * Ancho real de {@link #clampedBtn()}: piso {@link #BTN_MIN_W} (nunca
     * ilegible) y techo {@code maxW} (nunca invade al vecino) - a
     * diferencia de {@link #smartWidth()}, que trata cualquier `w` de texto
     * como piso decorativo y jamas como techo. Factoreado aparte para que
     * {@link ButtonRow#add} pueda usar el mismo calculo sin duplicarlo.
     */
    private static int clampedWidth(int maxW, String label) {
        // Antes Math.max(BTN_MIN_W, maxW) dejaba el boton con 36px aunque
        // el hueco fuera menor: el boton se salia del marco. Ahora nunca pasa de maxW;
        // si el texto no entra, el boton encoge y button lo trunca con elipsis.
        return Math.max(1, Math.min(btnWidth(label), maxW));
    }

    /**
     * Como {@link #smartBtn()}, pero {@code maxW} es una restriccion DURA
     * del layout (una columna, un hueco entre 2 vecinos fijos) - nunca
     * decorativa. Si {@code btnWidth(label) > maxW}, el boton se crea con
     * {@code w = maxW} igual (nunca mas ancho) y {@link #button} se
     * encarga de encoger la letra (fit-scale existente) y, si ni asi
     * entra, truncar con elipsis (truncate existente). Nunca devuelve
     * un boton mas ancho que {@code maxW} - a diferencia de smartBtn()/
     * smartWidth(), que ignoran el `w` para texto. Piso {@link #BTN_MIN_W}
     * se mantiene igual (ver {@link #clampedWidth()}); si {@code maxW} <
     * BTN_MIN_W, el boton gana el piso y el layout que le da una columna
     * mas angosta que BTN_MIN_W es responsable de ese error de diseno, no
     * el boton.
     */
    public static KvePlateButton clampedBtn(int x, int y, int maxW, String label, Button.OnPress action) {
        return new KvePlateButton(x, y, clampedWidth(maxW, label), BTN_H, Component.literal(label), action);
    }

    /**
     * Fila de botones que wrappea a una linea nueva en vez de solapar o
     * salirse del ancho disponible. Uso:
     * <pre>
     * KveGuiStyle.ButtonRow row = new KveGuiStyle.ButtonRow(bx, by, maxX);
     * addRenderableWidget(row.add(0, saveLabel, b -&gt; save));
     *   addRenderableWidget(row.add(0, deleteLabel, b -&gt; delete));
     *...
     *   int nextY() = row.bottomY(); // para lo que se dibuje debajo de la fila
     * </pre>
     * No reemplaza el cursor manual (`cx += btnWidth(...) + BTN_GAP`) que
     * ya usan varias pantallas migradas - es una alternativa para filas
     * NUEVAS o pantallas que se migren mas adelante, que ademas resuelve
     * el wrap que ese patron manual no cubre. Ninguna pantalla existente
     * fue reescrita para usar esto todavia.
     */
    /** Tooltip de dos lineas: el nombre en dorado y, si hay, que hace debajo en gris. */
    public static net.minecraft.client.gui.components.Tooltip iconTip(String nameKey, String descKey) {
        net.minecraft.network.chat.MutableComponent c = Component.literal(com.kve.common.Lang.get(nameKey))
                .withStyle(net.minecraft.ChatFormatting.GOLD);
        if (descKey != null) c.append(Component.literal("\n" + com.kve.common.Lang.get(descKey)).withStyle(net.minecraft.ChatFormatting.GRAY));
        return net.minecraft.client.gui.components.Tooltip.create(c);
    }

    /**
     * Fila de acciones de un editor: a la vista solo Guardar (destacado), "Mas ▾" y Eliminar
     * (al final, en rojo). Todo lo demas va dentro del menu, en grupos separados por una linea
     * y siempre en el mismo orden: revisar (vista previa, duplicar), herramientas del editor,
     * estado (activo, solo lectura) y deshacer/restablecer.
     */
    public static final class ActionBar {
        private static final int REVIEW = 0, TOOLS = 1, STATE = 2, HISTORY = 3;

        private record Entry(int group, KveMenuButton.Item item) {}

        private final ButtonRow row;
        private final java.util.function.Consumer<? super KvePlateButton> add;
        private final List<Entry> entries = new ArrayList<>();

        public ActionBar(ButtonRow row, java.util.function.Consumer<? super KvePlateButton> add) {
            this.row = row;
            this.add = add;
        }

        /** La accion principal, siempre a la vista. */
        public KvePlateButton primary(String label, Button.OnPress action) {
            KvePlateButton b = row.add(0, label, action).withPrimary(true);
            add.accept(b);
            return b;
        }

        private ActionBar put(int group, String label, Runnable action, boolean active) {
            entries.add(new Entry(group, new KveMenuButton.Item(label, action, false, active)));
            return this;
        }

        /** Revisar: vista previa, duplicar. */
        public ActionBar button(String label, Button.OnPress action) { return put(REVIEW, label, () -> action.onPress(null), true); }

        /** Herramientas propias del editor (plantillas, ver drops, historial...). */
        public ActionBar more(String label, Runnable action) { return put(TOOLS, label, action, true); }

        public ActionBar more(String label, Runnable action, boolean active) { return put(TOOLS, label, action, active); }

        /** Estado activo/desactivado, escrito como estado ("Activo: Sí"). */
        public ActionBar enabled(boolean on, Button.OnPress action) {
            return put(STATE, com.kve.common.Lang.get(on ? "gui.kve.actions.enabled_on" : "gui.kve.actions.enabled_off"),
                    () -> action.onPress(null), true);
        }

        public ActionBar state(String label, Runnable action) { return put(STATE, label, action, true); }

        /** Deshacer y restablecer. */
        public ActionBar history(String label, Runnable action) { return put(HISTORY, label, action, true); }

        public ActionBar history(String label, Runnable action, boolean active) { return put(HISTORY, label, action, active); }

        /** Cierra la fila: el menu "Mas ▾" (si tiene algo) y Eliminar al final (si {@code delete} no es null). */
        public void end(Runnable delete) {
            if (!entries.isEmpty()) {
                List<Entry> sorted = new ArrayList<>(entries);
                sorted.sort(java.util.Comparator.comparingInt(Entry::group));
                List<KveMenuButton.Item> items = new ArrayList<>();
                int last = -1;
                for (Entry e : sorted) {
                    if (last >= 0 && e.group() != last) items.add(KveMenuButton.Item.separator());
                    items.add(e.item());
                    last = e.group();
                }
                String label = com.kve.common.Lang.get("gui.kve.actions.more");
                int w = btnWidth(label);
                int[] p = row.reserve(w);
                add.accept(new KveMenuButton(p[0], p[1], w, BTN_H, Component.literal(label), items));
            }
            if (delete != null) {
                row.gap(10);
                KvePlateButton d = row.add(0, com.kve.common.Lang.get("gui.kve.btn.delete"), b -> delete.run()).withDanger(true);
                add.accept(d);
            }
        }
    }

    public static final class ButtonRow {
        private final int startX;
        private final int maxX;
        private int x, y;
        // variante que crece hacia
        // ARRIBA en vez de hacia abajo al wrappear - para filas ancladas
        // cerca del borde inferior REAL de la ventana (TradeEditorScreen,
        // by=T+GUI_H-20) donde crecer hacia abajo empuja lineas nuevas mas
        // alla de GUI_H, encima de la barra inferior del shell. La linea
        // pasada al constructor sigue siendo la ULTIMA (la de mas abajo,
        // siempre visible - "Guardar"/"Mas opciones"); cada wrap agrega
        // una linea NUEVA arriba de la anterior en vez de abajo, asi el
        // borde inferior de la fila nunca se mueve. Default false (mismo
        // comportamiento de siempre) para no tocar los 39 call sites
        // existentes.
        private final boolean growUpward;

        public ButtonRow(int x, int y, int maxX) {
            this(x, y, maxX, false);
        }

        public ButtonRow(int x, int y, int maxX, boolean growUpward) {
            this.startX = x;
            this.x = x;
            this.y = y;
            this.maxX = maxX;
            this.growUpward = growUpward;
        }

        /** X donde iria el proximo boton (para alinear otros botones a la misma fila). */
        public int nextX() { return x; }

        /** Y de la linea actual (cambia si la fila wrappeo). */
        public int nextY() { return y; }

        /** Reserva el lugar de un boton de ancho {@code bw} (con el mismo salto de linea que add) y devuelve {x, y}. */
        public int[] reserve(int bw) {
            if (x != startX && x + bw > maxX) {
                x = startX;
                y += growUpward ? -(BTN_H + BTN_GAP) : (BTN_H + BTN_GAP);
            }
            int[] p = {x, y};
            x += bw + BTN_GAP;
            return p;
        }

        public KvePlateButton add(int w, String label, Button.OnPress action) {
            boolean isIcon = label != null && label.length() <= 2;
            int bw = isIcon ? smartWidth(w, label) : (w > 0 ? clampedWidth(w, label) : btnWidth(label));
            if (x != startX && x + bw > maxX) {
                x = startX;
                y += growUpward ? -(BTN_H + BTN_GAP) : (BTN_H + BTN_GAP);
            }
            int rowW = maxX - x;
            if (bw > rowW) bw = clampedWidth(rowW, label);
            KvePlateButton b = new KvePlateButton(x, y, bw, BTN_H, Component.literal(label), action);
            x += bw + BTN_GAP;
            return b;
        }

        /**
         * Separador visual dentro de la fila: agrega {@code px} de aire extra antes del proximo {@link #add}, para agrupar por proximidad - acciones seguras/frecuentes juntas, acciones destructivas (Eliminar/Limpiar) separadas al final, lejos de Guardar (error prevention + Fitts: el clic apurado sobre "Guardar" no cae sobre "Eliminar"). No hace nada al inicio de una linea (nada que separar) - un {@link #add} que wrappea igual reinicia x en startX, asi que el aire nunca se arrastra a la linea nueva.
         */
        public ButtonRow gap(int px) {
            if (x != startX) x += Math.max(0, px);
            return this;
        }

        /**
         * Empieza una seccion con titulo dentro de la fila (corta la
         * linea actual y reserva {@code headerH} px arriba de la siguiente).
         * Devuelve la Y donde dibujar el titulo.
         */
        public int section(int headerH) {
            if (x != startX) {
                x = startX;
                y += BTN_H + BTN_GAP;
            }
            int headerY = y + 2;
            y += headerH + 2;
            return headerY;
        }

        /** Y del borde inferior de la ultima linea usada - para posicionar lo que va debajo de la fila sin asumir a mano cuantas lineas termino ocupando. No usar con growUpward=true (ahi la linea "de abajo" siempre es la del constructor; usar {@link #y} + BTN_H si hace falta el borde inferior real). */
        public int bottomY() {
            return y + BTN_H;
        }

        /** Y del borde SUPERIOR de la primera linea usada (la mas arriba tras wrappear) - para growUpward=true, cuando hace falta saber hasta donde crecio la fila hacia arriba para no superponer algo encima. */
        public int topY() {
            return y;
        }

        /** Cursor X actual (despues del ultimo add, o startX si la fila esta vacia) - para un widget final que necesita "llenar lo que queda" de la fila (ej. un boton Menu que se estira hasta el borde derecho) en vez de un ancho fijo propio. */
        public int x() { return x; }

        public int y() { return y; }
    }

    /**
     * Columna de botones apilados en Y, todos con el MISMO {@code maxW}
     * real (el ancho del panel/columna que los contiene) -,
     * analogo vertical de {@link ButtonRow}. A diferencia de ButtonRow
     * (que crece horizontalmente y wrappea a una linea nueva), esta
     * nunca excede {@code maxW}: cada {@link #add} usa {@link #clampedBtn()}
     * en vez de {@link #smartBtn()}, asi que un label largo se encoge en
     * vez de invadir el panel de al lado. Reemplaza el patron de 4
     * call sites con un `w` "decorativo" por uno donde ese ancho es real.
     */
    public static final class ButtonColumn {
        private final int x, maxW;
        private int y;
        private final int gapY;

        public ButtonColumn(int x, int y, int maxW, int gapY) {
            this.x = x;
            this.y = y;
            this.maxW = maxW;
            this.gapY = gapY;
        }

        /** Agrega un boton al final de la columna (debajo del anterior) y avanza el cursor Y. Nunca mas ancho que {@code maxW}. */
        public KvePlateButton add(String label, Button.OnPress action) {
            KvePlateButton b = clampedBtn(x, y, maxW, label, action);
            y += BTN_H + gapY;
            return b;
        }

        public int bottomY() { return y; }
    }

    public static final class GridTabRow {
        private final KvePlateButton[] buttons;

        /**
         * @param x0 borde izquierdo de la fila.
         * @param x1 borde derecho disponible (ej. splitter o borde del panel) - nunca se cruza.
         * @param labels textos ya traducidos, uno por columna.
         * @param actions una accion por columna, mismo orden que labels.
         * @param activeIndex indice de la tab actualmente seleccionada (ese boton queda con.active=false, mismo convenio "you're already here" que craftModeBtn/machineModeBtn).
         */
        public GridTabRow(int x0, int x1, int y, int gap, String[] labels, Runnable[] actions, int activeIndex) {
            int n = labels.length;
            if (n == 0 || labels.length != actions.length) throw new IllegalArgumentException("labels/actions length mismatch");
            int totalGap = gap * Math.max(0, n - 1);
            int tabW = Math.max(BTN_MIN_W / 2, (x1 - x0 - totalGap) / n);
            Font font = Minecraft.getInstance().font;
            buttons = new KvePlateButton[n];
            for (int i = 0; i < n; i++) {
                int x = x0 + i * (tabW + gap);
                String fit = truncate(font, labels[i], Math.max(0, tabW - 8));
                Runnable action = actions[i];
                KvePlateButton b = new KvePlateButton(x, y, tabW, BTN_H, Component.literal(fit), btn -> action.run());
                b.active = i != activeIndex;
                buttons[i] = b;
            }
        }

        public KvePlateButton get(int i) { return buttons[i]; }

        /**
         * Registra las N tabs en la pantalla de una - reemplaza N llamadas sueltas a addRenderableWidget(tabs.get(i)).
         * Recibe un {@code java.util.function.Consumer<KvePlateButton>} en vez de un {@code Screen} directo:
         * {@code Screen#addRenderableWidget(T)} es protected, así que no se puede invocar desde acá (KveGuiStyle
         * no extiende Screen). El call site (dentro de la propia Screen) sí tiene acceso, así que pasa una
         * method reference {@code this::addRenderableWidget}.
         */
        public void addAllTo(java.util.function.Consumer<net.minecraft.client.gui.components.AbstractWidget> adder) {
            for (KvePlateButton b : buttons) adder.accept(b);
        }
    }

    public static int afterLabel(int labelX, String label, int gap) {
        return labelX + Minecraft.getInstance().font.width(label) + gap;
    }

    /**
     * Esquinas en "L" de 3px dentro de cada esquina del panel - decorativo,
     * sin interaccion. Recibe el mismo rect que {@link #drawWindow()} / {@link #plate}.
     */
    public static void cornerMarks(GuiGraphics g, int x1, int y1, int x2, int y2) {
        int in = 2, len = 3;
        if (x2 - x1 < (in + len) * 2 + 2 || y2 - y1 < (in + len) * 2 + 2) return;
        int l = x1 + in, t = y1 + in, r = x2 - in, b = y2 - in;
        g.fill(l, t, l + len, t + 1, CORNER_MARK);
        g.fill(l, t, l + 1, t + len, CORNER_MARK);
        g.fill(r - len, t, r, t + 1, CORNER_MARK);
        g.fill(r - 1, t, r, t + len, CORNER_MARK);
        g.fill(l, b - 1, l + len, b, CORNER_MARK);
        g.fill(l, b - len, l + 1, b, CORNER_MARK);
        g.fill(r - len, b - 1, r, b, CORNER_MARK);
        g.fill(r - 1, b - len, r, b, CORNER_MARK);
    }

    public static final int PAD           = 8;
    public static final int ROW_H         = 18;
    public static final int ROW_H_COMPACT = 13;  // dense lists only (era 12) - crece menos que ROW_H a proposito, para que la lista "compacta" siga leyendose mas apretada que la normal
    public static final int SECTION_GAP   = 10;
    public static final int TITLEBAR_H    = 16;

    /** Draws the standard window frame: navy fill, title bar, cyan border, gold title text. */
    public static void drawWindow(GuiGraphics g, int x, int y, int w, int h, String title) {
        g.fill(x + 4, y + 4, x + w + 4, y + h + 4, 0x28000000);
        g.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x40000000);
        g.fill(x, y, x + w, y + h, BG_PANEL);
        g.fill(x, y, x + w, y + TITLEBAR_H, BG_TITLEBAR);
        g.hLine(x, x + w - 1, y, BORDER_OUTER);
        g.hLine(x, x + w - 1, y + h - 1, BORDER_OUTER);
        g.vLine(x, y, y + h - 1, BORDER_OUTER);
        g.vLine(x + w - 1, y, y + h - 1, BORDER_OUTER);
        if (title != null && !title.isEmpty()) {
            drawTextSafe(g, title, x + 4, y + 3, w - 8, ACCENT_GOLD);
        }
    }

    public static String randomId() {
        return "shovel_js:" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    public static String randomBareId() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static final int DEFAULT_TEXT_WIDTH = 150;

    /** Recortes ya calculados (texto + ancho): las listas dibujan las mismas filas en cada fotograma. */
    private static final java.util.Map<String, String> TRUNCATED = new java.util.HashMap<>();

    public static String truncate(Font font, String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (text == null) return "";
        if (text.length() > 512) return truncateUncached(font, text, maxWidth);
        String key = maxWidth + "\u0000" + text;
        String hit = TRUNCATED.get(key);
        if (hit != null) return hit;
        String out = truncateUncached(font, text, maxWidth);
        if (TRUNCATED.size() > 4096) TRUNCATED.clear();
        TRUNCATED.put(key, out);
        return out;
    }

    /** Fecha formateada con cache: las listas con fechas (historiales, copias) la pedian en cada fotograma. */
    public static String formatDate(String pattern, long millis) {
        String key = pattern + "\u0000" + millis;
        String hit = DATES.get(key);
        if (hit != null) return hit;
        String out = new java.text.SimpleDateFormat(pattern, java.util.Locale.ROOT).format(new java.util.Date(millis));
        if (DATES.size() > 2048) DATES.clear();
        DATES.put(key, out);
        return out;
    }

    private static final java.util.Map<String, String> DATES = new java.util.HashMap<>();

    private static String truncateUncached(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "…";
        int room = Math.max(0, maxWidth - font.width(ellipsis));
        if (room <= 0) return ellipsis;

        int headRoom = (int) (room * 0.35);
        int tailRoom = room - headRoom;
        String head = font.plainSubstrByWidth(text, headRoom);
        String tail = font.plainSubstrByWidth(text, tailRoom, true);

        if (head.length() + tail.length() >= text.length()) return text;

        return head + ellipsis + tail;
    }

    public static void drawTextSafe(GuiGraphics g, String text, int x, int y, int maxWidth, int color) {
        if (uiTextScale < 0.999f) { drawTextSafeScaled(g, text, x, y, maxWidth, color, uiTextScale); return; }
        Font font = Minecraft.getInstance().font;
        String safe = truncate(font, text, maxWidth);
        noteTruncated(g, text, safe, x, y, maxWidth, font.lineHeight);
        g.drawString(font, safe, x, y, color, false);
    }

    /**
     * Convenience overload with no explicit width, truncating to a generic
     * default ({@value #DEFAULT_TEXT_WIDTH}px). Prefer the 5-argument
     * overload wherever you actually know the available width (a column,
     * a slot, a button) - this one is a safety net for one-off labels, not
     * a substitute for knowing your own layout.
     */
    public static void drawTextSafe(GuiGraphics g, String text, int x, int y, int color) {
        drawTextSafe(g, text, x, y, DEFAULT_TEXT_WIDTH, color);
    }

    public static void drawTextSafeScaled(GuiGraphics g, String text, int x, int y, int maxWidth, int color, float scale) {
        if (scale >= 0.999f) { drawTextSafe(g, text, x, y, maxWidth, color); return; }
        Font font = Minecraft.getInstance().font;
        int unscaledMaxWidth = (int) (maxWidth / scale);
        String safe = truncate(font, text, unscaledMaxWidth);
        noteTruncated(g, text, safe, x, y, maxWidth, Math.max(1, (int) (font.lineHeight * scale)));
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, safe, 0, 0, color, false);
        g.pose().popPose();
    }

    public static List<String> wrapTextSafe(Font font, String text, int maxWidth) {
        float scale = uiTextScale;
        int wrapWidth = (scale < 0.999f) ? Math.max(1, (int) (maxWidth / scale)) : maxWidth;
        List<net.minecraft.util.FormattedCharSequence> seqs = font.split(Component.literal(text), wrapWidth);
        List<String> lines = new ArrayList<>(seqs.size());
        for (net.minecraft.util.FormattedCharSequence seq : seqs) {
            StringBuilder sb = new StringBuilder();
            seq.accept((idx, style, cp) -> { sb.appendCodePoint(cp); return true; });
            lines.add(sb.toString());
        }
        return lines;
    }

    public static void drawFramedTooltip(GuiGraphics g, String text, int mx, int my, int screenW, int screenH) {
        drawFramedTooltip(g, text, mx, my, screenW, screenH, BORDER_OUTER);
    }

    @FunctionalInterface
    public interface IconPainter { void paint(GuiGraphics g, int x, int y, int size); }

    public static void drawFramedIconTooltip(GuiGraphics g, IconPainter icon, String text, int mx, int my, int screenW, int screenH, int accentColor) {
        if (text == null || text.isEmpty()) return;
        if (screenW <= 0 || screenH <= 0) {
            screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
            screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        }
        Font font = Minecraft.getInstance().font;
        float scale = uiTextScale;

        int maxWidth = Math.min(240, Math.max(60, screenW - 24));
        List<String> lines = wrapTextSafe(font, text, maxWidth);

        int lineH = Math.max(9, Math.round(font.lineHeight * scale) + 2);
        int textMaxW = 0;
        for (String l : lines) textMaxW = Math.max(textMaxW, Math.round(font.width(l) * scale));

        int iconSize = 16;
        int padX = 6, padY = 5, accentW = 3, iconGap = 6;
        int boxW = accentW + padX + iconSize + iconGap + textMaxW + padX;
        int textH = lines.size() * lineH;
        int boxH = Math.max(iconSize, textH) + padY * 2;

        int bx = mx + 12;
        int by = my - 12;
        if (bx + boxW > screenW - 4) bx = Math.max(4, screenW - 4 - boxW);
        if (by + boxH > screenH - 4) by = my - boxH - 8;
        if (by < 4) by = 4;

        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(bx - 1, by - 1, bx + boxW + 1, by + boxH + 1, BORDER_OUTER);
        g.fill(bx, by, bx + boxW, by + boxH, BG_PANEL);
        g.fill(bx, by, bx + accentW, by + boxH, accentColor);
        cornerMarks(g, bx, by, bx + boxW, by + boxH);

        int iconX = bx + accentW + padX;
        int iconY = by + (boxH - iconSize) / 2;
        if (icon != null) {
            try { icon.paint(g, iconX, iconY, iconSize); } catch (Throwable ignored) {}
        }

        int textX = iconX + iconSize + iconGap;
        int textY = by + (boxH - textH) / 2;
        for (int i = 0; i < lines.size(); i++) {
            drawTextSafeScaled(g, lines.get(i), textX, textY + i * lineH, textMaxW + 2, TEXT_PRIMARY, scale);
        }
        g.pose().popPose();
        hoveredTruncatedText = null;
    }

    /** Overload con franja de acento propia (ej. {@link #TEXT_ERROR} para tooltips de diagnostico) - ver {@link #drawFramedTooltip(GuiGraphics, String, int, int, int, int)}. */
    public static void drawFramedTooltip(GuiGraphics g, String text, int mx, int my, int screenW, int screenH, int accentColor) {
        if (text == null || text.isEmpty()) return;
        if (screenW <= 0 || screenH <= 0) {
            screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
            screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        }
        Font font = Minecraft.getInstance().font;
        float scale = uiTextScale;

        int maxWidth = Math.min(280, Math.max(80, screenW - 24));
        List<String> lines = wrapTextSafe(font, text, maxWidth);

        int lineH = Math.max(9, Math.round(font.lineHeight * scale) + 2);
        int textMaxW = 0;
        for (String l : lines) textMaxW = Math.max(textMaxW, Math.round(font.width(l) * scale));

        int padX = 6, padY = 5, accentW = 3;
        int boxW = textMaxW + padX * 2 + accentW;
        int boxH = lines.size() * lineH + padY * 2;

        int bx = mx + 12;
        int by = my - 12;
        if (bx + boxW > screenW - 4) bx = Math.max(4, screenW - 4 - boxW);
        if (by + boxH > screenH - 4) by = my - boxH - 8;
        if (by < 4) by = 4;

        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(bx - 1, by - 1, bx + boxW + 1, by + boxH + 1, BORDER_OUTER);
        g.fill(bx, by, bx + boxW, by + boxH, BG_PANEL);
        g.fill(bx, by, bx + accentW, by + boxH, accentColor);
        cornerMarks(g, bx, by, bx + boxW, by + boxH);
        for (int i = 0; i < lines.size(); i++) {
            drawTextSafeScaled(g, lines.get(i), bx + accentW + padX, by + padY + i * lineH, textMaxW + 2, TEXT_PRIMARY, scale);
        }
        g.pose().popPose();
        hoveredTruncatedText = null;
    }

    public static int[] drawFloatingNotice(GuiGraphics g, String text, String btnLabel,
                                            int anchorCX, int anchorTopY, int screenW, int screenH,
                                            int accentColor) {
        if (text == null || text.isEmpty()) return null;
        Font font = Minecraft.getInstance().font;
        float scale = uiTextScale;

        if (screenW <= 0 || screenH <= 0) {
            var window = Minecraft.getInstance().getWindow();
            screenW = window.getGuiScaledWidth();
            screenH = window.getGuiScaledHeight();
        }

        int maxWidth = Math.min(220, Math.max(80, screenW - 24));
        List<String> lines = wrapTextSafe(font, text, maxWidth);

        int lineH = Math.max(9, Math.round(font.lineHeight * scale) + 2);
        int textMaxW = 0;
        for (String l : lines) textMaxW = Math.max(textMaxW, Math.round(font.width(l) * scale));

        int padX = 8, padY = 7, accentW = 3, gapBtn = 8;
        int btnW = Math.max(70, Math.round(font.width(btnLabel) * scale) + 16);
        int btnH = 14;

        int boxW = Math.max(textMaxW, btnW) + padX * 2 + accentW;
        int textH = lines.size() * lineH;
        int boxH = textH + gapBtn + btnH + padY * 2;

        int bx = anchorCX - boxW / 2;
        int by = anchorTopY;
        if (bx < 4) bx = 4;
        if (bx + boxW > screenW - 4) bx = Math.max(4, screenW - 4 - boxW);
        if (by + boxH > screenH - 4) by = Math.max(4, screenH - 4 - boxH);
        if (by < 4) by = 4;

        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(bx - 1, by - 1, bx + boxW + 1, by + boxH + 1, BORDER_OUTER);
        g.fill(bx, by, bx + boxW, by + boxH, BG_PANEL);
        g.fill(bx, by, bx + accentW, by + boxH, accentColor);
        cornerMarks(g, bx, by, bx + boxW, by + boxH);

        int textX = bx + accentW + padX;
        int textY = by + padY;
        for (int i = 0; i < lines.size(); i++) {
            drawTextSafeScaled(g, lines.get(i), textX, textY + i * lineH, textMaxW + 2, TEXT_PRIMARY, scale);
        }

        int btnX = bx + (boxW - btnW) / 2;
        int btnY = textY + textH + gapBtn;
        g.fill(btnX - 1, btnY - 1, btnX + btnW + 1, btnY + btnH + 1, BORDER_OUTER);
        g.fill(btnX, btnY, btnX + btnW, btnY + btnH, BG_FIELD);
        drawCenteredTextSafe(g, btnLabel, btnX + btnW / 2, btnY + (btnH - Math.round(font.lineHeight * scale)) / 2, TEXT_PRIMARY);

        g.pose().popPose();
        return new int[]{btnX, btnY, btnW, btnH};
    }

    /** Posicion del mouse en coordenadas de GUI - para widgets que no reciben mx/my en render. */
    public static double mouseX() {
        Minecraft mc = Minecraft.getInstance();
        return mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / Math.max(1, mc.getWindow().getScreenWidth());
    }

    public static double mouseY() {
        Minecraft mc = Minecraft.getInstance();
        return mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / Math.max(1, mc.getWindow().getScreenHeight());
    }

    static String hoveredTruncatedText = null;

    private static void noteTruncated(GuiGraphics g, String full, String shown, int x, int y, int w, int h) {
        if (full == null || shown.equals(full)) return;
        org.joml.Matrix4f m = g.pose().last().pose();
        if (m.m30() != 0f || m.m31() != 0f || m.m00() != 1f) return;
        double mx = mouseX(), my = mouseY();
        if (mx >= x && mx < x + w && my >= y - 1 && my < y + h + 1) hoveredTruncatedText = full;
    }

    public static void drawCenteredTextSafe(GuiGraphics g, String text, int centerX, int y, int color) {
        Font font = Minecraft.getInstance().font;
        float scale = uiTextScale;
        int w = font.width(text);
        if (scale >= 0.999f) {
            g.drawString(font, text, centerX - w / 2, y, color, false);
            return;
        }
        g.pose().pushPose();
        g.pose().translate(centerX, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, text, -w / 2, 0, color, false);
        g.pose().popPose();
    }

    /** Draws an item/value slot with a real layered-alpha glow when hovered. */
    public static void drawSlotGlow(GuiGraphics g, int x, int y, int size, boolean hovered, int glowColorRGB) {
        drawSlotGlowRect(g, x, y, size, size, hovered, glowColorRGB);
    }

    /** Rectangular generalization of {@link #drawSlotGlow()} for non-square slots (e.g. a tall fluid-tank slot) - identical glow/border treatment, independent width/height. */
    public static void drawSlotGlowRect(GuiGraphics g, int x, int y, int w, int h, boolean hovered, int glowColorRGB) {
        int base = glowColorRGB & 0xFFFFFF;
        if (hovered) {
            g.fill(x - 3, y - 3, x + w + 3, y + h + 3, (0x20 << 24) | base);
            g.fill(x - 2, y - 2, x + w + 2, y + h + 2, (0x38 << 24) | base);
            g.fill(x - 1, y - 1, x + w + 1, y + h + 1, (0x55 << 24) | base);
        }
        g.fill(x, y, x + w, y + h, BG_FIELD);
        int borderColor = hovered ? (0xFF000000 | base) : BORDER_FIELD;
        g.hLine(x, x + w - 1, y, borderColor);
        g.hLine(x, x + w - 1, y + h - 1, borderColor);
        g.vLine(x, y, y + h - 1, borderColor);
        g.vLine(x + w - 1, y, y + h - 1, borderColor);
    }

    public static boolean isHovered(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static final int HELP_ICON_SIZE = 12;

    /**
     * Draws a {@value #HELP_ICON_SIZE}x{@value #HELP_ICON_SIZE} "?" icon at
     * (x,y). If the cursor is over it, also renders {@code tip} as a
     * tooltip at the cursor and returns true (so a caller juggling several
     * hotspots in one render can early-return the same way
     * {@code drawTooltips} methods elsewhere in this codebase already
     * do). Returns false, and draws nothing but the icon, when not
     * hovered. The "?" glyph is centered via font metrics (not a hardcoded
     * offset) so it stays centered regardless of {@link #HELP_ICON_SIZE}.
     */
    public static boolean helpIcon(GuiGraphics g, int x, int y, int mx, int my, Component tip) {
        boolean hov = isHovered(mx, my, x, y, HELP_ICON_SIZE, HELP_ICON_SIZE);
        Font font = Minecraft.getInstance().font;
        g.fill(x, y, x + HELP_ICON_SIZE, y + HELP_ICON_SIZE, hov ? ACCENT_GOLD : BORDER_FIELD);
        int glyphColor = hov ? 0xFF101018 : TEXT_MUTED;
        int gx = x + (HELP_ICON_SIZE - font.width("?")) / 2;
        int gy = y + (HELP_ICON_SIZE - font.lineHeight) / 2;
        g.drawString(font, "?", gx, gy, glyphColor, false);
        if (hov) g.renderTooltip(font, tip, mx, my);
        return hov;
    }

    private static final int ERROR_BADGE_SIZE = 8;

    /**
     * Draws an 8x8 "!" badge at (x,y) - but only if {@code messages} is
     * non-empty. Returns false and draws nothing when the list is empty,
     * so callers can invoke this unconditionally every frame (e.g. once
     * per section) without an extra isEmpty() guard at every call site.
     * On hover, renders every message in {@code messages} as a stacked
     * tooltip (one problem per line) via
     * {@link GuiGraphics#renderComponentTooltip()}, and returns true.
     */
    public static boolean errorBadge(GuiGraphics g, int x, int y, int mx, int my, List<Component> messages) {
        if (messages == null || messages.isEmpty()) return false;
        boolean hov = isHovered(mx, my, x, y, ERROR_BADGE_SIZE, ERROR_BADGE_SIZE);
        Font font = Minecraft.getInstance().font;
        g.fill(x, y, x + ERROR_BADGE_SIZE, y + ERROR_BADGE_SIZE, hov ? 0xFFFF6644 : 0xFFAA2222);
        g.drawString(font, "!", x + 3, y - 1, 0xFFFFFFFF, false);
        if (hov) g.renderComponentTooltip(font, messages, mx, my);
        return hov;
    }

    public static final String HINT_CLICK_CHANGE = "gui.kve.slot.hint.click_change";
    public static final String HINT_RIGHT_CLEAR  = "gui.kve.slot.hint.right_clear";
    public static final String HINT_SHIFT_OPTION = "gui.kve.slot.hint.shift_option";
    public static final String HINT_CTRL_NBT     = "gui.kve.slot.hint.ctrl_nbt";
    public static final String HINT_SCROLL_QTY   = "gui.kve.slot.hint.scroll_qty";
    private static final List<String> HINT_ORDER = List.of(
            HINT_CLICK_CHANGE, HINT_RIGHT_CLEAR, HINT_SHIFT_OPTION, HINT_CTRL_NBT, HINT_SCROLL_QTY);

    /**
     * Builds one gray "what do the mouse buttons/modifiers do on this slot"
     * line from the {@code HINT_*} keys the caller actually wired up for
     * this slot - e.g. {@code slotHint(HINT_CLICK_CHANGE, HINT_RIGHT_CLEAR,
     * HINT_SCROLL_QTY)} for a plain item slot with no NBT/alt-option
     * support, or the full 5 for one that has all of them. Only pass a key
     * whose action the slot actually performs - this never invents a
     * modifier the slot doesn't really support, same caution as
     * {@code CraftEditorScreen#nbtHotspotTip} already used per-slot.
     * Append the returned line to the slot's item tooltip (via
     * {@code new ArrayList<>(getTooltipFromItem(...))} + this, then
     * {@code renderComponentTooltip()}) rather than replacing it.
     */
    public static Component slotHint(String... keys) {
        StringBuilder sb = new StringBuilder();
        for (String k : HINT_ORDER) {
            boolean requested = false;
            for (String want : keys) if (want.equals(k)) { requested = true; break; }
            if (!requested) continue;
            if (sb.length() > 0) sb.append(" | ");
            sb.append(Lang.get(k));
        }
        return Component.literal(sb.toString()).withStyle(net.minecraft.ChatFormatting.GRAY);
    }

    public static Button pendingChangesButton(int x, int y, int w, int h, Screen parent) {
        int count = PendingChangeManager.size();
        KvePlateButton b = new KvePlateButton(x, y, w, h,
                Component.literal(Lang.get("gui.kve.pending.header", count)),
                btn -> openPendingOverview(parent));
        b.active = count > 0;
        return b;
    }

    private static void openPendingOverview(Screen parent) {
        Minecraft.getInstance().setScreen(new DiffPreviewScreen(parent,
                PendingChangeManager.snapshot(),
                () -> com.kve.client.gui.KveToast.applied(PendingChangeManager.applyAll())));
    }

    /**
     * Draws a small colored dot right after a field/button, plus an
     * optional "was &lt;reference&gt;" label when there's enough horizontal
     * room - the at-a-glance "what will actually get written" signal
 * describes ("was X" + colored dot), without
     * having to read the generated KubeJS/JSON panel to find out.
     * <p>
     * Green ({@link #TEXT_OK}) = {@code currentText} differs from
     * {@code referenceText} (this field WILL write an override). Gray
     * ({@link #TEXT_MUTED}) = matches the reference, or {@code currentText}
     * is blank (field editor's own "no override" convention, see
     * {@code ItemAttributeScreen#field}'s hint text) - nothing new gets
     * written for this field either way.
     * <p>
     * {@code referenceText} may be {@code null} to mean "no known vanilla/
     * default value for this field" (e.g. a non-damageable item has no
     * max-damage baseline) - the caller should simply not call this method
     * for that row rather than passing null defensively; this method does
     * NOT draw anything if referenceText is null, so either is safe.
     * <p>
     * Layout is caller-driven on purpose (x/y/maxWidth passed in, not
     * computed here) - every screen's field column sits at a different x
     * with a different amount of room left before the next panel divider
     * (see ItemAttributeScreen's LIST_W/INFO_W comments for how tight that
     * budget can get with panels resized), so this method has no business
     * guessing it.
     *
     * @param currentText   the field's current on-screen value, formatted
     *                      the same way the caller would format it for
     *                      saving (blank = no override entered)
     * @param referenceText the known vanilla/default value, pre-formatted
     *                      for display; null = skip (no known reference)
     * @param x             x just past the field/button's right edge
     * @param y             the field/button's own top y (row-aligned)
     * @param maxWidth      remaining horizontal budget past x for the
     *                      optional label; below ~24px only the dot draws
     */
    public static void drawFieldHint(GuiGraphics g, String currentText, String referenceText, int x, int y, int maxWidth) {
        if (referenceText == null || maxWidth < 4) return;
        if (!KveClientSettings.getOrDefault(KveClientSettings.SHOW_WAS_HINT, true)) return;
        boolean changed = currentText != null && !currentText.isBlank() && !currentText.equals(referenceText);
        int dotColor = changed ? TEXT_OK : TEXT_MUTED;
        g.fill(x, y + 2, x + 4, y + 6, dotColor);
        int labelBudget = maxWidth - 7;
        if (labelBudget >= 24) {
            drawTextSafe(g, Lang.get("gui.kve.field.was", referenceText), x + 7, y, labelBudget, TEXT_MUTED);
        }
    }

    public static final class Layout {
        private final int x0;
        private int y;
        private final int defaultRowH;

        public Layout(int x0, int startY, int defaultRowH) {
            this.x0 = x0;
            this.y = startY;
            this.defaultRowH = defaultRowH;
        }

        public int x() { return x0; }

        /** Y for the current row; does not advance. */
        public int y() { return y; }

        /** Returns the Y to draw at, then advances by the default row height. */
        public int row() { return row(defaultRowH); }

        /** Returns the Y to draw at, then advances by a custom height (e.g. a taller field). */
        public int row(int height) {
            int cur = y;
            y += height;
            return cur;
        }

        /** Adds extra vertical space without producing a row (section breaks, dividers). */
        public Layout gap(int px) { y += px; return this; }

        /** Y the NEXT row call would return, without advancing - useful for peeking. */
        public int nextY() { return y; }

        public Layout reset(int startY) { this.y = startY; return this; }
    }

    private static final boolean DEBUG_LAYOUT =
            Boolean.getBoolean("kve.debugLayout") || "1".equals(System.getenv("KVE_DEBUG_LAYOUT"));

    public static void checkOverlaps(Screen screen) {
        if (!DEBUG_LAYOUT) return;
        List<AbstractWidget> widgets = new ArrayList<>();
        for (var child : screen.children()) {
            if (child instanceof AbstractWidget w) widgets.add(w);
        }
        for (int i = 0; i < widgets.size(); i++) {
            AbstractWidget a = widgets.get(i);
            if (a.getWidth() <= 0 || a.getHeight() <= 0) continue;
            for (int j = i + 1; j < widgets.size(); j++) {
                AbstractWidget b = widgets.get(j);
                if (b.getWidth() <= 0 || b.getHeight() <= 0) continue;
                boolean overlap = a.getX() < b.getX() + b.getWidth()
                        && a.getX() + a.getWidth() > b.getX()
                        && a.getY() < b.getY() + b.getHeight()
                        && a.getY() + a.getHeight() > b.getY();
                if (overlap) {
                    com.kve.CraftEditorMod.LOGGER.warn(
                            "[Shovel JS][layout] overlap en {}: \"{}\" [{},{} {}x{}] vs \"{}\" [{},{} {}x{}]",
                            screen.getClass().getSimpleName(),
                            a.getMessage().getString(), a.getX(), a.getY(), a.getWidth(), a.getHeight(),
                            b.getMessage().getString(), b.getX(), b.getY(), b.getWidth(), b.getHeight());
                }
            }
        }
    }

    private static final java.util.regex.Pattern INLINE_ID_PATTERN =
            java.util.regex.Pattern.compile("\"([a-z0-9_.\\-]+:[a-z0-9_./\\-]+)\"");

    public static int renderInlineItemIcons(GuiGraphics g, Font font, String line, int x, int y, int color, boolean shadow) {
        // Settings "Inline icons" toggle (Editor / Keybinds) - calca
        // kve-studio-elementos-completos.html. Default true (misma
        // conducta que este metodo siempre tuvo desde que RawNbtTextArea
        // lo adopto, ver Javadoc de arriba). Off = texto plano sin resolver
        // ids contra el registro, mismo resultado que si esta linea nunca
        // hubiera pasado por este metodo.
        if (!KveClientSettings.getOrDefault(KveClientSettings.INLINE_ICONS, true)) {
            g.drawString(font, line, x, y, color, shadow);
            return x + font.width(line);
        }
        java.util.regex.Matcher m = INLINE_ID_PATTERN.matcher(line);
        int cursor = 0;
        int drawX = x;
        while (m.find()) {
            net.minecraft.world.item.Item item = resolveInlineItem(m.group(1));
            if (item == null) continue;

            int matchStart = m.start();
            if (matchStart + 1 > cursor) {
                String before = line.substring(cursor, matchStart + 1);
                g.drawString(font, before, drawX, y, color, shadow);
                drawX += font.width(before);
            }

            int iconSize = Math.max(6, font.lineHeight);
            float scale = iconSize / 16f;
            g.pose().pushPose();
            g.pose().translate(drawX, y - 1, 0);
            g.pose().scale(scale, scale, 1f);
            try { g.renderItem(new net.minecraft.world.item.ItemStack(item), 0, 0); } catch (Throwable ignored) {}
            g.pose().popPose();
            drawX += iconSize + 1;

            cursor = matchStart + 1;
        }
        if (cursor < line.length()) {
            String rest = line.substring(cursor);
            g.drawString(font, rest, drawX, y, color, shadow);
            drawX += font.width(rest);
        }
        return drawX;
    }

    private static net.minecraft.world.item.Item resolveInlineItem(String idStr) {
        try {
            net.minecraft.resources.ResourceLocation rl = new net.minecraft.resources.ResourceLocation(idStr);
            if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(rl)) return null;
            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
            return item == net.minecraft.world.item.Items.AIR ? null : item;
        } catch (Exception ex) {
            return null;
        }
    }
}
