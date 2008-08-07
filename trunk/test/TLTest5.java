import java.util.*;
import java.io.*;


class TLTest5 {

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
        1,
        2, 5, 10, 
        20, 50, 100,
        200, 500, 1000,
        2000, 5000, 10000 };


    static interface ITL { 
        public int get(int i); 
        public int set(int i); 
    }

    static class Holder {
        Object x;
        Object get() { return x; }
        void  set(Object y) { x = y; }
    }

    static class TL implements ITL {

        private final int id;
    
        private final Holder atl = new Holder();

        private final Integer[] ids = new Integer[4];

        TL (int i) { 
            id = 17 + i; 
            for (int j = 0; j < 4; ++j) ids[j] = new Integer(id + j);
        }

        public int get(int i) { 
            return ((Integer)(atl.get())).intValue() + i;
        }

        public int set(int i) { 
            int r = (id + i) & 3;
            atl.set(ids[r]);
            return r;
        }
    }

    static class TL2 extends TL {
        TL2 (int i) { super(i); }

        public int get(int i) { 
            return super.get(i) + 1;
        }

        public int set(int i) { 
            return super.set(i) + 1;
        }

    }

    static class TestThread extends Thread {
        volatile int result;
        final ITL[] tls;
        TestThread(ITL[] t) { tls = t; }

        public void run() { 
            int nl = tls.length;
            int sum = 0; 
            int k = 0;
            int j = (int)(Math.random() * nl);

            for (int i = 0; i < nl; ++i) tls[i].set(i);

            int i = GETS_PER_SET;
            do {
                if (i-- <= 0) {
                    i = GETS_PER_SET;
                    sum += tls[j].set(~j ^ k);
                }
                else 
                    sum += tls[j].get(k - (~i ^ j));
                if ((k & 15) == 0) j = (j+1 < nl)? j+1 : 0;
            } while (++k < ITERS);

            result += sum;
        }

    }


    public static void main(String[] args) {

        new TL2(0);
    
        for (int l = 0; l < NL.length; ++l) {

            int nlocals = NL[l];
            long times = 0;
            long smallest = 0;

            System.out.print(nlocals+":\t");

            TL[] tls = new TL[nlocals];
            for (int i = 0; i < nlocals; ++i) tls[i] = new TL(i);
        

            for (int reps = 0; reps < NREPS; ++reps) {
        
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


