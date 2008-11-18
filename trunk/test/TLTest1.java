/*
  More tests of various singleton implementations
  last update: Sun Apr  1 20:33:35 2001  Doug Lea  (dl at gee)
*/

import java.util.*;
import java.io.*;

class TLTest1 implements Runnable {

    static final int ITERS = 100000000;
    static final int NTHREADS = 2;
    static final int NMODES = 2;
    static final int NREPS = 5;

    static class SharedSingleton {
        static volatile SharedSingleton theInstance;
        volatile int vField = System.identityHashCode(this);
        public final int vMethod(int i) { 
            return vField++ ^ ~i;
        }

        private static synchronized SharedSingleton create() {
            if (theInstance == null) 
                theInstance = new SharedSingleton();
            return theInstance;
        }

        public static SharedSingleton getInstance() {
            SharedSingleton instance = theInstance;
            if (instance != null) 
                return instance;
            else
                return create();
        }

        static int loop(int n) {
            int sum = 0; 
            for (int i = 0; i < n; ++i) {
                SharedSingleton s = getInstance();
                sum += s.vMethod(i);
                if (sum == 0)
                    sum = s.hashCode();
            }
            return sum * sum;
        }
    }


    static class Singleton {
        int aField = System.identityHashCode(this);
        //        long p0, p1, p2, p3, p4, p5, p6, p7, p8, p9, pa, pb, pc, pd, pe;
        public final int aMethod(int i) { 
            return aField++ ^ ~i;
        }
    }

    static class ThreadLocalSingleton {
        private static final ThreadLocal perThreadInstance = 
            new ThreadLocal() {
                public Object initialValue() { return new Singleton(); }
            };
  
        public static Singleton getInstance() {
            return (Singleton)(perThreadInstance.get());
        }

        static int loop(int n) {
            int sum = 0; 
            for (int i = 0; i < n; ++i) {
                Singleton s = getInstance();
                sum += s.aMethod(i);
                if (sum == 0)
                    sum = s.hashCode();
            }
            return sum * sum;
        }
    }


    static volatile int total; 
    final int mode;
    TLTest1(int md) { mode = md; }


    static void printmode(int mode) {
        switch(mode) {
        case 0: System.out.print("ThreadLocal:    "); break;
        case 1: System.out.print("Shared  :       "); break;
        default: System.out.print("bad mode?:  "); break;
        }
    }

    public void run() {
        switch(mode) {
        case 0: total += ThreadLocalSingleton.loop(ITERS); return;
        case 1: total += SharedSingleton.loop(ITERS); return;
        default: return;
        }
    }

    static void warmUp() {
        int warmUpIters = 1000;
        for (int i = 0; i < 100; ++i) {
            total += ThreadLocalSingleton.loop(warmUpIters); 
            total += SharedSingleton.loop(warmUpIters); 
        }
    }

    public static void main(String[] args) {

        warmUp();

        long[] times = new long[NMODES];
        long[] least = new long[NMODES];

        Thread[] threads = new Thread[NTHREADS];
        TLTest1[] tests = new TLTest1[NTHREADS];

        for (int reps = 0; reps < NREPS; ++reps) {

            for (int i = 0; i < NTHREADS; ++i) 
                threads[i] = null;

            for (int mode = 0; mode < NMODES; ++mode) {

                if (mode != 2) { 
                    printmode(mode);
          
                    long startTime = System.currentTimeMillis();
          
                    for (int i = 0; i < NTHREADS; ++i) {
                        tests[i] = new TLTest1(mode);
                        threads[i] = new Thread(tests[i]);
                    }
          
                    for (int i = 0; i < NTHREADS; ++i) 
                        threads[i].start();
          
                    try {
                        for (int i = 0; i < NTHREADS; ++i) {
                            threads[i].join();
                        }
                    }   
                    catch (InterruptedException ie) {
                        System.out.println("Interrupted");
                        return;
                    }
          
                    long elapsed = System.currentTimeMillis() - startTime;
                    System.out.println(elapsed + "ms");
                    if (reps != 0) times[mode] += elapsed;
                    if (reps == 0 || elapsed < least[mode]) least[mode] = elapsed;
          
                    if (total == 0) 
                        System.out.println("useless number = " + total);
          
                }
            }

        }

        System.out.println("------------------------");
        for (int mode = 0; mode < NMODES; ++mode) {
            printmode(mode);
            long ave = (times[mode] * 1000 * 1000) / ((NREPS - 1) * ITERS);
            System.out.print(ave + "ns\t");
            long min = (least[mode] * 1000 * 1000) /  ITERS;
            System.out.println(min + "ns");
        }

    }
}

