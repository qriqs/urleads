package com.urleads.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /v3/api-docs should return OpenAPI 3 JSON definition publicly without auth")
    void testOpenApiDocsEndpoint() throws Exception {
        mockMvc.perform(get("/v3/api-docs")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value("UrLeads - Servicio Agenda y Notificaciones API"))
                .andExpect(jsonPath("$.info.version").value("v2.0.0"))
                .andExpect(jsonPath("$.components.securitySchemes.BearerAuth").exists());
    }

    @Test
    @DisplayName("GET /swagger-ui.html should redirect or return swagger UI without auth")
    void testSwaggerUiRedirect() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("swagger-ui/index.html")));
    }

    @Test
    @DisplayName("GET /swagger-ui/index.html should return 200 OK with HTML content")
    void testSwaggerUiIndex() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }
}
