package io.swagger.client.assignment1_p2;

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
  private final SkiersApi skiersApi = new SkiersApi();
  private final ArrayList<RequestLog> requestLogs = new ArrayList<>();
  private final ConcurrentLinkedQueue<ConsumerResult> resultsQueue;  // Queue to send results to main thread
  private final CountDownLatch firstDoneLatch;

  public static final int MAX_RETRIES = 5;
    public static final String BASE_URL = "http://localhost:8080/";
//  public static final String BASE_URL = "http://ec2-34-209-153-11.us-west-2.compute.amazonaws.com:8080/Server/";


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
    // Wait for all asynchronous tasks to complete
    resultsQueue.add(new ConsumerResult(numOfSuccess, numOfFailures, requestLogs));
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

}
