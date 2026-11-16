package com.giannisdal.shadowig.instagram;

import com.giannisdal.shadowig.config.InstagramMonitoringProperties;
import com.giannisdal.shadowig.config.InstagramMonitoringProperties.AccountConfig;
import com.giannisdal.shadowig.model.InstagramContentType;
import com.microsoft.playwright.*;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlaywrightInstagramContentProviderTest {
    @Test
    void inaccessibleStoriesNeverBecomeDetectionsAndPostsStillWork() {
        try (var staticPlaywright = mockStatic(Playwright.class)) {
            Playwright playwright = mock(Playwright.class);
            BrowserType chromium = mock(BrowserType.class);
            Browser browser = mock(Browser.class);
            BrowserContext context = mock(BrowserContext.class);
            Page page = mock(Page.class);
            staticPlaywright.when(() -> Playwright.create(any(Playwright.CreateOptions.class))).thenReturn(playwright);
            when(playwright.chromium()).thenReturn(chromium);
            when(chromium.launch(any())).thenReturn(browser);
            when(browser.newContext()).thenReturn(context);
            when(context.newPage()).thenReturn(page);
            when(page.getByRole(any(), any())).thenReturn(mock(Locator.class));
            when(page.evaluate(contains("const text"))).thenReturn("Authentication or challenge required", "");
            when(page.evaluate(contains("Array.from"))).thenReturn(List.of(Map.of("url", "/p/BA/", "time", "")));
            doAnswer(invocation -> {
                assertThat(invocation.<BooleanSupplier>getArgument(0).getAsBoolean()).isTrue();
                return null;
            }).when(page).waitForCondition(any(), any());
            var account = new AccountConfig("example", true, true, true, false);
            var properties = new InstagramMonitoringProperties(true, Duration.ofSeconds(60), false,
                    Duration.ofSeconds(1), 10, 3, Duration.ofSeconds(1), List.of(account));

            var result = new PlaywrightInstagramContentProvider(properties, Clock.systemUTC()).fetchLatest(account);

            assertThat(result.unsupportedTypes()).containsExactly(InstagramContentType.STORY);
            assertThat(result.items()).singleElement().satisfies(item -> assertThat(item.type()).isEqualTo(InstagramContentType.POST));
            InOrder order = inOrder(page);
            order.verify(page).navigate(eq("https://www.instagram.com/stories/example/"), any());
            order.verify(page).navigate(eq("https://www.instagram.com/example/"), any());
            verify(context).close();
            verify(browser).close();
            verify(playwright).close();
        }
    }

    @Test
    void retainsRealStoryIdsWhenObservationTimesOut() {
        try (var staticPlaywright = mockStatic(Playwright.class)) {
            Playwright playwright = mock(Playwright.class);
            BrowserType chromium = mock(BrowserType.class);
            Browser browser = mock(Browser.class);
            BrowserContext context = mock(BrowserContext.class);
            Page page = mock(Page.class);
            staticPlaywright.when(() -> Playwright.create(any(Playwright.CreateOptions.class))).thenReturn(playwright);
            when(playwright.chromium()).thenReturn(chromium);
            when(chromium.launch(any())).thenReturn(browser);
            when(browser.newContext()).thenReturn(context);
            when(context.newPage()).thenReturn(page);
            when(page.getByRole(any(), any())).thenReturn(mock(Locator.class));
            when(page.evaluate(contains("const text"))).thenReturn("");
            when(page.evaluate(contains("Array.from"))).thenReturn(List.of());
            when(page.url()).thenReturn("https://www.instagram.com/stories/example/123456789/");
            doAnswer(invocation -> {
                assertThat(invocation.<BooleanSupplier>getArgument(0).getAsBoolean()).isFalse();
                throw new TimeoutError("Observation deadline");
            }).when(page).waitForCondition(any(), any());
            var account = new AccountConfig("example", true, false, true, false);
            var properties = new InstagramMonitoringProperties(true, Duration.ofSeconds(60), false,
                    Duration.ofSeconds(1), 10, 3, Duration.ofSeconds(1), List.of(account));

            var result = new PlaywrightInstagramContentProvider(properties, Clock.systemUTC()).fetchLatest(account);

            assertThat(result.items()).singleElement().satisfies(item -> {
                assertThat(item.type()).isEqualTo(InstagramContentType.STORY);
                assertThat(item.id()).isEqualTo("123456789");
                assertThat(item.publishedAt()).isNull();
            });
            assertThat(result.unsupportedTypes()).containsExactly(InstagramContentType.STORY);
        }
    }

    @Test
    void readsReelsTabWhenProfileGridOnlyExposesPosts() {
        try (var staticPlaywright = mockStatic(Playwright.class)) {
            Playwright playwright = mock(Playwright.class);
            BrowserType chromium = mock(BrowserType.class);
            Browser browser = mock(Browser.class);
            BrowserContext context = mock(BrowserContext.class);
            Page page = mock(Page.class);
            staticPlaywright.when(() -> Playwright.create(any(Playwright.CreateOptions.class))).thenReturn(playwright);
            when(playwright.chromium()).thenReturn(chromium);
            when(chromium.launch(any())).thenReturn(browser);
            when(browser.newContext()).thenReturn(context);
            when(context.newPage()).thenReturn(page);
            when(page.getByRole(any(), any())).thenReturn(mock(Locator.class));
            when(page.evaluate(contains("const text"))).thenReturn("");
            when(page.evaluate(contains("Array.from")))
                    .thenReturn(List.of(Map.of("url", "/p/BA/", "time", "")))
                    .thenReturn(List.of(Map.of("url", "/reel/BB/", "time", "")));
            doAnswer(invocation -> {
                assertThat(invocation.<BooleanSupplier>getArgument(0).getAsBoolean()).isTrue();
                return null;
            }).when(page).waitForCondition(any(), any());
            var account = new AccountConfig("example", true, true, false, true);
            var properties = new InstagramMonitoringProperties(true, Duration.ofSeconds(60), false,
                    Duration.ofSeconds(1), 1, 3, Duration.ofSeconds(1), List.of(account));

            var result = new PlaywrightInstagramContentProvider(properties, Clock.systemUTC()).fetchLatest(account);

            assertThat(result.items()).extracting(item -> item.type())
                    .containsExactly(InstagramContentType.POST, InstagramContentType.REEL);
            assertThat(result.unsupportedTypes()).isEmpty();
            verify(page).navigate(eq("https://www.instagram.com/example/reels/"), any());
        }
    }

    @Test
    void rateLimitStopsFurtherRequestsAndSubsequentAccounts() {
        try (var staticPlaywright = mockStatic(Playwright.class)) {
            Playwright playwright = mock(Playwright.class);
            BrowserType chromium = mock(BrowserType.class);
            Browser browser = mock(Browser.class);
            BrowserContext context = mock(BrowserContext.class);
            Page page = mock(Page.class);
            Response response = mock(Response.class);
            staticPlaywright.when(() -> Playwright.create(any(Playwright.CreateOptions.class))).thenReturn(playwright);
            when(playwright.chromium()).thenReturn(chromium);
            when(chromium.launch(any())).thenReturn(browser);
            when(browser.newContext()).thenReturn(context);
            when(context.newPage()).thenReturn(page);
            when(response.url()).thenReturn("https://www.instagram.com/stories/example/");
            when(response.status()).thenReturn(429);
            when(response.headerValue("retry-after")).thenReturn("120");
            var responseHandler = new java.util.concurrent.atomic.AtomicReference<java.util.function.Consumer<Response>>();
            doAnswer(invocation -> {
                responseHandler.set(invocation.getArgument(0));
                return null;
            }).when(page).onResponse(any());
            when(page.navigate(anyString(), any())).thenAnswer(invocation -> {
                responseHandler.get().accept(response);
                return response;
            });
            var account = new AccountConfig("example", true, true, true, true);
            var properties = new InstagramMonitoringProperties(true, Duration.ofSeconds(60), false,
                    Duration.ofSeconds(1), 10, 3, Duration.ofSeconds(1), List.of(account));
            var provider = new PlaywrightInstagramContentProvider(properties, Clock.systemUTC());

            assertThat(provider.fetchLatest(account).items()).isEmpty();
            assertThat(provider.fetchLatest(account).items()).isEmpty();
            verify(page, times(1)).navigate(anyString(), any());
            staticPlaywright.verify(() -> Playwright.create(any(Playwright.CreateOptions.class)), times(1));
        }
    }
}
