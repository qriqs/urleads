package com.urleads.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    public static final ZoneId LIMA_ZONE = ZoneId.of("America/Lima");

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.system(LIMA_ZONE);
    }
}
