import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DeliverCallback;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import model.LiftRideEvent;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

public class ConsumerWorker implements Runnable {

  private Connection connection;
  private static final Gson GSON = new Gson();

  private static final String QUEUE_NAME = "rpc_queue";
  private Map<Integer, CopyOnWriteArrayList<LiftRideEvent>> records;
  private static AtomicInteger count;
  private static final DynamoDbClient dynamoDb = DynamoDbClient.builder()
      .endpointOverride(java.net.URI.create("http://localhost:8000")) // Change to AWS endpoint when deploying
      .build();

  public ConsumerWorker(Connection connection,
      Map<Integer, CopyOnWriteArrayList<LiftRideEvent>> records, AtomicInteger count) {
    this.connection = connection;
    this.records = records;
    this.count = count;
  }

  @Override
  public void run() {
    try {
      Channel channel = connection.createChannel();
      channel.basicQos(10);

//      System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

      DeliverCallback deliverCallback = (consumerTag, delivery) -> {
        String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
        try {
          LiftRideEvent liftRideEvent = GSON.fromJson(message, LiftRideEvent.class);
          saveToDynamoDB(liftRideEvent);
//          count.incrementAndGet();
//          System.out.println(count.get());

        } catch (JsonSyntaxException e) {
          throw new RuntimeException(e);
        }
//        System.out.println(" Count :" + count.toString() + " [x] Received '" + message + "'");
        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
      };

      channel.basicConsume(QUEUE_NAME, false, deliverCallback, consumerTag -> {
      });
    } catch (IOException e) {
      System.err.println("❌ Failed to connect to RabbitMQ: " + e.getMessage());
      throw new RuntimeException(e);
    }
  }

  private void saveToDynamoDB(LiftRideEvent event) {
    Map<String, AttributeValue> item = new HashMap<>();

    String seasonDayTime = event.getSeasonID() + "-" + event.getDayID() + "-" + event.getBody().getTime();
    String resortDayKey = event.getResortID() + "-" + event.getSeasonID() + "-" + event.getDayID();

    item.put("SkierID", AttributeValue.builder().n(String.valueOf(event.getSkierID())).build());
    item.put("SeasonDayTime", AttributeValue.builder().s(seasonDayTime).build());

    item.put("ResortID", AttributeValue.builder().n(String.valueOf(event.getResortID())).build());
    item.put("LiftID", AttributeValue.builder().n(String.valueOf(event.getBody().getLiftID())).build());
    item.put("Time", AttributeValue.builder().n(String.valueOf(event.getBody().getTime())).build());
    item.put("VerticalGain", AttributeValue.builder().n(String.valueOf(event.getBody().getLiftID() * 10)).build());

    // Required for GSI: ResortDayIndex
    item.put("ResortDayKey", AttributeValue.builder().s(resortDayKey).build());

    PutItemRequest request = PutItemRequest.builder()
        .tableName("lift_rides")
        .item(item)
        .build();

    dynamoDb.putItem(request);
//    System.out.println("📌 Inserted event into DynamoDB: " + event);
  }


}
