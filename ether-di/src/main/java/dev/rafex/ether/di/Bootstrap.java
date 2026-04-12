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
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Generic application bootstrap that wires a container with lifecycle management.
 *
 * <p>{@link Bootstrap} ties together three lifecycle concerns:
 * <ol>
 *   <li><b>Creation</b> — a {@link Supplier} builds the application-specific container.</li>
 *   <li><b>Warmup</b> — an optional {@link Consumer} eagerly initializes all components
 *       so startup failures surface immediately instead of at first request.</li>
 *   <li><b>Shutdown</b> — a JVM shutdown hook calls an optional pre-close callback,
 *       then closes the {@link Closer} in LIFO order.</li>
 * </ol>
 *
 * <p>The returned {@link Runtime} is {@link AutoCloseable}, so it integrates naturally
 * with try-with-resources for graceful shutdown in tests and CLI applications.
 *
 * <pre>{@code
 * var runtime = Bootstrap.start(
 *     AppContainer::new,       // factory
 *     AppContainer::warmup,    // fail-fast eager init
 *     container -> {}          // pre-shutdown callback
 * );
 *
 * try (runtime) {
 *     JettyServer.start(runtime.container());
 * }
 * }</pre>
 *
 * <p>This class has no public constructors — use the static factory methods.
 */
public final class Bootstrap {

    private Bootstrap() {
        // static factory only
    }

    /**
     * Immutable holder for the running application state.
     *
     * @param <C> type of the application container
     */
    public record Runtime<C>(C container, Closer closer) implements AutoCloseable {

        /** Creates a Runtime; both parameters must be non-null. */
        public Runtime {
            Objects.requireNonNull(container, "container");
            Objects.requireNonNull(closer, "closer");
        }

        /**
         * Closes the underlying {@link Closer}, releasing all registered resources in LIFO order.
         */
        @Override
        public void close() {
            closer.close();
        }
    }

    /**
     * Creates and starts the application.
     *
     * <p>Steps performed:
     * <ol>
     *   <li>Create a new {@link Closer}.</li>
     *   <li>Build the container via {@code containerFactory}.</li>
     *   <li>If the container implements {@link AutoCloseable}, register it with the closer.</li>
     *   <li>If {@code warmup} is non-null, invoke it — any exception aborts startup.</li>
     *   <li>Register a JVM shutdown hook that calls {@code onShutdown} (if non-null)
     *       then closes the closer.</li>
     * </ol>
     *
     * @param <C>              type of the application container
     * @param containerFactory supplier that creates the container; must not return {@code null}
     * @param warmup           optional consumer invoked after container creation to eagerly
     *                         initialize all components; {@code null} disables warmup
     * @param onShutdown       optional consumer invoked on JVM shutdown before resources are
     *                         closed; {@code null} disables the pre-shutdown callback
     * @return a {@link Runtime} holding the container and its closer
     */
    public static <C> Runtime<C> start(final Supplier<C> containerFactory,
                                       final Consumer<C> warmup,
                                       final Consumer<C> onShutdown) {
        Objects.requireNonNull(containerFactory, "containerFactory");

        final var closer = new Closer();
        final C container = Objects.requireNonNull(containerFactory.get(), "containerFactory returned null");

        if (container instanceof AutoCloseable ac) {
            closer.register(ac);
        }

        if (warmup != null) {
            warmup.accept(container);
        }

        final var runtime = new Runtime<>(container, closer);

        java.lang.Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (onShutdown != null) {
                try {
                    onShutdown.accept(container);
                } catch (final Exception e) {
                    // best-effort; don't prevent closer from running
                }
            }
            closer.close();
        }, "ether-di-shutdown"));

        return runtime;
    }

    /**
     * Convenience overload — starts without a pre-shutdown callback.
     *
     * @param <C>              type of the application container
     * @param containerFactory supplier that creates the container
     * @param warmup           optional warmup consumer; {@code null} disables warmup
     * @return a {@link Runtime} holding the container and its closer
     * @see #start(Supplier, Consumer, Consumer)
     */
    public static <C> Runtime<C> start(final Supplier<C> containerFactory,
                                       final Consumer<C> warmup) {
        return start(containerFactory, warmup, null);
    }

    /**
     * Convenience overload — starts without warmup or pre-shutdown callback.
     *
     * @param <C>              type of the application container
     * @param containerFactory supplier that creates the container
     * @return a {@link Runtime} holding the container and its closer
     * @see #start(Supplier, Consumer, Consumer)
     */
    public static <C> Runtime<C> start(final Supplier<C> containerFactory) {
        return start(containerFactory, null, null);
    }
}
