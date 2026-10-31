package com.kve.common.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Helpers compartidos que reemplazaron las copias privadas por exporter. */
class SharedHelpersTest {

    @Test
    void jsEscapingNeverLeavesALiteralOpen() {
        assertEquals("'it\\'s'", JsStrings.quote("it's"));
        assertEquals("a\\\\b", JsStrings.escape("a\\b"));
        assertEquals("line1\\nline2\\r", JsStrings.escape("line1\nline2\r"), "salto de linea crudo cortaba el literal");
        assertEquals("x\\u2028y", JsStrings.escape("x\u2028y"));
        assertEquals("", JsStrings.escape(null));
        assertEquals("''", JsStrings.quote(null));
        assertEquals("\"say \\\"hi\\\"\\n\"", JsStrings.quoteDouble("say \"hi\"\n"));
        assertEquals("'{display:{Name:\\'x\\'}}'", JsStrings.quote("{display:{Name:'x'}}"), "NBT con comillas simples");
    }

    @Test
    void numberFormatsMatchTheGoldenOutputOfEachExporter() {
        assertEquals("3", KveNumbers.padded4(3.0));
        assertEquals("0.2500", KveNumbers.padded4(0.25));
        assertEquals("-1.5000", KveNumbers.padded4(-1.5));
        assertEquals("3", KveNumbers.trimmed(3.0, 4));
        assertEquals("0.4", KveNumbers.trimmed(0.4, 4));
        assertEquals("0.1235", KveNumbers.trimmed(0.123456, 4));
        assertEquals("0", KveNumbers.trimmed(-0.00001, 4));
        assertEquals("2", KveNumbers.trimmed(1.99999, 3));
    }

    @Test
    void numberFormatIgnoresTheJvmLocale() {
        java.util.Locale prev = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(new java.util.Locale("es", "ES"));
            assertEquals("0.5000", KveNumbers.padded4(0.5), "coma decimal rompe Math.random() < 0,5");
            assertEquals("0.5", KveNumbers.trimmed(0.5, 4));
        } finally {
            java.util.Locale.setDefault(prev);
        }
    }

    @Test
    void atomicWriteCreatesParentsReplacesAndLeavesNoTemp(@TempDir Path dir) throws IOException {
        Path f = dir.resolve("a/b/state.json");
        SafeFiles.writeAtomic(f, "v1");
        SafeFiles.writeAtomic(f, "v2");
        assertEquals("v2", Files.readString(f));
        assertFalse(Files.exists(dir.resolve("a/b/state.json.tmp")));
    }
}
