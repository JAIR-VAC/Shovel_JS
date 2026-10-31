package com.kve.client.gui;

import com.kve.common.event.EventEntry;
import com.kve.common.kubejs.EventExporter;
import com.kve.common.kubejs.EventExporterTestAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EventTemplatesExportTest {

    @BeforeEach
    void setUp() { EventExporter.clearAll(); }

    @AfterEach
    void tearDown() { EventExporter.clearAll(); }

    @Test
    void everyTemplateIsStructurallyValidAndDispatched() {
        List<EventTemplates.Template> all = EventTemplates.all();
        assertTrue(all.size() > 0);
        Set<String> ids = new HashSet<>();
        for (EventTemplates.Template t : all) {
            EventEntry e = t.create();
            assertTrue(e.validateStructural().isEmpty(), t.nameKey + " is structurally invalid and would be skipped on export: " + e.validateStructural());
            if (!ids.add(e.id)) e.id = e.id + "_" + ids.size();
            ids.add(e.id);
            EventExporter.stageEvent(e);
        }
        String js = EventExporterTestAccess.buildEventsScript();
        for (EventEntry e : EventExporter.getEntries()) {
            String s = e.id.replaceAll("[^a-zA-Z0-9_]", "_");
            int n = 0;
            for (int i = js.indexOf("shovel_js_act_" + s + "("); i >= 0; i = js.indexOf("shovel_js_act_" + s + "(", i + 1)) n++;
            assertTrue(n >= 2, "template event " + e.id + " (" + e.trigger.type + ") is never dispatched");
        }
    }
}
