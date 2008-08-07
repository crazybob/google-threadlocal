import java.util.*;
import java.io.*;


class TLTest4 {

    static final int ITERS = 10000000;
    static final int NTHREADS = 3;
    static final int NREPS = 5;
    static final int GETS_PER_TL = 63;  // must be power of two minus one

    static final int[] XNL = { 1, 1, 1 };

    static final int[] NL = { 1, 1,
                              1,
                              2, 5, 10, 
                              20, 50, 100,
                              200, 500, 1000,
                              2000, 5000, 10000,
                              1,
                              2, 5, 10, 
                              20, 50, 100,
                              200, 500, 1000,
                              2000, 5000, 10000 };


    static final class TL {
        private final int id;
        private final Integer[] ids = new Integer[4];
    
        private final ThreadLocal atl = new ThreadLocal() {
                public Object initialValue() {
                    return ids[id & 3];
                }
            };

        TL (int i) { 
            id = 17 + i; 
            for (int j = 0; j < 4; ++j) 
                ids[j] = new Integer(id + j);
        }

        public int get(int i) { 
            return ((Integer)(atl.get())).intValue() + i;
        }
    }


    static class TestThread extends Thread {
        volatile int result;
        final int nl;
        final int iters;
        final int baseIndex;
        TestThread(int n, int its) { 
            iters = its;
            nl = n; 
            result = n; 
            baseIndex = (int)(Math.random() * nl);
        }
    
        public void run() { 
            final TL[] tls = new TL[nl];
            int sum = 0; 
            int j = baseIndex;
      
            for (int k = 0; k < iters; ++k) {
                if (tls[j] == null || (k & GETS_PER_TL) == 1) {
                    tls[j] = new TL(sum++);
                }
                else {
                    sum += tls[j].get(k ^ j);
                }
                if ((k & 7) == 0 && ++j >= nl)
                    j = 0;
                
            }
      
            result += sum;
        }
    }
  
    static void warmup() throws Exception {
        for (int i = 10; i < ITERS; i *= 10) {
            TestThread t = new TestThread(1, i);
            t.start();
            t.join();
            if (t.result == 0) 
                System.out.print("");
        }
    }

    public static void main(String[] args) throws Exception {
        warmup();

        for (int l = 0; l < NL.length; ++l) {

            int nlocals = NL[l];
            long times = 0;
            long smallest = 0;

            System.out.print(nlocals+":\t");

            for (int reps = 0; reps < NREPS; ++reps) {
        
                TestThread[] threads = new TestThread[NTHREADS];
                for (int i = 0; i < NTHREADS; ++i) 
                    threads[i] = new TestThread(nlocals, ITERS);
        
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


