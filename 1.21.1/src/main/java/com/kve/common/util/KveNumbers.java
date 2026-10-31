package com.kve.common.util;

import java.util.Locale;

public final class KveNumbers {
    private KveNumbers() {}

    /** {@code 3.0 -> "3"}, {@code 0.25 -> "0.2500"}. */
    public static String padded4(double v) {
        return isWhole(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.4f", v);
    }

    /** {@code 3.0 -> "3"}, {@code 0.4 -> "0.4"}, redondeado a {@code maxDecimals}. */
    public static String trimmed(double v, int maxDecimals) {
        if (isWhole(v)) return String.valueOf((long) v);
        String s = String.format(Locale.ROOT, "%." + maxDecimals + "f", v);
        if (s.indexOf('.') >= 0) {
            s = s.replaceAll("0+$", "");
            if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        }
        return "-0".equals(s) ? "0" : s;
    }

    private static boolean isWhole(double v) {
        return v == Math.floor(v) && !Double.isInfinite(v);
    }
}
