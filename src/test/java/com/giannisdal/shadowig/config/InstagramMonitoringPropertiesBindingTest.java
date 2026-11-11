package com.giannisdal.shadowig.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "instagram.monitoring.enabled=true",
        "instagram.monitoring.polling-interval=30s",
        "instagram.monitoring.notify-on-startup=true",
        "instagram.monitoring.request-timeout=5s",
        "instagram.monitoring.max-items-per-account=5",
        "instagram.monitoring.max-retries=2",
        "instagram.monitoring.retry-backoff=1s",
        "instagram.monitoring.accounts[0].username=alpha",
        "instagram.monitoring.accounts[0].enabled=true",
        "instagram.monitoring.accounts[0].posts=true",
        "instagram.monitoring.accounts[0].stories=false",
        "instagram.monitoring.accounts[0].reels=true",
        "instagram.api.access-token=test-token",
        "instagram.api.ig-user-id=123",
        "instagram.api.base-url=https://graph.facebook.com/v22.0",
        "notifications.email.enabled=true",
        "notifications.email.recipient=user@example.com",
        "notifications.email.sender=alerts@example.com",
        "notifications.email.max-retries=2",
        "notifications.email.retry-backoff=1s",
        "resend.api-key=re_test"
})
class InstagramMonitoringPropertiesBindingTest {

    @Autowired
    private InstagramMonitoringProperties properties;

    @Test
    void bindsMonitoringAccountConfig() {
        assertThat(properties.enabled()).isTrue();
        assertThat(properties.pollingInterval()).hasSeconds(30);
        assertThat(properties.enabledAccounts()).singleElement().satisfies(account -> {
            assertThat(account.username()).isEqualTo("alpha");
            assertThat(account.posts()).isTrue();
            assertThat(account.stories()).isFalse();
            assertThat(account.reels()).isTrue();
        });
    }
}
