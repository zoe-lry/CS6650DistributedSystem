import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;
import org.w3c.dom.css.Counter;

public class Main {

  public final static int N = 100000;
  public final static int NUM_EACH_THREAD = 100;
  ;

  public static Long singleThreadForArrayList(int num) {
    ArrayList<Integer> arrayList = new ArrayList<>();
    Long start = System.currentTimeMillis();
    for (int i = 0; i < num; i++) {
      arrayList.add(i);
    }
    Long end = System.currentTimeMillis();
    return end - start;
  }

  public static Long singleThreadForVector(int num) {
    Vector<Integer> vector = new Vector<>();
    Long start = System.currentTimeMillis();
    for (int i = 0; i < num; i++) {
      vector.add(i);
    }
    Long end = System.currentTimeMillis();
    return end - start;
  }

  public static Long singleThreadForHashtable(int num) {
    Vector<Integer> vector = new Vector<>();
    Long start = System.currentTimeMillis();
    for (int i = 0; i < num; i++) {
      vector.add(i);
    }
    Long end = System.currentTimeMillis();
    return end - start;
  }

  public static class multiThreadCounter {

    private static final AtomicInteger counter = new AtomicInteger(0);

    public static void main() throws InterruptedException {
      int num = N / NUM_EACH_THREAD;
      Long start = System.currentTimeMillis();
      Thread[] threads = new Thread[num];
      for (int i = 0; i < num; i++) {
        threads[i] = new Thread(() -> {
          for (int j = 0; j < NUM_EACH_THREAD; j++) {
            counter.incrementAndGet();
          }
        });
        threads[i].start();
      }
      for (Thread thread : threads) {
        thread.join();
      }
      Long end = System.currentTimeMillis();
      System.out.println("Final Counter Value: " + counter.get());
      System.out.println("MultiThreadingFileWrite multi thread Counter, time cost: " + (end - start));
    }
  }

  public static class multiThreadHashMap {

    private static final Map<Integer, Integer> map = new HashMap<>();

    public static void main() throws InterruptedException {
      Map<Integer, Integer> synmap = Collections.synchronizedMap(map);
      int num = N / NUM_EACH_THREAD;
      Long start = System.currentTimeMillis();
      Thread[] threads = new Thread[num];
      for (int i = 0; i < num; i++) {
        int finalI = i * NUM_EACH_THREAD;
        threads[i] = new Thread(() -> {
          for (int j = finalI; j < finalI + NUM_EACH_THREAD; j++) {
            synmap.put(j, j);
          }
        });
        threads[i].start();
      }
      for (Thread thread : threads) {
        thread.join();
      }
      Long end = System.currentTimeMillis();
      System.out.println("Final Counter Value: " + synmap.size());
      System.out.println("MultiThreadingFileWrite multi thread Counter, time cost: " + (end - start));
    }

  }
  public static class multiThreadHashTable {

    private static final Map<Integer, Integer> map = new Hashtable<>();

    public static void main() throws InterruptedException {
      int num = N / NUM_EACH_THREAD;
      Long start = System.currentTimeMillis();
      Thread[] threads = new Thread[num];
      for (int i = 0; i < num; i++) {
        int finalI = i * NUM_EACH_THREAD;
        threads[i] = new Thread(() -> {
          for (int j = finalI; j < finalI + NUM_EACH_THREAD; j++) {
            map.put(j, j);
          }
        });
        threads[i].start();
      }
      for (Thread thread : threads) {
        thread.join();
      }
      Long end = System.currentTimeMillis();
      System.out.println("Final Counter Value: " + map.size());
      System.out.println("MultiThreadingFileWrite multi thread Counter, time cost: " + (end - start));
    }

  }


  public static void main(String[] args) throws InterruptedException {
    int n = 100000;
    System.out.println(
        "MultiThreadingFileWrite single thread for ArrayList, n = " + N + ", time cost: " + singleThreadForArrayList(
            N)); // 10
    System.out.println(
        "MultiThreadingFileWrite single thread for Vector, n = " + N + ", time cost: " + singleThreadForVector(
            N)); // 29
    multiThreadCounter.main();
    multiThreadHashMap.main();
    multiThreadHashTable.main();

  }

}
