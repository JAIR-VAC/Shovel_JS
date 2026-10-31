package com.kve.common.trade;

import com.kve.common.util.JsStrings;

public record TradeEmitContext(String indent, boolean preferDedicated) {

    public static TradeEmitContext of(String indent) {
        return new TradeEmitContext(indent, true);
    }

    /** JS single-quoted string literal, escaping backslash/quote - delegates to {@link JsStrings#quote}. */
    public String quote(String s) {
        return JsStrings.quote(s);
    }
}
