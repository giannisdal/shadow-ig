package com.giannisdal.shadowig.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class StartupValidationRunner implements ApplicationRunner {

    private final InstagramMonitoringProperties monitoringProperties;
    private final InstagramApiProperties apiProperties;
    private final NotificationEmailProperties emailProperties;
    private final ResendProperties resendProperties;

    public StartupValidationRunner(
            InstagramMonitoringProperties monitoringProperties,
            InstagramApiProperties apiProperties,
            NotificationEmailProperties emailProperties,
            ResendProperties resendProperties
    ) {
        this.monitoringProperties = monitoringProperties;
        this.apiProperties = apiProperties;
        this.emailProperties = emailProperties;
        this.resendProperties = resendProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!monitoringProperties.enabled()) {
            return;
        }
        if (monitoringProperties.enabledAccounts().isEmpty()) {
            throw new IllegalStateException("instagram.monitoring.accounts must contain at least one enabled account");
        }
        if (emailProperties.enabled() && isBlank(resendProperties.apiKey())) {
            throw new IllegalStateException("resend.api-key is required when notifications.email.enabled=true");
        }
        if (isBlank(apiProperties.accessToken()) || isBlank(apiProperties.igUserId()) || isBlank(apiProperties.baseUrl())) {
            throw new IllegalStateException("instagram.api.access-token, instagram.api.ig-user-id, and instagram.api.base-url are required when monitoring is enabled");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
