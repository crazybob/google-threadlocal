import java.util.*;
import java.io.*;

class TLTest3 {

    static final int ITERS = 10000000;
    static final int NTHREADS = 1;
    static final int NREPS = 5;
    static final int GETS_PER_SET = 100; 

    static final int[] NL = { 
        1,
        2, 5, 10, 
        20, 50, 100,
        200, 500, 1000,
        2000, 5000, 10000,
        20000, 50000, 100000,
        1,
        2, 5, 10, 
        20, 50, 100,
        200, 500, 1000,
        2000, 5000, 10000,
        20000, 50000, 100000
    };


    // Some interfaces and classes used to vary resolvability
    // of calls.
    static interface ITL { 
        public int get(); 
        public void set(int i); 
    }

    static class TL implements ITL {
        private final int id;
        private final Integer[] ids = new Integer[4];
        private final ThreadLocal atl = new ThreadLocal() {
                public Object initialValue() {
                    return ids[id & 3];
                }
            };

        TL (int i) { 
            id = 17 + i; 
            for (int j = 0; j < 4; ++j) ids[j] = new Integer(id + j);
        }

        public int get() { 
            return ((Integer)(atl.get())).intValue();
        }

        public void set(int i) { 
            atl.set(ids[(id + i) & 3]);
        }
    }

    static class TL2 extends TL {
        TL2 (int i) { super(i); }
        public int  get() { return super.get() + 1;  }
        public void set(int i) { super.set(i + i);  }
    }

    static class TL3 implements ITL { 
        int ii;
        TL3 (int i) { ii = i; }
        public int  get() { return ii *= 17245 + 17; } 
        public void set(int i) { ii += i; } 
    }

    static class Holder {
        Object x;
        Object get() { return x; }
        void  set(Object y) { x = y; }
    }

    static class HTL implements ITL {
        private final int id;
        private final Holder atl = new Holder();
        private final Integer[] ids = new Integer[4];

        HTL (int i) { 
            id = 17 + i; 
            for (int j = 0; j < 4; ++j) ids[j] = new Integer(id + j);
        }

        public int get() { 
            return ((Integer)(atl.get())).intValue() + 17;
        }

        public void set(int i) { 
            int r = (id + i) & 3;
            atl.set(ids[r]);
        }
    }


    static class Tester implements Runnable {
        volatile int result;
        final ITL[] tls;
        Tester(ITL[] t) { tls = t; result = t.length; }

        public void run() { 
            int nl = tls.length;
            int sum = result; 
            int k = 0;
            int j = nl / 2;
            int i = GETS_PER_SET;

            for (int p = 0; p < nl; ++p) tls[p].set(p);

            do {
                if (i-- <= 0) {
                    i = GETS_PER_SET;
                    tls[j].set(sum);
                }
                else 
                    sum += tls[j].get();
                if ((k & 15) == 0) j = (j+1 < nl)? j+1 : 0;
            } while (++k < ITERS);

            result += sum;
            total = result;
        }

    }

    //  static InheritableThreadLocal junk = new InheritableThreadLocal();

    static volatile int total;

    static void warmUp() {
        int sum = 0;

        sum += warmUpGets(new TL(0));
        sum += warmUpGets(new HTL(0));
        sum += warmUpGets(new TL2(0));
        sum += warmUpGets(new TL3(0));

        total = sum;
    }

    static int warmUpGets(ITL tl) {
        int n = ITERS;
        int sum = 0;
        tl.set(0);
        for (int k = 0; k < n; ++k)
            sum += tl.get();
        return sum;
    }

    static Thread[] makeThreads(int nthreads, ITL[] tls) {
        Thread[] threads = new Thread[nthreads];
        for (int i = 0; i < nthreads; ++i) 
            threads[i] = new Thread(new Tester(tls));
        return threads;
    }

    static int runThreads(Thread[] threads) {
        for (int i = 0; i < threads.length; ++i) 
            threads[i].start();
    
        int sum = 0;
        try {
            for (int i = 0; i < threads.length; ++i) {
                threads[i].join();
                threads[i] = null;
            }
        }
        catch (InterruptedException ie) {
            System.out.println("Interrupted");
            return 0;
        }
        sum = total;
        return sum;
    }

    static void fillTLs(ITL[] tls) {
        for (int i = 0; i < tls.length; ++i) tls[i] = new TL(i);
    }

    public static void main(String[] args) {

        //    junk.set(null);
        warmUp();

        for (int l = 0; l < NL.length; ++l) {

            int nlocals = NL[l];
            long smallest = 0;
            ITL[] tls = new ITL[nlocals];

            System.out.print(nlocals+":\t");

            for (int reps = 0; reps < NREPS; ++reps) {

                fillTLs(tls);
                Thread[] threads = makeThreads(NTHREADS, tls);
       
                long startTime = System.currentTimeMillis();
                total += runThreads(threads);
                long elapsed = System.currentTimeMillis() - startTime;
                if (reps == 0 || elapsed < smallest) smallest = elapsed;

                System.out.print(elapsed + "\t");
            }
            long least = (smallest * 1000 * 1000) / (ITERS);
            System.out.println("\t" + least + " ns");

        }

        if (total == 0) // ensure total is live to avoid optimizing away
            System.out.println("useless number = " + total);
    }
}
