package com.giannisdal.shadowig.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class StartupValidationRunner implements ApplicationRunner {

    private final InstagramMonitoringProperties monitoringProperties;
    private final NotificationEmailProperties emailProperties;
    private final ResendProperties resendProperties;
    private final StoriesIgProperties storiesIgProperties;
    private final org.springframework.core.env.Environment environment;

    public StartupValidationRunner(
            InstagramMonitoringProperties monitoringProperties,
            NotificationEmailProperties emailProperties,
            ResendProperties resendProperties,
            StoriesIgProperties storiesIgProperties,
            org.springframework.core.env.Environment environment
    ) {
        this.monitoringProperties = monitoringProperties;
        this.emailProperties = emailProperties;
        this.resendProperties = resendProperties;
        this.storiesIgProperties = storiesIgProperties;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!monitoringProperties.enabled()) {
            return;
        }
        if (monitoringProperties.enabledAccounts().isEmpty()) {
            throw new IllegalStateException("instagram.monitoring.accounts must contain at least one enabled account");
        }
        if (environment.getProperty("instagram.monitoring.provider", "storiesig").equals("storiesig")
                && isBlank(storiesIgProperties.apiKey())) {
            throw new IllegalStateException("STORIESIG_API_KEY is required: obtain a free key at https://scraperapi.to and set it in .env");
        }
        if (emailProperties.enabled() && isBlank(resendProperties.apiKey())) {
            throw new IllegalStateException("resend.api-key is required when notifications.email.enabled=true");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
