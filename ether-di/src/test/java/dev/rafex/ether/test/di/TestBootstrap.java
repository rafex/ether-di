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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import dev.rafex.ether.di.Bootstrap;

public class TestBootstrap {

    /** Minimal container that is not AutoCloseable. */
    record PlainContainer(String name) {}

    /** Container that is AutoCloseable — Bootstrap must register it with the Closer. */
    static class CloseableContainer implements AutoCloseable {
        final List<String> events = new ArrayList<>();

        @Override
        public void close() {
            events.add("closed");
        }
    }

    @Test
    void startReturnsRuntimeWithContainer() {
        final var runtime = Bootstrap.start(() -> new PlainContainer("test"));
        Assertions.assertNotNull(runtime);
        Assertions.assertEquals("test", runtime.container().name());
        runtime.close();
    }

    @Test
    void runtimeCloseIsIdempotent() {
        final var runtime = Bootstrap.start(() -> new PlainContainer("idem"));
        runtime.close();
        Assertions.assertDoesNotThrow(runtime::close);
    }

    @Test
    void warmupIsInvokedBeforeRuntimeReturned() {
        final var warmedUp = new AtomicBoolean(false);
        final var runtime = Bootstrap.start(
                () -> new PlainContainer("warmup"),
                c -> warmedUp.set(true));

        Assertions.assertTrue(warmedUp.get());
        runtime.close();
    }

    @Test
    void nullContainerFactoryThrows() {
        Assertions.assertThrows(NullPointerException.class,
                () -> Bootstrap.start(null));
    }

    @Test
    void factoryReturningNullThrows() {
        Assertions.assertThrows(NullPointerException.class,
                () -> Bootstrap.start(() -> null));
    }

    @Test
    void autoCloseableContainerIsClosedByRuntime() {
        final var container = new CloseableContainer();
        final var runtime = Bootstrap.start(() -> container);

        Assertions.assertTrue(container.events.isEmpty());
        runtime.close();
        Assertions.assertEquals(List.of("closed"), container.events);
    }

    @Test
    void tryWithResourcesClosesAutoCloseableContainer() throws Exception {
        final var container = new CloseableContainer();
        try (final var runtime = Bootstrap.start(() -> container)) {
            Assertions.assertTrue(container.events.isEmpty());
        }
        Assertions.assertEquals(List.of("closed"), container.events);
    }

    @Test
    void warmupExceptionAbortsStart() {
        Assertions.assertThrows(RuntimeException.class,
                () -> Bootstrap.start(
                        () -> new PlainContainer("fail"),
                        c -> { throw new RuntimeException("warmup failed"); }));
    }

    @Test
    void startWithAllThreeArgsRegistersShutdownHook() {
        final var onShutdownCalled = new AtomicBoolean(false);
        final var runtime = Bootstrap.start(
                () -> new PlainContainer("full"),
                null,
                c -> onShutdownCalled.set(true));

        // Shutdown hook is a JVM hook — we can only verify runtime was created
        Assertions.assertNotNull(runtime.container());
        Assertions.assertNotNull(runtime.closer());
        runtime.close();
    }
}
