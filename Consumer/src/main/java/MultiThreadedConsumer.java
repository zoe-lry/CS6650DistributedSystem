import com.google.gson.Gson;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import model.LiftRideEvent;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class MultiThreadedConsumer {

  private static ConnectionFactory factory;

  private static final Integer NUM_WORKS = 50;
//  private static final String HOST = "localhost";
    private static final String HOST = "172.31.31.186"; //private
  //  private static final String HOST = "35.91.180.143"; //public
  private static final String QUEUE_NAME = "rpc_queue";

  // Number of threads actually writing to DynamoDB in parallel
  private static final int NUM_FLUSH_THREADS = 30;
  ;

  public static void main(String[] args)
      throws IOException, TimeoutException, InterruptedException {

//    DynamoDbClient dynamoDb = DynamoDbClient.builder()
//        .endpointOverride(java.net.URI.create("http://localhost:8000"))
//        .build();

    DynamoDbClient dynamoDb = DynamoDbClient.builder()
        .region(Region.US_WEST_2)
        .build();

    // 2) Create a shared batch writer that flushes to DynamoDB using multiple threads
    DynamoDBBatchWriter batchWriter = new DynamoDBBatchWriter(dynamoDb, NUM_FLUSH_THREADS);

    factory = new ConnectionFactory();
    factory.setHost(HOST);
    factory.setUsername("admin");
    factory.setPassword("admin");

    Connection connection = factory.newConnection();
    Channel channel = connection.createChannel();
    channel.queueDeclare(QUEUE_NAME, false, false, false, null);
    channel.queuePurge(QUEUE_NAME);

    System.out.println("✅ Successfully connected to RabbitMQ");

    ExecutorService executorService = Executors.newFixedThreadPool(NUM_WORKS);
    IntStream.range(0, NUM_WORKS).forEach(i -> {
      executorService.submit(new ConsumerWorker(connection, batchWriter));
    });
    executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);

  }


}
