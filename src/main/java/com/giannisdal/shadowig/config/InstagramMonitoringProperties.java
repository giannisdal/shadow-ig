package com.giannisdal.shadowig.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "instagram.monitoring")
public record InstagramMonitoringProperties(
        boolean enabled,
        @NotNull Duration pollingInterval,
        boolean notifyOnStartup,
        @NotNull Duration requestTimeout,
        int maxItemsPerAccount,
        int maxRetries,
        @NotNull Duration retryBackoff,
        @NotEmpty List<@Valid AccountConfig> accounts
) {

    public InstagramMonitoringProperties {
        accounts = List.copyOf(accounts);
    }

    public List<AccountConfig> enabledAccounts() {
        return accounts.stream().filter(AccountConfig::enabled).toList();
    }

    public record AccountConfig(
            @NotBlank String username,
            boolean enabled,
            boolean posts,
            boolean stories,
            boolean reels
    ) {
    }
}
