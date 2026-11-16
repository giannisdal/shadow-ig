package com.giannisdal.shadowig.monitoring;

import com.giannisdal.shadowig.config.InstagramMonitoringProperties;
import com.giannisdal.shadowig.instagram.InstagramContentProvider;
import com.giannisdal.shadowig.model.InstagramContentItem;
import com.giannisdal.shadowig.model.InstagramContentType;
import com.giannisdal.shadowig.model.NotificationDeliveryStatus;
import com.giannisdal.shadowig.notification.EmailNotificationService;
import com.giannisdal.shadowig.persistence.ProcessedInstagramContentEntity;
import com.giannisdal.shadowig.persistence.ProcessedInstagramContentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class InstagramMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(InstagramMonitoringService.class);

    private final InstagramMonitoringProperties monitoringProperties;
    private final InstagramContentProvider contentProvider;
    private final ProcessedInstagramContentRepository repository;
    private final EmailNotificationService emailNotificationService;
    private final Clock clock;
    private final ZoneId monitoringZone;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public InstagramMonitoringService(
            InstagramMonitoringProperties monitoringProperties,
            InstagramContentProvider contentProvider,
            ProcessedInstagramContentRepository repository,
            EmailNotificationService emailNotificationService,
            Clock clock,
            @Value("${instagram.monitoring.zone:Europe/Athens}") String monitoringZone
    ) {
        this.monitoringProperties = monitoringProperties;
        this.contentProvider = contentProvider;
        this.repository = repository;
        this.emailNotificationService = emailNotificationService;
        this.clock = clock;
        this.monitoringZone = ZoneId.of(monitoringZone);
    }

    @Scheduled(
            cron = "${instagram.monitoring.cron:0 0 21 * * *}",
            zone = "${instagram.monitoring.zone:Europe/Athens}"
    )
    public void scheduledMonitor() {
        if (!monitoringProperties.enabled()) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            log.warn("Skipping cycle because previous monitoring cycle is still running");
            return;
        }
        try {
            monitorOnce();
        } finally {
            running.set(false);
        }
    }

    public void monitorOnce() {
        for (var account : monitoringProperties.enabledAccounts()) {
            try {
                monitorAccount(account);
            } catch (Exception exception) {
                log.error("Failed monitoring for @{}", account.username(), exception);
            }
        }
    }

    public void monitorOnceOrThrow() {
        for (var account : monitoringProperties.enabledAccounts()) {
            monitorAccount(account);
        }
    }

    @Transactional
    void monitorAccount(InstagramMonitoringProperties.AccountConfig account) {
        var providerResult = contentProvider.fetchLatest(account);

        if (!providerResult.unsupportedTypes().isEmpty()) {
            log.info("Unavailable or incompletely observed public content types for @{}: {}", account.username(), providerResult.unsupportedTypes());
        }

        boolean firstRun = repository.countByUsername(account.username()) == 0;
        var windowEnd = clock.instant().atZone(monitoringZone).toLocalDate()
                .atTime(21, 0).atZone(monitoringZone);
        // Calendar days preserve 21:00 local time even across daylight saving changes.
        var windowStart = windowEnd.minusDays(1).toInstant();
        var windowEndInstant = windowEnd.toInstant();
        List<InstagramContentItem> pending = new ArrayList<>();
        for (InstagramContentItem item : providerResult.items()) {
            if (repository.existsByUsernameAndContentIdAndContentType(account.username(), item.id(), item.type())) {
                continue;
            }
            // Include posts after yesterday's check, through today's 21:00 check.
            boolean outsideWindow = item.type() != InstagramContentType.STORY
                    && (item.publishedAt() == null
                    || !item.publishedAt().isAfter(windowStart)
                    || item.publishedAt().isAfter(windowEndInstant));
            if (outsideWindow || firstRun && !monitoringProperties.notifyOnStartup()) {
                saveItem(account.username(), item, NotificationDeliveryStatus.BASELINE, null);
            } else {
                pending.add(item);
            }
        }
        if (pending.isEmpty()) {
            return;
        }
        // Persist only after a successful send, so failures can be retried next cycle.
        var messageId = emailNotificationService.sendDailyDigest(account.username(), List.copyOf(pending));
        for (var item : pending) {
            saveItem(account.username(), item, NotificationDeliveryStatus.SENT, messageId.orElse(null));
        }
        log.info("Daily digest sent for @{} with {} items", account.username(), pending.size());
    }

    private void saveItem(String username, InstagramContentItem item, NotificationDeliveryStatus status, String messageId) {
        var entity = new ProcessedInstagramContentEntity();
        entity.setUsername(username);
        entity.setContentId(item.id());
        entity.setContentType(item.type());
        entity.setPermalink(item.permalink());
        entity.setPublishedAt(item.publishedAt());
        entity.setDetectedAt(Instant.now(clock));

        entity.setResendMessageId(messageId);
        entity.setStatus(status);
        repository.save(entity);
    }
}
