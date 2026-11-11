package com.giannisdal.shadowig.notification;

import com.giannisdal.shadowig.config.NotificationEmailProperties;
import com.giannisdal.shadowig.exception.NotificationDeliveryException;
import com.giannisdal.shadowig.model.InstagramContentItem;
import com.giannisdal.shadowig.model.InstagramContentType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ResendEmailServiceTest {

    @Test
    void retriesAndSendsOnSecondAttempt() {
        var client = mock(ResendClient.class);
        var properties = new NotificationEmailProperties(true, "to@example.com", "from@example.com", 3, Duration.ofMillis(1));
        var service = new ResendEmailService(client, properties, Clock.fixed(Instant.parse("2026-11-11T10:31:00Z"), ZoneOffset.UTC));

        when(client.sendEmail(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new NotificationDeliveryException("429 rate limited", true))
                .thenReturn("msg_ok");

        Optional<String> id = service.sendNewContentNotification("account", new InstagramContentItem("1", InstagramContentType.POST, "https://ig/p/1", Instant.parse("2026-11-11T10:30:00Z")));

        assertThat(id).contains("msg_ok");
        verify(client, times(2)).sendEmail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void throwsForNonRetryableFailure() {
        var client = mock(ResendClient.class);
        var properties = new NotificationEmailProperties(true, "to@example.com", "from@example.com", 3, Duration.ofMillis(1));
        var service = new ResendEmailService(client, properties, Clock.systemUTC());

        when(client.sendEmail(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new NotificationDeliveryException("bad request", false));

        assertThatThrownBy(() -> service.sendNewContentNotification("account", new InstagramContentItem("1", InstagramContentType.POST, "https://ig/p/1", Instant.now())))
                .isInstanceOf(NotificationDeliveryException.class);
    }
}
