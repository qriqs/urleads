package com.urleads.security;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.security.KeyPair;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
class CrmJwtSecurityWebTest {

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

    @Autowired
    private JwtDecoder jwtDecoder;

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
        return JwtTestFixtureHelper.createSignedJwt(
                testKeyPair.getPrivate(),
                "urleads-crm",
                subject,
                List.of("crm-api", "comercial-api", "agenda-api"),
                scope,
                Instant.now(),
                Instant.now().plusSeconds(600),
                null
        );
    }

    // =========================================================================
    // A01: Sesión real, CSRF válido, emisión sin cuerpo -> 200, DTO exacto, RS256, claims R2
    // =========================================================================
    @Test
    @DisplayName("A01: Emisión exitosa de token con sesión autenticada y CSRF válido")
    void a01_EmisionConSesionValidaYCsrf_RetornaTokenYClaimsR2() throws Exception {
        MockHttpSession session = loginSession();

        MvcResult result = mockMvc.perform(post("/api/auth/access-token")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(600))
                .andExpect(jsonPath("$.access_token").isString())
                .andReturn();

        // Verificar campos exactos del JSON de respuesta (RFC 6749)
        JsonNode rootNode = objectMapper.readTree(result.getResponse().getContentAsString());
        Iterator<String> fieldNames = rootNode.fieldNames();
        int count = 0;
        while (fieldNames.hasNext()) {
            String name = fieldNames.next();
            assertThat(name).isIn("access_token", "token_type", "expires_in");
            count++;
        }
        assertThat(count).isEqualTo(3);

        // Verificar claims y firma RS256 con el JwtDecoder oficial
        String tokenStr = rootNode.get("access_token").asText();
        Jwt jwt = jwtDecoder.decode(tokenStr);

        assertThat(jwt.getHeaders().get("alg")).isEqualTo("RS256");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("urleads-crm");
        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getAudience()).containsExactlyInAnyOrder("crm-api", "comercial-api", "agenda-api");
        assertThat(jwt.getClaimAsString("scope")).isEqualTo("crm.leads.read comercial.access agenda.access");
        assertThat(jwt.getExpiresAt()).isAfter(jwt.getIssuedAt());
        assertThat(jwt.getExpiresAt().getEpochSecond() - jwt.getIssuedAt().getEpochSecond()).isEqualTo(600L);
    }

    // =========================================================================
    // A02: Sin sesión, CSRF válido -> 401
    // =========================================================================
    @Test
    @DisplayName("A02: Emisión sin sesión activa devuelve 401")
    void a02_EmisionSinSesion_Retorna401() throws Exception {
        mockMvc.perform(post("/api/auth/access-token")
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.access_token").doesNotExist());
    }

    // =========================================================================
    // A03: Sesión autenticada, CSRF ausente o inválido -> 403
    // =========================================================================
    @Test
    @DisplayName("A03: Emisión con sesión pero CSRF ausente o inválido devuelve 403")
    void a03_EmisionSinCsrfOInvalido_Retorna403() throws Exception {
        MockHttpSession session = loginSession();

        // 1. CSRF ausente
        mockMvc.perform(post("/api/auth/access-token")
                        .session(session))
                .andExpect(status().isForbidden());

        // 2. CSRF inválido
        mockMvc.perform(post("/api/auth/access-token")
                        .session(session)
                        .with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // A04: Solo Bearer token válido en endpoint de emisión -> 401
    // =========================================================================
    @Test
    @DisplayName("A04: Un token Bearer solo no autoriza la emisión de nuevos tokens")
    void a04_EmisionConBearerSolo_Retorna401() throws Exception {
        String token = createValidToken("42", "crm.leads.read comercial.access agenda.access");

        mockMvc.perform(post("/api/auth/access-token")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.access_token").doesNotExist());
    }

    // =========================================================================
    // A05: Parámetros del cliente en cuerpo/query no pueden controlar claims emitidos
    // =========================================================================
    @Test
    @DisplayName("A05: Parámetros manipulados en query/cuerpo no alteran la identidad emitida")
    void a05_ParametrosClienteManipulados_NoAfectanClaims() throws Exception {
        MockHttpSession session = loginSession();

        MvcResult result = mockMvc.perform(post("/api/auth/access-token")
                        .session(session)
                        .with(csrf())
                        .param("ownerId", "999")
                        .param("sub", "999")
                        .param("aud", "hacker-api")
                        .param("scope", "superadmin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerId\":999,\"sub\":\"999\",\"scope\":\"superadmin\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        Jwt jwt = jwtDecoder.decode(root.get("access_token").asText());

        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getAudience()).containsExactlyInAnyOrder("crm-api", "comercial-api", "agenda-api");
        assertThat(jwt.getClaimAsString("scope")).isEqualTo("crm.leads.read comercial.access agenda.access");
    }

    // =========================================================================
    // A06: Token válido y firmado en sonda interna -> 200 JSON con ownerId numérico
    // =========================================================================
    @Test
    @DisplayName("A06: Acceso con token válido a sonda interna resuelve propietario numérico sin HTML")
    void a06_TokenValidoEnSondaInterna_Retorna200JsonYPropietario() throws Exception {
        String token = createValidToken("42", "crm.leads.read comercial.access agenda.access");

        mockMvc.perform(get("/internal/v1/c02-probe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString(MediaType.APPLICATION_JSON_VALUE)))
                .andExpect(jsonPath("$.ownerId").value(42))
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(content().string(not(containsString("<!DOCTYPE"))));
    }

    // =========================================================================
    // A07: Sin JWT, token malformado, clave no confiable o firma alterada -> 401
    // =========================================================================
    @Test
    @DisplayName("A07: Solicitud sin JWT, malformada, con clave ajena o firma alterada devuelve 401")
    void a07_SinJwtMalformadoClaveAjenaFirmaAlterada_Retorna401() throws Exception {
        // 1. Sin cabecera Authorization
        mockMvc.perform(get("/internal/v1/c02-probe"))
                .andExpect(status().isUnauthorized());

        // 2. Token malformado
        mockMvc.perform(get("/internal/v1/c02-probe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token.invalido.malformado"))
                .andExpect(status().isUnauthorized());

        // 3. Token firmado con par de claves RSA no confiable
        String untrustedToken = JwtTestFixtureHelper.createSignedJwt(
                untrustedKeyPair.getPrivate(),
                "urleads-crm",
                "42",
                List.of("crm-api"),
                "crm.leads.read",
                Instant.now(),
                Instant.now().plusSeconds(600),
                null
        );
        mockMvc.perform(get("/internal/v1/c02-probe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + untrustedToken))
                .andExpect(status().isUnauthorized());

        // 4. Token con firma manipulada (tampered)
        String validToken = createValidToken("42", "crm.leads.read");
        String tamperedToken = JwtTestFixtureHelper.createTamperedJwt(validToken);
        mockMvc.perform(get("/internal/v1/c02-probe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tamperedToken))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // A08: Emisor o audiencia incorrecta/ausente -> 401; audiencia crm-api aceptada
    // =========================================================================
    @Test
    @DisplayName("A08: Validación de emisor y audiencia obligatoria crm-api")
    void a08_EmisorYAudiencia_Validaciones() throws Exception {
        // 1. Emisor incorrecto
        String wrongIssuer = JwtTestFixtureHelper.createSignedJwt(
                testKeyPair.getPrivate(), "otro-emisor", "42", List.of("crm-api"), "crm.leads.read",
                Instant.now(), Instant.now().plusSeconds(600), null);
        mockMvc.perform(get("/internal/v1/c02-probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + wrongIssuer))
                .andExpect(status().isUnauthorized());

        // 2. Emisor ausente
        String nullIssuer = JwtTestFixtureHelper.createSignedJwt(
                testKeyPair.getPrivate(), null, "42", List.of("crm-api"), "crm.leads.read",
                Instant.now(), Instant.now().plusSeconds(600), null);
        mockMvc.perform(get("/internal/v1/c02-probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + nullIssuer))
                .andExpect(status().isUnauthorized());

        // 3. Audiencia ajena que no contiene crm-api
        String wrongAudience = JwtTestFixtureHelper.createSignedJwt(
                testKeyPair.getPrivate(), "urleads-crm", "42", List.of("comercial-api", "agenda-api"), "crm.leads.read",
                Instant.now(), Instant.now().plusSeconds(600), null);
        mockMvc.perform(get("/internal/v1/c02-probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + wrongAudience))
                .andExpect(status().isUnauthorized());

        // 4. Audiencia ausente
        String nullAudience = JwtTestFixtureHelper.createSignedJwt(
                testKeyPair.getPrivate(), "urleads-crm", "42", null, "crm.leads.read",
                Instant.now(), Instant.now().plusSeconds(600), null);
        mockMvc.perform(get("/internal/v1/c02-probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + nullAudience))
                .andExpect(status().isUnauthorized());

        // 5. Audiencia múltiple que sí contiene crm-api -> Aceptada
        String validAudience = JwtTestFixtureHelper.createSignedJwt(
                testKeyPair.getPrivate(), "urleads-crm", "42", List.of("crm-api", "comercial-api"), "crm.leads.read",
                Instant.now(), Instant.now().plusSeconds(600), null);
        mockMvc.perform(get("/internal/v1/c02-probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + validAudience))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // A09: Expirado fuera de margen o sin exp -> 401; dentro de tolerancia -> 200
    // =========================================================================
    @Test
    @DisplayName("A09: Token expirado fuera de tolerancia o sin exp devuelve 401")
    void a09_ExpiracionYMargenDeReloj() throws Exception {
        // 1. Expirado hace 45 segundos (fuera de tolerancia de 30s)
        String expiredOutsideSkew = JwtTestFixtureHelper.createSignedJwt(
                testKeyPair.getPrivate(), "urleads-crm", "42", List.of("crm-api"), "crm.leads.read",
                Instant.now().minusSeconds(600), Instant.now().minusSeconds(45), null);
        mockMvc.perform(get("/internal/v1/c02-probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredOutsideSkew))
                .andExpect(status().isUnauthorized());

        // 2. Sin claim exp
        String noExp = JwtTestFixtureHelper.createSignedJwt(
                testKeyPair.getPrivate(), "urleads-crm", "42", List.of("crm-api"), "crm.leads.read",
                Instant.now(), null, null);
        mockMvc.perform(get("/internal/v1/c02-probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + noExp))
                .andExpect(status().isUnauthorized());

        // 3. Expirado hace 10 segundos (dentro de tolerancia de 30s de reloj)
        String expiredInsideSkew = JwtTestFixtureHelper.createSignedJwt(
                testKeyPair.getPrivate(), "urleads-crm", "42", List.of("crm-api"), "crm.leads.read",
                Instant.now().minusSeconds(600), Instant.now().minusSeconds(10), null);
        mockMvc.perform(get("/internal/v1/c02-probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredInsideSkew))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // A10: Sujeto inválido -> 401; sujeto decimal positivo -> 200
    // =========================================================================
    @Test
    @DisplayName("A10: Rechazo de sujetos inválidos (no numérico, UUID, cero, negativo, overflow)")
    void a10_ValidacionEstrictaDeSujeto() throws Exception {
        String[] invalidSubs = {
                null,
                "",
                "   ",
                "admin",
                "550e8400-e29b-41d4-a716-446655440000",
                "0",
                "-42",
                "99999999999999999999999999999"
        };

        for (String invalidSub : invalidSubs) {
            String token = JwtTestFixtureHelper.createSignedJwt(
                    testKeyPair.getPrivate(), "urleads-crm", invalidSub, List.of("crm-api"), "crm.leads.read",
                    Instant.now(), Instant.now().plusSeconds(600), null);

            mockMvc.perform(get("/internal/v1/c02-probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }

        // Sujeto válido decimal positivo
        String validSubToken = JwtTestFixtureHelper.createSignedJwt(
                testKeyPair.getPrivate(), "urleads-crm", "42", List.of("crm-api"), "crm.leads.read",
                Instant.now(), Instant.now().plusSeconds(600), null);
        mockMvc.perform(get("/internal/v1/c02-probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + validSubToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value(42));
    }

    // =========================================================================
    // A11: Token válido pero sin scope requerido crm.leads.read -> 403 Forbidden
    // =========================================================================
    @Test
    @DisplayName("A11: Token válido pero sin scope crm.leads.read devuelve 403 Forbidden")
    void a11_ScopeInsuficiente_Retorna403() throws Exception {
        // 1. Sin claim scope
        String noScope = JwtTestFixtureHelper.createSignedJwt(
                testKeyPair.getPrivate(), "urleads-crm", "42", List.of("crm-api"), null,
                Instant.now(), Instant.now().plusSeconds(600), null);
        mockMvc.perform(get("/internal/v1/c02-probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + noScope))
                .andExpect(status().isForbidden());

        // 2. Solo scope de otro servicio
        String otherScope = JwtTestFixtureHelper.createSignedJwt(
                testKeyPair.getPrivate(), "urleads-crm", "42", List.of("crm-api"), "comercial.access agenda.access",
                Instant.now(), Instant.now().plusSeconds(600), null);
        mockMvc.perform(get("/internal/v1/c02-probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + otherScope))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // A12: Token con algoritmo 'none' o HS256 -> 401
    // =========================================================================
    @Test
    @DisplayName("A12: Rechazo de algoritmos inseguros o incompatibles ('none', HS256)")
    void a12_AlgoritmosNoneYHs256_Retornan401() throws Exception {
        // 1. Algoritmo 'none'
        String noneToken = JwtTestFixtureHelper.createNoneAlgJwt(
                "urleads-crm", "42", List.of("crm-api"), "crm.leads.read",
                Instant.now(), Instant.now().plusSeconds(600));
        mockMvc.perform(get("/internal/v1/c02-probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + noneToken))
                .andExpect(status().isUnauthorized());

        // 2. Algoritmo simétrico HS256
        byte[] secret = new byte[32]; // 256 bits
        String hs256Token = JwtTestFixtureHelper.createHmacJwt(
                secret, "urleads-crm", "42", List.of("crm-api"), "crm.leads.read",
                Instant.now(), Instant.now().plusSeconds(600));
        mockMvc.perform(get("/internal/v1/c02-probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + hs256Token))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // A13: Claim nbf en el futuro -> 401
    // =========================================================================
    @Test
    @DisplayName("A13: Token con nbf en el futuro fuera de margen de reloj devuelve 401")
    void a13_NbfEnElFuturo_Retorna401() throws Exception {
        String futureNbf = JwtTestFixtureHelper.createSignedJwt(
                testKeyPair.getPrivate(), "urleads-crm", "42", List.of("crm-api"), "crm.leads.read",
                Instant.now(), Instant.now().plusSeconds(600), Instant.now().plusSeconds(60));

        mockMvc.perform(get("/internal/v1/c02-probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + futureNbf))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // A14: Sesión válida sin token, o sesión + token inválido en probe -> 401 (sin fallback)
    // =========================================================================
    @Test
    @DisplayName("A14: Sesión web en ruta interna no autoriza ni provee fallback ante token ausente o inválido")
    void a14_SesionWebEnRutaInterna_SinFallback_Retorna401() throws Exception {
        MockHttpSession session = loginSession();

        // 1. Sesión activa sin cabecera Authorization
        mockMvc.perform(get("/internal/v1/c02-probe").session(session))
                .andExpect(status().isUnauthorized());

        // 2. Sesión activa con token inválido
        mockMvc.perform(get("/internal/v1/c02-probe")
                        .session(session)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token.invalido"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // A15: Usuario de sesión y query ownerId difieren de JWT sub -> Solo JWT sub controla
    // =========================================================================
    @Test
    @DisplayName("A15: Únicamente el claim 'sub' del JWT verificado determina el propietario en rutas internas")
    void a15_IdentidadDeterminadaExclusivamentePorJwtSub() throws Exception {
        MockHttpSession session = loginSession(); // Sesión pertenece a admin (ID 42 en DB)
        String tokenOwner88 = createValidToken("88", "crm.leads.read");

        mockMvc.perform(get("/internal/v1/c02-probe")
                        .session(session)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner88)
                        .param("ownerId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value(88));
    }

    // =========================================================================
    // A16: Invocación interna con Bearer no persiste sesión HTTP; llamada posterior sin sesión da 401
    // =========================================================================
    @Test
    @DisplayName("A16: Invocación interna con Bearer es stateless y no crea sesión ni cookies JSESSIONID")
    void a16_LlamadaInternaStateless_NoCreaSesion() throws Exception {
        String token = createValidToken("42", "crm.leads.read");

        MvcResult result = mockMvc.perform(get("/internal/v1/c02-probe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        // No se debe emitir cookie de sesión
        assertThat(result.getResponse().getHeader("Set-Cookie")).isNull();
        assertThat(result.getRequest().getSession(false)).isNull();

        // Solicitud posterior a API privada sin sesión ni token da 401
        mockMvc.perform(get("/api/leads"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // A18: Logout invalida sesión web pero JWT emitido previamente sigue técnicamente válido hasta exp
    // =========================================================================
    @Test
    @DisplayName("A18: Logout invalida la sesión web pero los JWT emitidos conservan vigencia hasta su expiración")
    void a18_LogoutInvalidaSesionPeroJwtConservaVigenciaTecnica() throws Exception {
        MockHttpSession session = loginSession();

        // 1. Emitir token durante la sesión activa
        MvcResult tokenResult = mockMvc.perform(post("/api/auth/access-token")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(tokenResult.getResponse().getContentAsString())
                .get("access_token").asText();

        // 2. Cerrar sesión
        mockMvc.perform(post("/api/auth/logout")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        // 3. Sesión invalidada: /api/auth/me devuelve 401
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());

        // 4. Token JWT previamente emitido sigue siendo técnicamente válido en la sonda interna
        mockMvc.perform(get("/internal/v1/c02-probe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value(42));
    }
}
