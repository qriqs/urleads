package com.urleads.comercial.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class JwtSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Health endpoint is accessible without token")
    void whenHealthEndpoint_thenSuccessWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.service").value("urleads-comercial"))
            .andExpect(jsonPath("$.port").value(8081));
    }

    @Test
    @DisplayName("401 Unauthorized when no token is provided to secure endpoint")
    void whenNoTokenProvided_thenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/secure-ping"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").value("Token inválido o no suministrado"));
    }

    @Test
    @DisplayName("401 Unauthorized when token is malformed")
    void whenMalformedToken_thenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/secure-ping")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.valid.jwt.token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("401 Unauthorized when token signature was created with unknown key")
    void whenTokenSignedWithUnknownKey_thenReturns401() throws Exception {
        String token = JwtTestUtils.generateTokenWithUnknownKey();

        mockMvc.perform(get("/api/v1/secure-ping")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("401 Unauthorized when token is expired")
    void whenTokenExpired_thenReturns401() throws Exception {
        String token = JwtTestUtils.generateExpiredToken();

        mockMvc.perform(get("/api/v1/secure-ping")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("401 Unauthorized when issuer is not urleads-crm")
    void whenInvalidIssuer_thenReturns401() throws Exception {
        String token = JwtTestUtils.generateTokenWithInvalidIssuer("rogue-issuer");

        mockMvc.perform(get("/api/v1/secure-ping")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("401 Unauthorized when audience does not contain comercial-api")
    void whenMissingAudience_thenReturns401() throws Exception {
        String token = JwtTestUtils.generateTokenWithAudiences(List.of("agenda-api", "crm-api"));

        mockMvc.perform(get("/api/v1/secure-ping")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("401 Unauthorized when subject is non-numeric")
    void whenNonNumericSubject_thenReturns401() throws Exception {
        String token = JwtTestUtils.generateTokenWithInvalidSubject("john-doe-uuid");

        mockMvc.perform(get("/api/v1/secure-ping")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("403 Forbidden when token lacks comercial.access scope")
    void whenMissingRequiredScope_thenReturns403() throws Exception {
        String token = JwtTestUtils.generateValidToken("42", "crm.leads.read");

        mockMvc.perform(get("/api/v1/secure-ping")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
            .andExpect(jsonPath("$.message").value("Permiso insuficiente (scope comercial.access requerido)"));
    }

    @Test
    @DisplayName("200 OK when token is valid with exact comercial-api audience and comercial.access scope")
    void whenValidToken_thenReturns200() throws Exception {
        String token = JwtTestUtils.generateValidToken();

        mockMvc.perform(get("/api/v1/secure-ping")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("pong"))
            .andExpect(jsonPath("$.ownerId").value("42"))
            .andExpect(jsonPath("$.scope").value("comercial.access"));
    }

    @Test
    @DisplayName("200 OK when token contains multiple audiences including comercial-api")
    void whenMultipleAudiencesIncludingComercial_thenReturns200() throws Exception {
        String token = JwtTestUtils.generateTokenWithAudiences(List.of("crm-api", "comercial-api", "agenda-api"));

        mockMvc.perform(get("/api/v1/secure-ping")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ownerId").value("42"));
    }
}
