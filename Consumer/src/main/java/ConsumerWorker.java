import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import model.LiftRideEvent;

public class ConsumerWorker implements Runnable {

  private Connection connection;
  private static final Gson GSON = new Gson();

  private static final String QUEUE_NAME = "rpc_queue";
  private Map<Integer, CopyOnWriteArrayList<LiftRideEvent>> records;
  private static AtomicInteger count;


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
      channel.queueDeclare(QUEUE_NAME, false, false, false, null);
      channel.queuePurge(QUEUE_NAME);

      channel.basicQos(1);

//      System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

      DeliverCallback deliverCallback = (consumerTag, delivery) -> {
        String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
        try {
          LiftRideEvent liftRideEvent = GSON.fromJson(message, LiftRideEvent.class);
          Integer skierID = liftRideEvent.getSkierID();
          records.computeIfAbsent(skierID, k -> new CopyOnWriteArrayList<>()).add(liftRideEvent);
          count.incrementAndGet();


//          Thread.sleep(10);
//        } catch (InterruptedException e) {
//          throw new RuntimeException(e);
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

}
