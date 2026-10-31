package com.kve.common.kubejs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** "nombre visible siempre" sin nombre escrito no debe deshacerse. */
class MobNameVisibilityTest {

    private static String body(KubeJSMobExporter.MobEntry e) {
        StringBuilder sb = new StringBuilder();
        KubeJSMobExporter.appendMobBody(sb, e, "");
        return sb.toString();
    }

    @Test
    void visibleWithoutNameStaysVisible() {
        var e = new KubeJSMobExporter.MobEntry();
        e.customName = "";
        e.customNameSet = true;
        e.customNameVisible = true;
        String js = body(e);
        assertTrue(js.contains("mob.setCustomNameVisible(true)"), js);
        assertFalse(js.contains("setCustomName(null)"), js);
    }

    @Test
    void hiddenWithoutNameStillClearsName() {
        var e = new KubeJSMobExporter.MobEntry();
        e.customName = "";
        e.customNameSet = true;
        e.customNameVisible = false;
        String js = body(e);
        assertTrue(js.contains("mob.setCustomName(null)"), js);
        assertTrue(js.contains("mob.setCustomNameVisible(false)"), js);
    }
}
