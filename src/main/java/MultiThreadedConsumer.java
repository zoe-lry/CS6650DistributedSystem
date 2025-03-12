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

public class MultiThreadedConsumer {
  private static ConnectionFactory factory;

  private static final Integer NUM_WORKS = 1000;
//  private static final String HOST = "localhost";
  private static final String HOST = "172.31.31.186"; //private
//  private static final String HOST = "35.91.180.143"; //public
  private static Map<Integer, CopyOnWriteArrayList<LiftRideEvent>> records;;
  private static AtomicInteger count = new AtomicInteger(0);

  public static void main(String[] args) throws IOException, TimeoutException, InterruptedException {
    records = new ConcurrentHashMap<>();
    factory = new ConnectionFactory();
    factory.setHost(HOST);
    factory.setUsername("admin");
    factory.setPassword("admin");

    Connection connection = factory.newConnection();
    System.out.println("✅ Successfully connected to RabbitMQ");

    ExecutorService executorService = Executors.newFixedThreadPool(NUM_WORKS);
    IntStream.range(0, NUM_WORKS).forEach(i -> {
      executorService.submit(new ConsumerWorker(connection, records, count));
    });
    executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);

  }



}
