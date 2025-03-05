import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public class Test {
  public static void main(String[] args) throws Exception {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("35.91.180.143");
    factory.setUsername("admin");
    factory.setPassword("admin");

    try (Connection conn = factory.newConnection()) {
      System.out.println("✅ Successfully connected to RabbitMQ as admin!");
      Thread.sleep(20000); // Keep the connection open 20 seconds
    }
  }

}
