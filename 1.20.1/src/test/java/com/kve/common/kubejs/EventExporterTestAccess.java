package com.kve.common.kubejs;

/** Test-only bridge to the package-private {@link EventExporter#buildEventsScript()} for tests outside this package. */
public final class EventExporterTestAccess {
    private EventExporterTestAccess() {}

    public static String buildEventsScript() { return EventExporter.buildEventsScript(); }
}
