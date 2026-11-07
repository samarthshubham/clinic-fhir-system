import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class HealthCheck {
  public static void main(String[] args) throws Exception {
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(args[0])).timeout(Duration.ofSeconds(5)).build();
    int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    System.exit(status == 200 ? 0 : 1);
  }
}
