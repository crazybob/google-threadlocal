/*
 * Copyright (C) 2008 Google Inc.
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

package java.lang;

import java.lang.ref.WeakReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.List;
import java.util.ArrayList;

/**
 * A variable for which each thread has its own value. Supports {@code null}
 * values.
 * 
 * @see java.lang.Thread
 * @author Bob Lee
 */
public class ThreadLocal<T> {

    /** Hash counter. */
    private static AtomicInteger hashCounter = new AtomicInteger(0);

    /**
     * Internal hash. We deliberately don't bother with #hashCode().
     * Hashes must be even. This ensures that the result of
     * (hash & (table.length - 1)) points to a key and not a value.
     */
    private final int hash;

    /** Weak reference to this thread local instance. */
    private final ThreadLocalReference reference;
    /**
     * Creates a new thread local variable.
     */
    public ThreadLocal() {
        /*
         * We increment by Doug Lea's Magic Number(TM) (*2 since keys are in
         * every other bucket) to help prevent clustering.
         */
        this.hash = hashCounter.getAndAdd(0x61c88647 << 1);
        this.reference = new ThreadLocalReference<T>(this, this.hash);
    }

    /**
     * Cleans up after garbage collected thread locals. A more efficient
     * alternative implementation would clean up directly during garbage
     * collection instead of using a separate thread.
     */
    private static class Cleaner implements Runnable {

        private static final ThreadLocalReferenceQueue queue
                = new ThreadLocalReferenceQueue();

        static {
            Thread cleanerThread = new Thread(new Cleaner());
            cleanerThread.setDaemon(true);
            cleanerThread.start();
        }

        public void run() {
            List<ThreadLocalReference> references
                    = new ArrayList<ThreadLocalReference>();
            while (true) {
                references.clear();

                try {
                    references.add(queue.remove());
                } catch (InterruptedException e) {
                    continue;
                }

                ThreadLocalReference next;
                while ((next = queue.poll()) != null) {
                    references.add(next);
                }

                cleanUp(references);
            }
        }

        private static Thread[] threads = new Thread[Thread.activeCount() * 2];

        private static void cleanUp(List<ThreadLocalReference> references) {
            // Expand the array until it's big enough to hold every thread.
            while (Thread.enumerate(threads) == threads.length) {
                threads = new Thread[threads.length * 2];
            }

            for (int i = 0; i < threads.length; i++) {
                Thread thread = threads[i];
                threads[i] = null;

                // TODO: accessing non-volatile field from two threads!!!!
                ThreadLocalMap threadLocals = thread.threadLocals;
                if (threadLocals != null) {
                    Table table = threadLocals.table;
                    removeAll(table, references);
                }
            }
        }

        private static void removeAll(Table table,
                List<ThreadLocalReference> references) {
            /*
             * We know this list supports random access. Avoid iterator
             * allocation.
             */
            for (int i = references.size() - 1; i >= 0; i--) {
                ThreadLocalMap.remove(table, references.get(i));
            }
        }
    }


    /**
     * Queue of reference to thread locals which have been reclaimed by
     * the garbage collector.
     */
    private static class ThreadLocalReferenceQueue {

        private final ReferenceQueue<ThreadLocal<?>> delegate
                = new ReferenceQueue<ThreadLocal<?>>();

        /** @see java.lang.ref.ReferenceQueue#remove() */
        private ThreadLocalReference<?> remove() throws InterruptedException {
            return (ThreadLocalReference<?>) delegate.remove();
        }

        /** @see java.lang.ref.ReferenceQueue#poll() */
        private ThreadLocalReference<?> poll() {
            return (ThreadLocalReference<?>) delegate.poll();
        }
    }

    /**
     * A weak reference to a thread local. Contains the same hash as the
     * thread local so we can clean up entries after the ThreadLocal is
     * reclaimed by the garbage collector.
     *
     * <p>We really want to use PhantomReference here. Right now, if a
     * finalize() method resurects a ThreadLocal, the reference will still be
     * dead and we will not clean up after the entries any longer. To implement
     * this, we need to be able to access the referent for PhantomReference so
     * that we can make InheritableThreadLocal work.
     */
    private static class ThreadLocalReference<T>
            extends WeakReference<ThreadLocal<T>> {

        private final int hash;

        private ThreadLocalReference(ThreadLocal<T> referent, int hash) {
            /*
             * Accessing referenceQueue here triggers Cleaner's static
             * initializer and starts the cleanup thread if it hasn't
             * started already.
             */
            super(referent, Cleaner.queue.delegate);
            this.hash = hash;
        }
    }

    /**
     * Returns the value of this variable for the current thread. If an entry
     * doesn't yet exist for this variable on this thread, this method will
     * create an entry, populating the value with the result of
     * {@link #initialValue()}.
     */
    @SuppressWarnings("unchecked")
    public T get() {
        // Optimized for the fast path.
        Thread currentThread = Thread.currentThread();
        ThreadLocalMap values = values(currentThread);
        if (values != null) {
            Table table = values.table;
            int index = hash & table.mask;
            if (this.reference == table.get(index)) {
                return (T) table.get(index + 1);
            }
        } else {
            values = initializeValues(currentThread);
        }

        return (T) values.getAfterMiss(this);
    }

    /**
     * Provides the initial value of this variable for the current thread.
     * The default implementation returns {@code null}.
     */
    protected T initialValue() {
        return null;
    }

    /**
     * Sets the value of this variable for the current thread. If set to
     * null, the value will be set to null and the underlying entry will still
     * be present.
     */
    public void set(T value) {
        Thread currentThread = Thread.currentThread();
        ThreadLocalMap values = values(currentThread);
        if (values == null) {
            values = initializeValues(currentThread);
        }
        values.put(this, value);
    }

    /**
     * Removes the entry for this variable in the current thread. If this call
     * is followed by a {@link #get()} before a {@link #set(Object)},
     * {@code #get()} will call {@link #initialValue()} and create a new
     * entry with the resulting value.
     */
    public void remove() {
        Thread currentThread = Thread.currentThread();
        ThreadLocalMap values = values(currentThread);
        if (values != null) {
            values.remove(this);
        }
    }

    /**
     * Creates Values instance for this thread and variable type.
     */
    ThreadLocalMap initializeValues(Thread current) {
        return current.threadLocals = new ThreadLocalMap();
    }

    /**
     * Gets Values instance for this thread and variable type.
     */
    ThreadLocalMap values(Thread current) {
        return current.threadLocals;
    }

    /** Placeholder for deleted keys of deleted entries. */
    private static final Object TOMBSTONE = new Object();

    /**
     * Entry table. Array of alternating keys and values.
     *
     * TODO: Combine Table and ThreadLocalMap into one class.
     */
    private static class Table extends AtomicReferenceArray<Object> {

        /** Used to turn hashes into indices. */
        private final int mask;

        /** Total number of live and dead entries. */
        private int load;

        /** Maximum number of entries before we must build a new table. */
        private final int maximumLoad;

        /**
         * Number of tombstones in this table. This value is not exact.
         * We decide whether or not to rehash based upon the load (which
         * is exact). This rough tombstone count just enables us to estimate
         * the number of live entries so we can decide whether or not to expand
         * the table.
         */
        private final AtomicInteger tombstones;

        /**
         * Creates a new Table with the given capacity.
         */
        private static Table withCapacity(int capacity) {
            int length = capacity << 1;
            return new Table(length);
        }

        /** Constructs an empty table with the given length. */
        private Table(int length) {
            super(length);
            this.mask = length - 1;
            this.load = 0;
            this.maximumLoad = length / 3; // 2/3
            this.tombstones = new AtomicInteger(0);
        }

        /**
         * Gets the next index. If we're at the end of the table, we wrap
         * back around to 0.
         */
        private int next(int index) {
            return (index + 2) & mask;
        }

        /**
         * Returns maximum number of entries this table can hold.
         */
        private int capacity() {
            return length() >> 1;
        }
    }

    /**
     * Per-thread map of ThreadLocal instances to values.
     */
    static class ThreadLocalMap {

        /** Size, must always be a power of 2. */
        private static final int INITIAL_SIZE = 16;

        /**
         * Pointer to most current table.
         */
        private volatile Table table;

        /**
         * Constructs a new, empty instance.
         */
        ThreadLocalMap() {
            this.table = Table.withCapacity(INITIAL_SIZE);
        }

        /**
         * Constructs a new map with the given table.
         */
        private ThreadLocalMap(Table table) {
            this.table = table;
        }

        /**
         * Rehashes the table, expanding or contracting it as necessary.
         * Gets rid of tombstones. Returns true if a rehash occurred.
         * We must rehash every time we fill a null slot; we depend on the
         * presence of null slots to end searches (otherwise, we'll infinitely
         * loop).
         *
         * @return latest Table
         */
        private Table rehash() {
            Table oldTable = this.table;

            if (oldTable.load < oldTable.maximumLoad) {
                return oldTable;
            }

            int oldCapacity = oldTable.capacity();

            // Default to the same capacity. This will create a table of the
            // same size and move over the live entries, analogous to a
            // garbage collection. This should only happen if you churn a
            // bunch of thread local garbage (removing and reinserting
            // the same thread locals over and over will overwrite tombstones
            // and not fill up the table).
            int newCapacity = oldCapacity;

            int liveEntries = oldTable.load - oldTable.tombstones.get();
            if (liveEntries > (oldCapacity >> 1)) {
                // More than 1/2 filled w/ live entries.
                // Double size.
                newCapacity = oldCapacity << 1;
            }

            // Allocate new table.
            Table newTable = new Table(newCapacity);
            this.table = newTable;

            // Move over entries.
            for (int i = oldTable.length() - 2; i >= 0; i -= 2) {
                Object k = oldTable.get(i);
                if (k == null || k == TOMBSTONE) {
                    // Skip this entry.
                    continue;
                }

                // The table can only contain null, tombstones and references.
                @SuppressWarnings("unchecked")
                Reference<ThreadLocal<?>> reference
                        = (Reference<ThreadLocal<?>>) k;
                ThreadLocal<?> key = reference.get();
                if (key != null) {
                    // Entry is still live. Move it over.
                    put(newTable, key, oldTable.get(i + 1));
                }
            }

            return newTable;
        }

        /**
         * Sets entry for given ThreadLocal to given value, creating an
         * entry if necessary.
         */
        private void put(ThreadLocal<?> key, Object value) {
            put(rehash(), key, value);
        }

        /**
         * Sets entry for ThreadLocal to value in the given table, creating an
         * entry if necessary.
         */
        private static void put(Table table, ThreadLocal<?> key, Object value) {
            // Keep track of first tombstone. That's where we want to go back
            // and add an entry if necessary.
            int firstTombstone = -1;

            for (int index = key.hash & table.mask;;
                    index = table.next(index)) {
                Object k = table.get(index);

                if (k == key.reference) {
                    // Replace existing entry.
                    table.set(index + 1, value);
                    return;
                }

                if (k == null) {
                    if (firstTombstone == -1) {
                        // Fill in null slot.
                        table.set(index, key.reference);
                        table.set(index + 1, value);
                        table.load++;
                        return;
                    }

                    // Go back and replace first tombstone.
                    table.set(firstTombstone, key.reference);
                    table.set(firstTombstone + 1, value);
                    table.tombstones.decrementAndGet();
                    return;
                }

                // Remember first tombstone.
                if (firstTombstone == -1 && k == TOMBSTONE) {
                    firstTombstone = index;
                }
            }
        }

        /**
         * Gets value for given ThreadLocal after not finding it in the first
         * slot.
         */
        Object getAfterMiss(ThreadLocal<?> key) {
            Table table = this.table;
            int index = key.hash & table.mask;

            // If the first slot is empty, the search is over.
            if (table.get(index) == null) {
                Object value = key.initialValue();

                // If the table is still the same and the slot is still empty...
                if (this.table == table && table.get(index) == null) {
                    table.set(index, key.reference);
                    table.set(index + 1, value);
                    table.load++;

                    // The table could now exceed its maximum load.
                    rehash();
                    return value;
                }

                // The table changed during initialValue().
                put(key, value);
                return value;
            }

            // Keep track of first tombstone. That's where we want to go back
            // and add an entry if necessary.
            int firstTombstone = -1;

            // Continue search.
            for (index = table.next(index);; index = table.next(index)) {
                Object reference = table.get(index);
                if (reference == key.reference) {
                    return table.get(index + 1);
                }

                // If no entry was found...
                if (reference == null) {
                    Object value = key.initialValue();

                    // If the table is still the same...
                    if (this.table == table) {
                        // If we passed a tombstone and that slot still
                        // contains a tombstone...
                        if (firstTombstone > -1
                                && table.get(firstTombstone) == TOMBSTONE) {
                            table.set(firstTombstone, key.reference);
                            table.set(firstTombstone + 1, value);
                            table.tombstones.decrementAndGet();
                            table.load++;

                            // No need to clean up here. We aren't filling
                            // in a null slot.
                            return value;
                        }

                        // If this slot is still empty...
                        if (table.get(index) == null) {
                            table.set(index, key.reference);
                            table.set(index + 1, value);
                            table.load++;

                            // The table could now exceed its maximum load.
                            rehash();
                            return value;
                        }
                    }

                    // The table changed during initialValue().
                    put(key, value);
                    return value;
                }

                if (firstTombstone == -1 && reference == TOMBSTONE) {
                    // Keep track of this tombstone so we can overwrite it.
                    firstTombstone = index;
                }
            }
        }

        /**
         * Removes entry for the given ThreadLocal.
         */
        void remove(ThreadLocal<?> key) {
            remove(this.table, key.reference);
        }

        /**
         * Removes entry for the given ThreadLocal from the given Table.
         */
        private static void remove(Table table, ThreadLocalReference<?> k) {
            for (int index = k.hash & table.mask;; index = table.next(index)) {
                Object reference = table.get(index);

                if (reference == k) {
                    /*
                     * Success! We deliberately clear out the value before the
                     * key. If we replaced the key with a tombstone before
                     * nulling out the value from a background thread,
                     * the main thread could jump in and reuse the slot before
                     * we null out the value. If this happened, the background
                     * thread could accidentally null out the new value.
                     */
                    table.set(index + 1, null);
                    table.set(index, TOMBSTONE);
                    table.tombstones.incrementAndGet();
                    return;
                }

                if (reference == null) {
                    // No entry found.
                    return;
                }
            }
        }
    }

    /**
     * Inherits thread locals from parent thread.
     */
    static ThreadLocalMap createInheritedMap(ThreadLocalMap inherited) {
        Table parentTable = inherited.table;
        Table childTable = Table.withCapacity(parentTable.capacity());
        inheritValues(parentTable, childTable);
        return new ThreadLocalMap(childTable);
    }

    /**
     * Inherits values from a parent thread. Called in parent thread.
     */
    @SuppressWarnings({"unchecked"})
    private static void inheritValues(Table parentTable, Table childTable) {
        // Transfer values from parent to child thread.
        for (int i = parentTable.length() - 2; i >= 0; i -= 2) {
            Object k = parentTable.get(i);

            if (k == null) {
                // Skip this entry.
                continue;
            }

            // The table can only contain null, tombstones and references.
            Reference<InheritableThreadLocal<?>> reference
                    = (Reference<InheritableThreadLocal<?>>) k;
            // Raw type enables us to pass in an Object below.
            InheritableThreadLocal key = reference.get();
            if (key != null) {
                // Replace value with filtered value.
                // We should just let exceptions bubble out and tank
                // the thread creation
                childTable.set(i + 1,
                        key.childValue(parentTable.get(i + 1)));
            } else {
                // The key was reclaimed.
                childTable.set(i, TOMBSTONE);
                parentTable.set(i, TOMBSTONE);
                parentTable.set(i + 1, null);

                childTable.tombstones.incrementAndGet();
                parentTable.tombstones.incrementAndGet();
            }
        }
    }
}
