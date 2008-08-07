import java.util.*;
import java.io.*;
import java.util.concurrent.atomic.*;


class TLTest8 {

    static final int ITERS = 10000000;
    static final int NTHREADS = 3;
    static final int NREPS = 5;
    static final int GETS_PER_SET = 255;
    static final int GETS_PER_NEW = 1023;

    static final int[] NL = {1, 
                             1, 2, 4, 6, 8, 
                             10, 20, 40, 60, 80,
                             100, 200, 400, 600, 800, 
                             1000, 2000, 4000, 6000, 8000,
                             10000, 20000, 40000, 60000, 80000, 
                             100000 };

    static final int[] XNL = {1, 
                             1, 2, 4, 6, 8, 
                             10, 20, 40, 60, 80,
                             100, 200, 400, 600, 800, 
                             1000, 2000, 4000, 6000, 8000,
                             10000, 20000, 40000, 60000, 80000, 
                             100000, 200000, 400000, 600000, 800000, 
                             1000000 };


    public static final class XorShift32Random {
        static final AtomicInteger seq = new AtomicInteger(8862213);
        int x = -1831433054;
        public XorShift32Random(int seed) { x = seed;  }
        public XorShift32Random() { 
            this((int)System.nanoTime() + seq.getAndAdd(129)); 
        }
        public int next() {
            x ^= x << 6; 
            x ^= x >>> 21; 
            x ^= (x << 7);
            return x;
        }
    }

    static class TL {

        private int id;
    
        private final ThreadLocal atl = new ThreadLocal() {
                public Object initialValue() { 
                    return nextID(0);
                }
            };

        TL (int i) { 
            id = 17 + i; 
        }

        Integer nextID(int i) {
            return new Integer(++id + i);
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
            XorShift32Random rng = new XorShift32Random();
            
            int nl = tls.length;
            int sum = 0; 
            int k = 0;
            int position = nl >>> 1;
            int mask = 8;
            while (mask > nl) mask >>>= 1;
            if (mask > 0) --mask;
            int h = mask >>> 1;

            while (k++ < ITERS) {
                int r = rng.next();
                position += (r & mask) - h;
                while (position >= nl) position -= nl;  
                while (position < 0) position += nl;
                TL x = tls[position];
                r >>>= 3;
                if (x == null || (r & GETS_PER_NEW) == GETS_PER_NEW)
                    tls[position] = new TL(sum);
                else if ((r & GETS_PER_SET) == 0)
                    x.set(sum);
                else
                    sum += sum ^ x.get();
            }

            result = sum;
        }
    }


    public static void main(String[] args) {
    
        for (int l = 0; l < NL.length; ++l) {

            int nlocals = NL[l];
            long times = 0;
            long smallest = 0;

            System.out.print(nlocals+"\t");

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


