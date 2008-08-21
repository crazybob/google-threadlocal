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

/**
 * A variable for which each thread has its own value; child threads will
 * inherit the value at thread creation time.
 * 
 * @see java.lang.Thread
 * @see java.lang.ThreadLocal
 * @author Bob Lee
 */
public class InheritableThreadLocal<T> extends ThreadLocal<T> {

    private static final ThreadLocalMap.Factory MAP_FACTORY
            = new ThreadLocalMap.Factory() {
        ThreadLocalMap newMap(Thread current, int length) {
            return current.inheritableThreadLocals
                    = new ThreadLocalMap(this, length);
        }

        ThreadLocalMap getMap(Thread current) {
            return current.inheritableThreadLocals;
        }
    };

    /**
     * Creates a new inheritable thread local variable.
     */
    public InheritableThreadLocal() {
        super(MAP_FACTORY);
    }

    @Override
    ThreadLocalReference<T> newReference() {
        return new InheritableThreadLocalReference<T>(this);
    }

    /**
     * A specialized implementation that enables us to access the referent
     * so long as it's still alive. Unlike a vanilla phantom reference, this
     * implementation enables us to retrieve the referent so we can filter
     * thread local values when we inherit them.
     */
    private static class InheritableThreadLocalReference<T>
            extends ThreadLocalReference<T> {

        private InheritableThreadLocalReference(ThreadLocal<T> referent) {
            super(referent);
        }

        @Override
        boolean isInheritable() {
            return true;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public T get() {
        // Optimized for the fast path...
        Thread currentThread = Thread.currentThread();
        ThreadLocalMap map = currentThread.inheritableThreadLocals;
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
     * Creates a value for the child thread given the parent thread's value.
     * Called from the parent thread when creating a child thread. The default
     * implementation returns the parent thread's value.
     */
    protected T childValue(T parentValue) {
        return parentValue;
    }

    /**
     * Transfers inheritable values from parent to child thread. Executes in
     * parent thread.
     */
    @SuppressWarnings({"unchecked"})
    static void inheritValues(ThreadLocalMap parentMap,
            ThreadLocalMap childMap) {
        Object[] parentTable = parentMap.table;
        for (int i = parentTable.length - 2; i >= 0; i -= 2) {
            // No need for a volatile read. The write happened in this thread.
            Object k = parentTable[i];

            if (k == null || k == TOMBSTONE) {
                // Skip this entry.
                continue;
            }

            // The table can only contain null, tombstones and references.
            ThreadLocalReference reference = (ThreadLocalReference) k;
            // Raw type enables us to pass in an Object below.
            InheritableThreadLocal key
                    = (InheritableThreadLocal) reference.get();
            if (key != null) {
                /*
                 * Replace value with filtered value. We shouldn't need to
                 * rehash since we adopted the same length as our parent.
                 * We should just let exceptions bubble out and tank the
                 * thread creation
                 */
                childMap.put(reference, key.childValue(parentTable[i + 1]));
            }
        }
    }
}
