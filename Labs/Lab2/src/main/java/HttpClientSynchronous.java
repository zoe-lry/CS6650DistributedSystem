import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class HttpClientSynchronous {

  private static final HttpClient httpClient = HttpClient.newBuilder()
      .version(HttpClient.Version.HTTP_1_1)
      .connectTimeout(Duration.ofSeconds(10))
      .build();

  public static void main(String[] args) throws IOException, InterruptedException {
    String jsonData = "{\"liftID\":5,\"time\":360,\"waitTime\":0}";  // Example JSON payload

    HttpRequest request = HttpRequest.newBuilder()
        .POST(HttpRequest.BodyPublishers.ofString(jsonData))
        .uri(URI.create("http://54.213.252.136:8080/Lab2/skiers/12/seasons/2019/day/1/skier/123"))
        .setHeader("User-Agent", "Java 11 HttpClient Bot") // add request header
        .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    // print response headers
    HttpHeaders headers = response.headers();
    headers.map().forEach((k, v) -> System.out.println(k + ":" + v));

    // print status code
    System.out.println(response.statusCode());

    // print response body
    System.out.println(response.body());

  }

}
