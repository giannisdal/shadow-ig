package com.giannisdal.shadowig.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "resend")
public record ResendProperties(String apiKey) {
}
