package com.giannisdal.shadowig.monitoring;

import org.springframework.scheduling.support.CronExpression;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

record MonitoringWindow(Instant start, Instant end) {
    static MonitoringWindow latest(String cron, ZoneId zone, Instant now) {
        var expression = CronExpression.parse(cron);
        var localNow = now.atZone(zone);
        // Find the two most recent scheduled runs, preserving local time across DST.
        for (int days : new int[]{2, 32, 367, 3660}) {
            ZonedDateTime previous = null;
            ZonedDateTime latest = null;
            var candidate = expression.next(localNow.minusDays(days));
            while (candidate != null && !candidate.toInstant().isAfter(now)) {
                previous = latest;
                latest = candidate;
                candidate = expression.next(candidate);
            }
            if (previous != null) {
                return new MonitoringWindow(previous.toInstant(), latest.toInstant());
            }
        }
        throw new IllegalStateException("Cannot determine two previous runs from instagram.monitoring.cron");
    }
}
