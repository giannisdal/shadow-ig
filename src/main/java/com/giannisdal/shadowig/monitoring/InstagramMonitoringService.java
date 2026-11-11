package com.giannisdal.shadowig.monitoring;

import com.giannisdal.shadowig.config.InstagramMonitoringProperties;
import com.giannisdal.shadowig.exception.NotificationDeliveryException;
import com.giannisdal.shadowig.instagram.InstagramContentProvider;
import com.giannisdal.shadowig.model.InstagramContentItem;
import com.giannisdal.shadowig.model.NotificationDeliveryStatus;
import com.giannisdal.shadowig.notification.EmailNotificationService;
import com.giannisdal.shadowig.persistence.ProcessedInstagramContentEntity;
import com.giannisdal.shadowig.persistence.ProcessedInstagramContentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class InstagramMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(InstagramMonitoringService.class);

    private final InstagramMonitoringProperties monitoringProperties;
    private final InstagramContentProvider contentProvider;
    private final ProcessedInstagramContentRepository repository;
    private final EmailNotificationService emailNotificationService;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public InstagramMonitoringService(
            InstagramMonitoringProperties monitoringProperties,
            InstagramContentProvider contentProvider,
            ProcessedInstagramContentRepository repository,
            EmailNotificationService emailNotificationService,
            Clock clock
    ) {
        this.monitoringProperties = monitoringProperties;
        this.contentProvider = contentProvider;
        this.repository = repository;
        this.emailNotificationService = emailNotificationService;
        this.clock = clock;
    }

    @Scheduled(
            initialDelayString = "#{@monitoringScheduleDelayProvider.delayMillis()}",
            fixedDelayString = "#{@monitoringScheduleDelayProvider.delayMillis()}"
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

    @Transactional
    void monitorAccount(InstagramMonitoringProperties.AccountConfig account) {
        var providerResult = contentProvider.fetchLatest(account);

        if (!providerResult.unsupportedTypes().isEmpty()) {
            log.info("Unsupported content types for @{}: {}", account.username(), providerResult.unsupportedTypes());
        }

        boolean baselineMode = !monitoringProperties.notifyOnStartup() && repository.countByUsername(account.username()) == 0;
        for (InstagramContentItem item : providerResult.items()) {
            processItem(account.username(), item, baselineMode);
        }
    }

    private void processItem(String username, InstagramContentItem item, boolean baselineMode) {
        boolean alreadyProcessed = repository.existsByUsernameAndContentIdAndContentType(username, item.id(), item.type());
        if (alreadyProcessed) {
            return;
        }

        var entity = new ProcessedInstagramContentEntity();
        entity.setUsername(username);
        entity.setContentId(item.id());
        entity.setContentType(item.type());
        entity.setPermalink(item.permalink());
        entity.setPublishedAt(item.publishedAt());
        entity.setDetectedAt(Instant.now(clock));

        if (baselineMode) {
            entity.setStatus(NotificationDeliveryStatus.BASELINE);
            repository.save(entity);
            log.info("Baseline recorded for @{} content {}", username, item.id());
            return;
        }

        try {
            var messageId = emailNotificationService.sendNewContentNotification(username, item);
            entity.setResendMessageId(messageId.orElse(null));
            entity.setStatus(NotificationDeliveryStatus.SENT);
            log.info("Notification sent for @{} content {}", username, item.id());
        } catch (NotificationDeliveryException exception) {
            entity.setStatus(NotificationDeliveryStatus.FAILED);
            log.error("Notification failed for @{} content {}", username, item.id(), exception);
        }
        repository.save(entity);
    }
}
