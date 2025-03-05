package io.swagger.client.assignment_test;

import io.swagger.client.ApiException;
import io.swagger.client.ApiResponse;
import io.swagger.client.api.SkiersApi;
import io.swagger.client.assignment_model.ConsumerResult;
import io.swagger.client.assignment_model.LiftRideEvent;
import io.swagger.client.assignment_model.RequestLog;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class Consumer implements Runnable {

  private final BlockingQueue<LiftRideEvent> queue;
  private final Integer consumerId;
  private final Integer numRequests;
  private Integer numOfFailures = 0;
  private Integer numOfSuccess = 0;
  private AtomicInteger numOfSuccess2 = new AtomicInteger(0);
  private AtomicInteger numOfFailures2 = new AtomicInteger(0);
  private final SkiersApi skiersApi = new SkiersApi();
  private final ArrayList<RequestLog> requestLogs = new ArrayList<>();
  private final ConcurrentLinkedQueue<ConsumerResult> resultsQueue;  // Queue to send results to main thread
  private final CountDownLatch firstDoneLatch;

  public static final int MAX_RETRIES = 5;
      public static final String BASE_URL = "http://localhost:8080/";
//  public static final String BASE_URL = "http://ec2-34-209-153-11.us-west-2.compute.amazonaws.com:8080/Assignment/";


  public Consumer(BlockingQueue<LiftRideEvent> queue, int consumerId, int numRequests,
      ConcurrentLinkedQueue<ConsumerResult> resultsQueue, CountDownLatch firstDoneLatch) {
    this.queue = queue;
    this.consumerId = consumerId;
    this.numRequests = numRequests;
    this.resultsQueue = resultsQueue;
    this.firstDoneLatch = firstDoneLatch;
  }

  @Override
  public void run() {
    this.skiersApi.getApiClient().setBasePath(BASE_URL);

    ArrayList<CompletableFuture<Void>> futures = new ArrayList<>();
//    int numThreads = 15;
//    ExecutorService executor = Executors.newFixedThreadPool(numThreads);  // Adjust based on your system's capability
    IntStream.range(0, numRequests).anyMatch(i -> {
      try {
        LiftRideEvent event = queue.take();
        if (event.isPoisonPill()) {  // End if a poisonPill found
          return true;
        }
        sendRequest(event);  // Call the API.
//        CompletableFuture<Void> future = CompletableFuture.runAsync(()
//                                            -> sendAsynchronicityRequest(event), executor);
//        futures.add(future);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return false;
    });
    // Wait for all asynchronous tasks to complete
    resultsQueue.add(new ConsumerResult(numOfSuccess, numOfFailures, requestLogs));
//    Test asynchronous
//    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
//    executor.shutdown();
//    System.out.println("[Consumer-" + consumerId + "] Consumer Run Ends Here");
//    resultsQueue.add(new ConsumerResult(numOfSuccess2.get(), numOfFailures2.get(), requestLogs));

    if (firstDoneLatch.getCount() > 0) {
      firstDoneLatch.countDown();
    }
  }

  // If the client receives a 5XX response code (Web server error), or a 4XX response code (from your servlet),
  // it should retry the request up to 5 times before counting it as a failed request.
  private void sendRequest(LiftRideEvent event) {
    IntStream.rangeClosed(1, MAX_RETRIES).anyMatch(attempt -> {
      try {
        long start = System.currentTimeMillis();
        ApiResponse<Void> response = skiersApi.writeNewLiftRideWithHttpInfo(event.getBody(),
            event.getResortID(), event.getSeasonID(), event.getDayID(), event.getSkierID());
        long end = System.currentTimeMillis();
        // Add to the requestLogs
        requestLogs.add(
            new RequestLog(start, end, response.getStatusCode(), attempt, consumerId, "POST"));

        if (response.getStatusCode() == 201) {    // break if status code is 200
          numOfSuccess++;
          return true;
        } else if (response.getStatusCode() >= 400
            && response.getStatusCode() < 600) {  // retry if 400 - 600
          System.out.println(
              "Received Error Code: " + response.getStatusCode() + "Attempt: " + (attempt));
          if (attempt == MAX_RETRIES) {
            numOfFailures++;   // record the failed case
          }
        } else {
          numOfFailures++;
          return false; // break if other status code received
        }
      } catch (ApiException e) {
        System.err.println("Exception when calling ResortsApi#addSeason");
        e.printStackTrace();
        return false;
      }
      return false;
    });
  }

  // send Asynchronicity Request
  private void sendAsynchronicityRequest(LiftRideEvent event) {
    IntStream.rangeClosed(1, MAX_RETRIES).anyMatch(attempt -> {
      try {
        long start = System.currentTimeMillis();
        ApiResponse<Void> response = skiersApi.writeNewLiftRideWithHttpInfo(event.getBody(),
            event.getResortID(), event.getSeasonID(), event.getDayID(), event.getSkierID());
        long end = System.currentTimeMillis();
        // Add to the requestLogs
        requestLogs.add(
            new RequestLog(start, end, response.getStatusCode(), attempt, consumerId, "POST"));

        if (response.getStatusCode() == 201) {    // break if status code is 200
          numOfSuccess2.incrementAndGet();
          return true;
        } else if (response.getStatusCode() >= 400
            && response.getStatusCode() < 600) {  // retry if 400 - 600
          System.out.println(
              "Received Error Code: " + response.getStatusCode() + "Attempt: " + (attempt));
          if (attempt == MAX_RETRIES) {
            numOfFailures2.incrementAndGet();   // record the failed case
          }
        } else {
          numOfFailures2.incrementAndGet();
          return false; // break if other status code received
        }
      } catch (ApiException e) {
        System.err.println("Exception when calling ResortsApi#addSeason");
        e.printStackTrace();
        return false;
      }
      return false;
    });
  }
}
