package com.kve.common.kubejs;

import java.util.Locale;

public final class ItemAttrMath {
    private ItemAttrMath() {}

    public static final double ATTACK_DAMAGE_BASE  = 1.0;
    public static final double ATTACK_SPEED_BASE   = 4.0;
    public static final double MOVEMENT_SPEED_BASE = 0.1;

    public static String fmt(double v) {
        return (v == Math.floor(v)) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.3f", v);
    }

    /** Crudo (modifier real) -> lo que se pinta. NaN pasa como NaN. */
    public static double toEffective(double raw, boolean additive, double base) {
        return additive ? raw + base : raw;
    }

    /** Lo que se tipeo -> crudo (modifier real). NaN (campo vacio o no numerico) pasa como NaN. */
    public static double fromEffective(double effective, boolean additive, double base) {
        if (Double.isNaN(effective)) return Double.NaN;
        return additive ? effective - base : effective;
    }

    /**
     * True si {@code draftRaw} (lo que salio de leer el campo de texto)
     * representa el mismo valor que {@code baselineRaw} (el modifier real del
     * item) - es decir, el jugador NO lo cambio. Ambos NaN cuentan como
     * iguales ("sin dato" contra "sin dato").
     * <p>
     * Un valor que difiere del baseline solo por el redondeo de
     * {@link #fmt} (float promovido a double, o mas de 3 decimales reales)
     * cuenta como igual: es exactamente lo que el jugador estaba viendo en
     * el campo, no una edicion.
     */
    public static boolean sameAsBaseline(double draftRaw, double baselineRaw, boolean additive, double base) {
        boolean dn = Double.isNaN(draftRaw), bn = Double.isNaN(baselineRaw);
        if (dn || bn) return dn && bn;
        if (draftRaw == baselineRaw) return true;
        double shown = toEffective(baselineRaw, additive, base);
        double reparsed;
        try {
            reparsed = Double.parseDouble(fmt(shown).trim());
        } catch (NumberFormatException ex) {
            return false;
        }
        return draftRaw == fromEffective(reparsed, additive, base);
    }
}
