package dev.rafex.ether.test.di;

/*-
 * #%L
 * ether-di
 * %%
 * Copyright (C) 2026 Raúl Eduardo González Argote
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import dev.rafex.ether.di.Lazy;

public class TestLazy {

    @Test
    void supplierCalledOnce() {
        final var callCount = new AtomicInteger(0);
        final var lazy = new Lazy<>(() -> {
            callCount.incrementAndGet();
            return "hello";
        });

        Assertions.assertFalse(lazy.isInitialized());
        Assertions.assertEquals("hello", lazy.get());
        Assertions.assertEquals("hello", lazy.get());
        Assertions.assertEquals("hello", lazy.get());
        Assertions.assertEquals(1, callCount.get());
        Assertions.assertTrue(lazy.isInitialized());
    }

    @Test
    void nullSupplierThrows() {
        Assertions.assertThrows(NullPointerException.class, () -> new Lazy<>(null));
    }

    @Test
    void supplierReturningNullThrows() {
        final var lazy = new Lazy<>(() -> null);
        Assertions.assertThrows(NullPointerException.class, lazy::get);
    }

    @Test
    void concurrentGetInitializesOnce() throws InterruptedException {
        final var callCount = new AtomicInteger(0);
        final var lazy = new Lazy<>(() -> {
            callCount.incrementAndGet();
            return new Object();
        });

        final int threads = 16;
        final var latch = new CountDownLatch(1);
        final var ready = new CountDownLatch(threads);
        final var results = new Object[threads];

        try (final var executor = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                final int idx = i;
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        latch.await();
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    results[idx] = lazy.get();
                });
            }
            ready.await();
            latch.countDown();
        }

        Assertions.assertEquals(1, callCount.get());
        final Object first = results[0];
        for (final Object r : results) {
            Assertions.assertSame(first, r);
        }
    }
}
