package dev.luxoru.tunnelvision.source;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * What a {@link DetectionSource} can use to fetch its data.
 *
 * @param http the HTTP client
 * @param userAgent the user agent to send upstream
 * @param timeout the request timeout
 */
public record SourceContext(HttpClient http, String userAgent, Duration timeout) {
    /**
     * A context with a default HTTP client.
     *
     * @return the context
     */
    public static SourceContext defaults() {
        HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        return new SourceContext(http, "TunnelVision (+https://github.com/Luxoru/Tunnelvision)", Duration.ofSeconds(60));
    }

    /**
     * Fetch the given URL as text.
     *
     * @param url the URL
     * @return the response body
     * @throws IOException if the request fails or the response isn't a 2xx
     * @throws InterruptedException if interrupted
     */
    public String get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(timeout)
            .header("User-Agent", userAgent)
            .GET()
            .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("GET " + url + " returned " + response.statusCode());
        }
        return response.body();
    }
}
