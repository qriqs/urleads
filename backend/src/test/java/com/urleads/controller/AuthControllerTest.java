package com.urleads.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urleads.dto.LoginRequest;
import com.urleads.entity.Usuario;
import com.urleads.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String USUARIO_TEST = "admin";
    private static final String PASSWORD_TEST = "Password123!";
    private String hashGuardado;

    @BeforeEach
    void setUp() {
        usuarioRepository.deleteAll();
        hashGuardado = passwordEncoder.encode(PASSWORD_TEST);
        usuarioRepository.save(new Usuario(USUARIO_TEST, hashGuardado));
    }

    // =========================================================================
    // BLOQUE UL-06a: AUTENTICACIÓN Y GESTIÓN DE CREDENCIALES
    // =========================================================================

    @Nested
    @DisplayName("UL-06a: Pruebas de Autenticación")
    class AutenticacionTests {

        @Test
        @DisplayName("Credenciales válidas autentican, crean sesión y ocultan credenciales/hash")
        void login_ConCredencialesValidas_GeneraSesionYNoExponeSecretos() throws Exception {
            LoginRequest request = new LoginRequest(USUARIO_TEST, PASSWORD_TEST);

            MvcResult result = mockMvc.perform(post("/api/auth/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is(USUARIO_TEST)))
                // Criterio de aceptación: secretos no aparecen en la respuesta
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(content().string(not(containsString(hashGuardado))))
                .andExpect(content().string(not(containsString(PASSWORD_TEST))))
                .andReturn();

            MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
            assertNotNull(session, "Debe existir una sesión creada en el servidor");
        }

        @Test
        @DisplayName("POST /login con campos inválidos retorna 400 Bad Request")
        void login_ConPayloadInvalido_Retorna400() throws Exception {
            LoginRequest requestInvalido = new LoginRequest("", "");

            mockMvc.perform(post("/api/auth/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestInvalido)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Contraseña incorrecta devuelve error 401 no revelador")
        void login_ConPasswordIncorrecto_Retorna401() throws Exception {
            LoginRequest request = new LoginRequest(USUARIO_TEST, "ClaveIncorrecta123");

            mockMvc.perform(post("/api/auth/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Usuario inexistente devuelve 401 no revelador (evita enumeración de usuarios)")
        void login_ConUsuarioInexistente_Retorna401() throws Exception {
            LoginRequest request = new LoginRequest("no_existo", PASSWORD_TEST);

            mockMvc.perform(post("/api/auth/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Login rota el ID de sesión previa para mitigar Session Fixation")
        void login_RotaSesion_PrevencionSessionFixation() throws Exception {
            MockHttpSession sesionPrevia = new MockHttpSession();
            String idPrevio = sesionPrevia.getId();

            LoginRequest request = new LoginRequest(USUARIO_TEST, PASSWORD_TEST);
            MvcResult result = mockMvc.perform(post("/api/auth/login")
                    .session(sesionPrevia)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

            MockHttpSession sesionAutenticada = (MockHttpSession) result.getRequest().getSession(false);
            assertNotNull(sesionAutenticada);
            assertNotEquals(idPrevio, sesionAutenticada.getId(), "El identificador de sesión debe renovarse al autenticar");
        }
    }

    // =========================================================================
    // BLOQUE UL-06b: PROTECCIÓN DE SESIÓN, RUTAS PRIVADAS Y CSRF
    // =========================================================================

    @Nested
    @DisplayName("UL-06b: Sesión y Protección CSRF")
    class SesionYCsrfTests {

        @Test
        @DisplayName("GET /csrf expone el token CSRF para clientes SPA")
        void getCsrfToken_RetornaTokenValido() throws Exception {
            mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.headerName").isNotEmpty());
        }

        @Test
        @DisplayName("Ruta privada rechaza petición sin sesión (401)")
        void getMe_SinSesion_DebeRetornar401() throws Exception {
            mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Ruta privada permite acceso con sesión válida")
        void getMe_ConSesionValida_Retorna200YUsuario() throws Exception {
            MockHttpSession sesion = autenticarYObtenerSesion();

            mockMvc.perform(get("/api/auth/me").session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is(USUARIO_TEST)));
        }

        @Test
        @DisplayName("POST /logout sin sesión autenticada retorna 200 de forma idempotente")
        void logout_SinSesion_Retorna200() throws Exception {
            mockMvc.perform(post("/api/auth/logout")
                    .with(csrf()))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Mutación protegida con token CSRF válido es aceptada (200)")
        void logout_ConSesionYCsrfValido_InvalidaSesion() throws Exception {
            MockHttpSession sesion = autenticarYObtenerSesion();

            mockMvc.perform(post("/api/auth/logout")
                    .session(sesion)
                    .with(csrf()))
                .andExpect(status().isOk());

            // Verifica que la sesión antigua queda invalidada para accesos posteriores
            mockMvc.perform(get("/api/auth/me").session(sesion))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Mutación sin token CSRF es rechazada con 403 Forbidden")
        void mutacion_SinTokenCsrf_DebeSerRechazadaCon403() throws Exception {
            MockHttpSession sesion = autenticarYObtenerSesion();

            mockMvc.perform(post("/api/auth/logout").session(sesion))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Mutación con token CSRF inválido es rechazada con 403 Forbidden")
        void mutacion_ConTokenCsrfInvalido_DebeSerRechazadaCon403() throws Exception {
            MockHttpSession sesion = autenticarYObtenerSesion();

            mockMvc.perform(post("/api/auth/logout")
                    .session(sesion)
                    .with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
        }
    }

    // Método auxiliar para evitar duplicación de código en pruebas de sesión
    private MockHttpSession autenticarYObtenerSesion() throws Exception {
        LoginRequest login = new LoginRequest(USUARIO_TEST, PASSWORD_TEST);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
            .andExpect(status().isOk())
            .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertNotNull(session, "La sesión auxiliar debe existir");
        return session;
    }
}