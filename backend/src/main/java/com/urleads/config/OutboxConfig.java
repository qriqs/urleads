package com.urleads.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuración para habilitar la ejecución programada del publicador de outbox (Backend V2 C04).
 */
@Configuration
@EnableScheduling
public class OutboxConfig {
}
