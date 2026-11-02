package com.lorenzflow.app.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shared background executor for database and crypto work.
 *
 * Every call site used to build its own {@code Executors.newSingleThreadExecutor()},
 * which spawns a non-daemon core thread that is never shut down — so each task
 * save, archive or delete left another idle thread behind for the life of the
 * process. One shared single-thread executor keeps database access serialised
 * (the previous behaviour per operation) without the thread churn.
 */
public final class AppExecutors {

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "lf-io");
        thread.setDaemon(true);
        return thread;
    });

    private AppExecutors() {
    }

    /** The shared executor backing all Room and keystore work. */
    public static ExecutorService io() {
        return IO;
    }
}
