package org.noway.checkedfunctional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.noway.checkedfunctional.CheckedRunnable;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;


class CheckedRunnableTest {

    @Test
    public void testCheckedRunnableShouldThrowCheckedException() {
        Executable closure = () -> new MockRunnableRunner().runChecked(() -> {
            throw new CheckedException();
        });
        assertThrows(CheckedException.class, closure);
    }

    @Test
    public void testFollowedByRunnableShouldThrowCommonSupertypeException() {
        CheckedRunnable<CheckedSubTypeException> runnable = () -> {
            throw new CheckedSubTypeException();
        };
        CheckedRunnable<CheckedOtherSubTypeException> otherRunnable = () -> {
            throw new CheckedOtherSubTypeException();
        };

        //less beneficial syntax compired to combine because the type parameters need to be explicitly declared.
        CheckedRunnable<CheckedException> checkedRunnable = runnable
                .<CheckedException, CheckedSubTypeException, CheckedOtherSubTypeException>followedBy(otherRunnable);
        assertThrows(CheckedException.class, () -> new MockRunnableRunner().runChecked(checkedRunnable));
    }

    @Test
    public void testSequentialShouldThrowCommonSupertypeException() {
        CheckedRunnable<CheckedSubTypeException> runnable = () -> {
            throw new CheckedSubTypeException();
        };
        CheckedRunnable<CheckedOtherSubTypeException> otherRunnable = () -> {
            throw new CheckedOtherSubTypeException();
        };

        CheckedRunnable<CheckedException> sequential = CheckedRunnable.runSequentially(runnable, otherRunnable);
        assertThrows(CheckedException.class, () -> new MockRunnableRunner().runChecked(sequential));
    }

    @Test
    public void testSequentialCheckedRunnableWithHeterogenousSupertypeShouldThrowCommonSupertypeException() {
        CheckedRunnable<CheckedSubTypeException> runnable = () -> {
            throw new CheckedSubTypeException();
        };
        CheckedRunnable<CheckedOtherSubTypeException> otherRunnable = () -> {
            throw new CheckedOtherSubTypeException();
        };
        CheckedRunnable<Exception> thirdRunnable = () -> {
            throw new Exception();
        };

        CheckedRunnable<Exception> sequential = CheckedRunnable.runSequentially(runnable, otherRunnable, thirdRunnable);
        assertThrows(Exception.class, () -> new MockRunnableRunner().runChecked(sequential));
    }

    @Test
    public void testCheckedRunnableShouldBeWrappedInRuntimeException() throws InterruptedException {
        CheckedRunnable<CheckedException> runnable = () -> {
            throw new CheckedException();
        };

        Thread thread = new Thread(runnable.toRunnable());
        AtomicReference<Throwable> caught = new AtomicReference<>();
        thread.setUncaughtExceptionHandler((t, thr) -> caught.set(thr));

        thread.start();
        thread.join();

        assertTrue(caught.get() instanceof RuntimeException);
    }

    @Test
    public void testCheckedRunnableExecutedInContextShouldRethrowCheckedException() {
        CheckedRunnable<CheckedException> runnable = () -> {
            throw new CheckedException();
        };

        assertThrows(CheckedException.class, () -> {
            //example: how to run a runnable with checked exceptions in a context that does not allow checked exceptions
            runnable.runAsRunnableInContext((r) -> {
                new MockRunnableRunner().runUnchecked(r);
            });
        });
    }


    @Test
    public void testCheckedRunnableShouldRunInThreadAndRethrowCheckedException()
    {
        AtomicReference<String> calledThreadName = new AtomicReference<>();
        CheckedRunnable<CheckedException> runnable = () -> {
            calledThreadName.set(Thread.currentThread().getName());
            throw new CheckedException();
        };

        assertThrows(CheckedException.class, runnable::runAsRunnableInThread);

        assertNotNull(calledThreadName.get());
        assertNotEquals(calledThreadName, Thread.currentThread().getName());
    }


    @Test
    public void testCheckedRunnablesWithSameExceptionShouldBeRunConcurrentlyAndRethrowCheckedException()
    {
        CheckedRunnable<CheckedException> runnable1 = () -> { throw new CheckedException();};
        CheckedRunnable<CheckedException> runnable2 = () -> { throw new CheckedException();};

        assertThrows(CheckedException.class, () -> CheckedRunnable.runConcurrently(runnable1, runnable2));

    }

    @Test
    public void testCheckedRunnablesThrowingDifferentExceptionsShouldRethrowCommonSupertypeExceptionWhenRunConcurrently()
    {
        CheckedRunnable<CheckedException> runnable1 = () -> { throw new CheckedException();};
        CheckedRunnable<IOException> runnable2 = () -> { throw new IOException("for test");};

        try
        {
            CheckedRunnable.runConcurrently(runnable1, runnable2);
        }
        catch (Exception e)
        {
            assertTrue(e instanceof CheckedException);
            assertTrue(e.getSuppressed()[0] instanceof IOException);
        }
    }

    private class MockRunnableRunner {
        public <E extends Exception> void runChecked(CheckedRunnable<E> runnable) throws E {
            runnable.run();
        }

        public void runUnchecked(Runnable runnable){
            runnable.run();
        }
    }

    private class CheckedException extends Exception {

    }

    private class CheckedSubTypeException extends CheckedException {

    }

    private class CheckedOtherSubTypeException extends CheckedException {

    }

}