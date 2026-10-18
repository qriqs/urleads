package com.urleads.service;

import com.urleads.dto.AppointmentOutcomeRequest;
import com.urleads.dto.AppointmentOutcomeResponse;
import com.urleads.dto.AppointmentResponse;
import com.urleads.dto.CreateAppointmentRequest;
import com.urleads.dto.UpdateAppointmentRequest;
import com.urleads.entity.AgendaCita;
import com.urleads.entity.AgendaCitaMinuta;
import com.urleads.entity.Usuario;
import com.urleads.exception.ResourceNotFoundException;
import com.urleads.repository.AgendaCitaMinutaRepository;
import com.urleads.repository.AgendaCitaRepository;
import com.urleads.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AppointmentService {

    public static final String DEFAULT_ZONE = "America/Lima";
    public static final Set<String> ESTADOS_VALIDOS = Set.of("PROGRAMADA", "REALIZADA", "CANCELADA");

    private final AgendaCitaRepository agendaCitaRepository;
    private final UsuarioRepository usuarioRepository;
    private final LeadValidationService leadValidationService;
    private final AgendaCitaMinutaRepository agendaCitaMinutaRepository;

    public AppointmentService(
            AgendaCitaRepository agendaCitaRepository,
            UsuarioRepository usuarioRepository,
            LeadValidationService leadValidationService
    ) {
        this(agendaCitaRepository, usuarioRepository, leadValidationService, null);
    }

    @Autowired
    public AppointmentService(
            @Autowired(required = false) AgendaCitaRepository agendaCitaRepository,
            @Autowired(required = false) UsuarioRepository usuarioRepository,
            @Autowired(required = false) LeadValidationService leadValidationService,
            @Autowired(required = false) AgendaCitaMinutaRepository agendaCitaMinutaRepository
    ) {
        this.agendaCitaRepository = agendaCitaRepository;
        this.usuarioRepository = usuarioRepository;
        this.leadValidationService = leadValidationService;
        this.agendaCitaMinutaRepository = agendaCitaMinutaRepository;
    }

    /**
     * Valida y normaliza el identificador de zona horaria.
     */
    public static ZoneId parseZoneId(String zonaHoraria) {
        if (zonaHoraria == null || zonaHoraria.isBlank()) {
            return ZoneId.of(DEFAULT_ZONE);
        }
        try {
            return ZoneId.of(zonaHoraria.trim());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Zona horaria inválida: '" + zonaHoraria + "'. Ejemplo válido: 'America/Lima' o 'UTC'");
        }
    }

    /**
     * Parsea fechas ISO-8601 en UTC (Z), con offset explícito (-05:00) o local con zona fallback.
     */
    public static Instant parseIsoDateTime(String text, ZoneId fallbackZone) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("La fecha/hora no puede ser nula ni vacía");
        }
        String trimmed = text.trim();

        // 1. Instant directo en UTC (ej. 2026-10-25T15:00:00Z)
        try {
            return Instant.parse(trimmed);
        } catch (DateTimeParseException ignored) {
        }

        // 2. OffsetDateTime con desplazamiento explícito (ej. 2026-10-25T10:00:00-05:00)
        try {
            return OffsetDateTime.parse(trimmed).toInstant();
        } catch (DateTimeParseException ignored) {
        }

        // 3. ZonedDateTime con zona explícita (ej. 2026-10-25T10:00:00-05:00[America/Lima])
        try {
            return ZonedDateTime.parse(trimmed).toInstant();
        } catch (DateTimeParseException ignored) {
        }

        // 4. LocalDateTime local interpretado en la zona fallback
        try {
            return LocalDateTime.parse(trimmed).atZone(fallbackZone).toInstant();
        } catch (DateTimeParseException ignored) {
        }

        throw new IllegalArgumentException("Formato de fecha/hora inválido: '" + text + "'. Debe cumplir el estándar ISO-8601 (ej. '2026-10-25T10:00:00-05:00' o '2026-10-25T15:00:00Z')");
    }

    /**
     * Valida y normaliza el estado de la cita.
     */
    public static String validarEstado(String estado) {
        if (estado == null || estado.isBlank()) {
            return "PROGRAMADA";
        }
        String normalizado = estado.trim().toUpperCase();
        if (!ESTADOS_VALIDOS.contains(normalizado)) {
            throw new IllegalArgumentException("Estado de cita inválido: '" + estado + "'. Estados permitidos: PROGRAMADA, REALIZADA, CANCELADA");
        }
        return normalizado;
    }

    /**
     * Valida que la fecha de fin sea estrictamente posterior a la fecha de inicio.
     */
    public static void validarHorario(Instant inicio, Instant fin) {
        if (inicio == null || fin == null) {
            throw new IllegalArgumentException("Las fechas de inicio y fin son obligatorias");
        }
        if (!fin.isAfter(inicio)) {
            throw new IllegalArgumentException("La fecha de fin debe ser posterior a la de inicio");
        }
    }

    @Transactional
    public AppointmentResponse crearCita(Long usuarioId, CreateAppointmentRequest request, String token) {
        // 1. Validación de lead si está presente
        if (request.getLeadId() != null && leadValidationService != null) {
            leadValidationService.validarLeadPerteneceAUsuario(request.getLeadId(), token);
        }

        // 2. Validación de zona horaria
        ZoneId zoneId = parseZoneId(request.getZonaHoraria());
        String zonaStr = zoneId.getId();

        // 3. Parseo y validación estricta de fechas
        Instant inicio = parseIsoDateTime(request.getInicio(), zoneId);
        Instant fin = parseIsoDateTime(request.getFin(), zoneId);
        validarHorario(inicio, fin);

        // 4. Validación de estado
        String estado = validarEstado(request.getEstado());

        // 5. Cargar usuario
        Usuario usuario = usuarioRepository != null
                ? usuarioRepository.findById(usuarioId)
                    .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + usuarioId))
                : null;

        AgendaCita cita = AgendaCita.builder()
                .usuario(usuario)
                .leadId(request.getLeadId())
                .titulo(request.getTitulo())
                .descripcion(request.getDescripcion())
                .inicio(inicio)
                .fin(fin)
                .zonaHoraria(zonaStr)
                .estado(estado)
                .creadoEn(Instant.now())
                .actualizadoEn(Instant.now())
                .build();

        AgendaCita guardada = agendaCitaRepository != null ? agendaCitaRepository.save(cita) : cita;
        return AppointmentResponse.fromEntity(guardada);
    }

    @Transactional(readOnly = true)
    public Page<AppointmentResponse> obtenerCitas(
            Long usuarioId,
            Long leadId,
            String estado,
            Instant desde,
            Instant hasta,
            Pageable pageable
    ) {
        if (agendaCitaRepository == null) {
            return Page.empty(pageable);
        }

        String estadoNormalizado = (estado != null && !estado.isBlank()) ? validarEstado(estado) : null;

        Page<AgendaCita> pagina;
        if (desde != null && hasta != null) {
            pagina = agendaCitaRepository.findByUsuarioIdAndInicioBetweenOrderByInicioAscIdAsc(
                    usuarioId, desde, hasta, pageable);
        } else if (leadId != null && estadoNormalizado != null) {
            pagina = agendaCitaRepository.findByUsuarioIdAndEstadoAndLeadIdOrderByInicioAscIdAsc(
                    usuarioId, estadoNormalizado, leadId, pageable);
        } else if (leadId != null) {
            pagina = agendaCitaRepository.findByUsuarioIdAndLeadIdOrderByInicioAscIdAsc(
                    usuarioId, leadId, pageable);
        } else if (estadoNormalizado != null) {
            pagina = agendaCitaRepository.findByUsuarioIdAndEstadoOrderByInicioAscIdAsc(
                    usuarioId, estadoNormalizado, pageable);
        } else {
            pagina = agendaCitaRepository.findByUsuarioIdOrderByInicioAscIdAsc(usuarioId, pageable);
        }

        return pagina.map(AppointmentResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public AppointmentResponse obtenerCitaPorId(Long id, Long usuarioId) {
        if (agendaCitaRepository == null) {
            throw new ResourceNotFoundException("Cita no encontrada con ID: " + id);
        }
        AgendaCita cita = agendaCitaRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada con ID: " + id));
        return AppointmentResponse.fromEntity(cita);
    }

    @Transactional
    public AppointmentResponse actualizarCita(Long id, Long usuarioId, UpdateAppointmentRequest request, String token) {
        if (agendaCitaRepository == null) {
            throw new ResourceNotFoundException("Cita no encontrada con ID: " + id);
        }

        AgendaCita cita = agendaCitaRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada con ID: " + id));

        // Validar lead si se modifica o asigna
        if (request.getLeadId() != null && !Objects.equals(request.getLeadId(), cita.getLeadId())) {
            if (leadValidationService != null) {
                leadValidationService.validarLeadPerteneceAUsuario(request.getLeadId(), token);
            }
        }

        ZoneId zoneId = parseZoneId(request.getZonaHoraria() != null ? request.getZonaHoraria() : cita.getZonaHoraria());
        Instant inicio = parseIsoDateTime(request.getInicio(), zoneId);
        Instant fin = parseIsoDateTime(request.getFin(), zoneId);
        validarHorario(inicio, fin);

        String estado = (request.getEstado() != null && !request.getEstado().isBlank())
                ? validarEstado(request.getEstado())
                : cita.getEstado();

        cita.setTitulo(request.getTitulo());
        cita.setDescripcion(request.getDescripcion());
        cita.setLeadId(request.getLeadId());
        cita.setInicio(inicio);
        cita.setFin(fin);
        cita.setZonaHoraria(zoneId.getId());
        cita.setEstado(estado);
        cita.setActualizadoEn(Instant.now());

        AgendaCita guardada = agendaCitaRepository.save(cita);
        return AppointmentResponse.fromEntity(guardada);
    }

    @Transactional
    public AppointmentResponse actualizarEstadoCita(Long id, Long usuarioId, String nuevoEstado) {
        if (agendaCitaRepository == null) {
            throw new ResourceNotFoundException("Cita no encontrada con ID: " + id);
        }

        AgendaCita cita = agendaCitaRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada con ID: " + id));

        String estadoNormalizado = validarEstado(nuevoEstado);
        cita.setEstado(estadoNormalizado);
        cita.setActualizadoEn(Instant.now());

        AgendaCita guardada = agendaCitaRepository.save(cita);
        return AppointmentResponse.fromEntity(guardada);
    }

    @Transactional
    public void eliminarCita(Long id, Long usuarioId) {
        if (agendaCitaRepository == null) {
            throw new ResourceNotFoundException("Cita no encontrada con ID: " + id);
        }

        AgendaCita cita = agendaCitaRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada con ID: " + id));

        agendaCitaRepository.delete(cita);
    }

    /**
     * Detección y verificación de citas solapadas para el usuario autenticado.
     */
    @Transactional(readOnly = true)
    public List<AppointmentResponse> verificarSolapamientos(Long usuarioId, Instant inicio, Instant fin, Long excludeId) {
        if (agendaCitaRepository == null) {
            return List.of();
        }
        List<AgendaCita> overlapping = (excludeId != null)
                ? agendaCitaRepository.findOverlappingExcludingId(usuarioId, excludeId, inicio, fin)
                : agendaCitaRepository.findOverlapping(usuarioId, inicio, fin);

        return overlapping.stream()
                .map(AppointmentResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public boolean tieneSolapamiento(Long usuarioId, Instant inicio, Instant fin, Long excludeId) {
        return !verificarSolapamientos(usuarioId, inicio, fin, excludeId).isEmpty();
    }

    /**
     * Registra o actualiza el resultado estructurado, acuerdos y minuta al finalizar una cita.
     * Almacenamiento exclusivamente local en Agenda; sin llamadas ni inyección en el historial de CRM.
     */
    @Transactional
    public AppointmentOutcomeResponse registrarResultadoCita(Long citaId, Long usuarioId, AppointmentOutcomeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("El cuerpo de la solicitud no puede ser nulo");
        }
        if (agendaCitaRepository == null || agendaCitaMinutaRepository == null) {
            throw new ResourceNotFoundException("Servicio de persistencia de citas o minutas no disponible");
        }

        String resumen = (request.getResumen() != null && !request.getResumen().isBlank())
                ? request.getResumen().trim()
                : null;
        String acuerdos = (request.getAcuerdos() != null && !request.getAcuerdos().isBlank())
                ? request.getAcuerdos().trim()
                : null;
        String proximasAcciones = (request.getProximasAcciones() != null && !request.getProximasAcciones().isBlank())
                ? request.getProximasAcciones().trim()
                : null;

        if (resumen == null && acuerdos == null && proximasAcciones == null) {
            throw new IllegalArgumentException("Debe proporcionar al menos un resumen, acuerdo o próxima acción para registrar el resultado");
        }

        if (resumen == null) {
            resumen = "Resultado de cita registrado";
        }

        // 1. Obtener cita y verificar titularidad estricta del usuario autenticado
        AgendaCita cita = agendaCitaRepository.findByIdAndUsuarioId(citaId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada con ID: " + citaId));

        // 2. Determinar y actualizar estado de la cita si corresponde
        String resultadoNormalizado = (request.getResultado() != null && !request.getResultado().isBlank())
                ? request.getResultado().trim()
                : "REALIZADA";

        boolean actualizarEstado = request.getActualizarEstadoCita() == null || Boolean.TRUE.equals(request.getActualizarEstadoCita());
        if (actualizarEstado) {
            if (ESTADOS_VALIDOS.contains(resultadoNormalizado.toUpperCase())) {
                cita.setEstado(resultadoNormalizado.toUpperCase());
            } else if ("PROGRAMADA".equals(cita.getEstado())) {
                cita.setEstado("REALIZADA");
            }
            cita.setActualizadoEn(Instant.now());
            agendaCitaRepository.save(cita);
        }

        // 3. Persistir minuta/resultado en Agenda (operación idempotente)
        AgendaCitaMinuta minuta = agendaCitaMinutaRepository.findByCitaIdAndUsuarioId(citaId, usuarioId)
                .orElseGet(() -> AgendaCitaMinuta.builder()
                        .cita(cita)
                        .usuario(cita.getUsuario())
                        .creadoEn(Instant.now())
                        .build());

        minuta.setResumen(resumen);
        minuta.setAcuerdos(acuerdos);
        minuta.setProximasAcciones(proximasAcciones);
        minuta.setResultado(resultadoNormalizado);
        if (request.getAsistencia() != null && !request.getAsistencia().isBlank()) {
            minuta.setAsistencia(request.getAsistencia().trim());
        }
        minuta.setActualizadoEn(Instant.now());

        AgendaCitaMinuta guardada = agendaCitaMinutaRepository.save(minuta);
        return AppointmentOutcomeResponse.fromEntity(guardada);
    }

    /**
     * Consulta el resultado y minuta estructurada de una cita del usuario autenticado.
     */
    @Transactional(readOnly = true)
    public AppointmentOutcomeResponse obtenerResultadoCita(Long citaId, Long usuarioId) {
        if (agendaCitaRepository == null || agendaCitaMinutaRepository == null) {
            throw new ResourceNotFoundException("Servicio de persistencia no disponible");
        }

        agendaCitaRepository.findByIdAndUsuarioId(citaId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada con ID: " + citaId));

        AgendaCitaMinuta minuta = agendaCitaMinutaRepository.findByCitaIdAndUsuarioId(citaId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("No se ha registrado resultado o minuta para la cita ID: " + citaId));

        return AppointmentOutcomeResponse.fromEntity(minuta);
    }
}
