package com.urleads.soap;

import com.urleads.repository.LeadAuditLogRepository;
import com.urleads.repository.LeadRepository;
import com.urleads.repository.NotaRepository;
import com.urleads.repository.UsuarioRepository;
import com.urleads.security.JwtTestFixtureHelper;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletConfig;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.ws.transport.http.MessageDispatcherServlet;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration," +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
        "urleads.jwt.enabled=true"
})
@AutoConfigureMockMvc
class LeadSoapSecurityWebTest {

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
    private ApplicationContext applicationContext;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @MockBean
    private UsuarioRepository usuarioRepository;

    @MockBean
    private LeadRepository leadRepository;

    @MockBean
    private LeadAuditLogRepository leadAuditLogRepository;

    @MockBean
    private NotaRepository notaRepository;

    private MessageDispatcherServlet messageDispatcherServlet;

    @BeforeEach
    void setUp() throws Exception {
        messageDispatcherServlet = new MessageDispatcherServlet();
        messageDispatcherServlet.setApplicationContext(applicationContext);
        messageDispatcherServlet.setTransformWsdlLocations(true);
        messageDispatcherServlet.init(new MockServletConfig("messageDispatcherServlet"));
    }

    private MockHttpServletResponse dispatchThroughSecurity(MockHttpServletRequest request) throws Exception {
        if (request.getServletPath() == null || request.getServletPath().isEmpty()) {
            request.setServletPath(request.getRequestURI());
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        springSecurityFilterChain.doFilter(request, response, (req, res) -> {
            try {
                messageDispatcherServlet.service(req, res);
            } catch (Exception e) {
                throw new ServletException(e);
            }
        });
        return response;
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

    private static final String SAMPLE_SOAP_REQUEST =
            "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:v1=\"urn:urleads:crm:leads:v1\">" +
            "   <soapenv:Header/>" +
            "   <soapenv:Body>" +
            "      <v1:GetLeadRequest>" +
            "         <v1:leadId>105</v1:leadId>" +
            "      </v1:GetLeadRequest>" +
            "   </soapenv:Body>" +
            "</soapenv:Envelope>";

    // =========================================================================
    // 1. WSDL público sin debilitar POST
    // =========================================================================
    @Test
    @DisplayName("R1 & R2: GET /ws/leads.wsdl es publico y retorna definicion WSDL correcta sin autenticacion")
    void wsdl_PublicAccess_Returns200WithWsdlContent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/leads.wsdl");
        request.setServletPath("/ws/leads.wsdl");

        MockHttpServletResponse response = dispatchThroughSecurity(request);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).contains("xml");
        String content = response.getContentAsString();
        assertThat(content).contains("targetNamespace=\"urn:urleads:crm:leads:v1\"");
        assertThat(content).contains("name=\"LeadsPort\"");
        assertThat(content).contains("urn:urleads:crm:leads:v1:GetLead");
        assertThat(content).contains("/ws/leads");
    }

    // =========================================================================
    // 2. Token ausente -> HTTP 401
    // =========================================================================
    @Test
    @DisplayName("R2: POST /ws/leads sin cabecera Authorization retorna HTTP 401 pre-dispatch")
    void post_MissingToken_ReturnsHttp401WithWwwAuthenticateBearer() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ws/leads");
        request.setContentType(MediaType.TEXT_XML_VALUE);
        request.setContent(SAMPLE_SOAP_REQUEST.getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse response = dispatchThroughSecurity(request);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNotNull();
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
    }

    // =========================================================================
    // 3. Token con firma invalida -> HTTP 401
    // =========================================================================
    @Test
    @DisplayName("R2: POST /ws/leads con token firmado por clave no confiable retorna HTTP 401")
    void post_InvalidSignatureToken_ReturnsHttp401() throws Exception {
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

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ws/leads");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + untrustedToken);
        request.setContentType(MediaType.TEXT_XML_VALUE);
        request.setContent(SAMPLE_SOAP_REQUEST.getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse response = dispatchThroughSecurity(request);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).contains("invalid_token");
    }

    // =========================================================================
    // 4. Token expirado -> HTTP 401
    // =========================================================================
    @Test
    @DisplayName("R2: POST /ws/leads con token expirado retorna HTTP 401")
    void post_ExpiredToken_ReturnsHttp401() throws Exception {
        String expiredToken = JwtTestFixtureHelper.createSignedJwt(
                testKeyPair.getPrivate(),
                "urleads-crm",
                "42",
                List.of("crm-api"),
                "crm.leads.read",
                Instant.now().minusSeconds(1200),
                Instant.now().minusSeconds(600),
                null
        );

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ws/leads");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken);
        request.setContentType(MediaType.TEXT_XML_VALUE);
        request.setContent(SAMPLE_SOAP_REQUEST.getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse response = dispatchThroughSecurity(request);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).contains("invalid_token");
    }

    // =========================================================================
    // 5. Token con emisor incorrecto -> HTTP 401
    // =========================================================================
    @Test
    @DisplayName("R2: POST /ws/leads con emisor no canonico retorna HTTP 401")
    void post_WrongIssuerToken_ReturnsHttp401() throws Exception {
        String badIssuerToken = JwtTestFixtureHelper.createSignedJwt(
                testKeyPair.getPrivate(),
                "foreign-auth-service",
                "42",
                List.of("crm-api"),
                "crm.leads.read",
                Instant.now(),
                Instant.now().plusSeconds(600),
                null
        );

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ws/leads");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + badIssuerToken);
        request.setContentType(MediaType.TEXT_XML_VALUE);
        request.setContent(SAMPLE_SOAP_REQUEST.getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse response = dispatchThroughSecurity(request);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).contains("invalid_token");
    }

    // =========================================================================
    // 6. Token con audiencia incorrecta -> HTTP 401
    // =========================================================================
    @Test
    @DisplayName("R2: POST /ws/leads sin audiencia crm-api retorna HTTP 401")
    void post_WrongAudienceToken_ReturnsHttp401() throws Exception {
        String badAudienceToken = JwtTestFixtureHelper.createSignedJwt(
                testKeyPair.getPrivate(),
                "urleads-crm",
                "42",
                List.of("comercial-api", "agenda-api"),
                "crm.leads.read",
                Instant.now(),
                Instant.now().plusSeconds(600),
                null
        );

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ws/leads");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + badAudienceToken);
        request.setContentType(MediaType.TEXT_XML_VALUE);
        request.setContent(SAMPLE_SOAP_REQUEST.getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse response = dispatchThroughSecurity(request);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).contains("invalid_token");
    }

    // =========================================================================
    // 7. Token sin scope crm.leads.read -> HTTP 403
    // =========================================================================
    @Test
    @DisplayName("R2: POST /ws/leads con token valido pero carente de scope crm.leads.read retorna HTTP 403")
    void post_MissingScope_ReturnsHttp403WithInsufficientScope() throws Exception {
        String tokenWrongScope = createValidToken("42", "crm.leads.write");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ws/leads");
        request.setServletPath("/ws/leads");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWrongScope);
        request.setContentType(MediaType.TEXT_XML_VALUE);
        request.setContent(SAMPLE_SOAP_REQUEST.getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse response = dispatchThroughSecurity(request);

        System.out.println("MISSING SCOPE STATUS: " + response.getStatus());
        System.out.println("MISSING SCOPE AUTH: " + response.getHeader(HttpHeaders.WWW_AUTHENTICATE));
        System.out.println("MISSING SCOPE BODY: " + response.getContentAsString());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).contains("insufficient_scope");
    }

    // =========================================================================
    // 8. Sesión web JSESSIONID no sirve como fallback -> HTTP 401
    // =========================================================================
    @Test
    @DisplayName("R2: POST /ws/leads con sesion web JSESSIONID pero sin Bearer token es rechazado con HTTP 401")
    void post_WebSessionCookieRejected_ReturnsHttp401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ws/leads");
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        request.setContentType(MediaType.TEXT_XML_VALUE);
        request.setContent(SAMPLE_SOAP_REQUEST.getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse response = dispatchThroughSecurity(request);

        assertThat(response.getStatus()).isEqualTo(401);
    }

    // =========================================================================
    // 9. Autenticación básica rechazada -> HTTP 401
    // =========================================================================
    @Test
    @DisplayName("R2: POST /ws/leads con cabecera Basic Auth es rechazado con HTTP 401 Bearer requerido")
    void post_BasicAuthRejected_ReturnsHttp401() throws Exception {
        String basicCreds = Base64.getEncoder().encodeToString("admin:Password123!".getBytes(StandardCharsets.UTF_8));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ws/leads");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic " + basicCreds);
        request.setContentType(MediaType.TEXT_XML_VALUE);
        request.setContent(SAMPLE_SOAP_REQUEST.getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse response = dispatchThroughSecurity(request);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
    }

    // =========================================================================
    // 10. Token por query param rechazado -> HTTP 401
    // =========================================================================
    @Test
    @DisplayName("R2: POST /ws/leads con token en query parameter access_token es rechazado con HTTP 401")
    void post_QueryParamTokenRejected_ReturnsHttp401() throws Exception {
        String token = createValidToken("42", "crm.leads.read");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ws/leads");
        request.setParameter("access_token", token);
        request.setContentType(MediaType.TEXT_XML_VALUE);
        request.setContent(SAMPLE_SOAP_REQUEST.getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse response = dispatchThroughSecurity(request);

        assertThat(response.getStatus()).isEqualTo(401);
    }

    // =========================================================================
    // 11. Carga XML sobredimensionada (> 16 KB) -> HTTP 413
    // =========================================================================
    @Test
    @DisplayName("R5: POST /ws/leads con carga XML superior a 16 KB es rechazada a nivel HTTP con 413")
    void post_OversizedPayload_RejectedAtHttpIngressWith413() throws Exception {
        String token = createValidToken("42", "crm.leads.read");

        // Construir carga XML de 20 KB con comentarios XML seguros
        String padding = "<!-- " + "A".repeat(20 * 1024) + " -->";
        String oversizedSoap = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:v1=\"urn:urleads:crm:leads:v1\">" +
                padding +
                "<soapenv:Body><v1:GetLeadRequest><v1:leadId>105</v1:leadId></v1:GetLeadRequest></soapenv:Body></soapenv:Envelope>";

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ws/leads");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        request.setContentType(MediaType.TEXT_XML_VALUE);
        byte[] bytes = oversizedSoap.getBytes(StandardCharsets.UTF_8);
        request.setContent(bytes);

        MockHttpServletResponse response = dispatchThroughSecurity(request);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("Carga XML excede el limite maximo permitido");
    }

    // =========================================================================
    // 12. Content-Length sobredimensionado -> HTTP 413 inmediato
    // =========================================================================
    @Test
    @DisplayName("R5: POST /ws/leads con Content-Length declarado superior a 16 KB es rechazado con 413")
    void post_ContentLengthHeaderOversized_RejectedImmediatelyWith413() throws Exception {
        String token = createValidToken("42", "crm.leads.read");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ws/leads");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        request.setContentType(MediaType.TEXT_XML_VALUE);
        request.addHeader(HttpHeaders.CONTENT_LENGTH, "50000");
        request.setContent(SAMPLE_SOAP_REQUEST.getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse response = dispatchThroughSecurity(request);

        assertThat(response.getStatus()).isEqualTo(413);
    }
}
