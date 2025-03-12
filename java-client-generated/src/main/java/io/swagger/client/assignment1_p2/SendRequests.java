package io.swagger.client.assignment1_p2;

import io.swagger.client.assignment_model.ConsumerResult;
import io.swagger.client.assignment_model.LiftRideEvent;
import io.swagger.client.assignment_model.RequestLog;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.IntStream;

public class SendRequests {

  public static final int TOTAL_NUM_EVENTS = 200_000;
  static final int INITIAL_NUM_CONSUMERS = 32;
  public static final int INITIAL_NUM_REQUEST_PER_THREAD = 1000;
  public static final int PHASE_TWO_NUM_CONSUMERS = 500;
  public static final int PHASE_NUM_REQUEST_PER_THREAD =
      TOTAL_NUM_EVENTS - (INITIAL_NUM_CONSUMERS * INITIAL_NUM_REQUEST_PER_THREAD);
  public static final int QUEUE_CAPACITY = 1000;
  public static CountDownLatch firstDoneLatch = new CountDownLatch(1);
  public static List<Thread> consumerThreads = new ArrayList<>();
  public static ConcurrentLinkedQueue<ConsumerResult> resultsQueue = new ConcurrentLinkedQueue<>();
  public static BlockingQueue<LiftRideEvent> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);

  public static void createThread(int numThreads, int numRequests) {
    IntStream.range(0, numThreads).forEach(i -> {
      Thread cThread = new Thread(
          new Consumer(queue, i, numRequests, resultsQueue, firstDoneLatch)
      );
      cThread.start();
      consumerThreads.add(cThread);
    });
  }

  private static void summarizeResult(Long end, Long start) {
    int totalFailures = 0;
    int totalSuccess = 0;
    List<RequestLog> allLogs = new CopyOnWriteArrayList<>();
    for (ConsumerResult result : resultsQueue) {
      totalSuccess += result.getNumOfSuccess();
      totalFailures += result.getNumOfFailures();
      allLogs.addAll(result.getLogs());
    }

    long wallTime = (end - start);
    System.out.println("\nAll done. Main thread exiting：");
    System.out.printf("Number of threads in Phase Two  :  %d%n", PHASE_TWO_NUM_CONSUMERS);
    System.out.printf("Number of successful requests   :  %d%n", totalSuccess);
    System.out.printf("Number of unsuccessful requests :  %d%n", totalFailures);
    System.out.printf("Total run time                  :  %d%n", wallTime);
    System.out.printf("Total throughput per second     :  %.2f requests/second%n",
        (TOTAL_NUM_EVENTS / (wallTime / 1000.0)));

    // Write to CSV
    CSVHandler.writeLogsToCSV(allLogs, "request_logs.csv");
    // Calculate the mean, medium, p99, min, max
    CSVHandler.printResponseTimeStats(allLogs);
  }

  public static void main(String[] args) throws InterruptedException {

    long start = System.currentTimeMillis();  // Start time

    // Start the producer thread
    Thread producerThread = new Thread(new Producer(queue, TOTAL_NUM_EVENTS));
    producerThread.start();

    // Phase 1: start 32 threads and each will send 1000 requests
    createThread(INITIAL_NUM_CONSUMERS, INITIAL_NUM_REQUEST_PER_THREAD);
    // Wait for any one of them to complete
    firstDoneLatch.await();
    System.out.println("Time spend for firstDoneLatch: " + (System.currentTimeMillis() - start));

    // Phase 2: wait for one thread to be completed to start the phase two
    createThread(PHASE_TWO_NUM_CONSUMERS, PHASE_NUM_REQUEST_PER_THREAD);

    producerThread.join();
    System.out.println("Time spend for producerThread: " + (System.currentTimeMillis() - start));

    // Insert 1 poison pill per phase two consumer
    IntStream.range(0, PHASE_TWO_NUM_CONSUMERS).forEach(i -> {
      try {
        queue.put(LiftRideEvent.poisonPill());
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    });
    System.out.println("Time spend for poisonPill: " + (System.currentTimeMillis() - start));

    // Wait for all thread
    for (Thread cThread : consumerThreads) {
      cThread.join();
    }
    long end = System.currentTimeMillis();  // End time
    // Summarize the result
    summarizeResult(end, start);
  }


}
