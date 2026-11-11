package com.giannisdal.shadowig.config;

import com.resend.Resend;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Clock;

@Configuration
public class AppConfig {

    @Bean
    RestClient restClient(InstagramMonitoringProperties monitoringProperties) {
        return RestClient.builder().build();
    }

    @Bean
    Resend resendClient(ResendProperties resendProperties) {
        return new Resend(resendProperties.apiKey());
    }

    @Bean
    Clock utcClock() {
        return Clock.systemUTC();
    }
}
