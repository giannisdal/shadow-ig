package com.giannisdal.shadowig.instagram;

import com.giannisdal.shadowig.config.InstagramMonitoringProperties;
import com.giannisdal.shadowig.config.InstagramMonitoringProperties.AccountConfig;
import com.giannisdal.shadowig.exception.InstagramProviderException;
import com.giannisdal.shadowig.model.InstagramContentItem;
import com.giannisdal.shadowig.model.InstagramContentType;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;
import com.microsoft.playwright.options.AriaRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "instagram.monitoring.provider", havingValue = "browser")
public class PlaywrightInstagramContentProvider implements InstagramContentProvider {
    private static final Logger log = LoggerFactory.getLogger(PlaywrightInstagramContentProvider.class);
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._]{1,30}");
    private static final String LINKS = """
            () => Array.from(document.querySelectorAll('main a[href], [role="main"] a[href]'))
                .filter(a => a.getClientRects().length > 0)
                .map(a => ({url: a.href,
                    time: a.closest('article')?.querySelector('time[datetime]')?.getAttribute('datetime') || ''}))
            """;
    private static final String RESTRICTION = """
            () => {
                const text = (document.body?.innerText || '').toLowerCase();
                if (['/accounts/login', '/challenge', '/checkpoint'].some(path => location.pathname.includes(path)))
                    return 'Authentication or challenge required';
                if (document.querySelector('input[type="password"], iframe[src*="captcha"], [class*="captcha"]'))
                    return 'Login or CAPTCHA required';
                if (/please wait a few minutes|try again later|too many requests/.test(text))
                    return 'Instagram has restricted access; waiting until a later polling cycle';
                if (/this account is private|this page isn.t available|sorry, this page|story is unavailable|stories are unavailable/.test(text))
                    return 'Private account or public content unavailable';
                return '';
            }
            """;

    private final InstagramMonitoringProperties properties;
    private final Clock clock;
    private Instant retryAfter = Instant.MIN;

    public PlaywrightInstagramContentProvider(InstagramMonitoringProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public synchronized InstagramProviderResult fetchLatest(AccountConfig account) {
        if (!USERNAME.matcher(account.username()).matches()) {
            throw new InstagramProviderException("Invalid Instagram username: " + account.username());
        }
        var items = new LinkedHashMap<String, InstagramContentItem>();
        var unavailable = EnumSet.noneOf(InstagramContentType.class);
        if (clock.instant().isBefore(retryAfter)) {
            log.warn("Public Instagram access paused until {} after an access restriction", retryAfter);
            markUnavailable(account, unavailable);
            return new InstagramProviderResult(List.of(), unavailable);
        }
        // Playwright is thread confined. Each scheduled fetch owns and closes its entire browser session.
        try (Playwright playwright = Playwright.create(new Playwright.CreateOptions()
                     .setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                     .setHeadless(true).setTimeout(properties.requestTimeout().toMillis()));
             BrowserContext context = browser.newContext()) {
            context.setDefaultTimeout(properties.requestTimeout().toMillis());
            context.setDefaultNavigationTimeout(properties.requestTimeout().toMillis());
            Page page = context.newPage();
            // Observe status only on requests the browser makes; never call private endpoints or replay requests.
            page.onResponse(response -> {
                String host = java.net.URI.create(response.url()).getHost();
                if (host != null && (host.equals("instagram.com") || host.endsWith(".instagram.com")) &&
                        (response.status() == 429 || response.status() == 403)) {
                    pauseAccess(response);
                }
            });
            if (account.stories()) {
                scan(page, "https://www.instagram.com/stories/" + account.username() + "/", account, true, items, unavailable);
            }
            boolean profileAccessible = false;
            if (!clock.instant().isBefore(retryAfter) && (account.posts() || account.reels())) {
                profileAccessible = scan(page, "https://www.instagram.com/" + account.username() + "/", account, false, items, unavailable);
            }
            // The profile grid can omit reels. Read the public Reels tab as well, within the same item bound.
            if (!clock.instant().isBefore(retryAfter) && account.reels() && profileAccessible && items.values().stream().noneMatch(i -> i.type() == InstagramContentType.REEL)) {
                var reelsAccount = new AccountConfig(account.username(), account.enabled(), false, false, true);
                scan(page, "https://www.instagram.com/" + account.username() + "/reels/", reelsAccount, false, items, unavailable);
            }
            if (clock.instant().isBefore(retryAfter)) {
                markUnavailable(account, unavailable);
            }
        } catch (PlaywrightException exception) {
            markUnavailable(account, unavailable);
            log.warn("Public Instagram browser failed for @{}: {}. Ensure Playwright Chromium is installed.",
                    account.username(), exception.getMessage());
        }
        // Bound each content type independently so a full post grid never crowds out Stories or Reels.
        var counts = new EnumMap<InstagramContentType, Integer>(InstagramContentType.class);
        var bounded = items.values().stream().filter(item -> counts.merge(item.type(), 1, Integer::sum) <= properties.maxItemsPerAccount()).toList();
        return new InstagramProviderResult(bounded, unavailable);
    }

    private boolean scan(Page page, String url, AccountConfig account, boolean storiesOnly,
                      Map<String, InstagramContentItem> items, Set<InstagramContentType> unavailable) {
        try {
            Response response = page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
            if (response != null && response.status() >= 400) {
                reportUnavailable(account, storiesOnly, unavailable, "HTTP " + response.status());
                return false;
            }
            String[] reason = {""};
            try {
                page.waitForCondition(() -> {
                    dismissOptionalCookies(page);
                    reason[0] = (String) page.evaluate(RESTRICTION);
                    if (reason[0].startsWith("Instagram has restricted access")) {
                        retryAfter = clock.instant().plusSeconds(Math.max(60, properties.pollingInterval().toSeconds()));
                    }
                    if (clock.instant().isBefore(retryAfter) || !reason[0].isEmpty()) {
                        return true;
                    }
                    collect(page, account, storiesOnly, items);
                    long count = items.values().stream().filter(i -> matchesRequestedType(i, account, storiesOnly)).count();
                    // Observe Story auto-advances for at most request-timeout; never invent an ID from a ring.
                    return storiesOnly ? count >= properties.maxItemsPerAccount() : count > 0;
                }, new Page.WaitForConditionOptions().setTimeout(properties.requestTimeout().toMillis()));
            } catch (TimeoutError timeout) {
                // A bounded Story observation can time out after collecting real items; keep those items.
                reason[0] = "Timed out waiting for accessible public content";
            }
            boolean found = items.values().stream().anyMatch(i -> matchesRequestedType(i, account, storiesOnly));
            if (!reason[0].isEmpty() || !found) {
                reportUnavailable(account, storiesOnly, unavailable, reason[0].isEmpty() ? "No concrete public content identifiers exposed" : reason[0]);
            }
            return reason[0].isEmpty() || reason[0].startsWith("Timed out");
        } catch (PlaywrightException exception) {
            reportUnavailable(account, storiesOnly, unavailable, "Navigation failed or timed out: " + exception.getMessage());
            return false;
        }
    }

    private boolean matchesRequestedType(InstagramContentItem item, AccountConfig account, boolean storiesOnly) {
        return storiesOnly ? item.type() == InstagramContentType.STORY :
                (item.type() == InstagramContentType.POST && account.posts()) ||
                (item.type() == InstagramContentType.REEL && account.reels());
    }

    private void dismissOptionalCookies(Page page) {
        Locator decline = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^(Decline optional cookies|Only allow essential cookies)$", Pattern.CASE_INSENSITIVE)));
        if (decline.count() == 1 && decline.isVisible()) {
            decline.click(new Locator.ClickOptions().setTimeout(1000));
        }
    }

    @SuppressWarnings("unchecked")
    private void collect(Page page, AccountConfig account, boolean storiesOnly, Map<String, InstagramContentItem> items) {
        if (storiesOnly) {
            PublicInstagramContentParser.parse(page.url(), null, account, true)
                    .ifPresent(item -> items.putIfAbsent(item.type() + ":" + item.id(), item));
        }
        for (Map<String, String> link : (List<Map<String, String>>) page.evaluate(LINKS)) {
            PublicInstagramContentParser.parse(link.get("url"), link.get("time"), account, storiesOnly)
                    .ifPresent(item -> items.putIfAbsent(item.type() + ":" + item.id(), item));
        }
    }

    private void pauseAccess(Response response) {
        long delay = Math.max(60, properties.pollingInterval().toSeconds());
        String header = response.headerValue("retry-after");
        if (header != null) {
            try {
                delay = Math.max(delay, Long.parseLong(header));
            } catch (NumberFormatException ignored) {
                try {
                    Instant date = java.time.ZonedDateTime.parse(header, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                    delay = Math.max(delay, java.time.Duration.between(clock.instant(), date).toSeconds());
                } catch (java.time.format.DateTimeParseException ignoredDate) {
                    // Fall back to the polling interval, with a minimum one-minute pause.
                }
            }
        }
        Instant until = clock.instant().plusSeconds(delay);
        if (until.isAfter(retryAfter)) {
            retryAfter = until;
        }
    }

    private void reportUnavailable(AccountConfig account, boolean storiesOnly, Set<InstagramContentType> unavailable, String reason) {
        if (storiesOnly) {
            unavailable.add(InstagramContentType.STORY);
            log.warn("Stories for @{} unavailable: {}. Public Stories may require authentication; no detection is generated without a unique Story ID.", account.username(), reason);
        } else {
            if (account.posts()) unavailable.add(InstagramContentType.POST);
            if (account.reels()) unavailable.add(InstagramContentType.REEL);
            log.warn("Public Posts/Reels for @{} unavailable or incomplete: {}", account.username(), reason);
        }
    }

    private void markUnavailable(AccountConfig account, Set<InstagramContentType> unavailable) {
        if (account.stories()) unavailable.add(InstagramContentType.STORY);
        if (account.posts()) unavailable.add(InstagramContentType.POST);
        if (account.reels()) unavailable.add(InstagramContentType.REEL);
    }
}
