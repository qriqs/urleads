package com.urleads.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urleads.dto.LoginRequest;
import com.urleads.entity.Lead;
import com.urleads.entity.Nota;
import com.urleads.entity.Usuario;
import com.urleads.repository.LeadAuditLogRepository;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.security.KeyPair;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
class InternalLeadSecurityWebTest {

    private static KeyPair testKeyPair;
    private static KeyPair untrustedKeyPair;

    @DynamicPropertySource
    static void configureJwtProperties(DynamicPropertyRegistry registry) {
        testKeyPair = JwtTestFixtureHelper.generateRsaKeyPair(2048);
        untrustedKeyPair = JwtTestFixtureHelper.generateRsaKeyPair(2048);

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
    private LeadAuditLogRepository leadAuditLogRepository;

    @MockBean
    private NotaRepository notaRepository;

    private Usuario testUsuario;

    @BeforeEach
    void setUp() {
        testUsuario = new Usuario();
        testUsuario.setId(42L);
        testUsuario.setUsername("admin");
        testUsuario.setPasswordHash(passwordEncoder.encode("Password123!"));

        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(testUsuario));
    }

    private MockHttpSession loginSession() throws Exception {
        LoginRequest loginRequest = new LoginRequest("admin", "Password123!");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        return (MockHttpSession) loginResult.getRequest().getSession(false);
    }

    private String createValidToken(String subject, String scope) throws Exception {
        return createToken(subject, List.of("crm-api", "comercial-api", "agenda-api"), scope,
                Instant.now(), Instant.now().plusSeconds(600), null);
    }

    private String createToken(
            String subject,
            List<String> audiences,
            String scope,
            Instant issuedAt,
            Instant expiresAt,
            Instant notBefore) throws Exception {
        return JwtTestFixtureHelper.createSignedJwt(
                testKeyPair.getPrivate(),
                "urleads-crm",
                subject,
                audiences,
                scope,
                issuedAt,
                expiresAt,
                notBefore
        );
    }

    // =========================================================================
    // R1 & R2: Token válido, propietario 42, lead 105 -> 200 JSON, DTO exacto, sin PII
    // =========================================================================
    @Test
    @DisplayName("R1 & R2: Token válido retorna 200 con exactamente id/stage/nextFollowUp y sin PII")
    void r1_r2_TokenValido_Retorna200DtoExactoSinPii() throws Exception {
        Lead lead = new Lead();
        lead.setId(105L);
        lead.setUsuario(testUsuario);
        lead.setNombre("Carlos Juan Perez Gomez");
        lead.setTelefono("+51 987 654 321");
        lead.setCorreo("carlos.perez@empresa-secreta.com");
        lead.setService("Plan Corporativo Enterprise 2026");
        lead.setEtapa("EN_SEGUIMIENTO");
        lead.setProximoSeguimiento(LocalDate.of(2026, 10, 25));
        lead.setCreadoEn(OffsetDateTime.now().minusDays(5));
        lead.setActualizadoEn(OffsetDateTime.now());

        Nota notaPrivada = new Nota();
        notaPrivada.setId(1L);
        notaPrivada.setLead(lead);
        notaPrivada.setContenido("Nota confidencial interna de negociacion de precio");
        List<Nota> notas = new ArrayList<>();
        notas.add(notaPrivada);
        lead.setNotas(notas);

        when(leadRepository.findByIdAndUsuarioId(105L, 42L)).thenReturn(Optional.of(lead));

        String token = createValidToken("42", "crm.leads.read");

        MvcResult result = mockMvc.perform(get("/internal/v1/leads/105")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString(MediaType.APPLICATION_JSON_VALUE)))
                .andReturn();

        JsonNode rootNode = objectMapper.readTree(result.getResponse().getContentAsString());

        // R1: Campos presentes con valores correctos; id es un número JSON
        assertThat(rootNode.get("id").isNumber()).isTrue();
        assertThat(rootNode.get("id").asLong()).isEqualTo(105L);
        assertThat(rootNode.get("stage").isTextual()).isTrue();
        assertThat(rootNode.get("stage").asText()).isEqualTo("EN_SEGUIMIENTO");
        assertThat(rootNode.get("nextFollowUp").isTextual()).isTrue();
        assertThat(rootNode.get("nextFollowUp").asText()).isEqualTo("2026-10-25");

        // R2: Exactamente las 3 propiedades raíz en el payload, sin filtraciones PII
        Set<String> fieldNames = new HashSet<>();
        Iterator<String> iterator = rootNode.fieldNames();
        while (iterator.hasNext()) {
            fieldNames.add(iterator.next());
        }
        assertThat(fieldNames).containsExactlyInAnyOrder("id", "stage", "nextFollowUp");

        // Verificación explícita de ausencia de campos sensibles
        assertThat(rootNode.has("name")).isFalse();
        assertThat(rootNode.has("nombre")).isFalse();
        assertThat(rootNode.has("phone")).isFalse();
        assertThat(rootNode.has("telefono")).isFalse();
        assertThat(rootNode.has("email")).isFalse();
        assertThat(rootNode.has("correo")).isFalse();
        assertThat(rootNode.has("service")).isFalse();
        assertThat(rootNode.has("notes")).isFalse();
        assertThat(rootNode.has("notas")).isFalse();
        assertThat(rootNode.has("createdAt")).isFalse();
        assertThat(rootNode.has("creadoEn")).isFalse();
        assertThat(rootNode.has("updatedAt")).isFalse();
        assertThat(rootNode.has("actualizadoEn")).isFalse();
        assertThat(rootNode.has("owner")).isFalse();
        assertThat(rootNode.has("usuario")).isFalse();
        assertThat(rootNode.has("ownerId")).isFalse();
        assertThat(rootNode.has("usuarioId")).isFalse();

        // Repositorio fue consultado de forma acotada por usuario autenticado
        verify(leadRepository).findByIdAndUsuarioId(105L, 42L);
    }

    // =========================================================================
    // R3: Etapas canónicas y fechas nulas (CERRADO y NUEVO sin seguimiento)
    // =========================================================================
    @Test
    @DisplayName("R3: Lead en etapa CERRADO retorna nextFollowUp explícitamente null en JSON")
    void r3_LeadCerrado_RetornaNextFollowUpNull() throws Exception {
        Lead cerrado = new Lead();
        cerrado.setId(106L);
        cerrado.setUsuario(testUsuario);
        cerrado.setNombre("Lead Cerrado");
        cerrado.setEtapa("CERRADO");
        cerrado.setProximoSeguimiento(null);

        when(leadRepository.findByIdAndUsuarioId(106L, 42L)).thenReturn(Optional.of(cerrado));

        String token = createValidToken("42", "crm.leads.read");

        MvcResult result = mockMvc.perform(get("/internal/v1/leads/106")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(106))
                .andExpect(jsonPath("$.stage").value("CERRADO"))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(root.has("nextFollowUp")).isTrue();
        assertThat(root.get("nextFollowUp").isNull()).isTrue();
    }

    @Test
    @DisplayName("R3: Lead en etapa NUEVO sin seguimiento retorna nextFollowUp explícitamente null")
    void r3_LeadNuevoSinSeguimiento_RetornaNextFollowUpNull() throws Exception {
        Lead nuevoSinFecha = new Lead();
        nuevoSinFecha.setId(107L);
        nuevoSinFecha.setUsuario(testUsuario);
        nuevoSinFecha.setNombre("Lead Recién Ingresado");
        nuevoSinFecha.setEtapa("NUEVO");
        nuevoSinFecha.setProximoSeguimiento(null);

        when(leadRepository.findByIdAndUsuarioId(107L, 42L)).thenReturn(Optional.of(nuevoSinFecha));

        String token = createValidToken("42", "crm.leads.read");

        MvcResult result = mockMvc.perform(get("/internal/v1/leads/107")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(107))
                .andExpect(jsonPath("$.stage").value("NUEVO"))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(root.has("nextFollowUp")).isTrue();
        assertThat(root.get("nextFollowUp").isNull()).isTrue();
    }

    // =========================================================================
    // R4: Formato estricto de LocalDate como YYYY-MM-DD sin componente de tiempo/zona
    // =========================================================================
    @Test
    @DisplayName("R4: LocalDate se serializa estrictamente en formato ISO YYYY-MM-DD")
    void r4_FormatoFecha_StrictIsoYyyyMmDd() throws Exception {
        Lead lead = new Lead();
        lead.setId(108L);
        lead.setUsuario(testUsuario);
        lead.setNombre("Lead con Fecha Exacta");
        lead.setEtapa("NUEVO");
        lead.setProximoSeguimiento(LocalDate.of(2026, 12, 31));

        when(leadRepository.findByIdAndUsuarioId(108L, 42L)).thenReturn(Optional.of(lead));

        String token = createValidToken("42", "crm.leads.read");

        MvcResult result = mockMvc.perform(get("/internal/v1/leads/108")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        String fechaStr = root.get("nextFollowUp").asText();
        assertThat(fechaStr).matches("^\\d{4}-\\d{2}-\\d{2}$");
        assertThat(fechaStr).isEqualTo("2026-12-31");
        assertThat(fechaStr).doesNotContain("T");
        assertThat(fechaStr).doesNotContain("Z");
        assertThat(fechaStr).doesNotContain("+");
    }

    // =========================================================================
    // R5: Lead inexistente o perteneciente a otro propietario -> 404 idéntico y seguro
    // =========================================================================
    @Test
    @DisplayName("R5: Lead inexistente y lead de otro propietario devuelven 404 idéntico")
    void r5_LeadInexistenteYOtroPropietario_Retornan404Identico() throws Exception {
        // Lead 999 no existe para usuario 42
        when(leadRepository.findByIdAndUsuarioId(999L, 42L)).thenReturn(Optional.empty());

        // Lead 105 existe para usuario 42, pero para usuario 88 la consulta por propietario retorna empty
        when(leadRepository.findByIdAndUsuarioId(105L, 88L)).thenReturn(Optional.empty());

        String token42 = createValidToken("42", "crm.leads.read");
        String token88 = createValidToken("88", "crm.leads.read");

        // 1. Lead inexistente
        MvcResult missingResult = mockMvc.perform(get("/internal/v1/leads/999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token42))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Lead no encontrado"))
                .andReturn();

        // 2. Lead existente pero de otro propietario
        MvcResult otherOwnerResult = mockMvc.perform(get("/internal/v1/leads/105")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token88))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Lead no encontrado"))
                .andReturn();

        // Ambas respuestas de error son idénticas en cuerpo y estructura para prevenir filtración de existencia
        assertThat(otherOwnerResult.getResponse().getContentAsString())
                .isEqualTo(missingResult.getResponse().getContentAsString());

        verify(leadRepository).findByIdAndUsuarioId(999L, 42L);
        verify(leadRepository).findByIdAndUsuarioId(105L, 88L);
    }

    // =========================================================================
    // R6: Token ausente, malformado, clave ajena o audiencia ajena -> 401; sin invocar repo
    // =========================================================================
    @Test
    @DisplayName("R6: Token ausente, malformado, con firma inválida o audiencia errónea devuelve 401")
    void r6_TokenInvalidoOIncorrecto_Retorna401SinInvocarRepositorio() throws Exception {
        // 1. Sin cabecera Authorization
        mockMvc.perform(get("/internal/v1/leads/105"))
                .andExpect(status().isUnauthorized());

        // 2. Token malformado
        mockMvc.perform(get("/internal/v1/leads/105")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token.malformado.invalido"))
                .andExpect(status().isUnauthorized());

        // 3. Firma con par de claves ajenas (no confiables)
        String untrustedToken = JwtTestFixtureHelper.createSignedJwt(
                untrustedKeyPair.getPrivate(), "urleads-crm", "42", List.of("crm-api"), "crm.leads.read",
                Instant.now(), Instant.now().plusSeconds(600), null);
        mockMvc.perform(get("/internal/v1/leads/105")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + untrustedToken))
                .andExpect(status().isUnauthorized());

        // 4. Token firmado válido pero con firma manipulada
        String validToken = createValidToken("42", "crm.leads.read");
        String tampered = JwtTestFixtureHelper.createTamperedJwt(validToken);
        mockMvc.perform(get("/internal/v1/leads/105")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized());

        // 5. Audiencia ajena (sin crm-api)
        String wrongAudience = JwtTestFixtureHelper.createSignedJwt(
                testKeyPair.getPrivate(), "urleads-crm", "42", List.of("comercial-api", "agenda-api"), "crm.leads.read",
                Instant.now(), Instant.now().plusSeconds(600), null);
        mockMvc.perform(get("/internal/v1/leads/105")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + wrongAudience))
                .andExpect(status().isUnauthorized());

        // 6. Audiencia ausente
        String noAudience = JwtTestFixtureHelper.createSignedJwt(
                testKeyPair.getPrivate(), "urleads-crm", "42", null, "crm.leads.read",
                Instant.now(), Instant.now().plusSeconds(600), null);
        mockMvc.perform(get("/internal/v1/leads/105")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + noAudience))
                .andExpect(status().isUnauthorized());

        // En ningún caso 401 debe interactuarse con la capa de persistencia
        verifyNoInteractions(leadRepository);
    }

    // =========================================================================
    // R7: Token sin scope o solo scope ajeno -> 403; sin invocar repo
    // =========================================================================
    @Test
    @DisplayName("R7: Token sin scope crm.leads.read devuelve 403 sin consultar base de datos")
    void r7_ScopeInsuficiente_Retorna403SinInvocarRepositorio() throws Exception {
        // 1. Sin claim scope
        String noScope = JwtTestFixtureHelper.createSignedJwt(
                testKeyPair.getPrivate(), "urleads-crm", "42", List.of("crm-api"), null,
                Instant.now(), Instant.now().plusSeconds(600), null);
        mockMvc.perform(get("/internal/v1/leads/105")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + noScope))
                .andExpect(status().isForbidden());

        // 2. Solo scope de Comercial
        String commercialOnly = JwtTestFixtureHelper.createSignedJwt(
                testKeyPair.getPrivate(), "urleads-crm", "42", List.of("crm-api"), "comercial.access",
                Instant.now(), Instant.now().plusSeconds(600), null);
        mockMvc.perform(get("/internal/v1/leads/105")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + commercialOnly))
                .andExpect(status().isForbidden());

        // En ningún caso 403 debe interactuarse con el repositorio
        verifyNoInteractions(leadRepository);
    }

    // =========================================================================
    // R8: Token con lista múltiple de audiencias que incluye crm-api -> 200
    // =========================================================================
    @Test
    @DisplayName("R8: Token con lista múltiple de audiencias que incluye crm-api es aceptado")
    void r8_AudienciaMultipleConCrmApi_Retorna200() throws Exception {
        Lead lead = new Lead();
        lead.setId(105L);
        lead.setUsuario(testUsuario);
        lead.setNombre("Lead Multi-Aud");
        lead.setEtapa("EN_SEGUIMIENTO");
        lead.setProximoSeguimiento(LocalDate.of(2026, 11, 1));

        when(leadRepository.findByIdAndUsuarioId(105L, 42L)).thenReturn(Optional.of(lead));

        String multiAudToken = JwtTestFixtureHelper.createSignedJwt(
                testKeyPair.getPrivate(), "urleads-crm", "42",
                List.of("agenda-api", "crm-api", "comercial-api"),
                "crm.leads.read",
                Instant.now(), Instant.now().plusSeconds(600), null);

        mockMvc.perform(get("/internal/v1/leads/105")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + multiAudToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(105))
                .andExpect(jsonPath("$.stage").value("EN_SEGUIMIENTO"));

        verify(leadRepository).findByIdAndUsuarioId(105L, 42L);
    }

    // =========================================================================
    // R9: Emisor incorrecto, expirado (>30s) o sujeto inválido -> 401 antes de lookup
    // =========================================================================
    @Test
    @DisplayName("R9: Emisor incorrecto, expiración fuera de tolerancia o sujeto inválido devuelven 401")
    void r9_EmisorExpiracionSujetoInvalido_Retornan401() throws Exception {
        // 1. Emisor desconocido
        String badIssuer = JwtTestFixtureHelper.createSignedJwt(
                testKeyPair.getPrivate(), "emisor-desconocido", "42", List.of("crm-api"), "crm.leads.read",
                Instant.now(), Instant.now().plusSeconds(600), null);
        mockMvc.perform(get("/internal/v1/leads/105").header(HttpHeaders.AUTHORIZATION, "Bearer " + badIssuer))
                .andExpect(status().isUnauthorized());

        // 2. Expirado hace 45s (fuera de margen de reloj de 30s)
        String expired = JwtTestFixtureHelper.createSignedJwt(
                testKeyPair.getPrivate(), "urleads-crm", "42", List.of("crm-api"), "crm.leads.read",
                Instant.now().minusSeconds(600), Instant.now().minusSeconds(45), null);
        mockMvc.perform(get("/internal/v1/leads/105").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized());

        // 3. Sujetos inválidos: "admin", "0", "-42", overflow
        String[] invalidSubs = {"admin", "0", "-42", "99999999999999999999999999999"};
        for (String invalidSub : invalidSubs) {
            String token = JwtTestFixtureHelper.createSignedJwt(
                    testKeyPair.getPrivate(), "urleads-crm", invalidSub, List.of("crm-api"), "crm.leads.read",
                    Instant.now(), Instant.now().plusSeconds(600), null);
            mockMvc.perform(get("/internal/v1/leads/105").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }

        // 4. Token expirado hace 10s (dentro del margen de tolerancia de 30s) -> Permitido
        Lead lead = new Lead();
        lead.setId(105L);
        lead.setUsuario(testUsuario);
        lead.setNombre("Lead Skew Test");
        lead.setEtapa("NUEVO");
        when(leadRepository.findByIdAndUsuarioId(105L, 42L)).thenReturn(Optional.of(lead));

        String insideSkew = JwtTestFixtureHelper.createSignedJwt(
                testKeyPair.getPrivate(), "urleads-crm", "42", List.of("crm-api"), "crm.leads.read",
                Instant.now().minusSeconds(600), Instant.now().minusSeconds(10), null);
        mockMvc.perform(get("/internal/v1/leads/105").header(HttpHeaders.AUTHORIZATION, "Bearer " + insideSkew))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // R10: Sesión web en ruta interna no autoriza ni provee fallback ante token ausente o inválido
    // =========================================================================
    @Test
    @DisplayName("R10: Sesión activa sin token o con Bearer inválido devuelve 401 (sin fallback)")
    void r10_SesionWebSinTokenOConTokenInvalido_Retorna401() throws Exception {
        MockHttpSession session = loginSession();

        // 1. Con sesión activa pero sin cabecera Bearer
        mockMvc.perform(get("/internal/v1/leads/105")
                        .session(session))
                .andExpect(status().isUnauthorized());

        // 2. Con sesión activa pero con Bearer inválido
        mockMvc.perform(get("/internal/v1/leads/105")
                        .session(session)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.bearer.token"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(leadRepository);
    }

    // =========================================================================
    // R11: Manipulación de usuario: solo el JWT 'sub' determina el propietario consultado
    // =========================================================================
    @Test
    @DisplayName("R11: Solo JWT sub gobierna la consulta al repositorio; parámetros query o sesión son ignorados")
    void r11_SoloJwtSubDeterminaPropietarioEnConsulta() throws Exception {
        // Sesión activa pertenece a admin (ID 42 en DB)
        MockHttpSession session = loginSession();

        // Lead 105 existe para propietario 42
        Lead lead42 = new Lead();
        lead42.setId(105L);
        lead42.setUsuario(testUsuario);
        lead42.setNombre("Lead Propietario 42");
        lead42.setEtapa("EN_SEGUIMIENTO");
        when(leadRepository.findByIdAndUsuarioId(105L, 42L)).thenReturn(Optional.of(lead42));

        // Para propietario 88, el lead 105 no existe
        when(leadRepository.findByIdAndUsuarioId(105L, 88L)).thenReturn(Optional.empty());

        // Token firmado con sub="88"
        String token88 = createValidToken("88", "crm.leads.read");

        // Solicitud con sesión de usuario 42, query params ownerId=42 y userId=42, pero token sub 88
        mockMvc.perform(get("/internal/v1/leads/105")
                        .session(session)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token88)
                        .param("ownerId", "42")
                        .param("userId", "42"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // Se verifica que la consulta al repositorio usó el ID 88 del token, NO el 42 de la sesión o query
        verify(leadRepository).findByIdAndUsuarioId(105L, 88L);
    }

    // =========================================================================
    // R13: Invocación interna con Bearer es stateless, sin CSRF y sin cookies
    // =========================================================================
    @Test
    @DisplayName("R13: Invocación interna con Bearer es stateless: sin CSRF, sin sesión ni Set-Cookie")
    void r13_InvocacionInternaStateless_SinCsrfSinSesion() throws Exception {
        Lead lead = new Lead();
        lead.setId(105L);
        lead.setUsuario(testUsuario);
        lead.setNombre("Lead Stateless");
        lead.setEtapa("NUEVO");
        when(leadRepository.findByIdAndUsuarioId(105L, 42L)).thenReturn(Optional.of(lead));

        String token = createValidToken("42", "crm.leads.read");

        MvcResult result = mockMvc.perform(get("/internal/v1/leads/105")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        // No requiere CSRF (ruta /internal/** tiene csrf.disable())
        // No emite cookies Set-Cookie
        assertThat(result.getResponse().getHeader("Set-Cookie")).isNull();
        // No crea sesión HTTP
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    // =========================================================================
    // R14: Manejo de cabeceras Accept: nunca sirve el HTML de la SPA
    // =========================================================================
    @Test
    @DisplayName("R14: Solicitud con Accept application/json retorna JSON y nunca sirve el HTML de la SPA")
    void r14_AcceptApplicationJson_RetornaJson() throws Exception {
        Lead lead = new Lead();
        lead.setId(105L);
        lead.setUsuario(testUsuario);
        lead.setNombre("Lead Json Accept");
        lead.setEtapa("EN_SEGUIMIENTO");
        when(leadRepository.findByIdAndUsuarioId(105L, 42L)).thenReturn(Optional.of(lead));

        String token = createValidToken("42", "crm.leads.read");

        mockMvc.perform(get("/internal/v1/leads/105")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString(MediaType.APPLICATION_JSON_VALUE)))
                .andExpect(jsonPath("$.id").value(105))
                .andExpect(content().string(not(containsString("<!DOCTYPE"))));
    }

    @Test
    @DisplayName("R14: Solicitud con Accept text/html retorna 406 Not Acceptable y nunca sirve index.html de SPA")
    void r14_AcceptTextHtml_NoSirveSpaHtml() throws Exception {
        Lead lead = new Lead();
        lead.setId(105L);
        lead.setUsuario(testUsuario);
        lead.setNombre("Lead Html Accept");
        lead.setEtapa("EN_SEGUIMIENTO");
        when(leadRepository.findByIdAndUsuarioId(105L, 42L)).thenReturn(Optional.of(lead));

        String token = createValidToken("42", "crm.leads.read");

        // Cuando el cliente solicita exclusivamente text/html, el controlador REST concreto gana
        // pero rechaza la conversión produciendo 406 Not Acceptable; jamás delega a SpaController
        mockMvc.perform(get("/internal/v1/leads/105")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().string(not(containsString("<!DOCTYPE"))));
    }

    // =========================================================================
    // Parámetro de ruta inválido (no numérico) -> 400 Bad Request, nunca 500
    // =========================================================================
    @Test
    @DisplayName("R15: Parámetro de ruta id no numérico devuelve 400 Bad Request sin error 500")
    void rutaInvalida_IdNoNumerico_Retorna400BadRequestSin500() throws Exception {
        String token = createValidToken("42", "crm.leads.read");

        mockMvc.perform(get("/internal/v1/leads/no-es-numero")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("<!DOCTYPE"))));

        verifyNoInteractions(leadRepository);
    }
}
