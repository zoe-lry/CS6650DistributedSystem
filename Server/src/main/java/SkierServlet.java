import com.google.gson.JsonSyntaxException;
import exceptions.InvalidEventException;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.google.gson.Gson;
import model.LiftRide;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import model.LiftRideEvent;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import rmqpool.RMQChannelFactory;

@WebServlet(name = "SkierServlet", value = "/SkierServlet", loadOnStartup = 1)
public class SkierServlet extends HttpServlet {

  private static final Gson GSON = new Gson();
  private static final String QUEUE_NAME = "rpc_queue";
//  private static final String HOST = "172.31.31.xxx";
  private static final String HOST = "localhost";
  private static final int NUM_CHANS = 500;   // Number of channels to add to pools
  private static final int WAIT_TIME_SECS = 5;
  private GenericObjectPool<Channel> pool;
  private Connection connection;

  @Override
  public void init() throws ServletException {
    super.init();
    // Setup RabbitMQ connection
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost(HOST);
    factory.setUsername("admin");
    factory.setPassword("admin");
    try {
      connection = factory.newConnection();
      System.out.println("✅ Successfully connected to RabbitMQ as admin!");
    } catch (IOException | TimeoutException e) {
      throw new ServletException("Failed to create RabbitMQ connection", e);
    }
    //create the pool
    pool = generateChannelPool();
    declareQueue();
  }

  /**
   * Declare queue only one time
   */
  private void declareQueue() {
    try (Channel initChannel = connection.createChannel()) {
      initChannel.queueDeclare(QUEUE_NAME, false, false, false, null);
    } catch (Exception e) {
      System.err.println("Failed to declare queue during initialization: " + e.getMessage());
    }
  }

  /**
   * Create Channel Pool
   * @return
   */
  private GenericObjectPool<Channel> generateChannelPool() {
    // Config the Channel Pool
    GenericObjectPoolConfig<Channel> config = new GenericObjectPoolConfig<>();
    config.setMaxTotal(NUM_CHANS);
    config.setMaxIdle(NUM_CHANS);
    // config.setMinIdle(NUM_CHANS);
    config.setBlockWhenExhausted(true); // clients will block when pool is exhausted, for a maximum duration of WAIT_TIME_SECS
    config.setMaxWait(Duration.ofSeconds(WAIT_TIME_SECS));  // tune WAIT_TIME_SECS to meet your workload/demand
    // The channel facory generates new channels on demand, as needed by the GenericObjectPool
    RMQChannelFactory chanFactory = new RMQChannelFactory(connection);
    return new GenericObjectPool<Channel>(chanFactory, config);
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse res)
      throws IOException {
//    res.setContentType("text/plain");
    LiftRideEvent liftRideEvent;
    // Validate the request
    try {
      liftRideEvent = parseLiftRideEvent(req);
    } catch (InvalidEventException e) {
      // If parsing/validation fails, return 404 or 400 depending on your assignment specs
      res.setStatus(HttpServletResponse.SC_NOT_FOUND);
      res.getWriter().write("Invalid event: " + e.getMessage());
      return;
    }
    // Send to the Rabbit MQ
    try {
      sendToRabbitMQ(liftRideEvent);
      res.setStatus(HttpServletResponse.SC_CREATED);
    } catch (Exception e) {
      System.err.println("Error publishing to RabbitMQ: " + e.getMessage());
      res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      res.getWriter().write("Failed to publish to RabbitMQ");
    }

  }

  private void sendToRabbitMQ(LiftRideEvent liftRideEvent) throws Exception {
    Channel channel = pool.borrowObject();
    channel.basicPublish("", QUEUE_NAME, null,
        GSON.toJson(liftRideEvent).getBytes(StandardCharsets.UTF_8));
    pool.returnObject(channel);
  }

  @Override
  public void destroy() {
    super.destroy();
    if (pool != null) {
      pool.close();  // This closes all idle channels in the pool
    }
    if (connection != null && connection.isOpen()) {
      try {
        connection.close();
      } catch (IOException e) {
        System.err.println("Error closing RabbitMQ connection: " + e.getMessage());
      }
    }
  }

  /**
   * Parses the LiftRideEvent from the request URL and JSON body.
   * Expected URL pattern: /{resortID}/seasons/{seasonID}/days/{dayID}/skiers/{skierID}
   */
  private LiftRideEvent parseLiftRideEvent(HttpServletRequest req)
      throws IOException, InvalidEventException {
    String urlPath = req.getPathInfo();
    // check we have a URL!
    if (urlPath == null || urlPath.isEmpty()) {
      throw new InvalidEventException("Missing or empty URL");
    }
    String[] parts = urlPath.split("/");
    // Expected: /{resortID}/seasons/{seasonID}/days/{dayID}/skiers/{skierID}
    // parts = ["", "1", "seasons", "2025", "days", "1", "skiers", "123"]
    if (parts.length != 8 ||
        !"seasons".equals(parts[2]) ||
        !"days".equals(parts[4]) ||
        !"skiers".equals(parts[6])) {
      throw new InvalidEventException("URL path format is incorrect");
    }

    // Parse numeric fields
    int resortId, seasonId, dayId, skierId;
    try {
      resortId = Integer.parseInt(parts[1]);
      seasonId = Integer.parseInt(parts[3]);
      dayId = Integer.parseInt(parts[5]);
      skierId = Integer.parseInt(parts[7]);
    } catch (NumberFormatException e) {
      throw new InvalidEventException("One of the URL path fields is not a valid integer");
    }

    // Basic constraints
    if (resortId < 1 || resortId > 10) {
      throw new InvalidEventException("resortId out of range");
    }
    if (seasonId != 2025) {
      throw new InvalidEventException("seasonId must be 2025");
    }
    if (dayId < 1 || dayId > 366) {
      throw new InvalidEventException("dayId out of range");
    }
    if (skierId < 1 || skierId > 100000) {
      throw new InvalidEventException("skierId out of range");
    }

    // 2) Parse JSON body into LiftRide
    LiftRide liftRide = parseLiftRideBody(req);
    if (liftRide == null) {
      throw new InvalidEventException("Invalid or missing JSON body");
    }

    // 3) If everything is valid, create and return a LiftRideEvent
    return new LiftRideEvent(liftRide, resortId, String.valueOf(seasonId),
        String.valueOf(dayId), skierId);
  }


  /**
     * Reads the JSON from the request and attempts to parse a LiftRide object.
     * Returns null if parsing fails.
   */
  private LiftRide parseLiftRideBody(HttpServletRequest req) {
    try (BufferedReader reader = req.getReader()) {
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line);
      }
      LiftRide liftRide = GSON.fromJson(sb.toString(), LiftRide.class);
      // Validate numeric constraints for the LiftRide
      if (liftRide.getTime() == null || liftRide.getTime() < 1 || liftRide.getTime() > 360) {
        throw new InvalidEventException("time must be between 1 and 360");
      }
      if (liftRide.getLiftID() == null || liftRide.getLiftID() < 1 || liftRide.getLiftID() > 40) {
        throw new InvalidEventException("liftID must be between 1 and 40");
      }
      return liftRide;
    } catch (IOException | JsonSyntaxException | InvalidEventException e) {
      return null;
    }
  }

//  @Override
//  protected void doGet(HttpServletRequest req, HttpServletResponse res)
//      throws ServletException, IOException {
//    res.setContentType("text/plain");
//    String urlPath = req.getPathInfo();
//
//    // check we have a URL!
//    if (urlPath == null || urlPath.isEmpty()) {
//      res.setStatus(HttpServletResponse.SC_NOT_FOUND);
//      res.getWriter().write("missing parameters");
//      return;
//    }
//
//    String[] urlParts = urlPath.split("/");
//    // and now validate url path and return the response status code
//    // (and maybe also some value if input is valid)
//
//    if (isUrlValid(urlPath)) {
//      res.setStatus(HttpServletResponse.SC_OK);
//      // do any sophisticated processing with urlParts which contains all the url params
//      // TODO: process url params in `urlParts`
//      res.getWriter().write("It works!");
//    } else {
//      res.setStatus(HttpServletResponse.SC_NOT_FOUND);
//    }
//  }
//

}