package com.urleads.service;

import com.urleads.dto.UserPreferencesRequest;
import com.urleads.dto.UserPreferencesResponse;
import com.urleads.entity.UserPreferences;
import com.urleads.entity.Usuario;
import com.urleads.repository.UserPreferencesRepository;
import com.urleads.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UserPreferencesServiceTest {

    private UserPreferencesRepository userPreferencesRepository;
    private UsuarioRepository usuarioRepository;
    private UserPreferencesService userPreferencesService;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        userPreferencesRepository = mock(UserPreferencesRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        userPreferencesService = new UserPreferencesService(userPreferencesRepository, usuarioRepository);

        usuario = new Usuario(42L, "alex", "pass");
        when(usuarioRepository.findById(42L)).thenReturn(Optional.of(usuario));
    }

    @Test
    @DisplayName("validarZonaHoraria: zonas IANA válidas son aceptadas")
    void validarZonaHoraria_validZones_pass() {
        assertThat(UserPreferencesService.validarZonaHoraria("America/Lima")).isEqualTo(ZoneId.of("America/Lima"));
        assertThat(UserPreferencesService.validarZonaHoraria("UTC")).isEqualTo(ZoneId.of("UTC"));
        assertThat(UserPreferencesService.validarZonaHoraria("America/Bogota")).isEqualTo(ZoneId.of("America/Bogota"));
        assertThat(UserPreferencesService.validarZonaHoraria(null)).isEqualTo(ZoneId.of("America/Lima"));
        assertThat(UserPreferencesService.validarZonaHoraria("")).isEqualTo(ZoneId.of("America/Lima"));
    }

    @Test
    @DisplayName("validarZonaHoraria: zona inválida lanza IllegalArgumentException")
    void validarZonaHoraria_invalidZone_throwsIllegalArgument() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                UserPreferencesService.validarZonaHoraria("ZonaInvalida/Falsa"));

        assertThat(ex.getMessage()).contains("Zona horaria inválida");
    }

    @Test
    @DisplayName("validarHorarioLaboral: fin posterior a inicio pasa validación")
    void validarHorarioLaboral_validHours_pass() {
        UserPreferencesService.validarHorarioLaboral(LocalTime.of(9, 0), LocalTime.of(18, 0));
        UserPreferencesService.validarHorarioLaboral(LocalTime.of(8, 30), LocalTime.of(17, 30));
    }

    @Test
    @DisplayName("validarHorarioLaboral: fin igual o anterior a inicio lanza IllegalArgumentException")
    void validarHorarioLaboral_invalidHours_throwsIllegalArgument() {
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () ->
                UserPreferencesService.validarHorarioLaboral(LocalTime.of(18, 0), LocalTime.of(9, 0)));
        assertThat(ex1.getMessage()).contains("debe ser posterior a la hora de inicio");

        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () ->
                UserPreferencesService.validarHorarioLaboral(LocalTime.of(9, 0), LocalTime.of(9, 0)));
        assertThat(ex2.getMessage()).contains("debe ser posterior a la hora de inicio");
    }

    @Test
    @DisplayName("obtenerPreferencias: retorna preferencias por defecto si aún no han sido guardadas")
    void obtenerPreferencias_nonExistent_returnsDefaults() {
        when(userPreferencesRepository.findByUsuarioId(42L)).thenReturn(Optional.empty());
        when(userPreferencesRepository.save(any(UserPreferences.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserPreferencesResponse response = userPreferencesService.obtenerPreferencias(42L);

        assertThat(response).isNotNull();
        assertThat(response.getZonaHoraria()).isEqualTo("America/Lima");
        assertThat(response.getInicioJornada()).isEqualTo(LocalTime.of(9, 0));
        assertThat(response.getFinJornada()).isEqualTo(LocalTime.of(18, 0));
        assertThat(response.getCanalWebsocket()).isTrue();
        assertThat(response.getCanalEmail()).isFalse();
        assertThat(response.getNotificacionesHabilitadas()).isTrue();
    }

    @Test
    @DisplayName("obtenerPreferencias: retorna preferencias existentes persistidas")
    void obtenerPreferencias_existing_returnsStored() {
        UserPreferences stored = UserPreferences.builder()
                .id(1L)
                .usuario(usuario)
                .zonaHoraria("America/Bogota")
                .inicioJornada(LocalTime.of(8, 0))
                .finJornada(LocalTime.of(17, 0))
                .diasLaborales("1,2,3,4,5,6")
                .notificacionesHabilitadas(true)
                .canalWebsocket(true)
                .canalEmail(true)
                .build();

        when(userPreferencesRepository.findByUsuarioId(42L)).thenReturn(Optional.of(stored));

        UserPreferencesResponse response = userPreferencesService.obtenerPreferencias(42L);

        assertThat(response).isNotNull();
        assertThat(response.getZonaHoraria()).isEqualTo("America/Bogota");
        assertThat(response.getInicioJornada()).isEqualTo(LocalTime.of(8, 0));
        assertThat(response.getFinJornada()).isEqualTo(LocalTime.of(17, 0));
        assertThat(response.getCanalesHabilitados()).containsExactlyInAnyOrder("WEBSOCKET", "EMAIL");
    }

    @Test
    @DisplayName("actualizarPreferencias: actualiza zona horaria, horario laboral y canales válidos")
    void actualizarPreferencias_validRequest_updatesAndReturns() {
        UserPreferences existing = UserPreferences.builder()
                .id(1L)
                .usuario(usuario)
                .zonaHoraria("America/Lima")
                .inicioJornada(LocalTime.of(9, 0))
                .finJornada(LocalTime.of(18, 0))
                .build();

        when(userPreferencesRepository.findByUsuarioId(42L)).thenReturn(Optional.of(existing));
        when(userPreferencesRepository.save(any(UserPreferences.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserPreferencesRequest request = UserPreferencesRequest.builder()
                .zonaHoraria("Europe/Madrid")
                .inicioJornada(LocalTime.of(8, 0))
                .finJornada(LocalTime.of(16, 30))
                .diasLaborales("1,2,3,4")
                .canalesHabilitados(List.of("WEBSOCKET"))
                .build();

        UserPreferencesResponse response = userPreferencesService.actualizarPreferencias(42L, request);

        assertThat(response.getZonaHoraria()).isEqualTo("Europe/Madrid");
        assertThat(response.getInicioJornada()).isEqualTo(LocalTime.of(8, 0));
        assertThat(response.getFinJornada()).isEqualTo(LocalTime.of(16, 30));
        assertThat(response.getDiasLaborales()).isEqualTo("1,2,3,4");
        assertThat(response.getCanalWebsocket()).isTrue();
        assertThat(response.getCanalEmail()).isFalse();
    }

    @Test
    @DisplayName("actualizarPreferencias: rechaza zona horaria inválida")
    void actualizarPreferencias_invalidZone_throwsIllegalArgument() {
        UserPreferencesRequest request = UserPreferencesRequest.builder()
                .zonaHoraria("Planeta/Marte")
                .build();

        assertThrows(IllegalArgumentException.class, () ->
                userPreferencesService.actualizarPreferencias(42L, request));
    }

    @Test
    @DisplayName("actualizarPreferencias: rechaza horario inconsistente")
    void actualizarPreferencias_invalidBusinessHours_throwsIllegalArgument() {
        UserPreferencesRequest request = UserPreferencesRequest.builder()
                .inicioJornada(LocalTime.of(18, 0))
                .finJornada(LocalTime.of(9, 0))
                .build();

        assertThrows(IllegalArgumentException.class, () ->
                userPreferencesService.actualizarPreferencias(42L, request));
    }
}
