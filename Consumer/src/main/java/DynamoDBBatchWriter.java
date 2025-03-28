import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import model.LiftRideEvent;
import model.QueuedMessage;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
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

  private static final int MAX_BATCH_SIZE = 25;
  private static final int EMPTY_QUEUE_DELAY_MS = 50;
  private static final int MAX_QUEUE_SIZE = 1000;

  private final DynamoDbClient dynamoDb;
  private final BlockingQueue<QueuedMessage> queue;
  private final ExecutorService flushPool;

  public DynamoDBBatchWriter(DynamoDbClient dynamoDb, int flushThreads) {
    this.dynamoDb = dynamoDb;
    this.queue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);
    this.flushPool = Executors.newFixedThreadPool(flushThreads);

    // Spawn flushThreads each running flushLoop()
    for (int i = 0; i < flushThreads; i++) {
      flushPool.submit(this::flushLoop);
    }
  }

  /**
   * Called by ConsumerWorkers to add events to the batch queue.
   */
  public void addMessage(QueuedMessage queuedMsg) {
    this.queue.offer(queuedMsg);
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
        List<QueuedMessage> batch = new ArrayList<>(MAX_BATCH_SIZE);
        queue.drainTo(batch, MAX_BATCH_SIZE);
        // If no items, sleep
        if (batch.isEmpty()) {
          Thread.sleep(EMPTY_QUEUE_DELAY_MS);
          continue;
        }
        writeBatch(batch);

      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    }
  }


  private void writeBatch(List<QueuedMessage> messages) throws IOException {
    Map<WriteRequest, QueuedMessage> writeMap = new HashMap<>();
    List<WriteRequest> writeRequests = new ArrayList<>(messages.size());

    for (QueuedMessage qm : messages) {
      Map<String, AttributeValue> item = buildItem(qm.getEvent());
      PutRequest putReq = PutRequest.builder().item(item).build();
      WriteRequest wreq = WriteRequest.builder().putRequest(putReq).build();
      writeRequests.add(wreq);
      writeMap.put(wreq, qm);
    }

    Map<String, List<WriteRequest>> requestItems = new HashMap<>();
    requestItems.put("lift_rides", writeRequests);
    BatchWriteItemRequest request = BatchWriteItemRequest.builder()
        .requestItems(requestItems)
        .build();

    BatchWriteItemResponse response = dynamoDb.batchWriteItem(request);
    Map<String, List<WriteRequest>> unprocessed = response.unprocessedItems();

    // 3) Retry unprocessed items until success
    while (unprocessed != null && !unprocessed.isEmpty()) {
      // Build a new request for unprocessed
      BatchWriteItemRequest retryRequest = BatchWriteItemRequest.builder()
          .requestItems(unprocessed)
          .build();
      BatchWriteItemResponse retryResponse = dynamoDb.batchWriteItem(retryRequest);
      unprocessed = retryResponse.unprocessedItems();
    }

    //  ack all these messages on its original channel
    for (QueuedMessage qm : messages) {
      qm.getChannel().basicAck(qm.getDeliveryTag(), false);
    }
  }

  private Map<String, AttributeValue> buildItem(LiftRideEvent event) {
    Map<String, AttributeValue> item = new HashMap<>();
    String seasonDayTime = event.getSeasonID() + "-" + event.getDayID() + "-" + event.getBody().getTime();
    String resortSeasonDayKey = event.getResortID() + "-" + event.getSeasonID() + "-" + event.getDayID();

    item.put("SkierID", AttributeValue.builder().n(String.valueOf(event.getSkierID())).build());
    item.put("SeasonDayTime", AttributeValue.builder().s(seasonDayTime).build());
    item.put("ResortID", AttributeValue.builder().n(String.valueOf(event.getResortID())).build());
    item.put("LiftID", AttributeValue.builder().n(String.valueOf(event.getBody().getLiftID())).build());
    item.put("Time", AttributeValue.builder().n(String.valueOf(event.getBody().getTime())).build());
    item.put("VerticalGain", AttributeValue.builder().n(String.valueOf(event.getBody().getLiftID() * 10)).build());
    item.put("ResortSeasonDayKey", AttributeValue.builder().s(resortSeasonDayKey).build());

    return item;
  }

}