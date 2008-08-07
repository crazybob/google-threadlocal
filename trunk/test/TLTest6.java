import java.util.*;
import java.io.*;
import java.util.concurrent.atomic.*;


class TLTest6 {

    static final int ITERS = 10000000;
    static final int NTHREADS = 1;
    static final int NREPS = 5;
    static final int GETS_PER_SET = 10000;

    static final int[] NL = { 1, 100, 1000,
                              1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 
                              11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
                              30, 40, 50, 60, 70, 80, 90, 100,
                              200, 300, 400, 500, 600, 700, 800, 900, 1000,
                              2000, 3000, 4000, 5000, 6000, 7000, 8000, 9000,
                              10000, 20000, 30000, 40000, 50000,
                              60000, 70000, 80000, 90000, 100000 };


    static interface ITL { public int get(int i); }

    static class TL implements ITL {

        private final AtomicInteger id;
    
        private final InheritableThreadLocal atl = new InheritableThreadLocal();

        TL (int i) { 
            id = new AtomicInteger(17 + i); 
        }

        synchronized int inc() {
            return id.getAndIncrement();
        }

        Integer nextID(int i) {
            return new Integer(inc() + i);
        }

        public int get(int i) { 
            return ((Integer)(atl.get())).intValue() + i;
        }

        public void set(int i) { 
            atl.set(nextID(i));
        }

    }

    static class TL2 extends TL {
        TL2 (int i) { super(i); }

        public int get(int i) { 
            return super.get(i) + 1;
        }
    }

    static class TL3 implements ITL { public int get(int i) { return 17; } }


    static class TestThread extends Thread {
        volatile int result;
        final TL[] tls;
        TestThread(TL[] t) { tls = t; }

        public void run() { 
            if (staticITL.get() != ITLVAL) throw new Error("ITL?");

            int nl = tls.length;
            int sum = 0; 
            int k = 0;

            while (k < ITERS) {

                for (int j = 0; j < nl && ++k < ITERS; ++j) {
                    tls[j].set(sum + j);
                }
        
                for (int j = 0; j < nl && k < ITERS; ++j) {
                    for (int i = 0; i < GETS_PER_SET && ++k < ITERS; ++i) {
                        sum += tls[j].get(i+j);
                    }
                }
       
            }

            result = sum;
        }

        public void xxrun() { 
            int nl = tls.length;
            int runs = ITERS/GETS_PER_SET;
            int sum = 0; 
            int k = 0;

            while (k < ITERS) {

                for (int j = 0; j < nl && ++k < ITERS; ++j) {
                    tls[j].set(sum + j);
                }
        
                for (int i = 0; i < GETS_PER_SET && k < ITERS; ++i) {
                    for (int j = 0; j < nl && ++k < ITERS; ++j) {
                        sum += tls[j].get(j);
                    }
                }
       
            }

            result = sum;
        }

    }

    static final Object ITLVAL = new Object();
    static InheritableThreadLocal staticITL = new InheritableThreadLocal();

    public static void main(String[] args) {

        new TL2(0);
        new TL3();
        staticITL.set(ITLVAL);

        for (int l = 0; l < NL.length; ++l) {

            int nlocals = NL[l];
            long times = 0;
            long smallest = 0;

            System.out.print(nlocals+":\t");

            TL[] tls = new TL[nlocals];
        

            for (int reps = 0; reps < NREPS; ++reps) {

                for (int i = 0; i < nlocals; ++i) tls[i] = new TL(i);
        
                TestThread[] threads = new TestThread[NTHREADS];
                for (int i = 0; i < NTHREADS; ++i) 
                    threads[i] = new TestThread(tls);
        
                long startTime = System.currentTimeMillis();
        
                for (int i = 0; i < NTHREADS; ++i) 
                    threads[i].start();
        
                int total = 0;
                try {
                    for (int i = 0; i < NTHREADS; ++i) {
                        threads[i].join();
                        total += threads[i].result;
                        threads[i] = null;
                    }
                }
                catch (InterruptedException ie) {
                    System.out.println("Interrupted");
                    return;
                }
        
                long elapsed = System.currentTimeMillis() - startTime;
                System.out.print(elapsed + "\t");
                times += elapsed;
                if (reps == 0 || elapsed < smallest) smallest = elapsed;

                if (total == 0) // ensure total is live to avoid optimizing away
                    System.out.println("useless number = " + total);
            }
      
            long ave = (times * 1000 * 1000) / (NREPS * ITERS);
            System.out.print("A:" + ave);

            long least = (smallest * 1000 * 1000) / (ITERS);
            System.out.println("\tL:" + least);

        }
    }
}


