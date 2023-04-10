/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.common.test.utility;

import static com.swirlds.common.test.AssertionUtils.assertEventuallyEquals;
import static com.swirlds.common.test.AssertionUtils.assertEventuallyFalse;
import static com.swirlds.common.threading.interrupt.Uninterruptable.abortAndLogIfInterrupted;
import static com.swirlds.common.threading.interrupt.Uninterruptable.abortAndThrowIfInterrupted;
import static com.swirlds.common.threading.interrupt.Uninterruptable.abortIfInterrupted;
import static com.swirlds.common.threading.interrupt.Uninterruptable.retryIfInterrupted;
import static com.swirlds.common.threading.interrupt.Uninterruptable.tryToSleep;
import static com.swirlds.common.threading.manager.ThreadManagerFactory.getStaticThreadManager;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.threading.interrupt.InterruptableSupplier;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Uninterruptable Test")
class UninterruptableTest {

    @Test
    @DisplayName("retryIfInterrupted() Test")
    void retryIfInterruptedTest() throws InterruptedException {

        final BlockingQueue<Integer> queue = new LinkedBlockingDeque<>(1);

        final AtomicBoolean exceptionEncountered = new AtomicBoolean(false);

        final Thread thread = getStaticThreadManager()
                .newThreadConfiguration()
                .setRunnable(() -> {
                    retryIfInterrupted(() -> queue.put(0));
                    retryIfInterrupted(() -> queue.put(1));
                })
                .setExceptionHandler((t, throwable) -> exceptionEncountered.set(true))
                .build(true);

        assertEventuallyEquals(1, queue::size, Duration.ofSeconds(1), "element should eventually added to queue");

        // Thread will be blocked on adding next element. Interrupt should not unblock the thread.
        thread.interrupt();

        MILLISECONDS.sleep(20);
        assertTrue(thread.isAlive(), "thread should still be alive");

        assertEquals(0, queue.remove(), "unexpected element in queue");
        assertEventuallyFalse(thread::isAlive, Duration.ofSeconds(1), "thread should have finished");
        assertEquals(1, queue.remove(), "unexpected element in queue");
        assertTrue(queue.isEmpty(), "nothing else should be in the queue");
        assertFalse(exceptionEncountered.get(), "no exceptions should have been thrown");
    }

    @Test
    @DisplayName("retryIfInterrupted() Supplier Test")
    void retryIfInterruptedSupplierTest() throws InterruptedException {

        final BlockingQueue<Integer> queue = new LinkedBlockingDeque<>(1);

        final InterruptableSupplier<Integer> operation = () -> {
            retryIfInterrupted(() -> queue.put(0));
            retryIfInterrupted(() -> queue.put(1));
            return 1234;
        };

        final AtomicBoolean exceptionEncountered = new AtomicBoolean(false);

        final Thread thread = getStaticThreadManager()
                .newThreadConfiguration()
                .setRunnable(() -> assertEquals(1234, retryIfInterrupted(operation), "unexpected value"))
                .setExceptionHandler((t, throwable) -> exceptionEncountered.set(true))
                .build(true);

        assertEventuallyEquals(1, queue::size, Duration.ofSeconds(1), "element should eventually added to queue");

        // Thread will be blocked on adding next element. Interrupt should not unblock the thread.
        thread.interrupt();

        MILLISECONDS.sleep(20);
        assertTrue(thread.isAlive(), "thread should still be alive");

        assertEquals(0, queue.remove(), "unexpected element in queue");
        assertEventuallyFalse(thread::isAlive, Duration.ofSeconds(1), "thread should have finished");
        assertEquals(1, queue.remove(), "unexpected element in queue");
        assertTrue(queue.isEmpty(), "nothing else should be in the queue");
        assertFalse(exceptionEncountered.get(), "no exceptions should have been thrown");
    }

    @Test
    @DisplayName("abortIfInterrupted() Test")
    void abortIfInterruptedTest() {
        final AtomicBoolean exceptionEncountered = new AtomicBoolean(false);

        final BlockingQueue<Integer> queue = new LinkedBlockingDeque<>(1);

        final Thread thread = getStaticThreadManager()
                .newThreadConfiguration()
                .setRunnable(() -> {
                    abortIfInterrupted(() -> queue.put(0));
                    abortIfInterrupted(() -> queue.put(1));
                })
                .setExceptionHandler((t, throwable) -> exceptionEncountered.set(true))
                .build(true);

        assertEventuallyEquals(1, queue::size, Duration.ofSeconds(1), "element should eventually added to queue");

        // Thread will be blocked on adding next element. Interrupt should unblock the thread.
        thread.interrupt();

        assertEventuallyFalse(thread::isAlive, Duration.ofSeconds(1), "thread should be dead");

        assertEquals(0, queue.remove(), "unexpected element in queue");
        assertTrue(queue.isEmpty(), "nothing else should be in the queue");
        assertFalse(exceptionEncountered.get(), "no exceptions should have been thrown");
    }

    @Test
    @DisplayName("abortAndLogIfInterrupted() Test")
    void abortAndLogIfInterruptedTest() {
        final AtomicBoolean exceptionEncountered = new AtomicBoolean(false);

        final BlockingQueue<Integer> queue = new LinkedBlockingDeque<>(1);

        final Thread thread = getStaticThreadManager()
                .newThreadConfiguration()
                .setRunnable(() -> {
                    abortAndLogIfInterrupted(() -> queue.put(0), "unexpected error");
                    abortAndLogIfInterrupted(() -> queue.put(1), "expected error");
                })
                .setExceptionHandler((t, throwable) -> exceptionEncountered.set(true))
                .build(true);

        assertEventuallyEquals(1, queue::size, Duration.ofSeconds(1), "element should eventually added to queue");

        // Thread will be blocked on adding next element. Interrupt should unblock the thread.
        thread.interrupt();

        assertEventuallyFalse(thread::isAlive, Duration.ofSeconds(1), "thread should be dead");

        assertEquals(0, queue.remove(), "unexpected element in queue");
        assertTrue(queue.isEmpty(), "nothing else should be in the queue");
        assertFalse(exceptionEncountered.get(), "no exceptions should have been thrown");
    }

    @Test
    @DisplayName("abortAndThrowIfInterrupted() Test")
    void abortAndThrowIfInterruptedTest() {
        final AtomicBoolean exceptionEncountered = new AtomicBoolean(false);

        final BlockingQueue<Integer> queue = new LinkedBlockingDeque<>(1);

        final Thread thread = getStaticThreadManager()
                .newThreadConfiguration()
                .setRunnable(() -> {
                    abortAndThrowIfInterrupted(() -> queue.put(0), "unexpected error");
                    abortAndThrowIfInterrupted(() -> queue.put(1), "expected error");
                })
                .setExceptionHandler((t, throwable) -> exceptionEncountered.set(true))
                .build(true);

        assertEventuallyEquals(1, queue::size, Duration.ofSeconds(1), "element should eventually added to queue");

        // Thread will be blocked on adding next element. Interrupt should unblock the thread.
        thread.interrupt();

        assertEventuallyFalse(thread::isAlive, Duration.ofSeconds(1), "thread should be dead");

        assertEquals(0, queue.remove(), "unexpected element in queue");
        assertTrue(queue.isEmpty(), "nothing else should be in the queue");
        assertTrue(exceptionEncountered.get(), "exception should have been thrown");
    }

    @Test
    @DisplayName("tryToSleep() Test")
    void tryToSleepTest() throws InterruptedException {
        final AtomicBoolean exceptionEncountered = new AtomicBoolean(false);

        final Thread thread = getStaticThreadManager()
                .newThreadConfiguration()
                .setRunnable(() -> {
                    tryToSleep(Duration.ofSeconds(1000));
                })
                .setExceptionHandler((t, throwable) -> exceptionEncountered.set(true))
                .build(true);

        MILLISECONDS.sleep(20);
        assertTrue(thread.isAlive(), "thread should be sleeping");

        thread.interrupt();

        assertEventuallyFalse(thread::isAlive, Duration.ofSeconds(1), "thread should be dead by now");
        assertFalse(exceptionEncountered.get(), "no exceptions should have been thrown");
    }

    @Test
    @DisplayName("abortAndLogIfInterrupted() Consumer Test")
    void abortAndLogIfInterruptedConsumerTest() {
        final AtomicBoolean exceptionEncountered = new AtomicBoolean(false);

        final BlockingQueue<Integer> queue = new LinkedBlockingDeque<>(1);

        final Thread thread = getStaticThreadManager()
                .newThreadConfiguration()
                .setRunnable(() -> {
                    abortAndLogIfInterrupted(queue::put, 0, "unexpected error");
                    abortAndLogIfInterrupted(queue::put, 1, "expected error");
                })
                .setExceptionHandler((t, throwable) -> exceptionEncountered.set(true))
                .build(true);

        assertEventuallyEquals(1, queue::size, Duration.ofSeconds(1), "element should eventually added to queue");

        // Thread will be blocked on adding next element. Interrupt should unblock the thread.
        thread.interrupt();

        assertEventuallyFalse(thread::isAlive, Duration.ofSeconds(1), "thread should be dead");

        assertEquals(0, queue.remove(), "unexpected element in queue");
        assertTrue(queue.isEmpty(), "nothing else should be in the queue");
        assertFalse(exceptionEncountered.get(), "no exceptions should have been thrown");
    }
}
