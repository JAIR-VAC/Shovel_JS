package com.kve.common.structure;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class StructureSaveExecutor {

    private StructureSaveExecutor() {}

    private static final AtomicInteger THREAD_COUNT = new AtomicInteger(0);

    private static final ThreadFactory FACTORY = r -> {
        Thread t = new Thread(r, "Shovel JS-Structure-Save-" + THREAD_COUNT.incrementAndGet());
        t.setDaemon(true);
        return t;
    };

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(FACTORY);

    public static ExecutorService get() {
        return EXECUTOR;
    }
}
