package com.urleads.scheduler;

import com.urleads.service.ReminderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
@ConditionalOnProperty(name = "reminder.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final ReminderService reminderService;
    private final Clock clock;

    public ReminderScheduler(
            ReminderService reminderService,
            @Autowired(required = false) Clock clock
    ) {
        this.reminderService = reminderService;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Scheduled(fixedDelayString = "${reminder.scheduler.interval-ms:30000}")
    public void ejecutarDespachoRecordatorios() {
        try {
            int procesados = reminderService.procesarRecordatoriosPendientes(Instant.now(clock));
            if (procesados > 0) {
                log.info("Scheduler de recordatorios: {} recordatorios despachados exitosamente", procesados);
            }
        } catch (Exception ex) {
            log.error("Error durante el ciclo periódico del scheduler de recordatorios: {}", ex.getMessage(), ex);
        }
    }
}
