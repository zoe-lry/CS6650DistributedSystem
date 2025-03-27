import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import model.LiftRideEvent;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutRequest;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

/**
 * A shared batch writer that:
 * 1) Holds a BlockingQueue of LiftRideEvents,
 * 2) Spawns multiple flush threads that:
 *      - Drain up to MAX_BATCH_SIZE items at a time
 *      - Use batchWriteItem() to DynamoDB
 *      - Retry unprocessed items
 * 3) Lets any number of ConsumerWorkers add to the queue concurrently.
 */
public class DynamoDBBatchWriter {

  // DynamoDB limits a single batchWriteItem to 25 items max
  private static final int MAX_BATCH_SIZE = 25;
  // A small sleep time if queue is empty, so flush threads don't spin at 100% CPU
  private static final int EMPTY_QUEUE_DELAY_MS = 50;

  private final DynamoDbClient dynamoDb;
  private final BlockingQueue<LiftRideEvent> queue;
  private final ExecutorService flushPool;

  /**
   * @param dynamoDb      The synchronous DynamoDB client
   * @param flushThreads  Number of concurrent flush threads to use
   */
  public DynamoDBBatchWriter(DynamoDbClient dynamoDb, int flushThreads) {
    this.dynamoDb = dynamoDb;
    this.queue = new LinkedBlockingQueue<>();
    this.flushPool = Executors.newFixedThreadPool(flushThreads);

    // Spawn flushThreads each running flushLoop()
    for (int i = 0; i < flushThreads; i++) {
      flushPool.submit(this::flushLoop);
    }
  }

  /**
   * Called by ConsumerWorkers to add events to the batch queue.
   */
  public void addEvent(LiftRideEvent event) {
    this.queue.offer(event);
  }

  /**
   * The main loop for each flusher thread:
   * - Drains up to 25 items
   * - batchWriteItem
   * - retries unprocessed
   * - repeats
   */
  private void flushLoop() {
    while (!Thread.currentThread().isInterrupted()) {
      try {
        // Drain up to MAX_BATCH_SIZE items
        List<LiftRideEvent> batch = new ArrayList<>(MAX_BATCH_SIZE);
        queue.drainTo(batch, MAX_BATCH_SIZE);

        if (batch.isEmpty()) {
          // If no items, sleep briefly to avoid busy-loop
          Thread.sleep(EMPTY_QUEUE_DELAY_MS);
          continue;
        }

        writeBatch(batch);

      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break; // Exit flush loop if interrupted
      } catch (Exception ex) {
        // In production, you might want better error handling
        ex.printStackTrace();
      }
    }
  }

  /**
   * Helper that builds and executes the DynamoDB batch request,
   * and retries any unprocessed items until they succeed.
   */
  private void writeBatch(List<LiftRideEvent> events) {
    // Convert the events to WriteRequests
    List<WriteRequest> writeRequests = new ArrayList<>(events.size());
    for (LiftRideEvent event : events) {
      Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue> item = buildItem(event);
      PutRequest putRequest = PutRequest.builder().item(item).build();
      WriteRequest writeReq = WriteRequest.builder().putRequest(putRequest).build();
      writeRequests.add(writeReq);
    }

    // Build initial request
    Map<String, List<WriteRequest>> requestItems = new HashMap<>();
    requestItems.put("lift_rides", writeRequests);

    BatchWriteItemRequest batchRequest = BatchWriteItemRequest.builder()
        .requestItems(requestItems)
        .build();

    // Execute and handle unprocessed items
    BatchWriteItemResponse response = dynamoDb.batchWriteItem(batchRequest);
    Map<String, List<WriteRequest>> unprocessed = response.unprocessedItems();

    // Retry as long as we have unprocessed items
    while (unprocessed != null && !unprocessed.isEmpty()) {
      // Sleep a bit if you want to avoid tight loop on throttling
      // Thread.sleep(100);

      BatchWriteItemRequest retryRequest = BatchWriteItemRequest.builder()
          .requestItems(unprocessed)
          .build();
      response = dynamoDb.batchWriteItem(retryRequest);
      unprocessed = response.unprocessedItems();
    }
  }

  /**
   * Convert a LiftRideEvent into DynamoDB attributes.
   */
  private Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue> buildItem(LiftRideEvent event) {
    Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue> item = new HashMap<>();

    String seasonDayTime = event.getSeasonID() + "-" +
        event.getDayID() + "-" +
        event.getBody().getTime();

    String resortSeasonDayKey = event.getResortID() + "-" +
        event.getSeasonID() + "-" +
        event.getDayID();

    item.put("SkierID", avNumber(event.getSkierID()));
    item.put("SeasonDayTime", avString(seasonDayTime));
    item.put("ResortID", avNumber(event.getResortID()));
    item.put("LiftID", avNumber(event.getBody().getLiftID()));
    item.put("Time", avNumber(event.getBody().getTime()));
    item.put("VerticalGain", avNumber(event.getBody().getLiftID() * 10));
    item.put("ResortSeasonDayKey", avString(resortSeasonDayKey));

    return item;
  }

  private software.amazon.awssdk.services.dynamodb.model.AttributeValue avString(String s) {
    return software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder().s(s).build();
  }

  private software.amazon.awssdk.services.dynamodb.model.AttributeValue avNumber(int n) {
    return software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder().n(String.valueOf(n)).build();
  }

  /**
   * Optional shutdown if you want to stop flush threads gracefully.
   */
  public void shutdown() {
    flushPool.shutdown();
    try {
      if (!flushPool.awaitTermination(60, TimeUnit.SECONDS)) {
        flushPool.shutdownNow();
      }
    } catch (InterruptedException e) {
      flushPool.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}

