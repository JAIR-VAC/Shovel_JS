package com.kve.common.kubejs;

import com.kve.CraftEditorMod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class ScriptWriteGuard {
    private ScriptWriteGuard() {}

    public static boolean looksWellFormed(String js) {
        int paren = 0, brace = 0, brack = 0;
        boolean inString = false;
        char quote = 0;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        for (int i = 0; i < js.length(); i++) {
            char c = js.charAt(i);
            char next = i + 1 < js.length() ? js.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (c == '\n') inLineComment = false;
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') { inBlockComment = false; i++; }
                continue;
            }
            if (inString) {
                if (c == '\\') { i++; continue; }
                if (c == quote) inString = false;
                continue;
            }
            if (c == '/' && next == '/') { inLineComment = true; i++; continue; }
            if (c == '/' && next == '*') { inBlockComment = true; i++; continue; }
            switch (c) {
                case '\'', '"', '`' -> { inString = true; quote = c; }
                case '(' -> paren++;
                case ')' -> paren--;
                case '{' -> brace++;
                case '}' -> brace--;
                case '[' -> brack++;
                case ']' -> brack--;
                default -> { }
            }
            if (paren < 0 || brace < 0 || brack < 0) return false;
        }
        return !inString && !inBlockComment && paren == 0 && brace == 0 && brack == 0;
    }

    /** Writes `content` to `file` only if it passes {@link #looksWellFormed()}.
     *  Returns true if actually written; false means the previous file on
     *  disk (if any) was left untouched. */
    public static boolean writeIfValid(Path file, String content, String sourceLabel) {
        if (!looksWellFormed(content)) {
            CraftEditorMod.LOGGER.error(
                    "[Shovel JS] Refused to write {} - the generated script failed a basic syntax sanity " +
                    "check (unbalanced braces/parens/quotes). The previous version on disk was left " +
                    "untouched. This usually means one entry has a stray quote or bracket in a " +
                    "hand-typed field ({}).", file.getFileName(), sourceLabel);
            return false;
        }
        try {
            backup(file);
            // Atomico: KubeJS nunca lee un script a medio escribir en /reload.
            com.kve.common.util.SafeFiles.writeAtomic(file, content);
            return true;
        } catch (IOException ex) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Write failed for {}: {}", file, ex.getMessage());
            return false;
        }
    }

    private static void backup(Path file) {
        if (!Files.isRegularFile(file)) return;
        try {
            Path bak = file.resolveSibling(file.getFileName().toString() + ".bak");
            Files.copy(file, bak, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] Could not write .bak for {}: {}", file, ex.getMessage());
        }
    }
}
