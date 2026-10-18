package com.urleads.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urleads.dto.UserPreferencesRequest;
import com.urleads.dto.UserPreferencesResponse;
import com.urleads.entity.Usuario;
import com.urleads.repository.*;
import com.urleads.security.CurrentUserProvider;
import com.urleads.security.JwtService;
import com.urleads.service.UserPreferencesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class UserPreferencesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private UserPreferencesService userPreferencesService;

    @MockBean
    private CurrentUserProvider currentUserProvider;

    @MockBean
    private UsuarioRepository usuarioRepository;

    @MockBean
    private NotaRepository notaRepository;

    @MockBean
    private NotificationRepository notificationRepository;

    @MockBean
    private AgendaTareaRepository agendaTareaRepository;

    @MockBean
    private AgendaCitaRepository agendaCitaRepository;

    @MockBean
    private AgendaCitaMinutaRepository agendaCitaMinutaRepository;

    @MockBean
    private LeadRepository leadRepository;

    private String validToken;

    @BeforeEach
    void setUp() {
        Usuario usuario = new Usuario(42L, "alex", "hash");
        when(currentUserProvider.getCurrentUserId()).thenReturn(42L);
        when(usuarioRepository.findById(42L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.findByUsername("42")).thenReturn(Optional.of(usuario));
        when(usuarioRepository.findByUsername("alex")).thenReturn(Optional.of(usuario));

        validToken = "Bearer " + jwtService.generateToken("42");
    }

    @Test
    @DisplayName("GET /api/v1/preferences retorna 200 OK con las preferencias del usuario autenticado")
    void getPreferences_authenticated_returns200() throws Exception {
        UserPreferencesResponse response = UserPreferencesResponse.builder()
                .id(1L)
                .usuarioId(42L)
                .zonaHoraria("America/Lima")
                .inicioJornada(LocalTime.of(9, 0))
                .finJornada(LocalTime.of(18, 0))
                .diasLaborales("1,2,3,4,5")
                .notificacionesHabilitadas(true)
                .canalWebsocket(true)
                .canalEmail(false)
                .canalesHabilitados(List.of("WEBSOCKET"))
                .build();

        when(userPreferencesService.obtenerPreferencias(42L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/preferences")
                        .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(42L))
                .andExpect(jsonPath("$.zonaHoraria").value("America/Lima"))
                .andExpect(jsonPath("$.inicioJornada").value("09:00"))
                .andExpect(jsonPath("$.finJornada").value("18:00"))
                .andExpect(jsonPath("$.canalWebsocket").value(true))
                .andExpect(jsonPath("$.canalEmail").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/preferences sin token de autenticación retorna 401 Unauthorized")
    void getPreferences_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/preferences"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /api/v1/preferences actualiza preferencias del usuario y retorna 200 OK")
    void updatePreferences_authenticated_returns200() throws Exception {
        UserPreferencesRequest request = UserPreferencesRequest.builder()
                .zonaHoraria("America/Bogota")
                .inicioJornada(LocalTime.of(8, 30))
                .finJornada(LocalTime.of(17, 30))
                .diasLaborales("1,2,3,4,5")
                .canalesHabilitados(List.of("WEBSOCKET", "EMAIL"))
                .build();

        UserPreferencesResponse updated = UserPreferencesResponse.builder()
                .id(1L)
                .usuarioId(42L)
                .zonaHoraria("America/Bogota")
                .inicioJornada(LocalTime.of(8, 30))
                .finJornada(LocalTime.of(17, 30))
                .diasLaborales("1,2,3,4,5")
                .canalWebsocket(true)
                .canalEmail(true)
                .canalesHabilitados(List.of("WEBSOCKET", "EMAIL"))
                .build();

        when(userPreferencesService.actualizarPreferencias(eq(42L), any(UserPreferencesRequest.class)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/v1/preferences")
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zonaHoraria").value("America/Bogota"))
                .andExpect(jsonPath("$.inicioJornada").value("08:30"))
                .andExpect(jsonPath("$.finJornada").value("17:30"))
                .andExpect(jsonPath("$.canalWebsocket").value(true))
                .andExpect(jsonPath("$.canalEmail").value(true));
    }

    @Test
    @DisplayName("PUT /api/v1/preferences con zona horaria inválida retorna 400 Bad Request")
    void updatePreferences_invalidZone_returns400() throws Exception {
        UserPreferencesRequest request = UserPreferencesRequest.builder()
                .zonaHoraria("ZonaInvalida")
                .build();

        when(userPreferencesService.actualizarPreferencias(eq(42L), any(UserPreferencesRequest.class)))
                .thenThrow(new IllegalArgumentException("Zona horaria inválida: 'ZonaInvalida'"));

        mockMvc.perform(put("/api/v1/preferences")
                        .header("Authorization", validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Zona horaria inválida: 'ZonaInvalida'"));
    }

    @Test
    @DisplayName("PUT /api/v1/preferences sin token de autenticación retorna 401 Unauthorized")
    void updatePreferences_unauthenticated_returns401() throws Exception {
        UserPreferencesRequest request = UserPreferencesRequest.builder()
                .zonaHoraria("America/Lima")
                .build();

        mockMvc.perform(put("/api/v1/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
