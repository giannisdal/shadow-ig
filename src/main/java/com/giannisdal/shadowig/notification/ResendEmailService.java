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
                DATE_TIME_FORMATTER.format(content.publishedAt()),
                content.permalink(),
                DATE_TIME_FORMATTER.format(clock.instant())
        );

        var delay = properties.retryBackoff();
        for (int attempt = 1; attempt <= properties.maxRetries(); attempt++) {
            try {
                String resendMessageId = resendClient.sendEmail(properties.sender(), properties.recipient(), subject, body);
                return Optional.ofNullable(resendMessageId);
            } catch (NotificationDeliveryException exception) {
                if (!exception.retryable() || attempt >= properties.maxRetries()) {
                    log.error("Email notification failed for @{} {} after {} attempt(s)", username, content.id(), attempt, exception);
                    throw exception;
                }
                log.warn("Temporary email failure for @{} {}. Retrying attempt {}/{}", username, content.id(), attempt + 1, properties.maxRetries());
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
