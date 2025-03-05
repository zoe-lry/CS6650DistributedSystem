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
import com.rabbitmq.client.AMQP;
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
//  private static final String HOST = "172.31.31.186";
  private static final String HOST = "172.31.31.186";
//  private static final String HOST = "localhost";
  // Number of channels to add to pools
  private static final int NUM_CHANS = 500;
  private static final int ON_DEMAND = -1;
  private static final int WAIT_TIME_SECS = 5;
  private ConnectionFactory factory;
  private GenericObjectPool<Channel> pool;
  private Connection connection;

  public void init() {
    this.factory = new ConnectionFactory();
    factory.setHost(HOST);
    factory.setUsername("admin");
    factory.setPassword("admin");
    try {
      System.out.println("✅ connecting to RabbitMQ as admin!");
      connection = factory.newConnection();
      System.out.println("✅ Successfully connected to RabbitMQ as admin!");
    } catch (IOException | TimeoutException e) {
      throw new RuntimeException(e);
    }
    // we use this object to tailor the behavior of the GenericObjectPool
    GenericObjectPoolConfig config = new GenericObjectPoolConfig();
    // The code as is allows the cahhnel pool to grow to meet demand.
    // Change to config.setMaxTotal(NUM_CHANS) to limit the pool size
    config.setMaxTotal(NUM_CHANS);
    config.setMaxIdle(NUM_CHANS);
//    config.setMinIdle(NUM_CHANS);
    // clients will block when pool is exhausted, for a maximum duration of WAIT_TIME_SECS
    config.setBlockWhenExhausted(true);
    // tune WAIT_TIME_SECS to meet your workload/demand
    config.setMaxWait(Duration.ofSeconds(WAIT_TIME_SECS));

    // The channel facory generates new channels on demand, as needed by the GenericObjectPool
    RMQChannelFactory chanFactory = new RMQChannelFactory (connection);
    //create the pool
    pool = new GenericObjectPool<>(chanFactory, config);
    try {
      Channel initChannel = connection.createChannel();
      initChannel.queueDeclare(QUEUE_NAME, false, false, false, null);
      initChannel.close();
    } catch (Exception e) {
      System.err.println("Failed to declare queue during initialization: " + e.getMessage());
    }
  }


  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    res.setContentType("text/plain");
    LiftRideEvent liftRideEvent;
    try {
      liftRideEvent = parseLiftRideEvent(req);
    } catch (InvalidEventException e) {
      // If parsing/validation fails, return 404 or 400 depending on your assignment specs
      res.setStatus(HttpServletResponse.SC_NOT_FOUND);
      res.getWriter().write("Invalid event: " + e.getMessage());
      return;
    }

    Channel channel = null;
    try {
      channel = pool.borrowObject();
      String eventJson = GSON.toJson(liftRideEvent);
      channel.basicPublish("", QUEUE_NAME, null, eventJson.getBytes(StandardCharsets.UTF_8));
      res.setStatus(HttpServletResponse.SC_CREATED);
      res.getWriter().write("It works!");

    } catch (Exception e) {
      System.out.println(e.getMessage());
      res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      res.getWriter().write("Failed to publish to RabbitMQ");
    } finally {
      // 4) Return the channel to the pool (if it was successfully borrowed)
      if (channel != null) {
        try {
          pool.returnObject(channel);
        } catch (Exception e) {
          // Log an error if returning object to pool fails
          System.err.println("Error returning channel to pool: " + e.getMessage());
        }
      }
    }
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
        e.printStackTrace();
      }
    }
  }
  private LiftRideEvent parseLiftRideEvent(HttpServletRequest req)
      throws IOException, InvalidEventException {
    String urlPath = req.getPathInfo();
    BufferedReader body = req.getReader();
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

      // Validate numeric constraints for the LiftRide
      if (liftRide.getTime() == null || liftRide.getTime() < 1 || liftRide.getTime() > 360) {
        throw new InvalidEventException("time must be between 1 and 360");
      }
      if (liftRide.getLiftID() == null || liftRide.getLiftID() < 1 || liftRide.getLiftID() > 40) {
        throw new InvalidEventException("liftID must be between 1 and 40");
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
        return GSON.fromJson(sb.toString(), LiftRide.class);
      } catch (IOException | JsonSyntaxException e) {
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

  private LiftRideEvent isUrlValid(String urlPath) {
    // TODO: validate the request url path according to the API spec
    // urlPath  = "/1/seasons/2019/day/1/skier/123"
    // urlParts = [, 1, seasons, 2019, day, 1, skier, 123]
//    System.out.println("urlPaths: " + Arrays.toString(urlPaths));
    String[] urlParts = urlPath.split("/");

    if (urlParts.length != 8 || !urlParts[2].equals("seasons") || !urlParts[4].equals("days")
        || !urlParts[6].equals("skiers")) {
      return null;
    }
    try {
      // Extracting and validating numeric parameters
      int resortID = Integer.parseInt(urlParts[1]);
      int seasonID = Integer.parseInt(urlParts[3]);
      int dayID = Integer.parseInt(urlParts[5]);
      int skierID = Integer.parseInt(urlParts[7]);
      // Validating against constraints
      if (resortID >= 1 && resortID <= 10 && seasonID == 2025 && dayID == 1 && skierID >= 1
          && skierID <= 100000) {
        return new LiftRideEvent(
            resortID,
            String.valueOf(seasonID),
            String.valueOf(dayID),
            skierID);
      }
    } catch (NumberFormatException e) {
//      return null;  // Invalid number format in one of the fields
    }
    return null;
  }


  private boolean isBodyValid(BufferedReader buffIn) {
    try {
      StringBuilder sb = new StringBuilder();
      String s;
      while ((s = buffIn.readLine()) != null) {
        sb.append(s);
      }
      LiftRide liftRide = GSON.fromJson(sb.toString(), LiftRide.class);
      if (liftRide.getTime() < 1 || liftRide.getTime() > 360) {
        return false;
      }
      if (liftRide.getLiftID() < 1 || liftRide.getLiftID() > 40) {
        return false;
      }

//      System.out.println("Body: " + sb);
      return true;
    } catch (IOException | JsonSyntaxException e) {
      throw new RuntimeException(e);
    }
  }
}