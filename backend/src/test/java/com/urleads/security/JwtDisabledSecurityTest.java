package com.urleads.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urleads.dto.LoginRequest;
import com.urleads.entity.Usuario;
import com.urleads.repository.LeadRepository;
import com.urleads.repository.NotaRepository;
import com.urleads.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration," +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
        "urleads.jwt.enabled=false"
})
@AutoConfigureMockMvc
class JwtDisabledSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private UsuarioRepository usuarioRepository;

    @MockBean
    private LeadRepository leadRepository;

    @MockBean
    private NotaRepository notaRepository;

    @BeforeEach
    void setUp() {
        Usuario usuario = new Usuario();
        usuario.setId(42L);
        usuario.setUsername("admin");
        usuario.setPasswordHash(passwordEncoder.encode("Password123!"));
        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(usuario));
    }

    @Test
    @DisplayName("A17 (Parte 1): Con JWT desactivado, endpoints públicos funcionan y rutas internas fallan de forma cerrada (401)")
    void a17_JwtDesactivado_RutasInternasFallanCerradasCon401() throws Exception {
        // Endpoint de salud responde 200 normalmente
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        // Ruta interna sin configuración falla cerrada devolviendo 401
        mockMvc.perform(get("/internal/v1/c02-probe"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A17 (Parte 2): Solicitud de emisión con sesión activa cuando JWT está desactivado devuelve 503")
    void a17_EmisionConJwtDesactivado_Retorna503() throws Exception {
        LoginRequest loginRequest = new LoginRequest("admin", "Password123!");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(post("/api/auth/access-token")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.message").value("Servicio de emisión de tokens no configurado"));
    }
}
