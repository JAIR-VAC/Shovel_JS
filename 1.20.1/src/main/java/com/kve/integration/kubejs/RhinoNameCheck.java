package com.kve.integration.kubejs;

import dev.latvian.mods.rhino.CompilerEnvirons;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Node;
import dev.latvian.mods.rhino.Parser;
import dev.latvian.mods.rhino.Token;
import dev.latvian.mods.rhino.ast.AstNode;
import dev.latvian.mods.rhino.ast.AstRoot;
import dev.latvian.mods.rhino.ast.Assignment;
import dev.latvian.mods.rhino.ast.BreakStatement;
import dev.latvian.mods.rhino.ast.CatchClause;
import dev.latvian.mods.rhino.ast.ContinueStatement;
import dev.latvian.mods.rhino.ast.ErrorCollector;
import dev.latvian.mods.rhino.ast.FunctionNode;
import dev.latvian.mods.rhino.ast.Name;
import dev.latvian.mods.rhino.ast.ObjectProperty;
import dev.latvian.mods.rhino.ast.PropertyGet;
import dev.latvian.mods.rhino.ast.Scope;
import dev.latvian.mods.rhino.ast.UnaryExpression;
import dev.latvian.mods.rhino.ast.VariableInitializer;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class RhinoNameCheck {

    private RhinoNameCheck() {}

    /** Un nombre sin declarar: offset y largo sobre el texto completo (lineas unidas con '\n'). */
    public record Problem(int offset, int length, String name) {}

    private static final int MAX_PROBLEMS = 50;

    public static List<Problem> check(String source, String sourceName, Predicate<String> isGlobal) {
        AstRoot root;
        try {
            ErrorCollector collector = new ErrorCollector();
            Context cx = Context.enter();
            CompilerEnvirons env = new CompilerEnvirons();
            env.initFromContext(cx);
            env.setErrorReporter(collector);
            root = new Parser(cx, env, collector).parse(source, sourceName, 1);
            for (var p : collector.getErrors()) {
                if (p.getType() == dev.latvian.mods.rhino.ast.ParseProblem.Type.Error) return List.of();
            }
        } catch (Throwable t) {
            return List.of();
        }
        try {
            List<AstNode> all = new ArrayList<>();
            collect(root, all, Collections.newSetFromMap(new IdentityHashMap<>()));
            Set<String> declared = new HashSet<>();
            for (AstNode n : all) addDeclared(n, declared);
            List<Problem> out = new ArrayList<>();
            for (AstNode n : all) {
                if (!(n instanceof Name name)) continue;
                String id = name.getIdentifier();
                if (id == null || id.isEmpty() || declared.contains(id) || isGlobal.test(id)) continue;
                if (!isReference(name)) continue;
                out.add(new Problem(name.getAbsolutePosition(), Math.max(1, name.getLength()), id));
                if (out.size() >= MAX_PROBLEMS) break;
            }
            return out;
        } catch (Throwable t) {
            return List.of();
        }
    }

    private static void addDeclared(AstNode n, Set<String> declared) {
        if (n instanceof Scope s && s.getSymbolTable() != null) declared.addAll(s.getSymbolTable().keySet());
        if (n instanceof FunctionNode f) {
            if (f.getFunctionName() != null) declared.add(f.getFunctionName().getIdentifier());
            for (AstNode p : f.getParams()) addNamesIn(p, declared);
        } else if (n instanceof CatchClause c && c.getVarName() != null) {
            declared.add(c.getVarName().getIdentifier());
        } else if (n instanceof VariableInitializer v) {
            addNamesIn(v.getTarget(), declared);
        } else if (n instanceof Assignment a && a.getType() == Token.ASSIGN && a.getLeft() instanceof Name left) {
            declared.add(left.getIdentifier());
        }
    }

    private static void addNamesIn(AstNode target, Set<String> declared) {
        if (target == null) return;
        List<AstNode> nodes = new ArrayList<>();
        collect(target, nodes, Collections.newSetFromMap(new IdentityHashMap<>()));
        for (AstNode x : nodes) if (x instanceof Name nm) declared.add(nm.getIdentifier());
    }

    private static boolean isReference(Name n) {
        AstNode p = n.getParent();
        if (p == null) return false;
        if (p instanceof PropertyGet pg) return pg.getTarget() == n;
        if (p instanceof ObjectProperty op) return op.getLeft() != n;
        if (p instanceof FunctionNode) return false;
        if (p instanceof VariableInitializer v) return v.getTarget() != n;
        if (p instanceof CatchClause || p instanceof BreakStatement || p instanceof ContinueStatement) return false;
        if (p instanceof UnaryExpression u && (u.getOperator() == Token.TYPEOF || u.getOperator() == Token.TYPEOFNAME)) return false;
        String cls = p.getClass().getSimpleName();
        if (cls.startsWith("Xml") || cls.startsWith("Label")) return false;
        return true;
    }

    private static final Map<Class<?>, List<Method>> GETTERS = new ConcurrentHashMap<>();
    private static final Set<String> SKIP_GETTERS = Set.of(
            "getParent", "getEnclosingScope", "getEnclosingFunction", "getAstRoot", "getParentScope",
            "getTop", "getInlineComment", "getChildScopes", "getDefiningScope", "getComments",
            "getSymbols", "getJumpStatement", "getLoop", "getNext", "getFirstChild", "getLastChild",
            "getScope");

    private static void collect(AstNode n, List<AstNode> out, Set<AstNode> seen) {
        if (n == null || !seen.add(n)) return;
        out.add(n);
        for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
            if (c instanceof AstNode a) collect(a, out, seen);
        }
        for (Method m : getters(n.getClass())) {
            Object v;
            try {
                v = m.invoke(n);
            } catch (Throwable t) {
                // Un getter que lanza en esta clase va a lanzar siempre: se descarta para no
                // pagar el costo de la excepcion en cada nodo (ver "getScope" arriba).
                GETTERS.computeIfPresent(n.getClass(), (k, l) -> l.stream().filter(x -> !x.equals(m)).toList());
                continue;
            }
            if (v instanceof AstNode a) {
                if (a.getParent() == n) collect(a, out, seen);
            } else if (v instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof AstNode a && a.getParent() == n) collect(a, out, seen);
                }
            }
        }
    }

    private static List<Method> getters(Class<?> cls) {
        return GETTERS.computeIfAbsent(cls, k -> {
            List<Method> list = new ArrayList<>();
            for (Method m : k.getMethods()) {
                if (m.getParameterCount() != 0 || Modifier.isStatic(m.getModifiers())) continue;
                if (!m.getName().startsWith("get") || SKIP_GETTERS.contains(m.getName())) continue;
                Class<?> r = m.getReturnType();
                if (AstNode.class.isAssignableFrom(r) || List.class.isAssignableFrom(r)) list.add(m);
            }
            return list;
        });
    }
}
