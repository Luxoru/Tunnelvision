package dev.luxoru.tunnelvision.util;

import java.util.concurrent.locks.Lock;

/**
 * Holds a lock from creation until closed, for try-with-resources:
 * <pre>{@code
 * try (var _ = new CloseableLock(lock)) {
 *     // Locked here
 * }
 * }</pre>
 */
public final class CloseableLock implements AutoCloseable {
    private final Lock lock;

    public CloseableLock(Lock lock) {
        lock.lock();
        this.lock = lock;
    }

    @Override
    public void close() {
        lock.unlock();
    }
}
