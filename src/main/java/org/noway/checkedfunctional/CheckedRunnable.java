package org.noway.checkedfunctional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@FunctionalInterface
public interface CheckedRunnable<E extends Exception> {

    /**
     * Create a {@link CheckedRunnable} that runs all given runnables in sequential order. The common exception
     * supertype is automatically inferred.
     *
     * @param runnables - vararg of runnables to run
     * @param <E>       the common exception supertype given by checked runnables
     * @return a {@link CheckedRunnable} that combines all given runnables
     */
    @SafeVarargs
    static <E extends Exception> CheckedRunnable<E> runSequentially(
            CheckedRunnable<? extends E>... runnables) {
        return () -> {
            final List<E> exceptions = new ArrayList<>();
            Arrays.stream(runnables).forEach(t -> {
                try {
                    t.run();
                } catch (Exception e) {
                    exceptions.add((E) e);
                }
            });

            //throw first caught exception, add all other as suppressed exceptions.
            if (!exceptions.isEmpty()) {
                E exception = exceptions.get(0);
                exceptions.subList(1, exceptions.size()).forEach(exception::addSuppressed);
                throw exception;
            }
        };
    }

    static <E extends Exception> void runConcurrently(CheckedRunnable<? extends E>... runnables) throws E {
        //create completable futures out of runnables and catch all checked exceptions
        List<Throwable> thrownExceptions = new ArrayList<>();
        CompletableFuture[] completableFutures = Arrays.stream(runnables).map(cr -> CompletableFuture
                .runAsync(cr.toRunnable()).exceptionally(ex -> {
                    thrownExceptions.add(ex);
                    //return null for void return type
                    return null;
                })).collect
                (Collectors.toList()).toArray(new
                CompletableFuture[0]);

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(completableFutures);
        allFutures.join();

        //A CompleteException wraps a RuntimeException that wraps a checked exception of type E
        List<E> unpackedExceptions = thrownExceptions.stream().map(Throwable::getCause).map(Throwable::getCause).map(t
                -> (E) t).collect
                (Collectors.toList());

        if (!unpackedExceptions.isEmpty()) {
            E exception = unpackedExceptions.get(0);
            unpackedExceptions.subList(1, unpackedExceptions.size()).forEach(exception::addSuppressed);
            throw exception;
        }
    }

    //E1 extends RE to ensure RE is super type exception of both E1, E2. However, E1 is not inferred by a typed
    // parameter. This requires the caller to specify types explicitly.
    default <RE extends Exception, E1 extends RE, E2 extends RE> CheckedRunnable<RE> followedBy(CheckedRunnable<E2>
                                                                                                        other) {
        CheckedRunnable<E1> thisRunnable = (CheckedRunnable<E1>) this;
        return runSequentially(thisRunnable, other);
    }

    /**
     * Convert this {@link CheckedRunnable} to a {@link Runnable} that throws a checked exception as cause of an
     * unchecked {@link RuntimeException}.
     *
     * @return - a converted {@link Runnable}
     */
    default Runnable toRunnable() {
        return toRunnable(RuntimeException::new);
    }

    /**
     * Convert this {@link CheckedRunnable} to a {@link Runnable} that throws the checked exception
     * as a custom runtime exception of any type ? extends RuntimeException. <br>
     * This method allows to use a custom type so that callers can differentiate between a "real"
     * runtime exception and the one that only wraps a checked exception.
     *
     * @param exceptionSupplier - supplies the runtime exception to throw
     * @return - a converted {@link Runnable}
     */
    default Runnable toRunnable(Supplier<? extends RuntimeException> exceptionSupplier) {
        return () -> {
            try {
                run();
            } catch (Exception e) {
                RuntimeException runtimeException = exceptionSupplier.get();
                runtimeException.initCause(e);
                throw runtimeException;
            }
        };
    }


    /**
     * Execute this {@link CheckedRunnable} as {@link Runnable} in a context that consumes a Runnable. Any checked
     * exception is caught and re-thrown.
     *
     * @param context the context that consumes a runnable
     * @throws RE inferred exception supertype of runnable and given context
     */
    default <RE extends Exception, E1 extends RE> void runAsRunnableInContext(CheckedConsumer<Runnable, E1> context)
            throws RE {
        try {
            context.consume(toRunnable(CheckedExceptionMarkerRuntimeException::new));
        } catch (CheckedExceptionMarkerRuntimeException e) {
            // a checked exception was caught inside the runnable. Extract and rethrow.
            // unchecked cast is ok, because it is known that only type E can be contained within this
            // runtime exception.
            throw (RE) e.getCause();
        }
    }

    /**
     * Execute a runnable in a dedicated {@link Thread} which re-throws checked exceptions in the caller thread.
     * <br>
     * This method blocks until the calling thread has finished. Please see
     * {@link #runConcurrently(CheckedRunnable[])} for multiple concurrent executions.
     *
     * @throws E checked exception to throw
     */
    default void runAsRunnableInThread() throws E {
        //additional try and unchecked cast because runAsRunnableInContext may throw any subtype of Exception.
        // However, for thread context it is known that the thrown type is exactly E so it can be downcast explicitly.
        try {
            runAsRunnableInContext((r) -> {
                AtomicReference<CheckedExceptionMarkerRuntimeException> caught = new AtomicReference<>();
                Thread thread = new Thread(r);
                thread.setUncaughtExceptionHandler((t, thr) -> caught.set((CheckedExceptionMarkerRuntimeException)
                        thr));

                thread.start();
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    //re-interrupt thread to preserve interrupt flag
                    Thread.currentThread().interrupt();
                }
                if (caught.get() != null) {
                    throw caught.get();
                }
            });
        } catch (Exception e) {
            throw (E) e;
        }
    }

    /**
     * The action to execute.
     *
     * @throws E type of exception to throw
     */
    void run() throws E;
}

