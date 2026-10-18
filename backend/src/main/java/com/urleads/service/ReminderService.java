package com.urleads.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urleads.config.ClockConfig;
import com.urleads.dto.CreateReminderRequest;
import com.urleads.dto.NotificationResponse;
import com.urleads.dto.ReminderResponse;
import com.urleads.entity.*;
import com.urleads.exception.ResourceNotFoundException;
import com.urleads.repository.*;
import com.urleads.websocket.NotificationWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);
    public static final ZoneId DEFAULT_ZONE = ClockConfig.LIMA_ZONE;
    public static final LocalTime HORA_INICIO_LABORAL = LocalTime.of(9, 0);

    private final RecordatorioRepository recordatorioRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificationRepository notificationRepository;
    private final AgendaCitaRepository agendaCitaRepository;
    private final AgendaTareaRepository agendaTareaRepository;
    private final NotificationWebSocketHandler notificationWebSocketHandler;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    @Autowired
    public ReminderService(
            @Autowired(required = false) RecordatorioRepository recordatorioRepository,
            @Autowired(required = false) UsuarioRepository usuarioRepository,
            @Autowired(required = false) NotificationRepository notificationRepository,
            @Autowired(required = false) AgendaCitaRepository agendaCitaRepository,
            @Autowired(required = false) AgendaTareaRepository agendaTareaRepository,
            @Autowired(required = false) NotificationWebSocketHandler notificationWebSocketHandler,
            @Autowired(required = false) Clock clock
    ) {
        this.recordatorioRepository = recordatorioRepository;
        this.usuarioRepository = usuarioRepository;
        this.notificationRepository = notificationRepository;
        this.agendaCitaRepository = agendaCitaRepository;
        this.agendaTareaRepository = agendaTareaRepository;
        this.notificationWebSocketHandler = notificationWebSocketHandler;
        this.clock = clock != null ? clock : Clock.system(DEFAULT_ZONE);
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Distingue una fecha de seguimiento (LocalDate sin hora) de un timestamp exacto (Instant).
     * La fecha de seguimiento se proyecta a la hora laboral de inicio (09:00 AM) en la zona horaria del usuario.
     */
    public static Instant convertirFechaSeguimientoATimestamp(LocalDate fechaSeguimiento, LocalTime horaLaboral, ZoneId zoneId) {
        if (fechaSeguimiento == null) {
            throw new IllegalArgumentException("La fecha de seguimiento no puede ser nula");
        }
        LocalTime hora = horaLaboral != null ? horaLaboral : HORA_INICIO_LABORAL;
        ZoneId zona = zoneId != null ? zoneId : DEFAULT_ZONE;
        return ZonedDateTime.of(fechaSeguimiento, hora, zona).toInstant();
    }

    /**
     * Parsea o deriva el timestamp exacto a partir de los datos de la solicitud.
     */
    public Instant resolverTimestampProgramado(CreateReminderRequest request) {
        // 1. Si viene timestamp exacto (programadoPara)
        if (request.getProgramadoPara() != null && !request.getProgramadoPara().isBlank()) {
            Instant exacto = AppointmentService.parseIsoDateTime(request.getProgramadoPara(), DEFAULT_ZONE);
            if (request.getAntelacionMinutos() != null && request.getAntelacionMinutos() > 0) {
                return exacto.minus(request.getAntelacionMinutos(), ChronoUnit.MINUTES);
            }
            return exacto;
        }

        // 2. Si viene fecha de seguimiento (fechaSeguimiento YYYY-MM-DD)
        if (request.getFechaSeguimiento() != null && !request.getFechaSeguimiento().isBlank()) {
            LocalDate fecha = LocalDate.parse(request.getFechaSeguimiento().trim());
            return convertirFechaSeguimientoATimestamp(fecha, HORA_INICIO_LABORAL, DEFAULT_ZONE);
        }

        throw new IllegalArgumentException("Debe proporcionar 'programadoPara' (timestamp exacto) o 'fechaSeguimiento' (fecha de seguimiento)");
    }

    /**
     * Genera un identificador de ocurrencia único e idempotente si no fue suministrado.
     */
    public static String generarOccurrenceId(String targetType, Long targetId, Instant programadoPara, String sufijo) {
        String base = "REM-" + targetType.trim().toUpperCase() + "-" + targetId + "-" + programadoPara.toEpochMilli();
        return (sufijo != null && !sufijo.isBlank()) ? base + "-" + sufijo.trim() : base;
    }

    @Transactional
    public ReminderResponse programarRecordatorio(Long usuarioId, CreateReminderRequest request) {
        Usuario usuario = usuarioRepository != null
                ? usuarioRepository.findById(usuarioId)
                    .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + usuarioId))
                : null;

        Instant programadoPara = resolverTimestampProgramado(request);
        String targetType = request.getTargetType().trim().toUpperCase();

        String occurrenceId = (request.getOccurrenceId() != null && !request.getOccurrenceId().isBlank())
                ? request.getOccurrenceId().trim()
                : generarOccurrenceId(targetType, request.getTargetId(), programadoPara, null);

        // Idempotencia: Si ya existe un recordatorio con este occurrenceId, se retorna el existente
        if (recordatorioRepository != null) {
            Optional<Recordatorio> existente = recordatorioRepository.findByOccurrenceId(occurrenceId);
            if (existente.isPresent()) {
                log.info("Recordatorio con occurrenceId {} ya existe. Retornando registro existente.", occurrenceId);
                return ReminderResponse.fromEntity(existente.get());
            }
        }

        Recordatorio recordatorio = Recordatorio.builder()
                .occurrenceId(occurrenceId)
                .usuario(usuario)
                .targetType(targetType)
                .targetId(request.getTargetId())
                .programadoPara(programadoPara)
                .estado("PENDIENTE")
                .creadoEn(Instant.now(clock))
                .build();

        Recordatorio guardado = recordatorioRepository != null ? recordatorioRepository.save(recordatorio) : recordatorio;
        return ReminderResponse.fromEntity(guardado);
    }

    @Transactional
    public ReminderResponse programarRecordatorioCita(AgendaCita cita, int minutosAntelacion) {
        if (cita == null || cita.getInicio() == null) {
            throw new IllegalArgumentException("La cita y su hora de inicio son obligatorias");
        }
        Instant programado = cita.getInicio().minus(minutosAntelacion, ChronoUnit.MINUTES);
        String occurrenceId = "REM-CITA-" + cita.getId() + "-" + minutosAntelacion + "M";

        if (recordatorioRepository != null) {
            Optional<Recordatorio> existente = recordatorioRepository.findByOccurrenceId(occurrenceId);
            if (existente.isPresent()) {
                return ReminderResponse.fromEntity(existente.get());
            }
        }

        Recordatorio recordatorio = Recordatorio.builder()
                .occurrenceId(occurrenceId)
                .usuario(cita.getUsuario())
                .targetType("CITA")
                .targetId(cita.getId())
                .programadoPara(programado)
                .estado("PENDIENTE")
                .creadoEn(Instant.now(clock))
                .build();

        Recordatorio guardado = recordatorioRepository != null ? recordatorioRepository.save(recordatorio) : recordatorio;
        return ReminderResponse.fromEntity(guardado);
    }

    @Transactional
    public ReminderResponse programarRecordatorioTarea(AgendaTarea tarea) {
        if (tarea == null || tarea.getFechaLimite() == null) {
            throw new IllegalArgumentException("La tarea y su fecha límite son obligatorias");
        }
        Instant programado = convertirFechaSeguimientoATimestamp(
                tarea.getFechaLimite(),
                tarea.getHoraLimite() != null ? tarea.getHoraLimite() : HORA_INICIO_LABORAL,
                DEFAULT_ZONE);

        String occurrenceId = "REM-TAREA-" + tarea.getId() + "-" + tarea.getFechaLimite();

        if (recordatorioRepository != null) {
            Optional<Recordatorio> existente = recordatorioRepository.findByOccurrenceId(occurrenceId);
            if (existente.isPresent()) {
                return ReminderResponse.fromEntity(existente.get());
            }
        }

        Recordatorio recordatorio = Recordatorio.builder()
                .occurrenceId(occurrenceId)
                .usuario(tarea.getUsuario())
                .targetType("TAREA")
                .targetId(tarea.getId())
                .programadoPara(programado)
                .estado("PENDIENTE")
                .creadoEn(Instant.now(clock))
                .build();

        Recordatorio guardado = recordatorioRepository != null ? recordatorioRepository.save(recordatorio) : recordatorio;
        return ReminderResponse.fromEntity(guardado);
    }

    /**
     * Proceso periódico e idempotente de despacho de recordatorios pendientes.
     * Garantiza que ningún recordatorio se dispare dos veces, incluso tras reinicios del servicio.
     */
    @Transactional
    public int procesarRecordatoriosPendientes(Instant ahora) {
        if (recordatorioRepository == null) {
            return 0;
        }

        Instant tiempoReferencia = ahora != null ? ahora : Instant.now(clock);
        List<Recordatorio> pendientes = recordatorioRepository.findPendingRemindersToDispatch(tiempoReferencia);
        if (pendientes.isEmpty()) {
            return 0;
        }

        int despachados = 0;
        for (Recordatorio r : pendientes) {
            // Transición atómica de estado para garantizar que solo una ejecución procese el recordatorio
            int actualizado = recordatorioRepository.updateEstadoIfCurrent(r.getId(), "PROCESADO", "PENDIENTE");
            if (actualizado == 0) {
                // Ya fue procesado o reclamado por otro hilo/nodo
                continue;
            }

            r.setEstado("PROCESADO");
            despacharNotificacionRecordatorio(r);
            despachados++;
        }

        log.info("Procesados {} recordatorios pendientes a las {}", despachados, tiempoReferencia);
        return despachados;
    }

    private void despacharNotificacionRecordatorio(Recordatorio r) {
        Long usuarioId = r.getUsuario() != null ? r.getUsuario().getId() : null;
        if (usuarioId == null) {
            return;
        }

        String tipo = "RECORDATORIO_" + r.getTargetType();
        String titulo = "Recordatorio pendiente";
        String mensaje = "Tienes una actividad programada en tu agenda.";
        String dataJson = null;

        if ("CITA".equalsIgnoreCase(r.getTargetType()) && agendaCitaRepository != null) {
            Optional<AgendaCita> citaOpt = agendaCitaRepository.findById(r.getTargetId());
            if (citaOpt.isPresent()) {
                AgendaCita c = citaOpt.get();
                if ("CANCELADA".equalsIgnoreCase(c.getEstado())) {
                    log.info("La cita {} asociada al recordatorio {} fue cancelada. Se omite notificación.", c.getId(), r.getOccurrenceId());
                    return;
                }
                titulo = "Recordatorio de Cita: " + c.getTitulo();
                mensaje = "Tienes la cita '" + c.getTitulo() + "' programada para iniciar próximamente.";
                dataJson = String.format("{\"targetType\":\"CITA\",\"targetId\":%d,\"inicio\":\"%s\"}", c.getId(), c.getInicio());
            }
        } else if ("TAREA".equalsIgnoreCase(r.getTargetType()) && agendaTareaRepository != null) {
            Optional<AgendaTarea> tareaOpt = agendaTareaRepository.findById(r.getTargetId());
            if (tareaOpt.isPresent()) {
                AgendaTarea t = tareaOpt.get();
                if ("COMPLETADA".equalsIgnoreCase(t.getEstado())) {
                    log.info("La tarea {} asociada al recordatorio {} ya fue completada. Se omite notificación.", t.getId(), r.getOccurrenceId());
                    return;
                }
                titulo = "Recordatorio de Tarea: " + t.getTitulo();
                mensaje = "La tarea '" + t.getTitulo() + "' vence el " + t.getFechaLimite() + ".";
                dataJson = String.format("{\"targetType\":\"TAREA\",\"targetId\":%d,\"fechaLimite\":\"%s\"}", t.getId(), t.getFechaLimite());
            }
        }

        Notification notif = Notification.builder()
                .usuario(r.getUsuario())
                .eventId(r.getOccurrenceId()) // UNIQUE en tabla notification garantiza idempotencia
                .tipo(tipo)
                .titulo(titulo)
                .mensaje(mensaje)
                .data(dataJson)
                .leida(false)
                .creadoEn(Instant.now(clock))
                .build();

        if (notificationRepository != null) {
            try {
                notificationRepository.save(notif);
            } catch (Exception ex) {
                log.warn("Notificación ya existía o falló inserción para occurrenceId {}: {}", r.getOccurrenceId(), ex.getMessage());
                return;
            }
        }

        // Emisión en tiempo real vía WebSocket si el usuario está conectado
        if (notificationWebSocketHandler != null) {
            try {
                notificationWebSocketHandler.sendNotificationToUser(usuarioId, NotificationResponse.fromEntity(notif));
            } catch (Exception ex) {
                log.warn("No se pudo emitir notificación en vivo por WebSocket al usuario {}: {}", usuarioId, ex.getMessage());
            }
        }
    }

    @Transactional(readOnly = true)
    public Page<ReminderResponse> obtenerRecordatoriosUsuario(Long usuarioId, String estado, Pageable pageable) {
        if (recordatorioRepository == null) {
            return Page.empty(pageable);
        }
        Page<Recordatorio> pagina = (estado != null && !estado.isBlank())
                ? recordatorioRepository.findByUsuarioIdAndEstadoOrderByProgramadoParaAsc(usuarioId, estado.toUpperCase(), pageable)
                : recordatorioRepository.findByUsuarioIdOrderByProgramadoParaAsc(usuarioId, pageable);

        return pagina.map(ReminderResponse::fromEntity);
    }

    @Transactional
    public void cancelarRecordatorio(Long id, Long usuarioId) {
        if (recordatorioRepository == null) {
            throw new ResourceNotFoundException("Recordatorio no encontrado con ID: " + id);
        }
        Recordatorio r = recordatorioRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Recordatorio no encontrado con ID: " + id));

        r.setEstado("CANCELADO");
        recordatorioRepository.save(r);
    }
}
