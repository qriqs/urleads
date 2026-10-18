package com.urleads.soap;

import com.urleads.dto.InternalLeadResponse;
import com.urleads.exception.LeadNotFoundException;
import com.urleads.repository.LeadAuditLogRepository;
import com.urleads.repository.LeadRepository;
import com.urleads.repository.NotaRepository;
import com.urleads.repository.UsuarioRepository;
import com.urleads.security.JwtTestFixtureHelper;
import com.urleads.service.LeadService;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.mock.web.MockServletConfig;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.ws.transport.http.MessageDispatcherServlet;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration," +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
        "urleads.jwt.enabled=true"
})
@AutoConfigureMockMvc
class LeadSoapEndpointWebTest {

    private static KeyPair testKeyPair;

    @DynamicPropertySource
    static void configureJwtProperties(DynamicPropertyRegistry registry) {
        testKeyPair = JwtTestFixtureHelper.generateRsaKeyPair(2048);
        registry.add("urleads.jwt.enabled", () -> "true");
        registry.add("urleads.jwt.private-key-value", () -> JwtTestFixtureHelper.toPkcs8Pem(testKeyPair.getPrivate()));
        registry.add("urleads.jwt.public-key-value", () -> JwtTestFixtureHelper.toX509Pem(testKeyPair.getPublic()));
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @MockBean
    private LeadService leadService;

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
                // Emulacion del contenedor de servlets (Tomcat): ante excepcion de analisis SOAP/XML o DTD, status 500
                ((HttpServletResponse) res).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        });
        return response;
    }

    private String createValidToken(String subject) throws Exception {
        return JwtTestFixtureHelper.createSignedJwt(
                testKeyPair.getPrivate(),
                "urleads-crm",
                subject,
                List.of("crm-api", "comercial-api", "agenda-api"),
                "crm.leads.read",
                Instant.now(),
                Instant.now().plusSeconds(600),
                null
        );
    }

    private String createSoapEnvelope(String bodyContent) {
        return "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:v1=\"urn:urleads:crm:leads:v1\">\n" +
                "   <soapenv:Header/>\n" +
                "   <soapenv:Body>\n" +
                bodyContent + "\n" +
                "   </soapenv:Body>\n" +
                "</soapenv:Envelope>";
    }

    // =========================================================================
    // 1. GetLead exitoso con token y scope correcto (HTTP 200, DTO sin PII)
    // =========================================================================
    @Test
    @DisplayName("R1 & R3: GetLead valido retorna 200 con GetLeadResponse exacto, fecha formateada y sin PII")
    void getLead_Success_Returns200WithMinimalDto() throws Exception {
        when(leadService.obtenerInterno(105L, 42L))
                .thenReturn(new InternalLeadResponse(105L, "EN_SEGUIMIENTO", LocalDate.of(2026, 10, 25)));

        String token = createValidToken("42");
        String requestXml = createSoapEnvelope("      <v1:GetLeadRequest><v1:leadId>105</v1:leadId></v1:GetLeadRequest>");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ws/leads");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        request.addHeader("SOAPAction", "urn:urleads:crm:leads:v1:GetLead");
        request.setContentType(MediaType.TEXT_XML_VALUE);
        request.setContent(requestXml.getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse response = dispatchThroughSecurity(request);

        assertThat(response.getStatus()).isEqualTo(200);
        String responseContent = response.getContentAsString();

        assertThat(responseContent).contains("GetLeadResponse");
        assertThat(responseContent).contains("urn:urleads:crm:leads:v1");
        assertThat(responseContent).contains("105");
        assertThat(responseContent).contains("EN_SEGUIMIENTO");
        assertThat(responseContent).contains("2026-10-25");

        // Verificación estricta de ausencia de PII y metadatos sensibles
        assertThat(responseContent).doesNotContain("name");
        assertThat(responseContent).doesNotContain("nombre");
        assertThat(responseContent).doesNotContain("email");
        assertThat(responseContent).doesNotContain("correo");
        assertThat(responseContent).doesNotContain("phone");
        assertThat(responseContent).doesNotContain("telefono");
        assertThat(responseContent).doesNotContain("notes");
        assertThat(responseContent).doesNotContain("notas");
        assertThat(responseContent).doesNotContain("ownerId");
        assertThat(responseContent).doesNotContain("usuarioId");

        // Verificación de invocación directa a LeadService con usuario propietario acotado desde 'sub'
        verify(leadService).obtenerInterno(105L, 42L);
    }

    // =========================================================================
    // 2. GetLead para lead en etapa CERRADO (fecha null)
    // =========================================================================
    @Test
    @DisplayName("R1: GetLead para lead CERRADO retorna 200 con stage CERRADO y nextFollowUp nulo o nil")
    void getLead_Cerrado_Returns200WithNullFollowUp() throws Exception {
        when(leadService.obtenerInterno(106L, 42L))
                .thenReturn(new InternalLeadResponse(106L, "CERRADO", null));

        String token = createValidToken("42");
        String requestXml = createSoapEnvelope("      <v1:GetLeadRequest><v1:leadId>106</v1:leadId></v1:GetLeadRequest>");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ws/leads");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        request.setContentType(MediaType.TEXT_XML_VALUE);
        request.setContent(requestXml.getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse response = dispatchThroughSecurity(request);

        assertThat(response.getStatus()).isEqualTo(200);
        String responseContent = response.getContentAsString();
        assertThat(responseContent).contains("106");
        assertThat(responseContent).contains("CERRADO");
        assertThat(responseContent).doesNotContain("2026-");
    }

    // =========================================================================
    // 3. Lead inexistente -> SOAP Fault Client LEAD_NOT_FOUND (HTTP 500)
    // =========================================================================
    @Test
    @DisplayName("R4: Lead inexistente retorna SOAP Fault Client LEAD_NOT_FOUND con HTTP 500")
    void getLead_NotFound_ReturnsSoapClientFault500() throws Exception {
        when(leadService.obtenerInterno(999L, 42L))
                .thenThrow(new LeadNotFoundException("Lead no encontrado"));

        String token = createValidToken("42");
        String requestXml = createSoapEnvelope("      <v1:GetLeadRequest><v1:leadId>999</v1:leadId></v1:GetLeadRequest>");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ws/leads");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        request.setContentType(MediaType.TEXT_XML_VALUE);
        request.setContent(requestXml.getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse response = dispatchThroughSecurity(request);

        assertThat(response.getStatus()).isEqualTo(500);
        String responseContent = response.getContentAsString();
        assertThat(responseContent).contains("faultcode");
        assertThat(responseContent).contains("Client");
        assertThat(responseContent).contains("LEAD_NOT_FOUND");
        assertThat(responseContent).contains("El lead solicitado no existe o no pertenece al usuario autenticado.");
    }

    // =========================================================================
    // 4. Lead ajeno -> exactamente el MISMO SOAP Fault Client LEAD_NOT_FOUND (Paridad estricta)
    // =========================================================================
    @Test
    @DisplayName("R4: Lead ajeno (de otro propietario) retorna exactamente el mismo SOAP Fault indistinguible")
    void getLead_ForeignLead_ReturnsIdenticalSoapFault() throws Exception {
        when(leadService.obtenerInterno(200L, 42L))
                .thenThrow(new LeadNotFoundException("Lead no encontrado"));

        String token = createValidToken("42");
        String requestXml = createSoapEnvelope("      <v1:GetLeadRequest><v1:leadId>200</v1:leadId></v1:GetLeadRequest>");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ws/leads");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        request.setContentType(MediaType.TEXT_XML_VALUE);
        request.setContent(requestXml.getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse response = dispatchThroughSecurity(request);

        assertThat(response.getStatus()).isEqualTo(500);
        String responseContent = response.getContentAsString();
        assertThat(responseContent).contains("faultcode");
        assertThat(responseContent).contains("Client");
        assertThat(responseContent).contains("LEAD_NOT_FOUND");
        assertThat(responseContent).contains("El lead solicitado no existe o no pertenece al usuario autenticado.");
    }

    // =========================================================================
    // 5. XML mal formado sintácticamente -> SOAP Fault Client (HTTP 400 o 500)
    // =========================================================================
    @Test
    @DisplayName("R4: Carga XML mal formada sintacticamente retorna SOAP Fault Client o HTTP 400/500")
    void getLead_MalformedXml_ReturnsSoapClientFault() throws Exception {
        String token = createValidToken("42");
        String malformedXml = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\"><soapenv:Body><unclosed>";

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ws/leads");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        request.setContentType(MediaType.TEXT_XML_VALUE);
        request.setContent(malformedXml.getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse response = dispatchThroughSecurity(request);

        assertThat(response.getStatus()).isIn(400, 500);
    }

    // =========================================================================
    // 6. Solicitud no conforme al esquema XSD (leadId no positivo) -> SOAP Fault Client
    // =========================================================================
    @Test
    @DisplayName("R4 & R5: Solicitud con leadId negativo o cero viola XSD y retorna SOAP Fault Client")
    void getLead_NegativeLeadId_ReturnsSchemaValidationFault() throws Exception {
        String token = createValidToken("42");
        String requestXml = createSoapEnvelope("      <v1:GetLeadRequest><v1:leadId>-5</v1:leadId></v1:GetLeadRequest>");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ws/leads");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        request.setContentType(MediaType.TEXT_XML_VALUE);
        request.setContent(requestXml.getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse response = dispatchThroughSecurity(request);

        assertThat(response.getStatus()).isEqualTo(500);
        String responseContent = response.getContentAsString();
        assertThat(responseContent).contains("faultcode");
        assertThat(responseContent).contains("Client");
    }

    // =========================================================================
    // 7. Solicitud con elemento obligatorio ausente en XSD -> SOAP Fault Client
    // =========================================================================
    @Test
    @DisplayName("R4 & R5: Solicitud sin leadId viola esquema XSD y retorna SOAP Fault Client")
    void getLead_MissingLeadId_ReturnsSchemaValidationFault() throws Exception {
        String token = createValidToken("42");
        String requestXml = createSoapEnvelope("      <v1:GetLeadRequest></v1:GetLeadRequest>");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ws/leads");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        request.setContentType(MediaType.TEXT_XML_VALUE);
        request.setContent(requestXml.getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse response = dispatchThroughSecurity(request);

        assertThat(response.getStatus()).isEqualTo(500);
        String responseContent = response.getContentAsString();
        assertThat(responseContent).contains("faultcode");
        assertThat(responseContent).contains("Client");
    }

    // =========================================================================
    // 8. Vector de ataque XXE con archivo centinela local -> Rechazo observable y sin invocación de servicio
    // =========================================================================
    @Test
    @DisplayName("R5 (C2): Vector XXE con archivo centinela es rechazado observablemente sin expansion y sin invocar servicio")
    void getLead_XxeExternalEntityWithSentinelFile_RejectedSafelyAndServiceNeverInvoked() throws Exception {
        String token = createValidToken("42");
        String sentinelSecret = "URLEADS_XXE_SENTINEL_SECRET_" + UUID.randomUUID();
        Path tempSentinelFile = Files.createTempFile("urleads-xxe-sentinel-", ".txt");

        try {
            Files.writeString(tempSentinelFile, sentinelSecret, StandardCharsets.UTF_8);

            String xxePayload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<!DOCTYPE test [\n" +
                    "  <!ENTITY xxe SYSTEM \"" + tempSentinelFile.toUri().toASCIIString() + "\" >\n" +
                    "]>\n" +
                    "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:v1=\"urn:urleads:crm:leads:v1\">\n" +
                    "   <soapenv:Header/>\n" +
                    "   <soapenv:Body>\n" +
                    "      <v1:GetLeadRequest>\n" +
                    "         <v1:leadId>&xxe;</v1:leadId>\n" +
                    "      </v1:GetLeadRequest>\n" +
                    "   </soapenv:Body>\n" +
                    "</soapenv:Envelope>";

            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ws/leads");
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            request.setContentType(MediaType.TEXT_XML_VALUE);
            request.setContent(xxePayload.getBytes(StandardCharsets.UTF_8));

            MockHttpServletResponse response = dispatchThroughSecurity(request);

            // 1. Debe retornar código de rechazo seguro (400 o 500)
            assertThat(response.getStatus()).isIn(400, 500);
            String responseContent = response.getContentAsString();

            // 2. El contenido secreto del centinela local NUNCA debe ser resuelto ni reflejado
            assertThat(responseContent).doesNotContain(sentinelSecret);

            // 3. El servicio de aplicación NUNCA debe haber sido invocado
            verify(leadService, never()).obtenerInterno(any(), any());
        } finally {
            Files.deleteIfExists(tempSentinelFile);
        }
    }

    // =========================================================================
    // 9. Vector de ataque con DTD externa local -> Rechazo observable y sin invocación de servicio
    // =========================================================================
    @Test
    @DisplayName("R5 (C2): Inclusión de DTD externa con centinela es rechazada observablemente sin invocar servicio")
    void getLead_ExternalDtdDeclarationWithSentinel_RejectedSafelyAndServiceNeverInvoked() throws Exception {
        String token = createValidToken("42");
        String dtdSecret = "URLEADS_DTD_SENTINEL_SECRET_" + UUID.randomUUID();
        Path tempDtdFile = Files.createTempFile("urleads-xxe-dtd-", ".dtd");

        try {
            Files.writeString(tempDtdFile, "<!ENTITY dtdEntity \"" + dtdSecret + "\">", StandardCharsets.UTF_8);

            String dtdPayload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<!DOCTYPE test SYSTEM \"" + tempDtdFile.toUri().toASCIIString() + "\">\n" +
                    "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:v1=\"urn:urleads:crm:leads:v1\">\n" +
                    "   <soapenv:Header/>\n" +
                    "   <soapenv:Body>\n" +
                    "      <v1:GetLeadRequest>\n" +
                    "         <v1:leadId>&dtdEntity;</v1:leadId>\n" +
                    "      </v1:GetLeadRequest>\n" +
                    "   </soapenv:Body>\n" +
                    "</soapenv:Envelope>";

            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ws/leads");
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            request.setContentType(MediaType.TEXT_XML_VALUE);
            request.setContent(dtdPayload.getBytes(StandardCharsets.UTF_8));

            MockHttpServletResponse response = dispatchThroughSecurity(request);

            // 1. Debe retornar código de rechazo seguro (400 o 500)
            assertThat(response.getStatus()).isIn(400, 500);
            String responseContent = response.getContentAsString();

            // 2. El contenido secreto de la DTD externa NUNCA debe ser resuelto ni reflejado
            assertThat(responseContent).doesNotContain(dtdSecret);

            // 3. El servicio de aplicación NUNCA debe haber sido invocado
            verify(leadService, never()).obtenerInterno(any(), any());
        } finally {
            Files.deleteIfExists(tempDtdFile);
        }
    }
}
