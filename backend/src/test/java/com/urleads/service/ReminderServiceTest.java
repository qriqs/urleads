package com.urleads.service;

import com.urleads.config.ClockConfig;
import com.urleads.dto.CreateReminderRequest;
import com.urleads.dto.NotificationResponse;
import com.urleads.dto.ReminderResponse;
import com.urleads.entity.*;
import com.urleads.repository.*;
import com.urleads.websocket.NotificationWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ReminderServiceTest {

    private RecordatorioRepository recordatorioRepository;
    private UsuarioRepository usuarioRepository;
    private NotificationRepository notificationRepository;
    private AgendaCitaRepository agendaCitaRepository;
    private AgendaTareaRepository agendaTareaRepository;
    private NotificationWebSocketHandler notificationWebSocketHandler;

    private ReminderService reminderService;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        recordatorioRepository = mock(RecordatorioRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        notificationRepository = mock(NotificationRepository.class);
        agendaCitaRepository = mock(AgendaCitaRepository.class);
        agendaTareaRepository = mock(AgendaTareaRepository.class);
        notificationWebSocketHandler = mock(NotificationWebSocketHandler.class);

        reminderService = new ReminderService(
                recordatorioRepository,
                usuarioRepository,
                notificationRepository,
                agendaCitaRepository,
                agendaTareaRepository,
                notificationWebSocketHandler,
                Clock.system(ClockConfig.LIMA_ZONE)
        );

        usuario = new Usuario(42L, "alex", "secret");
        when(usuarioRepository.findById(42L)).thenReturn(Optional.of(usuario));
    }

    @Test
    @DisplayName("Distingue fecha de seguimiento (LocalDate a las 09:00 Lima) de un timestamp exacto")
    void convertirFechaSeguimientoATimestamp_distinguishesDateFromExactTimestamp() {
        LocalDate seguimiento = LocalDate.of(2026, 10, 25);
        Instant timestamp = ReminderService.convertirFechaSeguimientoATimestamp(
                seguimiento, LocalTime.of(9, 0), ZoneId.of("America/Lima"));

        // 09:00:00 en Lima (-05:00) corresponde exactamente a 14:00:00 UTC
        assertThat(timestamp).isEqualTo(Instant.parse("2026-10-25T14:00:00Z"));
    }

    @Test
    @DisplayName("Programar recordatorio con timestamp exacto y antelación de minutos")
    void programarRecordatorio_exactTimestampWithAntelacion_calculatesCorrectTrigger() {
        CreateReminderRequest request = CreateReminderRequest.builder()
                .targetType("CITA")
                .targetId(10L)
                .programadoPara("2026-10-25T15:00:00Z") // 10:00 Lima
                .antelacionMinutos(15) // 15 minutos antes -> 14:45 UTC
                .build();

        when(recordatorioRepository.findByOccurrenceId(any())).thenReturn(Optional.empty());
        when(recordatorioRepository.save(any(Recordatorio.class))).thenAnswer(inv -> {
            Recordatorio r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });

        ReminderResponse response = reminderService.programarRecordatorio(42L, request);

        assertThat(response).isNotNull();
        assertThat(response.getProgramadoPara()).isEqualTo(Instant.parse("2026-10-25T14:45:00Z"));
        assertThat(response.getOccurrenceId()).contains("REM-CITA-10-");
        assertThat(response.getEstado()).isEqualTo("PENDIENTE");

        verify(recordatorioRepository).save(any(Recordatorio.class));
    }

    @Test
    @DisplayName("Idempotencia: no duplica recordatorios si el occurrenceId ya existe")
    void programarRecordatorio_existingOccurrenceId_returnsExistingRecord() {
        Recordatorio existente = Recordatorio.builder()
                .id(99L)
                .occurrenceId("REM-CITA-10-15M")
                .usuario(usuario)
                .targetType("CITA")
                .targetId(10L)
                .programadoPara(Instant.parse("2026-10-25T14:45:00Z"))
                .estado("PENDIENTE")
                .build();

        when(recordatorioRepository.findByOccurrenceId("REM-CITA-10-15M")).thenReturn(Optional.of(existente));

        CreateReminderRequest request = CreateReminderRequest.builder()
                .occurrenceId("REM-CITA-10-15M")
                .targetType("CITA")
                .targetId(10L)
                .programadoPara("2026-10-25T14:45:00Z")
                .build();

        ReminderResponse response = reminderService.programarRecordatorio(42L, request);

        assertThat(response.getId()).isEqualTo(99L);
        assertThat(response.getOccurrenceId()).isEqualTo("REM-CITA-10-15M");
        verify(recordatorioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Despacho periódico de recordatorios pendientes vence a la hora programada")
    void procesarRecordatoriosPendientes_dispatchesDueReminders() {
        Instant ahora = Instant.parse("2026-10-25T14:45:00Z");

        Recordatorio r1 = Recordatorio.builder()
                .id(1L)
                .occurrenceId("REM-CITA-10-15M")
                .usuario(usuario)
                .targetType("CITA")
                .targetId(10L)
                .programadoPara(Instant.parse("2026-10-25T14:45:00Z"))
                .estado("PENDIENTE")
                .build();

        AgendaCita cita = AgendaCita.builder()
                .id(10L)
                .usuario(usuario)
                .titulo("Reunión con cliente clave")
                .inicio(Instant.parse("2026-10-25T15:00:00Z"))
                .fin(Instant.parse("2026-10-25T16:00:00Z"))
                .estado("PROGRAMADA")
                .build();

        when(recordatorioRepository.findPendingRemindersToDispatch(ahora)).thenReturn(List.of(r1));
        when(recordatorioRepository.updateEstadoIfCurrent(1L, "PROCESADO", "PENDIENTE")).thenReturn(1);
        when(agendaCitaRepository.findById(10L)).thenReturn(Optional.of(cita));

        int procesados = reminderService.procesarRecordatoriosPendientes(ahora);

        assertThat(procesados).isEqualTo(1);
        assertThat(r1.getEstado()).isEqualTo("PROCESADO");

        // Verifica persistencia de la notificación con eventId = occurrenceId
        ArgumentCaptor<Notification> notifCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notifCaptor.capture());
        Notification savedNotif = notifCaptor.getValue();
        assertThat(savedNotif.getEventId()).isEqualTo("REM-CITA-10-15M");
        assertThat(savedNotif.getTitulo()).contains("Reunión con cliente clave");

        // Verifica emisión en tiempo real vía WebSocket
        verify(notificationWebSocketHandler).sendNotificationToUser(eq(42L), any(NotificationResponse.class));
    }

    @Test
    @DisplayName("Simulación de reinicio del servicio: no se duplican recordatorios ya procesados")
    void procesarRecordatoriosPendientes_afterRestart_preventsDuplicateDispatches() {
        Instant t1 = Instant.parse("2026-10-25T14:45:00Z");

        Recordatorio r1 = Recordatorio.builder()
                .id(1L)
                .occurrenceId("REM-CITA-10-15M")
                .usuario(usuario)
                .targetType("CITA")
                .targetId(10L)
                .programadoPara(t1)
                .estado("PENDIENTE")
                .build();

        AgendaCita cita = AgendaCita.builder()
                .id(10L)
                .usuario(usuario)
                .titulo("Cita demo")
                .inicio(Instant.parse("2026-10-25T15:00:00Z"))
                .estado("PROGRAMADA")
                .build();

        when(recordatorioRepository.findPendingRemindersToDispatch(t1)).thenReturn(List.of(r1));
        when(recordatorioRepository.updateEstadoIfCurrent(1L, "PROCESADO", "PENDIENTE")).thenReturn(1);
        when(agendaCitaRepository.findById(10L)).thenReturn(Optional.of(cita));

        // Primer ciclo: se despacha r1
        int primerPaso = reminderService.procesarRecordatoriosPendientes(t1);
        assertThat(primerPaso).isEqualTo(1);
        verify(notificationRepository, times(1)).save(any());

        // --- SIMULACIÓN DE REINICIO DEL SERVICIO ---
        // Se instancia un nuevo ReminderService simulando el arranque posterior al reinicio
        ReminderService newServiceInstanceAfterRestart = new ReminderService(
                recordatorioRepository,
                usuarioRepository,
                notificationRepository,
                agendaCitaRepository,
                agendaTareaRepository,
                notificationWebSocketHandler,
                Clock.system(ClockConfig.LIMA_ZONE)
        );

        // En base de datos r1 ya está PROCESADO, por lo que findPendingRemindersToDispatch no lo retorna
        when(recordatorioRepository.findPendingRemindersToDispatch(any())).thenReturn(List.of());

        // Segundo ciclo (tras reinicio): 0 despachos, sin duplicados
        int segundoPaso = newServiceInstanceAfterRestart.procesarRecordatoriosPendientes(t1.plus(1, ChronoUnit.MINUTES));
        assertThat(segundoPaso).isEqualTo(0);

        // La notificación solo se guardó una única vez en todo el ciclo de vida
        verify(notificationRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Cita cancelada: el recordatorio no despacha notificación al usuario")
    void procesarRecordatoriosPendientes_cancelledAppointment_skipsNotification() {
        Instant ahora = Instant.parse("2026-10-25T14:45:00Z");

        Recordatorio r1 = Recordatorio.builder()
                .id(1L)
                .occurrenceId("REM-CITA-10-CANCEL")
                .usuario(usuario)
                .targetType("CITA")
                .targetId(10L)
                .programadoPara(ahora)
                .estado("PENDIENTE")
                .build();

        AgendaCita citaCancelada = AgendaCita.builder()
                .id(10L)
                .usuario(usuario)
                .titulo("Cita anulada")
                .estado("CANCELADA")
                .build();

        when(recordatorioRepository.findPendingRemindersToDispatch(ahora)).thenReturn(List.of(r1));
        when(recordatorioRepository.updateEstadoIfCurrent(1L, "PROCESADO", "PENDIENTE")).thenReturn(1);
        when(agendaCitaRepository.findById(10L)).thenReturn(Optional.of(citaCancelada));

        int procesados = reminderService.procesarRecordatoriosPendientes(ahora);

        assertThat(procesados).isEqualTo(1);
        verify(notificationRepository, never()).save(any());
        verify(notificationWebSocketHandler, never()).sendNotificationToUser(any(), any());
    }

    @Test
    @DisplayName("Cancelar recordatorio cambia estado a CANCELADO")
    void cancelarRecordatorio_marksAsCancelled() {
        Recordatorio r = Recordatorio.builder()
                .id(1L)
                .usuario(usuario)
                .estado("PENDIENTE")
                .build();

        when(recordatorioRepository.findByIdAndUsuarioId(1L, 42L)).thenReturn(Optional.of(r));

        reminderService.cancelarRecordatorio(1L, 42L);

        assertThat(r.getEstado()).isEqualTo("CANCELADO");
        verify(recordatorioRepository).save(r);
    }
}
