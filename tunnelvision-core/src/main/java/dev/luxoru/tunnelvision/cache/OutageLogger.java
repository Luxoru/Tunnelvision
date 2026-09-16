package dev.luxoru.tunnelvision.cache;

import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

public final class OutageLogger {
    private final Logger logger;
    private final String backend;
    private final AtomicBoolean down = new AtomicBoolean(false);

    public OutageLogger(Logger logger, String backend) {
        this.logger = logger;
        this.backend = backend;
    }

    public void success() {
        if (down.compareAndSet(true, false)) {
            logger.info("{} verdict cache recovered", backend);
        }
    }

    public void failure(String action, RuntimeException ex) {
        if (down.compareAndSet(false, true)) {
            logger.warn("Couldn't {} {}. Further failures logged at DEBUG until it recovers", action, backend, ex);
        } else {
            logger.debug("Couldn't {} {}", action, backend, ex);
        }
    }


    public boolean isDown() {
        return down.get();
    }
}
