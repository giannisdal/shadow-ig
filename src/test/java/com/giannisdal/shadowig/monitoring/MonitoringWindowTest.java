package com.giannisdal.shadowig.monitoring;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.ZoneId;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

class MonitoringWindowTest {
    private final ZoneId athens = ZoneId.of("Europe/Athens");

    @Test
    void morningCronMovesBothBoundariesToNineAm() {
        var window = MonitoringWindow.latest("0 0 9 * * *", athens, Instant.parse("2026-11-11T07:00:03Z"));
        assertThat(window.start()).isEqualTo(Instant.parse("2026-11-10T07:00:00Z"));
        assertThat(window.end()).isEqualTo(Instant.parse("2026-11-11T07:00:00Z"));
    }

    @Test
    void scheduledHourRemainsTheSameAcrossDaylightSavingChange() {
        var window = MonitoringWindow.latest("0 0 9 * * *", athens, Instant.parse("2026-10-25T07:00:03Z"));
        assertThat(window.start()).isEqualTo(Instant.parse("2026-10-24T06:00:00Z"));
        assertThat(window.end()).isEqualTo(Instant.parse("2026-10-25T07:00:00Z"));
        assertThat(Duration.between(window.start(), window.end())).isEqualTo(Duration.ofHours(25));
    }

    @Test
    void manualCheckUsesLatestCompletedScheduleWindow() {
        var window = MonitoringWindow.latest("0 0 9 * * *", athens, Instant.parse("2026-11-11T06:30:00Z"));
        assertThat(window.start()).isEqualTo(Instant.parse("2026-11-09T07:00:00Z"));
        assertThat(window.end()).isEqualTo(Instant.parse("2026-11-10T07:00:00Z"));
    }
}
