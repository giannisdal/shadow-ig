package com.giannisdal.shadowig.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storiesig")
public record StoriesIgProperties(String apiKey, String baseUrl) {
}
