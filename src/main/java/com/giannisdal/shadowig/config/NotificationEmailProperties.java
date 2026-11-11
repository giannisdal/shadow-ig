package com.giannisdal.shadowig.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "notifications.email")
public record NotificationEmailProperties(
        boolean enabled,
        @NotBlank @Email String recipient,
        @NotBlank @Email String sender,
        int maxRetries,
        @NotNull Duration retryBackoff
) {
}
