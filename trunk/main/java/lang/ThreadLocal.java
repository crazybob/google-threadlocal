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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.PhantomReference;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.ArrayList;

import sun.misc.Unsafe;

/**
 * A variable for which each thread has its own value. Supports {@code null}
 * values.
 * 
 * @see java.lang.Thread
 * @author Bob Lee
 */
public class ThreadLocal<T> {

    /**
     * Factory for normal (non-inheritable) thread locals.
     */
    private static final ThreadLocalMap.Factory MAP_FACTORY
            = new ThreadLocalMap.Factory() {
        ThreadLocalMap newMap(Thread current, int length) {
            return current.threadLocals = new ThreadLocalMap(this, length);
        }

        ThreadLocalMap getMap(Thread current) {
            return current.threadLocals;
        }
    };

    /** Placeholder for deleted keys of deleted entries. */
    static final Object TOMBSTONE = new Object();

    /** Canonical phantom reference to this thread local instance. */
    final ThreadLocalReference<T> reference;

    /** Factory used to access the ThreadLocalMap. */
    private final ThreadLocalMap.Factory mapFactory;

    /**
     * Creates a new thread local variable using the given factory.
     */
    ThreadLocal(ThreadLocalMap.Factory mapFactory) {
        this.mapFactory = mapFactory;
        this.reference = newReference();
    }

    /**
     * Creates a new thread local variable.
     */
    public ThreadLocal() {
        this(MAP_FACTORY);
    }

    /**
     * Creates a ThreadLocalReference for this ThreadLocal. Overridden by
     * {@link InheritableThreadLocal}.
     */
    ThreadLocalReference<T> newReference() {
        return new ThreadLocalReference<T>(this);
    }

    /**
     * A canonical reference to a ThreadLocal instance. We use this reference
     * as the key in the ThreadLocalMap instead of the ThreadLocal directly
     * so as not to prevent garbage collection of the ThreadLocal.
     *
     * <p>We use a PhantomReference instead of a WeakReference because we want
     * to ensure that the ThreadLocal can't be resurrected after we clean up
     * its entries.
     */
    static class ThreadLocalReference<T>
            extends PhantomReference<ThreadLocal<T>> {

        /** Hash counter. */
        private static AtomicInteger hashCounter = new AtomicInteger(0);

        /**
         * Internal hash. Hashes must be even. This ensures that the result of
         * (hash & (table.length - 1)) points to a key and not a value.
         */
        final int hash;

        /**
         * Used to access the referent while it's still alive.
         */
        private final WeakReference<ThreadLocal<T>> weakReference;

        ThreadLocalReference(ThreadLocal<T> referent) {
            /*
             * Accessing Cleaner.queue here triggers Cleaner's static
             * initializer and starts the cleanup thread.
             */
            super(referent, Cleaner.queue.delegate);

            /*
             * We increment by Doug Lea's Magic Number(TM) (*2 since keys are in
             * every other bucket) to help prevent clustering.
             */
            this.hash = hashCounter.getAndAdd(0x61c88647 << 1);

            weakReference = new WeakReference<ThreadLocal<T>>(referent);
        }

        /**
         * Gets the referent if it's still strongly or softly reachable.
         */
        @Override
        public ThreadLocal<T> get() {
            return weakReference.get();
        }

        /**
         * Returns true if this references an inheritable thread local.
         */
        boolean isInheritable() {
            return false;
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
        // Optimized for the fast path...
        Thread currentThread = Thread.currentThread();
        ThreadLocalMap map = currentThread.threadLocals;
        if (map != null) {
            ThreadLocalReference<T> reference = this.reference;
            int index = reference.hash & map.mask;
            Object[] table = map.table;
            if (reference == VolatileArray.get(table, index)) {
                return (T) VolatileArray.get(table, index + 1);
            }
        } else {
            map = new ThreadLocalMap(MAP_FACTORY,
                    ThreadLocalMap.INITIAL_LENGTH);
            currentThread.threadLocals = map;
        }

        return (T) map.getAfterMiss(this);
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
        ThreadLocalMap map = mapFactory.getMap(currentThread);
        if (map == null) {
            map = mapFactory.newMap(currentThread);
            map.put(reference, value);
        } else {
            map.maybeRehash().put(reference, value);
        }
    }

    /**
     * Removes the entry for this variable in the current thread. If this call
     * is followed by a {@link #get()} before a {@link #set(Object)},
     * {@code #get()} will call {@link #initialValue()} and create a new
     * entry with the resulting value.
     */
    public void remove() {
        Thread currentThread = Thread.currentThread();
        ThreadLocalMap map = mapFactory.getMap(currentThread);
        if (map != null) {
            /*
             * Ensure that this ThreadLocal doesn't get garbage collected
             * during removal. If the ThreadLocal were to be reclaimed,
             * the background thread could accidentally overwrite a subsequent
             * value stored in the same slot.
             */
            map.pin = this;
            map.remove(reference);
            map.pin = null;
        }
    }

    /**
     * Per-thread map of ThreadLocal instances to values.
     */
    static class ThreadLocalMap {

        /** Capacity = 16 */
        static final int INITIAL_LENGTH = 32;

        /**
         * Looks up and creates ThreadLocalMaps.
         */
        static abstract class Factory {

            /**
             * Creates a new map for the given thread with the default array
             * length.
             */
            ThreadLocalMap newMap(Thread current) {
                return newMap(current, INITIAL_LENGTH);
            }

            /**
             * Creates a new map for the given thread with the given array
             * length.
             */
            abstract ThreadLocalMap newMap(Thread current, int length);

            /**
             * Gets the map for the given thread.
             */
            abstract ThreadLocalMap getMap(Thread current);
        }

        /** Used to turn hashes into indices. */
        final int mask;

        /** Total number of live and dead entries. */
        private int load;

        /** Maximum number of entries before we must rebuild the map. */
        private final int maximumLoad;

        /**
         * Number of tombstones in this map. This value is not exact.
         * We decide whether or not to rehash based upon the load (which
         * is exact). This rough tombstone count just enables us to estimate
         * the number of live entries so we can decide whether or not to expand
         * the entry array.
         */
        private final AtomicInteger tombstones;

        /**
         * Factory to use for creating new ThreadLocalMaps when rehashing.
         */
        private final Factory factory;

        /** Entry table. Alternating keys and values. */
        final Object[] table;

        /**
         * Used to prevent garbage collection of a ThreadLocal instance.
         */
        @SuppressWarnings("UnusedDeclaration")
        private volatile ThreadLocal<?> pin;

        /**
         * Constructs an empty map with the given array length.
         *
         * @param length of the underlying array, must be power of 2,
         *  2X capacity
         */
        ThreadLocalMap(Factory factory, int length) {
            this.table = new Object[length];

            this.factory = factory;
            this.mask = length - 1;
            this.load = 0;
            this.maximumLoad = length / 3; // 2/3 capacity
            this.tombstones = new AtomicInteger(0);
        }

        /**
         * Gets the next index. If we're at the end of the array, we wrap
         * back around to 0.
         */
        private int next(int index) {
            return (index + 2) & mask;
        }

        /**
         * Returns maximum number of entries map can hold.
         */
        private int capacity() {
            return table.length >> 1;
        }
        
        /**
         * Rehashes the map if necessary. Expands the underlying array if
         * necessary. Gets rid of tombstones. We must rehash every time we fill
         * a null slot; we depend on the presence of null slots to end searches
         * (otherwise, we'll infinitely loop).
         *
         * @return latest map
         */
        private ThreadLocalMap maybeRehash() {
            if (load < maximumLoad) {
                return this;
            }

            int oldCapacity = capacity();

            // Default to the same capacity. This will create a table of the
            // same size and move over the live entries, analogous to a
            // garbage collection. This should only happen if you churn a
            // bunch of thread local garbage (removing and reinserting
            // the same thread locals over and over will overwrite tombstones
            // and not fill up the table).
            int newCapacity = oldCapacity;

            int liveEntries = load - tombstones.get();
            if (liveEntries > (oldCapacity >> 1)) {
                // More than 1/2 filled w/ live entries.
                // Double size.
                newCapacity = oldCapacity << 1;
            }

            // Create new map.
            ThreadLocalMap newMap = factory.newMap(Thread.currentThread(),
                    newCapacity << 1);

            // Move over entries.
            for (int i = table.length - 2; i >= 0; i -= 2) {
                Object k = VolatileArray.get(table, i);
                if (k == null || k == TOMBSTONE) {
                    // Skip this entry.
                    continue;
                }

                /*
                 * The table can only contain null, tombstones and references,
                 * so this must be a reference.
                 */
                @SuppressWarnings("unchecked")
                ThreadLocalReference<?> reference
                        = (ThreadLocalReference<?>) k;
                ThreadLocal<?> threadLocal = reference.get();
                if (threadLocal != null) {
                    /*
                     * Entry is still alive. Move it over.
                     * 
                     * Ensure that threadLocal doesn't get garbage collected
                     * during put(). If it were to get reclaimed after the
                     * null check, the cleanup thread could try to remove the
                     * entry before we actually insert it, and we'd end up
                     * leaking the value (until the next
                     * rehash).  
                     */
                    pin = threadLocal;
                    newMap.put(reference, VolatileArray.get(table, i + 1));
                    pin = null;
                }
            }

            return newMap;
        }

        /**
         * Sets entry for ThreadLocal to value in the given table, creating an
         * entry if necessary. Assumes this map has adequate capacity.
         */
        void put(ThreadLocalReference<?> reference, Object value) {
            // Keep track of first tombstone. That's where we want to go back
            // and add an entry if necessary.
            int firstTombstone = -1;

            Object[] table = this.table;
            for (int index = reference.hash & mask;; index = next(index)) {
                Object k = VolatileArray.get(table, index);

                if (k == reference) {
                    // Replace existing entry.
                    VolatileArray.set(table, index + 1, value);
                    return;
                }

                if (k == null) {
                    if (firstTombstone == -1) {
                        // Fill in null slot.
                        VolatileArray.set2(table, index, reference, value);
                        load++;
                        return;
                    }

                    // Go back and replace first tombstone.
                    VolatileArray.set2(table, firstTombstone, reference, value);
                    tombstones.decrementAndGet();
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
            // TODO: Do we need to pin the ThreadLocal here?

            ThreadLocalReference<?> reference = key.reference;
            int index = reference.hash & mask;

            // If the first slot is empty, the search is over.
            Object[] table = this.table;
            if (VolatileArray.get(table, index) == null) {
                Object value = key.initialValue();

                // Get the latest map.
                ThreadLocalMap latest = factory.getMap(Thread.currentThread());

                // If the map is still the same and the slot is still empty...
                if (this == latest && VolatileArray.get(table, index) == null) {
                    VolatileArray.set2(table, index, reference, value);
                    load++;

                    // The table could now exceed its maximum load.
                    maybeRehash();
                    return value;
                }

                // The table changed during initialValue().
                latest.maybeRehash().put(reference, value);
                return value;
            }

            // Keep track of first tombstone. That's where we want to go back
            // and add an entry if necessary.
            int firstTombstone = -1;

            // Continue search.
            for (index = next(index);; index = next(index)) {
                Object k = VolatileArray.get(table, index);
                if (k == reference) {
                    return VolatileArray.get(table, index + 1);
                }

                // If no entry was found...
                if (k == null) {
                    Object value = key.initialValue();

                    // Get the latest map.
                    ThreadLocalMap latest
                            = factory.getMap(Thread.currentThread());

                    // If the map is still the same...
                    if (this == latest) {
                        // If we passed a tombstone and that slot still
                        // contains a tombstone...
                        if (firstTombstone > -1 && VolatileArray.get(
                                table, firstTombstone) == TOMBSTONE) {
                            VolatileArray.set2(table, firstTombstone,
                                    reference, value);
                            tombstones.decrementAndGet();

                            // No need to clean up here. We aren't filling
                            // in a null slot.
                            return value;
                        }

                        // If this slot is still empty...
                        if (VolatileArray.get(table, index) == null) {
                            VolatileArray.set2(table, index, reference, value);
                            load++;

                            // The table could now exceed its maximum load.
                            maybeRehash();
                            return value;
                        }
                    }

                    // The table changed during initialValue().
                    latest.maybeRehash().put(reference, value);
                    return value;
                }

                if (firstTombstone == -1 && k == TOMBSTONE) {
                    // Keep track of this tombstone so we can overwrite it.
                    firstTombstone = index;
                }
            }
        }

        /**
         * Removes entry for the given ThreadLocal.
         */
        void remove(ThreadLocalReference<?> key) {
            for (int index = key.hash & mask;;
                    index = next(index)) {
                Object reference = VolatileArray.get(table, index);

                if (reference == key) {
                    /*
                     * Success! We deliberately clear out the value before the
                     * key. If we replaced the key with a tombstone before
                     * nulling out the value from a background thread,
                     * the main thread could jump in and reuse the slot before
                     * we null out the value. If this happened, the background
                     * thread could accidentally null out the new value.
                     */
                    VolatileArray.set2(table, index, TOMBSTONE, null);
                    tombstones.incrementAndGet();
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
     * Utility method for performing volatile reads and writes to/from an
     * array.
     */
    static class VolatileArray {

        /** Prevents instantiation. */
        private VolatileArray() {}

        private static final Unsafe unsafe = Unsafe.getUnsafe();
        private static final int base = unsafe.arrayBaseOffset(Object[].class);
        private static final int elementSize
                = unsafe.arrayIndexScale(Object[].class);

        /**
         * Performs a volatile read from the given array.
         */
        static Object get(Object[] array, int index) {
            // assert index >= 0 && index < table.length
            return unsafe.getObjectVolatile(array, base + index * elementSize);
        }

        /**
         * Performs a volatile write to an array.
         */
        static void set(Object[] array, int index, Object value) {
            // assert index >= 0 && index < table.length
            unsafe.putObjectVolatile(array, base + index * elementSize, value);
        }

        /**
         * Stores b volatilly at array[index + 1] and then a at array[index].
         */
        static void set2(Object[] array, int index, Object a, Object b) {
            // assert index >= 0 && index < table.length - 1
            int aIndex = base + index * elementSize;
            unsafe.putObjectVolatile(array, aIndex + elementSize, b);
            unsafe.putObjectVolatile(array, aIndex, a);
        }
    }

    /**
     * Inherits thread locals from parent thread.
     */
    static ThreadLocalMap createInheritedMap(ThreadLocalMap parentMap) {
        /*
         * TODO: The background thread can't get to this map yet, but it
         * needs immediate access (or else we may leak entries for thread
         * locals that get reclaimed after we've already transferred them).
         */
        ThreadLocalMap childMap = new ThreadLocalMap(parentMap.factory,
                parentMap.table.length);
        InheritableThreadLocal.inheritValues(parentMap, childMap);
        return childMap;
    }

    /**
     * <b>Note:</b> This code should be rewritten more cleanly and efficiently
     * using VM-specific APIs. Ideally, the garbage collector would just do
     * this clean up directly. Then, we wouldn't have to incur the overhead of
     * an additional thread, the reference queue, etc.
     *
     * <p>Cleans up after garbage collected thread locals. A more efficient
     * alternative implementation would clean up directly during garbage
     * collection instead of using a separate thread.
     */
    private static class Cleaner implements Runnable {

        private static final ThreadLocalReferenceQueue queue
                = new ThreadLocalReferenceQueue();

        static {
            Thread cleanerThread = new Thread(new Cleaner(),
                    "ThreadLocal.Cleaner");
            cleanerThread.setDaemon(true);
            cleanerThread.start();
        }

        @SuppressWarnings("InfiniteLoopStatement")
        public void run() {
            List<ThreadLocalReference<?>> references
                    = new ArrayList<ThreadLocalReference<?>>();
            List<ThreadLocalReference<?>> inheritableReferences
                    = new ArrayList<ThreadLocalReference<?>>();
            while (true) {
                references.clear();
                inheritableReferences.clear();

                try {
                    ThreadLocalReference<?> reference = queue.remove();
                    reference.clear();
                    if (reference.isInheritable()) {
                        inheritableReferences.add(reference);
                    } else {
                        references.add(reference);
                    }
                } catch (InterruptedException e) {
                    continue;
                }

                ThreadLocalReference next;
                while ((next = queue.poll()) != null) {
                    next.clear();
                    if (next.isInheritable()) {
                        inheritableReferences.add(next);
                    } else {
                        references.add(next);
                    }
                }

                cleanUp(references, inheritableReferences);
            }
        }

        /** Reusable thread array. */
        private static Thread[] threads = new Thread[Thread.activeCount() * 2];

        private static void cleanUp(List<ThreadLocalReference<?>> references,
                List<ThreadLocalReference<?>> inheritableReferences) {
            /*
             * TODO: Do we need to worry about inactive threads? We may want
             * to keep track of threads ourselves. We'd keep a weak set and
             * add a thread when we create the first map for it.
             */

            // Expand the array until it's big enough to hold every thread.
            int threadCount;
            while ((threadCount = Thread.enumerate(threads))
                    == threads.length) {
                threads = new Thread[threads.length * 2];
            }

            for (int i = 0; i < threadCount; i++) {
                Thread thread = threads[i];
                threads[i] = null;

                ThreadLocalMap map = thread.threadLocals;
                if (map != null) {
                    removeAll(map, references);
                }

                ThreadLocalMap inheritableMap = thread.inheritableThreadLocals;
                if (inheritableMap != null) {
                    removeAll(inheritableMap, inheritableReferences);
                }
            }
        }

        private static void removeAll(ThreadLocalMap map,
                List<ThreadLocalReference<?>> references) {
            /*
             * We know this list supports random access. Avoid iterator
             * allocation.
             */
            for (int i = references.size() - 1; i >= 0; i--) {
                ThreadLocalReference<?> key = references.get(i);
                map.remove(key);
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
}
