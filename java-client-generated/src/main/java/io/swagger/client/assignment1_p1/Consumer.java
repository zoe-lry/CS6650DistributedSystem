package io.swagger.client.assignment1_p1;

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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class Consumer implements Runnable {

  private final BlockingQueue<LiftRideEvent> queue;
  private final Integer consumerId;
  private final Integer numRequests;
  private Integer numOfFailures = 0;
  private Integer numOfSuccess = 0;
  private final SkiersApi skiersApi = new SkiersApi();
  private final ConcurrentLinkedQueue<ConsumerResult> resultsQueue;  // Queue to send results to main thread
  private final CountDownLatch firstDoneLatch;
  private final AtomicBoolean firstDoneTriggered;

  public static final int MAX_RETRIES = 5;
  //      public static final String BASE_URL = "http://localhost:8080";
//  public static final String BASE_URL = "http://ec2-35-91-165-228.us-west-2.compute.amazonaws.com:8080/Server/"; // Single Servlet
  public static final String BASE_URL = "http://server-load-balancer-165699902.us-west-2.elb.amazonaws.com/Server/"; //Load Balancer


  public Consumer(BlockingQueue<LiftRideEvent> queue, int consumerId, int numRequests,
      ConcurrentLinkedQueue<ConsumerResult> resultsQueue, CountDownLatch firstDoneLatch,
      AtomicBoolean firstDoneTriggered) {
    this.queue = queue;
    this.consumerId = consumerId;
    this.numRequests = numRequests;
    this.resultsQueue = resultsQueue;
    this.firstDoneLatch = firstDoneLatch;
    this.firstDoneTriggered = firstDoneTriggered;
  }

  @Override
  public void run() {
    this.skiersApi.getApiClient().setBasePath(BASE_URL);
    IntStream.range(0, numRequests).anyMatch(i -> {
      try {
        LiftRideEvent event = queue.take();
        if (event.isPoisonPill()) {  // End if a poisonPill found
          return true;
        }
        sendRequest(event);  // Call the API.
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return false;
    });
    resultsQueue.add(new ConsumerResult(numOfSuccess, numOfFailures));
    if (firstDoneTriggered.compareAndSet(false, true)) {
      firstDoneLatch.countDown();
    }
  }

  // If the client receives a 5XX response code (Web server error), or a 4XX response code (from your servlet),
  // it should retry the request up to 5 times before counting it as a failed request.
  private void sendRequest(LiftRideEvent event) {
    for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
      try {
        ApiResponse<Void> response = skiersApi.writeNewLiftRideWithHttpInfo(
            event.getBody(), event.getResortID(), event.getSeasonID(), event.getDayID(), event.getSkierID());
        if (response.getStatusCode() == 201) {
          numOfSuccess++;
          break;
        } else if (response.getStatusCode() >= 400 && response.getStatusCode() < 600) {
          System.out.println("Consumer " + consumerId + " received error code: " +
              response.getStatusCode() + " on attempt " + attempt);
        }
      } catch (ApiException e) {
        System.err.println("Consumer " + consumerId + " encountered exception: " + e.getCode());
        e.printStackTrace();
      }
      if (attempt == MAX_RETRIES) {
        numOfFailures++;
      }
    }
  }
}
