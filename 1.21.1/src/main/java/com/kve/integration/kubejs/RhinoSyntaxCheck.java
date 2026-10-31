package com.kve.integration.kubejs;

import dev.latvian.mods.rhino.CompilerEnvirons;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Parser;
import dev.latvian.mods.rhino.ast.ErrorCollector;
import dev.latvian.mods.rhino.ast.ParseProblem;

import java.util.ArrayList;
import java.util.List;

public final class RhinoSyntaxCheck {

    private RhinoSyntaxCheck() {}

    /** Un error de sintaxis: offset y largo sobre el texto completo (lineas unidas con '\n'). */
    public record Problem(int offset, int length, String message) {}

    /**
     * Parsea {@code source} y devuelve los errores de sintaxis (vacio si es
     * valido). Rhino corta en el primer error, asi que en la practica es 0 o 1.
     * Nunca lanza: cualquier fallo interno del parser se trata como "sin
     * informacion" para no romper el editor.
     */
    public static List<Problem> check(String source, String sourceName) {
        ErrorCollector collector = new ErrorCollector();
        try {
            Context cx = new dev.latvian.mods.rhino.ContextFactory().enter();
            CompilerEnvirons env = new CompilerEnvirons();
            env.initFromContext(cx);
            env.setErrorReporter(collector);
            new Parser(cx, env, collector).parse(source, sourceName, 1);
        } catch (Throwable ignored) {
        }
        List<Problem> out = new ArrayList<>();
        for (ParseProblem p : collector.getErrors()) {
            if (p.getType() != ParseProblem.Type.Error) continue;
            out.add(new Problem(p.getFileOffset(), p.getLength(), p.getMessage()));
        }
        return out;
    }
}
