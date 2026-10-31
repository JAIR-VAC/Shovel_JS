package com.kve.common.util;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Deshacer/rehacer por instantaneas: antes de
 * cada cambio se guarda el estado con {@link #push}; {@link #undo} devuelve el
 * anterior y guarda el actual para {@link #redo}. Un cambio nuevo borra lo
 * que habia para rehacer, como en cualquier editor.
 */
public final class UndoStack<T> {
    private final int limit;
    private final Deque<T> undo = new ArrayDeque<>();
    private final Deque<T> redo = new ArrayDeque<>();

    public UndoStack(int limit) {
        this.limit = Math.max(1, limit);
    }

    /** Guarda {@code before} (el estado ANTES del cambio que se va a hacer). */
    public void push(T before) {
        undo.push(before);
        while (undo.size() > limit) undo.removeLast();
        redo.clear();
    }

    /** Estado anterior, o null si no hay; {@code current} queda para rehacer. */
    public T undo(T current) {
        if (undo.isEmpty()) return null;
        redo.push(current);
        return undo.pop();
    }

    public T redo(T current) {
        if (redo.isEmpty()) return null;
        undo.push(current);
        return redo.pop();
    }

    public boolean canUndo() { return !undo.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }

    public void clear() {
        undo.clear();
        redo.clear();
    }
}
