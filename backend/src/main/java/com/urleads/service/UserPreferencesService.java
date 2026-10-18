package com.urleads.service;

import com.urleads.dto.UserPreferencesRequest;
import com.urleads.dto.UserPreferencesResponse;
import com.urleads.entity.UserPreferences;
import com.urleads.entity.Usuario;
import com.urleads.exception.ResourceNotFoundException;
import com.urleads.repository.UserPreferencesRepository;
import com.urleads.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class UserPreferencesService {

    public static final String DEFAULT_ZONE = "America/Lima";
    public static final LocalTime DEFAULT_INICIO_JORNADA = LocalTime.of(9, 0);
    public static final LocalTime DEFAULT_FIN_JORNADA = LocalTime.of(18, 0);
    public static final String DEFAULT_DIAS_LABORALES = "1,2,3,4,5";

    private final UserPreferencesRepository userPreferencesRepository;
    private final UsuarioRepository usuarioRepository;

    @Autowired
    public UserPreferencesService(
            @Autowired(required = false) UserPreferencesRepository userPreferencesRepository,
            @Autowired(required = false) UsuarioRepository usuarioRepository
    ) {
        this.userPreferencesRepository = userPreferencesRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Valida si una cadena corresponde a un ZoneId IANA válido.
     */
    public static ZoneId validarZonaHoraria(String zonaHoraria) {
        if (zonaHoraria == null || zonaHoraria.isBlank()) {
            return ZoneId.of(DEFAULT_ZONE);
        }
        try {
            return ZoneId.of(zonaHoraria.trim());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Zona horaria inválida: '" + zonaHoraria + "'. Debe ser un identificador IANA válido (ej. 'America/Lima' o 'UTC')");
        }
    }

    /**
     * Valida que el fin de jornada sea estrictamente posterior al inicio.
     */
    public static void validarHorarioLaboral(LocalTime inicio, LocalTime fin) {
        if (inicio != null && fin != null) {
            if (!fin.isAfter(inicio)) {
                throw new IllegalArgumentException("La hora de fin de jornada (" + fin + ") debe ser posterior a la hora de inicio (" + inicio + ")");
            }
        }
    }

    @Transactional
    public UserPreferencesResponse obtenerPreferencias(Long usuarioId) {
        if (userPreferencesRepository == null) {
            throw new ResourceNotFoundException("Servicio de persistencia no disponible");
        }

        return userPreferencesRepository.findByUsuarioId(usuarioId)
                .map(UserPreferencesResponse::fromEntity)
                .orElseGet(() -> {
                    // Si no existen preferencias persistidas, crear y persistir las predeterminadas
                    Usuario usuario = usuarioRepository != null
                            ? usuarioRepository.findById(usuarioId).orElse(null)
                            : null;

                    if (usuario == null) {
                        // Respuesta con valores por defecto aunque el usuario no esté en BD (ej. en mocks)
                        UserPreferences defaults = UserPreferences.builder()
                                .usuario(new Usuario(usuarioId, "user_" + usuarioId, ""))
                                .zonaHoraria(DEFAULT_ZONE)
                                .inicioJornada(DEFAULT_INICIO_JORNADA)
                                .finJornada(DEFAULT_FIN_JORNADA)
                                .diasLaborales(DEFAULT_DIAS_LABORALES)
                                .notificacionesHabilitadas(true)
                                .canalWebsocket(true)
                                .canalEmail(false)
                                .build();
                        return UserPreferencesResponse.fromEntity(defaults);
                    }

                    UserPreferences nueva = UserPreferences.builder()
                            .usuario(usuario)
                            .zonaHoraria(DEFAULT_ZONE)
                            .inicioJornada(DEFAULT_INICIO_JORNADA)
                            .finJornada(DEFAULT_FIN_JORNADA)
                            .diasLaborales(DEFAULT_DIAS_LABORALES)
                            .notificacionesHabilitadas(true)
                            .canalWebsocket(true)
                            .canalEmail(false)
                            .creadoEn(Instant.now())
                            .actualizadoEn(Instant.now())
                            .build();

                    return UserPreferencesResponse.fromEntity(userPreferencesRepository.save(nueva));
                });
    }

    @Transactional
    public UserPreferencesResponse actualizarPreferencias(Long usuarioId, UserPreferencesRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("El cuerpo de la solicitud no puede ser nulo");
        }
        if (userPreferencesRepository == null) {
            throw new ResourceNotFoundException("Servicio de persistencia no disponible");
        }

        // 1. Obtener o crear entidad de preferencias
        UserPreferences prefs = userPreferencesRepository.findByUsuarioId(usuarioId)
                .orElseGet(() -> {
                    Usuario usuario = usuarioRepository != null
                            ? usuarioRepository.findById(usuarioId)
                                    .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + usuarioId))
                            : new Usuario(usuarioId, "user_" + usuarioId, "");
                    return UserPreferences.builder()
                            .usuario(usuario)
                            .zonaHoraria(DEFAULT_ZONE)
                            .inicioJornada(DEFAULT_INICIO_JORNADA)
                            .finJornada(DEFAULT_FIN_JORNADA)
                            .diasLaborales(DEFAULT_DIAS_LABORALES)
                            .notificacionesHabilitadas(true)
                            .canalWebsocket(true)
                            .canalEmail(false)
                            .creadoEn(Instant.now())
                            .actualizadoEn(Instant.now())
                            .build();
                });

        // 2. Validar y actualizar zona horaria
        if (request.getZonaHoraria() != null && !request.getZonaHoraria().isBlank()) {
            ZoneId zoneId = validarZonaHoraria(request.getZonaHoraria());
            prefs.setZonaHoraria(zoneId.getId());
        }

        // 3. Validar y actualizar horario laboral
        LocalTime nuevoInicio = request.getInicioJornada() != null ? request.getInicioJornada() : prefs.getInicioJornada();
        LocalTime nuevoFin = request.getFinJornada() != null ? request.getFinJornada() : prefs.getFinJornada();
        validarHorarioLaboral(nuevoInicio, nuevoFin);

        if (request.getInicioJornada() != null) {
            prefs.setInicioJornada(request.getInicioJornada());
        }
        if (request.getFinJornada() != null) {
            prefs.setFinJornada(request.getFinJornada());
        }

        if (request.getDiasLaborales() != null && !request.getDiasLaborales().isBlank()) {
            prefs.setDiasLaborales(request.getDiasLaborales().trim());
        }

        if (request.getNotificacionesHabilitadas() != null) {
            prefs.setNotificacionesHabilitadas(request.getNotificacionesHabilitadas());
        }

        // 4. Actualizar canales
        if (request.getCanalesHabilitados() != null) {
            List<String> canales = request.getCanalesHabilitados().stream()
                    .map(String::toUpperCase)
                    .toList();
            prefs.setCanalWebsocket(canales.contains("WEBSOCKET") || canales.contains("WS"));
            prefs.setCanalEmail(canales.contains("EMAIL") || canales.contains("CORREO"));
        }

        if (request.getCanalWebsocket() != null) {
            prefs.setCanalWebsocket(request.getCanalWebsocket());
        }
        if (request.getCanalEmail() != null) {
            prefs.setCanalEmail(request.getCanalEmail());
        }

        prefs.setActualizadoEn(Instant.now());
        UserPreferences guardada = userPreferencesRepository.save(prefs);
        return UserPreferencesResponse.fromEntity(guardada);
    }
}
