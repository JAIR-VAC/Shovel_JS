package com.kve.common.util;

public final class JsStrings {
    private JsStrings() {}

    /** Contenido escapado para ir DENTRO de comillas simples ({@code 'aca'}); null -> "". */
    public static String escape(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '\'' -> out.append("\\'");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case ' ' -> out.append("\\u2028");
                case ' ' -> out.append("\\u2029");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /** Literal completo entre comillas simples: {@code it's} -> {@code 'it\'s'}; null -> {@code ''}. */
    public static String quote(String s) {
        return "'" + escape(s) + "'";
    }

    /** Literal entre comillas dobles, para los pocos generadores que emiten {@code "..."}; null -> {@code ""}. */
    public static String quoteDouble(String s) {
        if (s == null) return "\"\"";
        StringBuilder out = new StringBuilder(s.length() + 8).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case ' ' -> out.append("\\u2028");
                case ' ' -> out.append("\\u2029");
                default -> out.append(c);
            }
        }
        return out.append('"').toString();
    }
}
