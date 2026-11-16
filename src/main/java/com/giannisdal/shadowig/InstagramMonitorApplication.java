package com.giannisdal.shadowig;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class InstagramMonitorApplication {

    public static void main(String[] args) {
        var context = SpringApplication.run(InstagramMonitorApplication.class, args);
        if (context.getEnvironment().getProperty("instagram.monitoring.run-once", Boolean.class, false)) {
            try (context) {
                context.getBean(com.giannisdal.shadowig.monitoring.InstagramMonitoringService.class).monitorOnceOrThrow();
            }
        }
    }
}
