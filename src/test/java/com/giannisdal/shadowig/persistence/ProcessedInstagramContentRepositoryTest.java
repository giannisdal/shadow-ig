package com.giannisdal.shadowig.persistence;

import com.giannisdal.shadowig.model.InstagramContentType;
import com.giannisdal.shadowig.model.NotificationDeliveryStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "instagram.monitoring.enabled=false",
        "notifications.email.enabled=false"
})
class ProcessedInstagramContentRepositoryTest {

    @Autowired
    private ProcessedInstagramContentRepository repository;

    @Test
    void persistsAndFindsProcessedContent() {
        var entity = new ProcessedInstagramContentEntity();
        entity.setUsername("account_one");
        entity.setContentId("id-1");
        entity.setContentType(InstagramContentType.POST);
        entity.setPermalink("https://ig/p/id-1");
        entity.setPublishedAt(Instant.parse("2026-11-11T10:30:00Z"));
        entity.setDetectedAt(Instant.parse("2026-11-11T10:31:00Z"));
        entity.setStatus(NotificationDeliveryStatus.SENT);
        repository.saveAndFlush(entity);

        assertThat(repository.existsByUsernameAndContentIdAndContentType("account_one", "id-1", InstagramContentType.POST)).isTrue();
        assertThat(repository.countByUsername("account_one")).isEqualTo(1);
    }
}
