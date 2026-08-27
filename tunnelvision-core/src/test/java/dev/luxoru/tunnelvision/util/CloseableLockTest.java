package dev.luxoru.tunnelvision.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloseableLockTest {
    private final ReentrantLock lock = new ReentrantLock();

    @Test
    void locksOnCreationAndUnlocksOnClose() {
        try (var _ = new CloseableLock(lock)) {
            assertThat(lock.isHeldByCurrentThread()).isTrue();
        }
        assertThat(lock.isLocked()).isFalse();
    }

    @Test
    void unlocksWhenTheBlockThrows() {
        assertThatThrownBy(() -> {
            try (var _ = new CloseableLock(lock)) {
                throw new IllegalStateException("boom");
            }
        }).isInstanceOf(IllegalStateException.class);
        assertThat(lock.isLocked()).isFalse();
    }
}
