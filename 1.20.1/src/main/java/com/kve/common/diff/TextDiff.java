package com.kve.common.diff;

import java.util.ArrayList;
import java.util.List;

public final class TextDiff {

    private TextDiff() {}

    public enum LineType { EQUAL, ADDED, REMOVED }

    public record DiffLine(LineType type, String text) {}

    public static boolean equalLines(String oldText, String newText) {
        String o = oldText == null ? "" : oldText;
        String n = newText == null ? "" : newText;
        return o.equals(n);
    }

    /**
     * Diffs two texts line by line and returns the ordered list of
     * {@link DiffLine}s (context + additions + removals) a caller can
     * render directly, top to bottom, same order as {@code diff -u} without
     * the hunk headers - this project's scripts are short enough (single
     * recipe/tag/event/etc. blocks, not whole-repo diffs) that hunk
     * collapsing isn't worth the complexity.
     */
    public static List<DiffLine> diffLines(String oldText, String newText) {
        String[] a = splitLines(oldText);
        String[] b = splitLines(newText);
        int n = a.length, m = b.length;

        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                lcs[i][j] = a[i].equals(b[j]) ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }

        List<DiffLine> out = new ArrayList<>();
        int i = 0, j = 0;
        while (i < n && j < m) {
            if (a[i].equals(b[j])) {
                out.add(new DiffLine(LineType.EQUAL, a[i]));
                i++; j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                out.add(new DiffLine(LineType.REMOVED, a[i]));
                i++;
            } else {
                out.add(new DiffLine(LineType.ADDED, b[j]));
                j++;
            }
        }
        while (i < n) { out.add(new DiffLine(LineType.REMOVED, a[i])); i++; }
        while (j < m) { out.add(new DiffLine(LineType.ADDED, b[j])); j++; }
        return out;
    }

    public static String toUnifiedText(List<DiffLine> lines) {
        StringBuilder sb = new StringBuilder();
        for (DiffLine l : lines) {
            String prefix = switch (l.type()) {
                case ADDED -> "+ ";
                case REMOVED -> "- ";
                case EQUAL -> "  ";
            };
            sb.append(prefix).append(l.text()).append('\n');
        }
        return sb.toString();
    }

    /** How many ADDED/REMOVED lines are in the diff - a screen can use this for a quick "+3 -1" summary badge without walking the list itself. */
    public static int[] countChanges(List<DiffLine> lines) {
        int added = 0, removed = 0;
        for (DiffLine l : lines) {
            if (l.type() == LineType.ADDED) added++;
            else if (l.type() == LineType.REMOVED) removed++;
        }
        return new int[] { added, removed };
    }

    private static String[] splitLines(String text) {
        if (text == null || text.isEmpty()) return new String[0];
        return text.split("\n", -1);
    }
}
