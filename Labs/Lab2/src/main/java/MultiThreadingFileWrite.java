import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class MultiThreadingFileWrite {

  public static final int THREAD_COUNT = 500;
  public static final int STRINGS_PER_THREAD = 1000;
  public static final String FILE_NAME = "output.txt";

  /**
   * write every string to the file immediately after it is generated in the loop in each thread
   */
  public static void TestWriteImmediately() throws IOException, InterruptedException {
    ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);

    Long start = System.currentTimeMillis();
    BufferedWriter writer = new BufferedWriter(new FileWriter(FILE_NAME));
    try {
      Object lock = new Object();
      for (int i = 0; i < THREAD_COUNT; i++) {
        executorService.submit(() -> {
          try {
            for (int j = 0; j < STRINGS_PER_THREAD; j++) {
              synchronized (lock) {
                writer.write("Current Thread ID: " + Thread.currentThread().getId() + "\n");
              }
            }
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    executorService.shutdown();
    if (!executorService.awaitTermination(100, TimeUnit.MINUTES)) {
      System.err.println("Some tasks did not complete within the timeout!");
    }
    writer.close();
    Long end = System.currentTimeMillis();
    System.out.println("Test Write Immediately, time: " + (end - start));
  }

  /**
   * write all the strings from one thread after they are generated and just before a thread
   * terminates
   */
  public static void TestWriteAllWithinOneThread() throws IOException, InterruptedException {
    ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);

    Long start = System.currentTimeMillis();
    BufferedWriter writer = new BufferedWriter(new FileWriter(FILE_NAME));
    try {
      Object lock = new Object();
      for (int i = 0; i < THREAD_COUNT; i++) {
        executorService.submit(() -> {
          StringBuilder strings = new StringBuilder();

          for (int j = 0; j < STRINGS_PER_THREAD; j++) {
            strings.append("Current Thread ID: ").append(Thread.currentThread().getId())
                .append("\n");
          }
          try {
            synchronized (lock) {
              writer.write(strings.toString());
            }
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    executorService.shutdown();
    if (!executorService.awaitTermination(100, TimeUnit.MINUTES)) {
      System.err.println("Some tasks did not complete within the timeout!");
    }
    writer.close();
    Long end = System.currentTimeMillis();
    System.out.println("Test Write Immediately, time: " + (end - start));
  }

  /**
   * Store all the strings from all threads in a shared collection, and write this to a file from
   * your main() thread after all threads are completed
   */
  public static void TestWriteAll() throws IOException, InterruptedException {
    ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
    StringBuilder strings = new StringBuilder();

    Long start = System.currentTimeMillis();

    try {
      Object lock = new Object();
      for (int i = 0; i < THREAD_COUNT; i++) {
        executorService.submit(() -> {
          for (int j = 0; j < STRINGS_PER_THREAD; j++) {
            synchronized (lock) {
              strings.append(System.currentTimeMillis()).append(", Current Thread ID: ")
                  .append(Thread.currentThread().getId())
                  .append("\n");
            }
          }
        });
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    executorService.shutdown();
    if (!executorService.awaitTermination(100, TimeUnit.MINUTES)) {
      System.err.println("Some tasks did not complete within the timeout!");
    }
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(FILE_NAME))) {
      writer.write(strings.toString());
    }
//    if (!executorService.awaitTermination(100, TimeUnit.MINUTES)) {
//      System.err.println("Some tasks did not complete within the timeout!");
//    }
    Long end = System.currentTimeMillis();
    System.out.println("Test Write Immediately, time: " + (end - start));
  }

  /**
   * Can you design a solution in which only one thread writes to the file while the threads are
   * generating the strings. Below is an example approach using a Producer/Consumer pattern with a
   * single writer thread and multiple “producer” threads that generate data. The key ideas are:
   * Producers (your 500 threads) generate strings and put them into a thread-safe queue (e.g.
   * BlockingQueue<String>). One dedicated Writer thread takes strings from the queue and writes
   * them to the file.
   */
  public static void TestWriteWhileGenerating() throws IOException, InterruptedException {
    // A thread-safe queue for transferring generated strings
    BlockingQueue<String> queue = new LinkedBlockingQueue<>();

    // A latch to track when all producer threads have finished generating
    CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

    // Fixed thread pool to run all producers
    ExecutorService producerExecutor = Executors.newFixedThreadPool(THREAD_COUNT);

    Long start = System.currentTimeMillis();

    // This thread is the single consumer/writer
    Thread writerThread = new Thread(() -> {
      try (BufferedWriter writer = new BufferedWriter(new FileWriter(FILE_NAME))) {
        while (true) {
          // If all producers are done and the queue is empty, we're finished
          if (latch.getCount() == 0 && queue.isEmpty()) {
            break;
          }
          // Get a line from the queue (waiting briefly if empty)
          String line = queue.poll(100, TimeUnit.MILLISECONDS);
//          writer.write(line);
          if (line != null) {
            writer.write(line);
          }
        }
      } catch (IOException | InterruptedException e) {
        e.printStackTrace();
      }
    });

    // Start the writer thread
    writerThread.start();

    // Submit producer tasks
    for (int i = 0; i < THREAD_COUNT; i++) {
      producerExecutor.submit(() -> {
        try {
          for (int j = 0; j < STRINGS_PER_THREAD; j++) {
            // Generate some data
            // Put data into the queue for the writer to pick up
            queue.put("Current Thread ID: " + Thread.currentThread().getId() + "\n");
          }
        } catch (InterruptedException e) {
          // Reset interrupt flag and exit if needed
          Thread.currentThread().interrupt();
        } finally {
          // Signal that this producer is done
          latch.countDown();
        }
      });
    }

    // Shut down the producer pool and wait for all tasks to finish
    producerExecutor.shutdown();
    producerExecutor.awaitTermination(1, TimeUnit.HOURS);

    // Wait for the writer thread to finish its work
    writerThread.join();
    Long end = System.currentTimeMillis();
    System.out.println("Test Write Immediately, time: " + (end - start));

  }


  public static void main(String[] args) throws IOException, InterruptedException {
    TestWriteImmediately(); // 286
    TestWriteAllWithinOneThread();  // 65
    TestWriteAll(); //152
    TestWriteWhileGenerating(); // 188
  }
}
