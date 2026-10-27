import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Checks the image's JVM trust store: the eMudhra test hosts (signed with the compromised intermediates) must be
 * refused, and a normal site must still connect. Exits 1 if either is wrong. Run by CI against the built image.
 */
public class TrustCheck {
    public static void main(String[] args) throws Exception {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        boolean ok = true;
        for (String url : new String[] {
            "https://dv-g3b-emudhra.we-have-a-problem.com/",
            "https://dv-g3b-aaa-emudhra.we-have-a-problem.com/",
        }) {
            try {
                int code = http.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.discarding()).statusCode();
                System.out.println("VULNERABLE " + url + " answered " + code);
                ok = false;
            } catch (javax.net.ssl.SSLException e) {
                System.out.println("OK " + url + " refused: " + e.getMessage());
            }
        }
        int google = http.send(HttpRequest.newBuilder(URI.create("https://www.google.com/")).build(), HttpResponse.BodyHandlers.discarding()).statusCode();
        System.out.println("google.com answered " + google);
        if (google >= 400) ok = false;
        System.exit(ok ? 0 : 1);
    }
}
