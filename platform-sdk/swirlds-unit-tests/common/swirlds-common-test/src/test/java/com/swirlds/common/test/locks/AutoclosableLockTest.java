/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.test.locks;

import static com.swirlds.common.test.AssertionUtils.assertEventuallyFalse;
import static com.swirlds.common.test.AssertionUtils.assertEventuallyTrue;
import static com.swirlds.common.threading.manager.ThreadManagerFactory.getStaticThreadManager;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.threading.locks.AutoClosableLock;
import com.swirlds.common.threading.locks.Locks;
import com.swirlds.common.threading.locks.internal.AcquiredOnTry;
import com.swirlds.common.threading.locks.internal.AutoNoOpLock;
import com.swirlds.common.threading.locks.internal.ResourceLock;
import com.swirlds.common.threading.locks.locked.Locked;
import com.swirlds.common.threading.locks.locked.LockedResource;
import com.swirlds.common.threading.locks.locked.MaybeLocked;
import com.swirlds.common.threading.locks.locked.MaybeLockedResource;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.StampedLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AutoclosableLockTest {
    @Test
    void resourceLockTest() throws InterruptedException {
        StampedLock sl = new StampedLock();
        final Lock backingLock = sl.asWriteLock();
        assertFalse(sl.isWriteLocked(), "a new lock should not be locked");
        int counter = 0;

        final ResourceLock<Integer> lock = new ResourceLock<>(backingLock, counter);
        try (final MaybeLockedResource<Integer> maybeLocked = lock.tryLock()) {
            assertTrue(sl.isWriteLocked(), "tryLock should have locked it");
            assertTrue(maybeLocked.isLockAcquired(), "lock should have been acquired");
            assertEquals(counter, maybeLocked.getResource(), "resource should always equal counter");
            maybeLocked.setResource(++counter);
        }
        assertFalse(sl.isWriteLocked(), "end of try block should have unlocked it");

        try (final MaybeLockedResource<Integer> maybeLocked = lock.tryLock(1, TimeUnit.SECONDS)) {
            assertTrue(sl.isWriteLocked(), "tryLock should have locked it");
            assertTrue(maybeLocked.isLockAcquired(), "lock should have been acquired");
            assertEquals(counter, maybeLocked.getResource(), "resource should always equal counter");
            maybeLocked.setResource(++counter);
        }

        try (final LockedResource<Integer> locked = lock.lock()) {
            assertTrue(sl.isWriteLocked(), "lock should have locked it");
            assertEquals(counter, locked.getResource(), "resource should always equal counter");
            locked.setResource(++counter);
        }

        try (final LockedResource<Integer> locked = lock.lockInterruptibly()) {
            assertTrue(sl.isWriteLocked(), "lockInterruptibly should have locked it");
            assertEquals(counter, locked.getResource(), "resource should always equal counter");
            locked.setResource(++counter);
        }

        backingLock.lock();
        assertTrue(sl.isWriteLocked(), "lock() should have locked it");
        try (final MaybeLockedResource<Integer> maybeLocked = lock.tryLock()) {
            assertFalse(maybeLocked.isLockAcquired(), "lock should not have been acquired");
            assertTrue(sl.isWriteLocked(), "it should still be locked");
            assertThrows(
                    Exception.class,
                    maybeLocked::getResource,
                    "we should throw when trying to access a resource that has not been acquired");
            assertThrows(
                    Exception.class,
                    () -> maybeLocked.setResource(Integer.MIN_VALUE),
                    "we should throw when trying to access a resource that has not been acquired");
        }

        try (final MaybeLockedResource<Integer> maybeLocked = lock.tryLock(1, TimeUnit.NANOSECONDS)) {
            assertFalse(maybeLocked.isLockAcquired(), "lock should not have been acquired");
            assertThrows(
                    Exception.class,
                    maybeLocked::getResource,
                    "we should throw when trying to access a resource that has not been acquired");
        }

        assertTrue(sl.isWriteLocked(), "end of try block should not have unlocked it");
        backingLock.unlock();
        assertFalse(sl.isWriteLocked(), "unlock() should have unlocked it");
    }

    @Test
    void acquiredOnTryTest() {
        final AtomicBoolean closed = new AtomicBoolean(false);
        final AtomicReference<MaybeLocked> acquiredOnTry =
                new AtomicReference<>(new AcquiredOnTry(() -> closed.set(true)));
        try (final MaybeLocked maybeLocked = acquiredOnTry.get()) {
            assertTrue(maybeLocked.isLockAcquired(), "should always be true");
        }
        assertTrue(closed.get(), "try with resources should have closed it");
    }

    @Test
    void notAcquiredOnTryTest() {
        assertFalse(MaybeLocked.NOT_ACQUIRED.isLockAcquired(), "should always be false");
        assertDoesNotThrow(MaybeLocked.NOT_ACQUIRED::close, "close should do nothing");
    }

    @Test
    @DisplayName("AutoLock Test")
    void autoLockTest() throws InterruptedException {

        final AutoClosableLock lock = Locks.createAutoLock();

        final CountDownLatch threadBlocker0 = new CountDownLatch(1);
        final AtomicBoolean threadGotLock0 = new AtomicBoolean(false);

        final CountDownLatch threadBlocker1 = new CountDownLatch(1);
        final AtomicBoolean threadGotLock1 = new AtomicBoolean(false);

        final Thread thread0 = getStaticThreadManager()
                .newThreadConfiguration()
                .setThreadName("thread0")
                .setInterruptableRunnable(() -> {
                    try (final Locked locked0 = lock.lock()) {
                        // Lock is reentrant, second lock on same thread should not block
                        try (final Locked locked1 = lock.lock()) {
                            threadGotLock0.set(true);
                            threadBlocker0.await();
                        }
                    }
                })
                .build(true);

        assertEventuallyTrue(threadGotLock0::get, Duration.ofSeconds(1), "thread should have acquired lock by now");

        final Thread thread1 = getStaticThreadManager()
                .newThreadConfiguration()
                .setThreadName("thread1")
                .setInterruptableRunnable(() -> {
                    while (true) {
                        try (final MaybeLocked maybeLocked = lock.tryLock(1, MILLISECONDS)) {
                            if (maybeLocked.isLockAcquired()) {
                                threadGotLock1.set(true);
                                threadBlocker1.await();
                                return;
                            }
                        }
                    }
                })
                .build(true);

        // Wait a little while to make sure that the other thread isn't able to get the lock
        MILLISECONDS.sleep(5);

        assertFalse(threadGotLock1.get(), "only one thread should have the lock");

        try (final MaybeLocked maybeLocked = lock.tryLock()) {
            assertFalse(maybeLocked.isLockAcquired(), "we should not be able to acquire the lock on this thread");
        }

        threadBlocker0.countDown();

        assertEventuallyFalse(thread0::isAlive, Duration.ofSeconds(1), "thread should have died by now");

        assertEventuallyTrue(threadGotLock1::get, Duration.ofSeconds(1), "thread should have acquired lock by now");

        threadBlocker1.countDown();

        assertEventuallyFalse(thread1::isAlive, Duration.ofSeconds(1), "thread should have died by now");
    }

    @Test
    @DisplayName("AutoNoOpLock Test")
    void autoNoOpLockTest() {

        final AutoClosableLock lock = AutoNoOpLock.getInstance();

        final CountDownLatch threadBlocker0 = new CountDownLatch(1);
        final AtomicBoolean threadGotLock0 = new AtomicBoolean(false);

        final CountDownLatch threadBlocker1 = new CountDownLatch(1);
        final AtomicBoolean threadGotLock1 = new AtomicBoolean(false);

        final Thread thread0 = getStaticThreadManager()
                .newThreadConfiguration()
                .setInterruptableRunnable(() -> {
                    try (final Locked locked0 = lock.lock()) {
                        try (final Locked locked1 = lock.lock()) {
                            threadGotLock0.set(true);
                            threadBlocker0.await();
                        }
                    }
                })
                .build(true);

        final Thread thread1 = getStaticThreadManager()
                .newThreadConfiguration()
                .setInterruptableRunnable(() -> {
                    while (true) {
                        try (final MaybeLocked maybeLocked = lock.tryLock(1, MILLISECONDS)) {
                            if (maybeLocked.isLockAcquired()) {
                                threadGotLock1.set(true);
                                threadBlocker1.await();
                                return;
                            }
                        }
                    }
                })
                .build(true);

        assertEventuallyTrue(
                () -> threadGotLock0.get() && threadGotLock1.get(),
                Duration.ofSeconds(1),
                "both thread should have acquired lock by now");

        try (final MaybeLocked maybeLocked = lock.tryLock()) {
            assertTrue(maybeLocked.isLockAcquired(), "lock should always be available");
        }

        threadBlocker0.countDown();
        threadBlocker1.countDown();

        assertEventuallyTrue(
                () -> !thread0.isAlive() && !thread1.isAlive(),
                Duration.ofSeconds(1),
                "both thread should have died by now");
    }
}
