package com.urleads.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Propiedades configurables para el publicador de outbox (Backend V2 C04).
 */
@Configuration
@ConfigurationProperties(prefix = "urleads.outbox.publisher")
public class OutboxProperties {

    private boolean enabled = true;
    private long intervalMs = 5000;
    private int batchSize = 20;
    private int maxRetries = 3;
    private long confirmTimeoutMs = 3000;
    private long baseBackoffSeconds = 5;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getIntervalMs() {
        return intervalMs;
    }

    public void setIntervalMs(long intervalMs) {
        this.intervalMs = intervalMs;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public long getConfirmTimeoutMs() {
        return confirmTimeoutMs;
    }

    public void setConfirmTimeoutMs(long confirmTimeoutMs) {
        this.confirmTimeoutMs = confirmTimeoutMs;
    }

    public long getBaseBackoffSeconds() {
        return baseBackoffSeconds;
    }

    public void setBaseBackoffSeconds(long baseBackoffSeconds) {
        this.baseBackoffSeconds = baseBackoffSeconds;
    }
}
