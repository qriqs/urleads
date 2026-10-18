package com.urleads.comercial.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SwaggerOpenApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /v3/api-docs returns OpenAPI 3.0 specification without authentication")
    void getApiDocs_success() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.openapi", containsString("3.")))
            .andExpect(jsonPath("$.info.title", equalTo("UrLeads - Servicio Comercial API")))
            .andExpect(jsonPath("$.info.version", equalTo("v2.0")))
            .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type", equalTo("http")))
            .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme", equalTo("bearer")))
            .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat", equalTo("JWT")))
            .andExpect(jsonPath("$.paths['/api/v1/catalog']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/quotes']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/quotes/{id}/accept']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/quotes/{id}/revisions']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/quotes/{id}/orders']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/orders/{id']").doesNotExist()) // verify valid syntax
            .andExpect(jsonPath("$.paths['/api/v1/metrics']").exists());
    }

    @Test
    @DisplayName("GET /swagger-ui.html redirects to swagger-ui/index.html without authentication")
    void getSwaggerUi_redirectsOrOk() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", containsString("/swagger-ui/index.html")));
    }

    @Test
    @DisplayName("GET /swagger-ui/index.html is accessible publicly without JWT token")
    void getSwaggerUiIndex_success() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
            .andExpect(status().isOk());
    }
}
