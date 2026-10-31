package com.kve.common.code;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class JsFormatter {

    private JsFormatter() {}

    public static final String INDENT = "  ";

    /** Devuelve las lineas reindentadas (lista nueva, del mismo largo). */
    public static List<String> reindent(List<String> lines) {
        List<String> out = new ArrayList<>(lines.size());
        Deque<Integer> open = new ArrayDeque<>();
        boolean inBlockComment = false;
        boolean inTemplate = false;

        for (String raw : lines) {
            String trimmedRight = stripTrailing(raw);
            String body = trimmedRight.stripLeading();

            boolean keepAsIs = inBlockComment || inTemplate;

            int lineDepth;
            if (keepAsIs || body.isEmpty()) {
                lineDepth = -1;
            } else {
                int leadingClosers = countLeadingClosers(body);
                if (leadingClosers > 0 && !open.isEmpty()) {
                    lineDepth = open.peek();
                } else {
                    lineDepth = open.isEmpty() ? 0 : open.peek() + 1;
                }
            }

            if (keepAsIs) out.add(trimmedRight);
            else if (body.isEmpty()) out.add("");
            else out.add(INDENT.repeat(lineDepth) + body);

            int depthForOpeners = lineDepth >= 0 ? lineDepth : (open.isEmpty() ? 0 : open.peek() + 1);

            char quote = 0;
            for (int i = 0; i < trimmedRight.length(); i++) {
                char c = trimmedRight.charAt(i);
                char next = i + 1 < trimmedRight.length() ? trimmedRight.charAt(i + 1) : 0;
                if (inBlockComment) {
                    if (c == '*' && next == '/') { inBlockComment = false; i++; }
                    continue;
                }
                if (inTemplate) {
                    if (c == '\\') { i++; continue; }
                    if (c == '`') inTemplate = false;
                    continue;
                }
                if (quote != 0) {
                    if (c == '\\') { i++; continue; }
                    if (c == quote) quote = 0;
                    continue;
                }
                if (c == '/' && next == '/') break;
                if (c == '/' && next == '*') { inBlockComment = true; i++; continue; }
                if (c == '\'' || c == '"') { quote = c; continue; }
                if (c == '`') { inTemplate = true; continue; }
                if (c == '{' || c == '[' || c == '(') open.push(depthForOpeners);
                else if (c == '}' || c == ']' || c == ')') { if (!open.isEmpty()) open.pop(); }
            }
        }
        return out;
    }

    /** True si reindentar cambiaria algo. */
    public static boolean wouldChange(List<String> lines) {
        return !reindent(lines).equals(lines);
    }

    private static int countLeadingClosers(String body) {
        int n = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '}' || c == ']' || c == ')') n++;
            else if (c == ' ' || c == '\t') continue;
            else break;
        }
        return n;
    }

    private static String stripTrailing(String s) {
        int end = s.length();
        while (end > 0 && Character.isWhitespace(s.charAt(end - 1))) end--;
        return s.substring(0, end);
    }
}
