package com.urleads.docs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urleads.dto.LoginRequest;
import com.urleads.dto.UpdateLeadRequest;
import com.urleads.entity.Lead;
import com.urleads.entity.Usuario;
import com.urleads.repository.LeadRepository;
import com.urleads.repository.NotaRepository;
import com.urleads.repository.UsuarioRepository;
import com.urleads.security.JwtTestFixtureHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.security.KeyPair;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration," +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
        "urleads.jwt.enabled=true"
})
@AutoConfigureMockMvc
class OpenApiSecurityWebTest {

    private static KeyPair testKeyPair;

    @DynamicPropertySource
    static void configureJwtProperties(DynamicPropertyRegistry registry) {
        testKeyPair = JwtTestFixtureHelper.generateRsaKeyPair(2048);
        registry.add("urleads.jwt.enabled", () -> "true");
        registry.add("urleads.jwt.private-key-value", () -> JwtTestFixtureHelper.toPkcs8Pem(testKeyPair.getPrivate()));
        registry.add("urleads.jwt.public-key-value", () -> JwtTestFixtureHelper.toX509Pem(testKeyPair.getPublic()));
    }

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

    private Usuario testUsuario;

    @BeforeEach
    void setUp() {
        testUsuario = new Usuario();
        testUsuario.setId(100L);
        testUsuario.setUsername("testadmin");
        testUsuario.setPasswordHash(passwordEncoder.encode("SecretPass123!"));

        when(usuarioRepository.findByUsername("testadmin")).thenReturn(Optional.of(testUsuario));
    }

    @Test
    @DisplayName("R1a: /v3/api-docs devuelve OpenAPI 3 válido con todas las rutas reales de CRM")
    void openApiDocsReturnsValidSpecificationWithAllExpectedRoutes() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(json);

        // Validar versión OpenAPI 3
        assertThat(root.has("openapi")).isTrue();
        assertThat(root.get("openapi").asText()).startsWith("3.");

        // Validar información del contrato
        assertThat(root.get("info").get("title").asText()).isEqualTo("UrLeads CRM REST API");

        // Validar rutas REST existentes
        JsonNode paths = root.get("paths");
        assertThat(paths).isNotNull();

        List<String> requiredPaths = List.of(
                "/api/health",
                "/api/auth/csrf",
                "/api/auth/login",
                "/api/auth/me",
                "/api/auth/session",
                "/api/auth/access-token",
                "/api/auth/logout",
                "/api/leads",
                "/api/leads/{id}",
                "/api/leads/{id}/notes",
                "/api/dashboard",
                "/internal/v1/leads/{id}"
        );

        for (String expectedPath : requiredPaths) {
            assertThat(paths.has(expectedPath))
                    .withFailMessage("OpenAPI debe documentar la ruta real: %s", expectedPath)
                    .isTrue();
        }

        // Validar que no se documentan rutas inexistentes o futuras (C13 SOAP, etc.)
        assertThat(paths.has("/ws/leads")).isFalse();
        assertThat(paths.has("/api/leads/{id}/quotes")).isFalse();

        // Validar esquemas de seguridad requeridos
        JsonNode securitySchemes = root.path("components").path("securitySchemes");
        assertThat(securitySchemes.has("cookieAuth")).isTrue();
        assertThat(securitySchemes.get("cookieAuth").get("type").asText()).isEqualTo("apiKey");
        assertThat(securitySchemes.get("cookieAuth").get("in").asText()).isEqualTo("cookie");
        assertThat(securitySchemes.get("cookieAuth").get("name").asText()).isEqualTo("JSESSIONID");

        assertThat(securitySchemes.has("csrfToken")).isTrue();
        assertThat(securitySchemes.get("csrfToken").get("type").asText()).isEqualTo("apiKey");
        assertThat(securitySchemes.get("csrfToken").get("in").asText()).isEqualTo("header");
        assertThat(securitySchemes.get("csrfToken").get("name").asText()).isEqualTo("X-XSRF-TOKEN");

        assertThat(securitySchemes.has("bearerJwt")).isTrue();
        assertThat(securitySchemes.get("bearerJwt").get("type").asText()).isEqualTo("http");
        assertThat(securitySchemes.get("bearerJwt").get("scheme").asText()).isEqualTo("bearer");
        assertThat(securitySchemes.get("bearerJwt").get("bearerFormat").asText()).isEqualTo("JWT");

        // Validar semántica exacta de seguridad y parámetros por operación:
        // 1. POST /api/auth/login: no requiere cookie de sesión, pero requiere parámetro header X-XSRF-TOKEN
        JsonNode loginOp = paths.path("/api/auth/login").path("post");
        assertThat(loginOp.isMissingNode()).isFalse();
        assertThat(hasCookieAuth(loginOp)).isFalse();
        assertThat(hasHeaderParam(loginOp, "X-XSRF-TOKEN")).isTrue();

        // 2. POST /api/leads: requiere cookieAuth Y cabecera X-XSRF-TOKEN (sin falsos OR)
        JsonNode createLeadOp = paths.path("/api/leads").path("post");
        assertThat(createLeadOp.isMissingNode()).isFalse();
        assertThat(getSingleSecurityScheme(createLeadOp)).isEqualTo("cookieAuth");
        assertThat(hasHeaderParam(createLeadOp, "X-XSRF-TOKEN")).isTrue();

        // 3. GET /api/leads: requiere cookieAuth pero NO cabecera X-XSRF-TOKEN
        JsonNode listLeadsOp = paths.path("/api/leads").path("get");
        assertThat(listLeadsOp.isMissingNode()).isFalse();
        assertThat(getSingleSecurityScheme(listLeadsOp)).isEqualTo("cookieAuth");
        assertThat(hasHeaderParam(listLeadsOp, "X-XSRF-TOKEN")).isFalse();

        // 4. GET /internal/v1/leads/{id}: requiere bearerJwt, no cookieAuth, no X-XSRF-TOKEN
        JsonNode internalLeadOp = paths.path("/internal/v1/leads/{id}").path("get");
        assertThat(internalLeadOp.isMissingNode()).isFalse();
        assertThat(getSingleSecurityScheme(internalLeadOp)).isEqualTo("bearerJwt");
        assertThat(hasHeaderParam(internalLeadOp, "X-XSRF-TOKEN")).isFalse();

        // 5. POST /api/auth/access-token: requiere cookieAuth Y cabecera X-XSRF-TOKEN
        JsonNode accessTokenOp = paths.path("/api/auth/access-token").path("post");
        assertThat(accessTokenOp.isMissingNode()).isFalse();
        assertThat(getSingleSecurityScheme(accessTokenOp)).isEqualTo("cookieAuth");
        assertThat(hasHeaderParam(accessTokenOp, "X-XSRF-TOKEN")).isTrue();

        // 6. POST /api/auth/logout: requiere cookieAuth Y cabecera X-XSRF-TOKEN
        JsonNode logoutOp = paths.path("/api/auth/logout").path("post");
        assertThat(logoutOp.isMissingNode()).isFalse();
        assertThat(getSingleSecurityScheme(logoutOp)).isEqualTo("cookieAuth");
        assertThat(hasHeaderParam(logoutOp, "X-XSRF-TOKEN")).isTrue();
    }

    private boolean hasHeaderParam(JsonNode operationNode, String headerName) {
        JsonNode parameters = operationNode.path("parameters");
        if (!parameters.isArray()) return false;
        for (JsonNode param : parameters) {
            if (headerName.equalsIgnoreCase(param.path("name").asText())
                    && "header".equalsIgnoreCase(param.path("in").asText())
                    && param.path("required").asBoolean()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasCookieAuth(JsonNode operationNode) {
        JsonNode security = operationNode.path("security");
        if (!security.isArray() || security.isEmpty()) return false;
        for (JsonNode req : security) {
            if (req.has("cookieAuth")) return true;
        }
        return false;
    }

    private String getSingleSecurityScheme(JsonNode operationNode) {
        JsonNode security = operationNode.path("security");
        assertThat(security.isArray()).isTrue();
        assertThat(security.size()).isEqualTo(1);
        JsonNode requirement = security.get(0);
        Iterator<String> fieldNames = requirement.fieldNames();
        assertThat(fieldNames.hasNext()).isTrue();
        String schemeName = fieldNames.next();
        assertThat(fieldNames.hasNext()).isFalse();
        return schemeName;
    }

    @Test
    @DisplayName("R1b: /swagger-ui.html redirige y el Swagger UI se sirve correctamente")
    void swaggerUiIsAccessible() throws Exception {
        MvcResult welcomeResult = mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String redirectedUrl = welcomeResult.getResponse().getRedirectedUrl();
        assertThat(redirectedUrl).contains("/swagger-ui/index.html");

        mockMvc.perform(get(redirectedUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("swagger-ui")));
    }

    @Test
    @DisplayName("R2: Rutas OpenAPI son públicas pero la seguridad de /api/** y /internal/** no se relaja")
    void swaggerIsPublicWithoutWeakeningApiOrInternalSecurity() throws Exception {
        // Swagger UI y OpenAPI docs accesibles sin sesión
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());

        // /api/leads requiere sesión (no relajado por Swagger permitAll)
        mockMvc.perform(get("/api/leads"))
                .andExpect(status().isUnauthorized());

        // /api/dashboard requiere sesión
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isUnauthorized());

        // /internal/v1/leads/{id} requiere Bearer JWT
        mockMvc.perform(get("/internal/v1/leads/42"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("R4: Verificación estricta de privacidad de contraseñas en esquemas de respuesta OpenAPI")
    void passwordPrivacyInOpenApiSchemas() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode schemas = root.path("components").path("schemas");
        assertThat(schemas.isObject()).isTrue();

        // La entidad Usuario interna jamás debe ser expuesta como esquema
        assertThat(schemas.has("Usuario"))
                .withFailMessage("La entidad interna JPA Usuario no debe figurar en los esquemas de OpenAPI")
                .isFalse();

        // Verificar que los esquemas de respuesta no contengan password ni password_hash ni passwordHash
        List<String> responseSchemas = List.of(
                "UserResponse",
                "InternalLeadResponse",
                "LeadResponse",
                "NotaResponse",
                "DashboardResponse",
                "AccessTokenResponse",
                "HealthResponse"
        );

        for (String schemaName : responseSchemas) {
            JsonNode schemaNode = schemas.get(schemaName);
            if (schemaNode != null && schemaNode.has("properties")) {
                JsonNode props = schemaNode.get("properties");
                assertThat(props.has("password"))
                        .withFailMessage("Esquema de respuesta %s expone el campo password", schemaName)
                        .isFalse();
                assertThat(props.has("passwordHash"))
                        .withFailMessage("Esquema de respuesta %s expone el campo passwordHash", schemaName)
                        .isFalse();
                assertThat(props.has("password_hash"))
                        .withFailMessage("Esquema de respuesta %s expone el campo password_hash", schemaName)
                        .isFalse();
            }
        }

        // UserResponse debe tener exactamente id y username
        JsonNode userResponseProps = schemas.path("UserResponse").path("properties");
        if (!userResponseProps.isMissingNode()) {
            assertThat(userResponseProps.has("id")).isTrue();
            assertThat(userResponseProps.has("username")).isTrue();
            assertThat(userResponseProps.size()).isEqualTo(2);
        }

        // InternalLeadResponse debe tener exactamente id, stage y nextFollowUp
        JsonNode internalLeadProps = schemas.path("InternalLeadResponse").path("properties");
        if (!internalLeadProps.isMissingNode()) {
            assertThat(internalLeadProps.has("id")).isTrue();
            assertThat(internalLeadProps.has("stage")).isTrue();
            assertThat(internalLeadProps.has("nextFollowUp")).isTrue();
            assertThat(internalLeadProps.size()).isEqualTo(3);
        }
    }

    @Test
    @DisplayName("R4: Verificación en tiempo de ejecución de no exposición de contraseña en respuestas de login y /me")
    void passwordNotExposedInAuthResponses() throws Exception {
        LoginRequest loginRequest = new LoginRequest("testadmin", "SecretPass123!");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.username").value("testadmin"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password_hash").doesNotExist())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mockMvc.perform(get("/api/auth/me")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.username").value("testadmin"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password_hash").doesNotExist());
    }

    @Test
    @DisplayName("R5: Regresión de aislamiento de propietario en CRUD: lead ajeno retorna 404 indistinguible y no se muta")
    void foreignOwnerLeadCannotBeAccessedOrMutatedInLegacyCrud() throws Exception {
        LoginRequest loginRequest = new LoginRequest("testadmin", "SecretPass123!");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        Long ownerA = 100L; // testadmin
        Long ownerB = 200L;
        Long foreignLeadId = 555L;

        Usuario userB = new Usuario(ownerB, "userB", "hashB");
        Lead leadDeB = new Lead();
        leadDeB.setId(foreignLeadId);
        leadDeB.setUsuario(userB);
        leadDeB.setNombre("Lead Privado de B");
        leadDeB.setEtapa("NUEVO");

        // El lead existe en el sistema y pertenece al usuario B
        when(leadRepository.findByIdAndUsuarioId(foreignLeadId, ownerB)).thenReturn(Optional.of(leadDeB));
        // Pero la consulta del usuario A (100) en el repositorio acotado retorna vacío
        when(leadRepository.findByIdAndUsuarioId(foreignLeadId, ownerA)).thenReturn(Optional.empty());

        // 1. Intento de lectura GET sobre lead de otro usuario -> 404 NOT_FOUND
        mockMvc.perform(get("/api/leads/" + foreignLeadId)
                        .session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Lead no encontrado"));

        // 2. Intento de actualización PUT sobre lead de otro usuario -> 404 NOT_FOUND
        UpdateLeadRequest updateReq = new UpdateLeadRequest();
        updateReq.setNombre("Nombre Alterado");
        updateReq.setCorreo("alterado@example.com");
        updateReq.setEtapa("EN_SEGUIMIENTO");
        updateReq.setProximoSeguimiento(LocalDate.of(2026, 11, 1));
        updateReq.setPriority("MEDIA");
        updateReq.setOrigin("MANUAL");

        mockMvc.perform(put("/api/leads/" + foreignLeadId)
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // 3. Intento de eliminación DELETE sobre lead de otro usuario -> 404 NOT_FOUND
        mockMvc.perform(delete("/api/leads/" + foreignLeadId)
                        .with(csrf())
                        .session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // 4. Verificación de invariantes: jamás se llamó a save(), delete(), ni findById unscoped
        verify(leadRepository, never()).save(any(Lead.class));
        verify(leadRepository, never()).delete(any(Lead.class));
        verify(leadRepository, never()).findById(any());

        // 5. Comparar con lead inexistente (999L): la respuesta 404 es exactamente idéntica
        when(leadRepository.findByIdAndUsuarioId(999L, ownerA)).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/leads/999")
                        .session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Lead no encontrado"));
    }
}
