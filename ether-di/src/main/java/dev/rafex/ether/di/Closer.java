package dev.rafex.ether.di;

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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Composite {@link AutoCloseable} that closes registered resources in LIFO order.
 *
 * <p>Resources are registered via {@link #register(AutoCloseable)} and are closed
 * in reverse registration order (last-in, first-out) when {@link #close()} is called.
 * If multiple resources throw during close, only the first exception is propagated;
 * subsequent ones are attached as {@linkplain Throwable#addSuppressed suppressed}.
 *
 * <p>Calling {@link #close()} more than once is a no-op after the first call.
 *
 * <pre>{@code
 * var closer = new Closer();
 * var pool   = closer.register(DataSourceFactory.create(config));
 * var cache  = closer.register(new CacheManager());
 *
 * try (closer) {
 *     runApplication(pool, cache);
 * }
 * // cache closed first, then pool
 * }</pre>
 */
public final class Closer implements AutoCloseable {

    private final Deque<AutoCloseable> resources = new ArrayDeque<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Registers a resource to be closed when {@link #close()} is called.
     *
     * @param <T>      type of the resource
     * @param resource the resource to register; must not be {@code null}
     * @return the resource itself, for chained assignment
     */
    public <T extends AutoCloseable> T register(final T resource) {
        if (resource == null) {
            throw new NullPointerException("resource");
        }
        synchronized (resources) {
            resources.push(resource);
        }
        return resource;
    }

    /**
     * Closes all registered resources in LIFO order.
     *
     * <p>Idempotent: subsequent calls after the first are no-ops.
     *
     * @throws RuntimeException wrapping the first exception thrown during close,
     *                          with any subsequent exceptions attached as suppressed
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        Throwable first = null;
        while (true) {
            final AutoCloseable resource;
            synchronized (resources) {
                resource = resources.isEmpty() ? null : resources.pop();
            }
            if (resource == null) {
                break;
            }
            try {
                resource.close();
            } catch (final Throwable t) {
                if (first == null) {
                    first = t;
                } else {
                    first.addSuppressed(t);
                }
            }
        }
        if (first != null) {
            if (first instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException("Error closing resources", first);
        }
    }

    /**
     * Returns {@code true} if {@link #close()} has already been called.
     *
     * @return {@code true} after the first call to {@link #close()}
     */
    public boolean isClosed() {
        return closed.get();
    }
}
