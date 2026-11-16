package com.giannisdal.shadowig.instagram;

import com.giannisdal.shadowig.config.InstagramMonitoringProperties;
import com.giannisdal.shadowig.config.StoriesIgProperties;
import com.giannisdal.shadowig.exception.InstagramProviderException;
import com.giannisdal.shadowig.model.InstagramContentItem;
import com.giannisdal.shadowig.model.InstagramContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Uses the documented StoriesIG REST API; never falls back to scraping or retries billed calls. */
@Component
@ConditionalOnProperty(name = "instagram.monitoring.provider", havingValue = "storiesig", matchIfMissing = true)
public class StoriesIgContentProvider implements InstagramContentProvider {
    private static final Logger log = LoggerFactory.getLogger(StoriesIgContentProvider.class);
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._]{1,30}");
    private final StoriesIgProperties api;
    private final InstagramMonitoringProperties monitoring;
    private final HttpClient client;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private long lastRequestNanos;

    @Autowired
    public StoriesIgContentProvider(StoriesIgProperties api, InstagramMonitoringProperties monitoring) {
        this(api, monitoring, HttpClient.newBuilder().connectTimeout(monitoring.requestTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }

    StoriesIgContentProvider(StoriesIgProperties api, InstagramMonitoringProperties monitoring, HttpClient client) {
        this.api = api;
        this.monitoring = monitoring;
        this.client = client;
    }

    @Override
    public synchronized InstagramProviderResult fetchLatest(InstagramMonitoringProperties.AccountConfig account) {
        if (!USERNAME.matcher(account.username()).matches()) {
            throw new InstagramProviderException("Invalid Instagram username");
        }
        if (api.apiKey() == null || api.apiKey().isBlank()) {
            throw new InstagramProviderException("STORIESIG_API_KEY is missing");
        }
        var items = new LinkedHashMap<String, InstagramContentItem>();
        var unavailable = EnumSet.noneOf(InstagramContentType.class);
        var endpoints = new LinkedHashMap<String, InstagramContentType>();
        if (account.stories()) endpoints.put("stories", InstagramContentType.STORY);
        if (account.posts()) endpoints.put("posts", InstagramContentType.POST);
        if (account.reels()) endpoints.put("reels", InstagramContentType.REEL);
        int successes = 0;
        for (var endpoint : endpoints.entrySet()) {
            try {
                var result = request(endpoint.getKey(), account.username());
                for (var item : parse(result, endpoint.getValue(), account.username())) {
                    items.putIfAbsent(item.type() + ":" + item.id(), item);
                }
                successes++;
            } catch (InstagramProviderException exception) {
                unavailable.add(endpoint.getValue());
                log.warn("StoriesIG {} unavailable for @{}: {}", endpoint.getKey(), account.username(), exception.getMessage());
                // Stop the cycle on auth, rate limit, or quota errors. No further billed requests.
                if (exception instanceof ApiAccessException) {
                    throw exception;
                }
            }
        }
        if (!endpoints.isEmpty() && successes == 0) {
            throw new InstagramProviderException("StoriesIG returned no successful content responses for @" + account.username());
        }
        log.info("StoriesIG returned {} items for @{}; unavailable types: {}", items.size(), account.username(), unavailable);
        return new InstagramProviderResult(List.copyOf(items.values()), unavailable);
    }

    private JsonNode request(String endpoint, String username) {
        try {
            // Free tier permits two requests/second, including across monitored accounts.
            long waitNanos = 550_000_000L - (System.nanoTime() - lastRequestNanos);
            if (lastRequestNanos != 0 && waitNanos > 0) {
                Thread.sleep(java.time.Duration.ofNanos(waitNanos));
            }
            var request = HttpRequest.newBuilder(URI.create(api.baseUrl() + "/" + endpoint))
                    .timeout(monitoring.requestTimeout())
                    .header("Authorization", "Bearer " + api.apiKey())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(java.util.Map.of("username", username))))
                    .build();
            lastRequestNanos = System.nanoTime();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (Set.of(401, 402, 403, 429).contains(status)) {
                throw new ApiAccessException("HTTP " + status + "; check API key, quota or rate limit; no immediate retry");
            }
            if (status < 200 || status >= 300) {
                throw new InstagramProviderException("HTTP " + status);
            }
            return mapper.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiAccessException("StoriesIG request interrupted");
        } catch (InstagramProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            // Do not log response bodies, request headers or credentials.
            throw new InstagramProviderException("Request failed (" + exception.getClass().getSimpleName() + ")");
        }
    }

    List<InstagramContentItem> parse(JsonNode response, InstagramContentType type, String username) {
        JsonNode collection = collection(response, type, 0);
        var result = new LinkedHashMap<String, InstagramContentItem>();
        for (var entry : collection) {
            var node = entry.has("node") ? entry.get("node") : entry;
            String id = text(node, "pk", "id");
            if (id != null) id = id.split("_")[0];
            String code = text(node, "code", "shortcode");
            if (id == null || !id.matches("[0-9]+")) {
                throw new InstagramProviderException("Unsupported StoriesIG item: numeric media id missing");
            }
            InstagramContentType actualType = type;
            if (type == InstagramContentType.POST && ("clips".equals(text(node, "product_type"))
                    || "reel".equalsIgnoreCase(text(node, "type")))) {
                actualType = InstagramContentType.REEL;
            }
            String link;
            if (type == InstagramContentType.STORY) {
                link = "https://www.instagram.com/stories/" + username + "/" + id + "/";
            } else {
                if (code == null || !code.matches("[A-Za-z0-9_-]+")) {
                    throw new InstagramProviderException("Unsupported StoriesIG post: shortcode missing");
                }
                link = "https://www.instagram.com/" + (actualType == InstagramContentType.REEL ? "reel/" : "p/") + code + "/";
            }
            var published = timestamp(node);
            result.putIfAbsent(actualType + ":" + id, new InstagramContentItem(id, actualType, link, published));
        }
        // One page/request per type; never make automatic pagination calls against the free quota.
        return result.values().stream().limit(monitoring.maxItemsPerAccount()).toList();
    }

    private JsonNode collection(JsonNode node, InstagramContentType type, int depth) {
        if (node == null || depth > 6 || node.isNull()) {
            throw new InstagramProviderException("Unsupported StoriesIG response shape");
        }
        if (node.isArray()) return node;
        if (node.has("error") && !node.get("error").isNull()
                || node.has("success") && !node.get("success").asBoolean(true)
                || "error".equals(text(node, "status"))) {
            throw new InstagramProviderException("StoriesIG reported an API error");
        }
        // API response envelopes vary; unknown structures fail visibly instead of meaning 'no stories'.
        String[] fields = {type == InstagramContentType.STORY ? "stories" : "posts", "items", "edges",
                "data", "result", "response", "user", "edge_owner_to_timeline_media"};
        for (String field : fields) {
            if (node.has(field) && !node.get(field).isNull()) return collection(node.get(field), type, depth + 1);
        }
        throw new InstagramProviderException("Unsupported StoriesIG response shape");
    }

    private String text(JsonNode node, String... fields) {
        for (String field : fields) {
            if (node.has(field) && !node.get(field).isNull()) {
                String value = node.get(field).asString();
                if (!value.isBlank()) return value;
            }
        }
        return null;
    }

    private Instant timestamp(JsonNode node) {
        String value = text(node, "takenAt", "taken_at", "taken_at_timestamp", "timestamp", "published_at");
        if (value == null) return null;
        try {
            if (value.matches("[0-9]+")) {
                long number = Long.parseLong(value);
                return number > 10_000_000_000L ? Instant.ofEpochMilli(number) : Instant.ofEpochSecond(number);
            }
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            throw new InstagramProviderException("Invalid StoriesIG publication timestamp");
        }
    }

    private static class ApiAccessException extends InstagramProviderException {
        ApiAccessException(String message) { super(message); }
    }
}
