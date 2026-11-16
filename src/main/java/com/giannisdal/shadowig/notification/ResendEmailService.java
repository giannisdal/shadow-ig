package com.giannisdal.shadowig.notification;

import com.giannisdal.shadowig.config.NotificationEmailProperties;
import com.giannisdal.shadowig.exception.NotificationDeliveryException;
import com.giannisdal.shadowig.model.InstagramContentItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.List;
import org.springframework.web.util.HtmlUtils;

@Service
public class ResendEmailService implements EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailService.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC);

    private final ResendClient resendClient;
    private final NotificationEmailProperties properties;
    private final Clock clock;

    public ResendEmailService(ResendClient resendClient, NotificationEmailProperties properties, Clock clock) {
        this.resendClient = resendClient;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public Optional<String> sendNewContentNotification(String username, InstagramContentItem content) {
        if (!properties.enabled()) {
            return Optional.empty();
        }

        String subject = "Instagram Alert — @" + username + " published a new " + content.type() + "!";
        String body = """
                <html><body>
                <p><strong>New Instagram activity detected!</strong></p>
                <p>Account: @%s</p>
                <p>Content Type: %s</p>
                <p>Published At: %s</p>
                <p>Instagram Link: <a href=\"%s\">Open content</a></p>
                <p>Detected At: %s</p>
                </body></html>
                """.formatted(
                username,
                content.type(),
                content.publishedAt() == null ? "Unavailable on public page" : DATE_TIME_FORMATTER.format(content.publishedAt()),
                content.permalink(),
                DATE_TIME_FORMATTER.format(clock.instant())
        );

        return send(username, content.id(), subject, body);
    }

    @Override
    public Optional<String> sendDailyDigest(String username, List<InstagramContentItem> contents) {
        if (!properties.enabled() || contents.isEmpty()) {
            return Optional.empty();
        }
        var body = new StringBuilder("<html><body><p>Instagram daily update: @")
                .append(HtmlUtils.htmlEscape(username)).append("</p><ul>");
        for (var item : contents) {
            body.append("<li>").append(item.type()).append(" — ")
                    .append(item.publishedAt() == null ? "Publication time unavailable" : DATE_TIME_FORMATTER.format(item.publishedAt()))
                    .append(" — <a href=\"").append(HtmlUtils.htmlEscape(item.permalink()))
                    .append("\">Open on Instagram</a></li>");
        }
        body.append("</ul><p>Checked at: ").append(DATE_TIME_FORMATTER.format(clock.instant()))
                .append("</p></body></html>");
        return send(username, "daily-digest", "Instagram daily update — @" + username + " (" + contents.size() + " new)", body.toString());
    }

    private Optional<String> send(String username, String contentId, String subject, String body) {
        var delay = properties.retryBackoff();
        for (int attempt = 1; attempt <= properties.maxRetries(); attempt++) {
            try {
                String resendMessageId = resendClient.sendEmail(properties.sender(), properties.recipient(), subject, body);
                return Optional.ofNullable(resendMessageId);
            } catch (NotificationDeliveryException exception) {
                if (!exception.retryable() || attempt >= properties.maxRetries()) {
                    log.error("Email notification failed for @{} {} after {} attempt(s)", username, contentId, attempt, exception);
                    throw exception;
                }
                log.warn("Temporary email failure for @{} {}. Retrying attempt {}/{}", username, contentId, attempt + 1, properties.maxRetries());
                sleep(delay.toMillis() * attempt);
            }
        }
        return Optional.empty();
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }
}
