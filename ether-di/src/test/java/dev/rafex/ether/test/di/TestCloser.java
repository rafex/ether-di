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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import dev.rafex.ether.di.Closer;

public class TestCloser {

    @Test
    void closesInLifoOrder() {
        final var order = new ArrayList<String>();
        final var closer = new Closer();

        closer.register(closeable("a", order));
        closer.register(closeable("b", order));
        closer.register(closeable("c", order));

        Assertions.assertFalse(closer.isClosed());
        closer.close();
        Assertions.assertTrue(closer.isClosed());
        Assertions.assertEquals(List.of("c", "b", "a"), order);
    }

    @Test
    void closeIsIdempotent() {
        final var order = new ArrayList<String>();
        final var closer = new Closer();
        closer.register(closeable("x", order));

        closer.close();
        closer.close();
        closer.close();

        Assertions.assertEquals(List.of("x"), order);
    }

    @Test
    void nullResourceThrows() {
        final var closer = new Closer();
        Assertions.assertThrows(NullPointerException.class, () -> closer.register(null));
    }

    @Test
    void registerReturnsSameInstance() {
        final var closer = new Closer();
        final AutoCloseable resource = closeable("r", new ArrayList<>());
        Assertions.assertSame(resource, closer.register(resource));
    }

    @Test
    void firstExceptionPropagatedWithSubsequentSuppressed() {
        final var closer = new Closer();
        closer.register(() -> { throw new IllegalStateException("third"); });
        closer.register(() -> { throw new IllegalStateException("second"); });
        closer.register(() -> { throw new IllegalStateException("first"); });

        final var ex = Assertions.assertThrows(RuntimeException.class, closer::close);
        Assertions.assertEquals("first", ex.getMessage());
        Assertions.assertEquals(2, ex.getSuppressed().length);
    }

    @Test
    void closeWithNoResourcesDoesNotThrow() {
        final var closer = new Closer();
        Assertions.assertDoesNotThrow(closer::close);
    }

    @Test
    void tryWithResourcesCloses() throws Exception {
        final var order = new ArrayList<String>();
        try (final var closer = new Closer()) {
            closer.register(closeable("first", order));
            closer.register(closeable("second", order));
        }
        Assertions.assertEquals(List.of("second", "first"), order);
    }

    private static AutoCloseable closeable(final String name, final List<String> order) {
        return () -> order.add(name);
    }
}
