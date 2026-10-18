package com.urleads.service;

import com.urleads.dto.AgendaItemResponse;
import com.urleads.dto.AppointmentResponse;
import com.urleads.dto.TaskResponse;
import com.urleads.entity.AgendaCita;
import com.urleads.entity.AgendaTarea;
import com.urleads.repository.AgendaCitaRepository;
import com.urleads.repository.AgendaTareaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class AgendaQueryService {

    public static final int MAX_DAYS_WINDOW = 31;
    public static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Lima");

    private final AgendaTareaRepository agendaTareaRepository;
    private final AgendaCitaRepository agendaCitaRepository;

    @Autowired
    public AgendaQueryService(
            @Autowired(required = false) AgendaTareaRepository agendaTareaRepository,
            @Autowired(required = false) AgendaCitaRepository agendaCitaRepository
    ) {
        this.agendaTareaRepository = agendaTareaRepository;
        this.agendaCitaRepository = agendaCitaRepository;
    }

    /**
     * Parsea fechas YYYY-MM-DD o ISO-8601 a LocalDate en la zona horaria de referencia.
     */
    public static LocalDate parseLocalDate(String text, ZoneId zoneId) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();
        // 1. Formato estándar YYYY-MM-DD
        try {
            return LocalDate.parse(trimmed);
        } catch (DateTimeParseException ignored) {
        }
        // 2. Timestamps ISO-8601 en UTC (Z)
        try {
            return Instant.parse(trimmed).atZone(zoneId).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }
        // 3. OffsetDateTime con desplazamiento numérico
        try {
            return OffsetDateTime.parse(trimmed).atZoneSameInstant(zoneId).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }
        // 4. LocalDateTime local
        try {
            return LocalDateTime.parse(trimmed).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }
        throw new IllegalArgumentException("Formato de fecha inválido para '" + text + "'. Debe cumplir formato YYYY-MM-DD o ISO-8601 (ej. '2026-10-01' o '2026-10-01T00:00:00-05:00')");
    }

    /**
     * Valida que el rango de consulta no sea nulo, 'hasta' no sea anterior a 'desde'
     * y la ventana no supere los 31 días máximos permitidos.
     */
    public static void validarVentanaTemporal(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            throw new IllegalArgumentException("Las fechas 'desde' y 'hasta' son obligatorias para consultar la agenda");
        }
        if (hasta.isBefore(desde)) {
            throw new IllegalArgumentException("La fecha 'hasta' no puede ser anterior a la fecha 'desde'");
        }
        long dias = ChronoUnit.DAYS.between(desde, hasta);
        if (dias > MAX_DAYS_WINDOW) {
            throw new IllegalArgumentException("El rango de consulta no puede superar los 31 días (máximo permitido: 31 días, solicitado: " + dias + " días)");
        }
    }

    @Transactional(readOnly = true)
    public Page<AgendaItemResponse> obtenerAgendaCombinada(
            Long usuarioId,
            String desdeStr,
            String hastaStr,
            Long leadId,
            String tipoFiltro,
            Pageable pageable
    ) {
        LocalDate desde = (desdeStr != null && !desdeStr.isBlank())
                ? parseLocalDate(desdeStr, DEFAULT_ZONE)
                : LocalDate.now(DEFAULT_ZONE);

        LocalDate hasta = (hastaStr != null && !hastaStr.isBlank())
                ? parseLocalDate(hastaStr, DEFAULT_ZONE)
                : desde.plusDays(7);

        validarVentanaTemporal(desde, hasta);

        List<AgendaItemResponse> items = new ArrayList<>();
        boolean incluirTareas = tipoFiltro == null || tipoFiltro.isBlank() || "ALL".equalsIgnoreCase(tipoFiltro) || "TAREA".equalsIgnoreCase(tipoFiltro);
        boolean incluirCitas = tipoFiltro == null || tipoFiltro.isBlank() || "ALL".equalsIgnoreCase(tipoFiltro) || "CITA".equalsIgnoreCase(tipoFiltro);

        // 1. Obtener tareas dentro del rango
        if (incluirTareas && agendaTareaRepository != null) {
            List<AgendaTarea> tareas = (leadId != null)
                    ? agendaTareaRepository.findByUsuarioIdAndLeadIdAndFechaLimiteBetweenOrderByFechaLimiteAscHoraLimiteAscIdAsc(
                            usuarioId, leadId, desde, hasta)
                    : agendaTareaRepository.findByUsuarioIdAndFechaLimiteBetweenOrderByFechaLimiteAscHoraLimiteAscIdAsc(
                            usuarioId, desde, hasta);

            for (AgendaTarea tarea : tareas) {
                LocalTime hora = tarea.getHoraLimite();
                Instant momentoUtc = (tarea.getFechaLimite() != null)
                        ? ZonedDateTime.of(tarea.getFechaLimite(), hora != null ? hora : LocalTime.of(9, 0), DEFAULT_ZONE).toInstant()
                        : tarea.getCreadoEn();

                items.add(AgendaItemResponse.builder()
                        .tipo("TAREA")
                        .id(tarea.getId())
                        .referencia("TAREA-" + tarea.getId())
                        .leadId(tarea.getLeadId())
                        .titulo(tarea.getTitulo())
                        .descripcion(tarea.getDescripcion())
                        .fecha(tarea.getFechaLimite())
                        .hora(hora)
                        .momentoUtc(momentoUtc)
                        .finUtc(null)
                        .estado(tarea.getEstado())
                        .tarea(TaskResponse.fromEntity(tarea))
                        .cita(null)
                        .build());
            }
        }

        // 2. Obtener citas dentro del rango
        if (incluirCitas && agendaCitaRepository != null) {
            Instant desdeInstant = desde.atStartOfDay(DEFAULT_ZONE).toInstant();
            Instant hastaInstant = hasta.atTime(LocalTime.MAX).atZone(DEFAULT_ZONE).toInstant();

            List<AgendaCita> citas = (leadId != null)
                    ? agendaCitaRepository.findByUsuarioIdAndLeadIdAndInicioBetweenOrderByInicioAscIdAsc(
                            usuarioId, leadId, desdeInstant, hastaInstant)
                    : agendaCitaRepository.findByUsuarioIdAndInicioBetweenOrderByInicioAscIdAsc(
                            usuarioId, desdeInstant, hastaInstant);

            for (AgendaCita cita : citas) {
                ZoneId citaZone = ZoneId.of(cita.getZonaHoraria() != null && !cita.getZonaHoraria().isBlank()
                        ? cita.getZonaHoraria() : DEFAULT_ZONE.getId());
                ZonedDateTime zdt = cita.getInicio().atZone(citaZone);

                items.add(AgendaItemResponse.builder()
                        .tipo("CITA")
                        .id(cita.getId())
                        .referencia("CITA-" + cita.getId())
                        .leadId(cita.getLeadId())
                        .titulo(cita.getTitulo())
                        .descripcion(cita.getDescripcion())
                        .fecha(zdt.toLocalDate())
                        .hora(zdt.toLocalTime())
                        .momentoUtc(cita.getInicio())
                        .finUtc(cita.getFin())
                        .estado(cita.getEstado())
                        .tarea(null)
                        .cita(AppointmentResponse.fromEntity(cita))
                        .build());
            }
        }

        // 3. Ordenamiento determinista estable: momentoUtc ASC -> tipo ASC -> id ASC
        items.sort(Comparator
                .comparing(AgendaItemResponse::getMomentoUtc, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AgendaItemResponse::getTipo)
                .thenComparing(AgendaItemResponse::getId));

        // 4. Paginación en memoria del rango acotado
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), items.size());
        List<AgendaItemResponse> pageContent = (start < items.size() && start >= 0)
                ? items.subList(start, end)
                : List.of();

        return new PageImpl<>(pageContent, pageable, items.size());
    }
}
