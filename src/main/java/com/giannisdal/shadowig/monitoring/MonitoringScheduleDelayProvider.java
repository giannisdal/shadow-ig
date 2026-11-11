package com.giannisdal.shadowig.monitoring;

import com.giannisdal.shadowig.config.InstagramMonitoringProperties;
import org.springframework.stereotype.Component;

@Component
public class MonitoringScheduleDelayProvider {

    private final InstagramMonitoringProperties monitoringProperties;

    public MonitoringScheduleDelayProvider(InstagramMonitoringProperties monitoringProperties) {
        this.monitoringProperties = monitoringProperties;
    }

    public long delayMillis() {
        return monitoringProperties.pollingInterval().toMillis();
    }
}
