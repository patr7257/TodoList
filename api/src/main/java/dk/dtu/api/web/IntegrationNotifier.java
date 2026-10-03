package dk.dtu.api.web;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dk.dtu.api.ApiConfig;
import dk.dtu.api.domain.IntegrationService;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tells the website that an integration sync changed something, so it can
 * notify people. Fire and forget: the call is {@code sendAsync} with a 3 second
 * connect and request timeout, so a down or slow target can neither fail nor
 * delay the upsert response. Only the outcome status is logged, never the
 * secret or the body.
 *
 * <p>Skipped silently when {@code TODO_NOTIFY_URL} or {@code TODO_NOTIFY_SECRET}
 * is unset or blank, and on a no-op sync (created + updated + closed == 0).
 * Callers must invoke it only after the sync transaction has committed.
 */
public final class IntegrationNotifier {

    static final Duration TIMEOUT = Duration.ofSeconds(3);
    static final int MAX_CREATED_ITEMS = 20;
    static final String EVENT = "integration.items.changed";

    private static final Logger log = LoggerFactory.getLogger(IntegrationNotifier.class);
    private static final Gson GSON = new Gson();

    private final String url;
    private final String secret;
    private final HttpClient client;

    public IntegrationNotifier(ApiConfig config) {
        this.url = config == null ? null : config.notifyUrl();
        this.secret = config == null ? null : config.notifySecret();
        this.client = url == null || secret == null
                ? null
                : HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    public void changed(String source, String listName, IntegrationService.SyncResult r) {
        if (client == null || r.created() + r.updated() + r.closed() == 0) {
            return;
        }
        try {
            List<Map<String, String>> created = new ArrayList<>();
            for (IntegrationService.CreatedItem i : r.createdItems()) {
                if (created.size() == MAX_CREATED_ITEMS) {
                    break;
                }
                Map<String, String> item = new java.util.LinkedHashMap<>();
                item.put("id", i.id());
                item.put("text", i.text());
                created.add(item);
            }
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("event", EVENT);
            body.put("source", source);
            body.put("listId", r.listId());
            body.put("listName", listName);
            body.put("created", r.created());
            body.put("updated", r.updated());
            body.put("closed", r.closed());
            body.put("createdItems", created);

            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + secret)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                    .build();
            client.sendAsync(req, HttpResponse.BodyHandlers.discarding())
                    .whenComplete((res, err) -> {
                        if (err != null) {
                            log.warn("Integration notify failed: {}", err.getClass().getSimpleName());
                        } else {
                            log.info("Integration notify answered {}", res.statusCode());
                        }
                    });
        } catch (RuntimeException e) {
            log.warn("Integration notify not sent: {}", e.getClass().getSimpleName());
        }
    }
}
