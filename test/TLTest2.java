import java.util.*;
import java.io.*;
import java.util.concurrent.atomic.*;


class TLTest2 {

    static final int ITERS = 10000000;
    static final int NTHREADS = 3;
    static final int NREPS = 5;
    static final int GETS_PER_SET = 1000;

    static final int[] xNL = {1, 
                             1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 
                             11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
                             30, 40, 50, 60, 70, 80, 90, 100,
                             200, 300, 400, 500, 600, 700, 800, 900, 1000,
                             2000, 3000, 4000, 5000, 6000, 7000, 8000, 9000,
                             10000, 20000, 30000, 40000, 50000,
                             60000, 70000, 80000, 90000, 100000 };

    static final int[] NL = {1, 
                             1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 
                             11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
                             30, 40, 50, 60, 70, 80, 90, 100,
                             200, 300, 400, 500, 600, 700, 800, 900, 1000,
                             2000, 3000, 4000, 5000, 6000, 7000, 8000, 9000,
                             10000, 20000, 30000, 40000, 50000,
                             60000, 70000, 80000, 90000, 100000,
                             1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 
                             11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
                             30, 40, 50, 60, 70, 80, 90, 100,
                             200, 300, 400, 500, 600, 700, 800, 900, 1000,
                             2000, 3000, 4000, 5000, 6000, 7000, 8000, 9000,
                             10000, 20000, 30000, 40000, 50000,
                             60000, 70000, 80000, 90000, 100000 };


    static class TL {

        private final AtomicInteger id;
    
        private final ThreadLocal atl = new ThreadLocal() {
                public Object initialValue() { 
                    return nextID(0);
                }
            };

        TL (int i) { 
            id = new AtomicInteger(17 + i); 
        }

        Integer nextID(int i) {
            return new Integer(id.getAndIncrement() + i);
        }

        public final int get() { 
            return ((Integer)(atl.get())).intValue();
        }

        public final void set(int i) { 
            atl.set(nextID(i));
        }

    }


    static class TestThread extends Thread {
        volatile int result;
        final TL[] tls;
        TestThread(TL[] t) { tls = t; }

        public void run() { 
            int nl = tls.length;
            int sum = 0; 
            int k = 0;

            while (k < ITERS) {

                for (int j = 0; j < nl && ++k < ITERS; ++j) {
                    tls[j].set(sum ^ j);
                }
        
                for (int i = 0; i < GETS_PER_SET && k < ITERS; ++i) {
                    for (int j = 0; j < nl && ++k < ITERS; ++j) {
                        sum += sum ^ tls[j].get();
                    }
                }
       
            }

            result = sum;
        }
    }


    public static void main(String[] args) {
    
        for (int l = 0; l < NL.length; ++l) {

            int nlocals = NL[l];
            long times = 0;
            long smallest = 0;

            System.out.print(nlocals+":\t");

            for (int reps = 0; reps < NREPS; ++reps) {
        
                TL[] tls = new TL[nlocals];
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


