package com.kve.common.code;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Deshacer/rehacer por pestaña del editor de Files. Guarda snapshots
 * completos del texto + cursor: los scripts que edita son chicos (decenas a
 * pocos cientos de lineas), asi que un snapshot por cambio es mas simple y
 * mas robusto que un log de operaciones inversas.
 * <p>
 * Coalescencia: varias teclas de escritura seguidas en la misma linea (dentro
 * de {@link #COALESCE_MS}) forman UN solo paso de deshacer, como en cualquier
 * editor - sin esto Ctrl+Z borraria letra por letra.
 */
public final class CodeUndoHistory {

    public record Snapshot(String text, int line, int col) {}

    public static final int MAX = 200;
    public static final long COALESCE_MS = 1000;

    private final Deque<Snapshot> undo = new ArrayDeque<>();
    private final Deque<Snapshot> redo = new ArrayDeque<>();
    private long lastTypingAt = 0;
    private int lastTypingLine = -1;

    /**
     * Registra que el texto cambio desde {@code before}. {@code typing} = el
     * cambio fue un caracter tipeado (candidato a coalescer); {@code now} en ms.
     */
    public void record(Snapshot before, boolean typing, long now) {
        boolean coalesce = typing && !undo.isEmpty() && before.line() == lastTypingLine
                && now - lastTypingAt <= COALESCE_MS;
        if (!coalesce) {
            undo.push(before);
            while (undo.size() > MAX) undo.removeLast();
        }
        redo.clear();
        if (typing) { lastTypingAt = now; lastTypingLine = before.line(); }
        else { lastTypingAt = 0; lastTypingLine = -1; }
    }

    public boolean canUndo() { return !undo.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }

    /** Devuelve el estado a restaurar, guardando {@code current} para rehacer; null si no hay nada. */
    public Snapshot undo(Snapshot current) {
        if (undo.isEmpty()) return null;
        redo.push(current);
        breakCoalescing();
        return undo.pop();
    }

    public Snapshot redo(Snapshot current) {
        if (redo.isEmpty()) return null;
        undo.push(current);
        breakCoalescing();
        return redo.pop();
    }

    /** Corta la coalescencia (p.ej. el cursor se movio): la proxima letra abre un paso nuevo. */
    public void breakCoalescing() { lastTypingAt = 0; lastTypingLine = -1; }
}
