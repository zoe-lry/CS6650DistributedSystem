import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class Test {
  public static void main(String[] args) {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("35.91.180.143");
    factory.setUsername("admin");
    factory.setPassword("admin");

    try (Connection connection = factory.newConnection()) {
      System.out.println("✅ Successfully connected to RabbitMQ!");
    } catch (IOException | TimeoutException e) {
      System.err.println("❌ Failed to connect to RabbitMQ: " + e.getMessage());
    }
  }

}
