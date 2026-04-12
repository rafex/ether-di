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

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Thread-safe lazy initializer using double-checked locking.
 *
 * <p>The supplier is called at most once, on the first call to {@link #get()}.
 * After initialization the supplier reference is cleared so the supplier itself
 * (and any objects it closes over) can be garbage-collected.
 *
 * <pre>{@code
 * var config   = new Lazy<>(AppConfig::load);
 * var database = new Lazy<>(() -> DataSource.create(config.get()));
 * }</pre>
 *
 * @param <T> type of the lazily-initialized value
 */
public final class Lazy<T> {

    private volatile T value;
    private volatile Supplier<T> supplier;

    /**
     * Creates a new {@code Lazy} backed by the given supplier.
     *
     * @param supplier factory for the value; called at most once; must not return {@code null}
     */
    public Lazy(final Supplier<T> supplier) {
        this.supplier = Objects.requireNonNull(supplier, "supplier");
    }

    /**
     * Returns the lazily-initialized value, initializing it on first call.
     *
     * @return the value produced by the supplier
     * @throws NullPointerException if the supplier returned {@code null}
     */
    public T get() {
        T v = value;
        if (v == null) {
            synchronized (this) {
                v = value;
                if (v == null) {
                    v = Objects.requireNonNull(supplier.get(), "Lazy supplier returned null");
                    value = v;
                    supplier = null; // allow GC of the supplier and its captures
                }
            }
        }
        return v;
    }

    /**
     * Returns {@code true} if the value has already been initialized.
     *
     * @return {@code true} after the first call to {@link #get()} completes
     */
    public boolean isInitialized() {
        return value != null;
    }
}
