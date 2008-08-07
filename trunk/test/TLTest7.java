import java.util.*;
import java.io.*;


class TLTest7 {

  static final int ITERS = 10000000;
  static final int NTHREADS = 3;
  static final int NREPS = 5;
  static final int GETS_PER_SET = 100; 


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
      id = i; 
      for (int j = 0; j < 4; ++j) ids[j] = new Integer(id + j);
    }

    public int get() { 
      return ((Integer)(atl.get())).intValue();
    }

    public void set(int i) { 
      int r = (id + i) & 3;
      atl.set(ids[r]);
    }

  }


  static class TestThread extends Thread {
    volatile int result;
    final TL[] tls;
    TestThread(TL[] t) { tls = t; result = t.length; }

    public void run() { 
      int nl = tls.length;
      int sum = 0; 
      int k = 0;
      int j = (int)(Math.random() * nl);

      int i = GETS_PER_SET;
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
    }

  }

  //  static InheritableThreadLocal junk = new InheritableThreadLocal();

  public static void main(String[] args) {

    //    junk.set(null);

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
        for (int i = 0; i < nlocals; ++i) tls[i] = null;
        Thread.yield();

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


