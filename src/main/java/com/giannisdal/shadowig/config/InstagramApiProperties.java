package com.giannisdal.shadowig.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "instagram.api")
public record InstagramApiProperties(
        String accessToken,
        String igUserId,
        String baseUrl
) {
}
