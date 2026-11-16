package com.giannisdal.shadowig.instagram;

import com.giannisdal.shadowig.config.InstagramMonitoringProperties;
import com.giannisdal.shadowig.config.StoriesIgProperties;
import com.giannisdal.shadowig.exception.InstagramProviderException;
import com.giannisdal.shadowig.model.InstagramContentType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class StoriesIgContentProviderTest {
    private final InstagramMonitoringProperties.AccountConfig account =
            new InstagramMonitoringProperties.AccountConfig("example", true, true, true, false);
    private final InstagramMonitoringProperties monitoring = new InstagramMonitoringProperties(
            true, Duration.ofDays(1), true, Duration.ofSeconds(30), 10, 1, Duration.ZERO, List.of(account));
    private final HttpClient client = mock(HttpClient.class);
    private final StoriesIgContentProvider provider = new StoriesIgContentProvider(
            new StoriesIgProperties("test-token", "https://scraperapi.to/scrape/instagram-scraper"), monitoring, client);
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void parsesStoriesWithStableMediaIdAndRealTimestamp() {
        var items = provider.parse(mapper.readTree("""
                {"data":{"items":[{"pk":"12345_999", "taken_at":1794823200}]}}
                """), InstagramContentType.STORY, "example");
        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo("12345");
            assertThat(item.permalink()).isEqualTo("https://www.instagram.com/stories/example/12345/");
            assertThat(item.publishedAt()).isEqualTo(Instant.ofEpochSecond(1794823200));
        });
    }

    @Test
    void parsesObservedStoriesIgEnvelopeAndCamelCasePublicationTime() {
        var items = provider.parse(mapper.readTree("""
                {"result":{"items":[{"id":"12345", "type":"video", "takenAt":1794823200}],
                "pageInfo":{"hasNextPage":false},"owner":{"username":"example"}}}
                """), InstagramContentType.STORY, "example");
        assertThat(items).singleElement().satisfies(item ->
                assertThat(item.publishedAt()).isEqualTo(Instant.ofEpochSecond(1794823200)));
    }

    @Test
    void parsesPostEdgesAndReelsWithoutInventingDates() {
        var items = provider.parse(mapper.readTree("""
                {"data":{"edges":[{"node":{"id":"123", "shortcode":"Ab_cd", "product_type":"clips"}}]}}
                """), InstagramContentType.POST, "example");
        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.type()).isEqualTo(InstagramContentType.REEL);
            assertThat(item.permalink()).isEqualTo("https://www.instagram.com/reel/Ab_cd/");
            assertThat(item.publishedAt()).isNull();
        });
    }

    @Test
    void distinguishesEmptyStoriesFromUnknownOrErrorResponses() {
        assertThat(provider.parse(mapper.readTree("{\"data\":[]}"), InstagramContentType.STORY, "example")).isEmpty();
        for (String json : List.of("{}", "{\"success\":false,\"items\":[]}", "{\"items\":[{\"url\":\"shell\"}]}")) {
            assertThatThrownBy(() -> provider.parse(mapper.readTree(json), InstagramContentType.STORY, "example"))
                    .isInstanceOf(InstagramProviderException.class);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void stopsAfterQuotaOrAuthenticationFailureWithoutRequestingPosts() throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(429);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        assertThatThrownBy(() -> provider.fetchLatest(account)).hasMessageContaining("429");
        verify(client, times(1)).send(argThat(request -> request.uri().getPath().endsWith("/stories")
                && request.method().equals("POST")
                && request.headers().firstValue("Authorization").orElse("").equals("Bearer test-token")), any());
    }
}
