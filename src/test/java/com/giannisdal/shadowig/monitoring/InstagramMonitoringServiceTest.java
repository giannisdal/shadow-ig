package com.giannisdal.shadowig.monitoring;

import com.giannisdal.shadowig.config.InstagramMonitoringProperties;
import com.giannisdal.shadowig.instagram.InstagramContentProvider;
import com.giannisdal.shadowig.instagram.InstagramProviderResult;
import com.giannisdal.shadowig.model.InstagramContentItem;
import com.giannisdal.shadowig.model.InstagramContentType;
import com.giannisdal.shadowig.model.NotificationDeliveryStatus;
import com.giannisdal.shadowig.notification.EmailNotificationService;
import com.giannisdal.shadowig.persistence.ProcessedInstagramContentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InstagramMonitoringServiceTest {

    @Mock
    private InstagramContentProvider provider;
    @Mock
    private ProcessedInstagramContentRepository repository;
    @Mock
    private EmailNotificationService emailNotificationService;

    private InstagramMonitoringService service;

    @BeforeEach
    void setUp() {
        var account = new InstagramMonitoringProperties.AccountConfig("account_one", true, true, true, true);
        var properties = new InstagramMonitoringProperties(true, Duration.ofSeconds(60), false, Duration.ofSeconds(5), 10, 3, Duration.ofSeconds(1), List.of(account));
        service = new InstagramMonitoringService(
                properties,
                provider,
                repository,
                emailNotificationService,
                Clock.fixed(Instant.parse("2026-11-11T10:31:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void firstRunCreatesBaselineWithoutNotification() {
        var item = new InstagramContentItem("media-1", InstagramContentType.POST, "https://ig/p/1", Instant.parse("2026-11-11T10:30:00Z"));
        when(provider.fetchLatest(any())).thenReturn(new InstagramProviderResult(List.of(item), Set.of(InstagramContentType.STORY)));
        when(repository.countByUsername("account_one")).thenReturn(0L);
        when(repository.existsByUsernameAndContentIdAndContentType("account_one", "media-1", InstagramContentType.POST)).thenReturn(false);

        service.monitorOnce();

        verify(emailNotificationService, never()).sendNewContentNotification(any(), any());
        var captor = ArgumentCaptor.forClass(com.giannisdal.shadowig.persistence.ProcessedInstagramContentEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(NotificationDeliveryStatus.BASELINE);
    }

    @Test
    void sendsNotificationForNewContentAfterBaseline() {
        var item = new InstagramContentItem("media-2", InstagramContentType.REEL, "https://ig/reel/2", Instant.parse("2026-11-11T11:00:00Z"));
        when(provider.fetchLatest(any())).thenReturn(new InstagramProviderResult(List.of(item), Set.of()));
        when(repository.countByUsername("account_one")).thenReturn(3L);
        when(repository.existsByUsernameAndContentIdAndContentType("account_one", "media-2", InstagramContentType.REEL)).thenReturn(false);
        when(emailNotificationService.sendNewContentNotification("account_one", item)).thenReturn(Optional.of("msg_123"));

        service.monitorOnce();

        verify(emailNotificationService).sendNewContentNotification("account_one", item);
        var captor = ArgumentCaptor.forClass(com.giannisdal.shadowig.persistence.ProcessedInstagramContentEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(NotificationDeliveryStatus.SENT);
        assertThat(captor.getValue().getResendMessageId()).isEqualTo("msg_123");
    }

    @Test
    void skipsDuplicateContent() {
        var item = new InstagramContentItem("media-3", InstagramContentType.POST, "https://ig/p/3", Instant.parse("2026-11-11T11:00:00Z"));
        when(provider.fetchLatest(any())).thenReturn(new InstagramProviderResult(List.of(item), Set.of()));
        when(repository.countByUsername("account_one")).thenReturn(5L);
        when(repository.existsByUsernameAndContentIdAndContentType("account_one", "media-3", InstagramContentType.POST)).thenReturn(true);

        service.monitorOnce();

        verify(repository, never()).save(any());
        verify(emailNotificationService, never()).sendNewContentNotification(any(), any());
    }
}
