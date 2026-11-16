package com.giannisdal.shadowig.instagram;

import com.giannisdal.shadowig.config.InstagramMonitoringProperties.AccountConfig;
import com.giannisdal.shadowig.model.InstagramContentItem;
import com.giannisdal.shadowig.model.InstagramContentType;

import java.math.BigInteger;
import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Pattern;

/** Accept only concrete public content links; profile rings and viewer shells are not content. */
final class PublicInstagramContentParser {
    private static final Pattern STORY = Pattern.compile("/stories/([A-Za-z0-9._]+)/([0-9]+)/?");
    private static final Pattern MEDIA = Pattern.compile("/(?:([A-Za-z0-9._]+)/)?(p|reel)/([A-Za-z0-9_-]+)/?");
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

    private PublicInstagramContentParser() {
    }

    static Optional<InstagramContentItem> parse(String link, String timestamp, AccountConfig account,
                                                 boolean storiesOnly) {
        try {
            URI uri = URI.create("https://www.instagram.com/").resolve(link);
            if (!"https".equals(uri.getScheme()) ||
                    !("www.instagram.com".equalsIgnoreCase(uri.getHost()) || "instagram.com".equalsIgnoreCase(uri.getHost()))) {
                return Optional.empty();
            }
            var story = STORY.matcher(uri.getPath());
            if (story.matches()) {
                if (!account.stories() || !story.group(1).equalsIgnoreCase(account.username())) {
                    return Optional.empty();
                }
                return Optional.of(new InstagramContentItem(story.group(2), InstagramContentType.STORY,
                        "https://www.instagram.com/stories/" + account.username() + "/" + story.group(2) + "/", publishedAt(timestamp)));
            }
            var media = MEDIA.matcher(uri.getPath());
            if (storiesOnly || !media.matches() || (media.group(1) != null && !media.group(1).equalsIgnoreCase(account.username()))) {
                return Optional.empty();
            }
            var type = "reel".equals(media.group(2)) ? InstagramContentType.REEL : InstagramContentType.POST;
            if ((type == InstagramContentType.POST && !account.posts()) || (type == InstagramContentType.REEL && !account.reels())) {
                return Optional.empty();
            }
            // Instagram shortcodes encode the numeric media ID, retaining deduplication of old Graph API rows.
            BigInteger id = BigInteger.ZERO;
            for (char character : media.group(3).toCharArray()) {
                id = id.shiftLeft(6).add(BigInteger.valueOf(ALPHABET.indexOf(character)));
            }
            return Optional.of(new InstagramContentItem(id.toString(), type,
                    "https://www.instagram.com/" + media.group(2) + "/" + media.group(3) + "/", publishedAt(timestamp)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static Instant publishedAt(String timestamp) {
        try {
            return timestamp == null || timestamp.isBlank() ? null : Instant.parse(timestamp);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }
}
