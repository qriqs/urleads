package com.urleads.docs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urleads.repository.LeadAuditLogRepository;
import com.urleads.repository.LeadImportReceiptRepository;
import com.urleads.repository.LeadRepository;
import com.urleads.repository.NotaRepository;
import com.urleads.repository.UsuarioRepository;
import com.urleads.security.CurrentUserProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration," +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration"
})
@AutoConfigureMockMvc
class OpenApiImportTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UsuarioRepository usuarioRepository;

    @MockBean
    private LeadRepository leadRepository;

    @MockBean
    private LeadAuditLogRepository leadAuditLogRepository;

    @MockBean
    private NotaRepository notaRepository;

    @MockBean
    private LeadImportReceiptRepository receiptRepository;

    @MockBean
    private CurrentUserProvider currentUserProvider;

    @Test
    @DisplayName("R8: /v3/api-docs contiene documentación OpenAPI de /api/leads/import/preview y /api/leads/import/confirm")
    void openApiDocsDocumentsImportEndpoints() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(json);
        JsonNode paths = root.path("paths");

        // 1. Ruta /api/leads/import/preview
        assertThat(paths.has("/api/leads/import/preview")).isTrue();
        JsonNode previewPost = paths.path("/api/leads/import/preview").path("post");
        assertThat(previewPost.isMissingNode()).isFalse();
        assertThat(previewPost.path("summary").asText()).contains("Vista previa");

        // 2. Ruta /api/leads/import/confirm
        assertThat(paths.has("/api/leads/import/confirm")).isTrue();
        JsonNode confirmPost = paths.path("/api/leads/import/confirm").path("post");
        assertThat(confirmPost.isMissingNode()).isFalse();
        assertThat(confirmPost.path("summary").asText()).contains("Confirmar");

        // 3. Ambas rutas usan multipart/form-data
        JsonNode previewContent = previewPost.path("requestBody").path("content");
        assertThat(previewContent.has("multipart/form-data")).isTrue();

        JsonNode confirmContent = confirmPost.path("requestBody").path("content");
        assertThat(confirmContent.has("multipart/form-data")).isTrue();

        // 4. Esquema de seguridad y respuestas documentadas
        assertThat(previewPost.path("security").isArray()).isTrue();
        assertThat(confirmPost.path("security").isArray()).isTrue();
        assertThat(previewPost.path("responses").has("200")).isTrue();
        assertThat(previewPost.path("responses").has("400")).isTrue();
        assertThat(confirmPost.path("responses").has("200")).isTrue();
        assertThat(confirmPost.path("responses").has("404")).isTrue();
    }
}
