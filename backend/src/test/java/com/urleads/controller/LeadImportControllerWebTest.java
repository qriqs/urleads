package com.urleads.controller;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.urleads.dto.ImportConfirmResponse;
import com.urleads.dto.ImportPreviewResponse;
import com.urleads.dto.ImportRowPreview;
import com.urleads.exception.LeadValidationException;
import com.urleads.exception.ResourceNotFoundException;
import com.urleads.security.CurrentUserProvider;
import com.urleads.security.SecurityConfig;
import com.urleads.security.UserDetailsServiceImpl;
import com.urleads.service.LeadImportService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LeadImportController.class)
@Import(SecurityConfig.class)
class LeadImportControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @MockBean
    private LeadImportService leadImportService;

    @MockBean
    private CurrentUserProvider currentUserProvider;

    @Test
    @WithAnonymousUser
    @DisplayName("R6: Preview no autenticado retorna 401")
    void testPreviewUnauthenticatedReturns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/leads/import/preview")
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("No autorizado"));
    }

    @Test
    @WithMockUser
    @DisplayName("R6: Preview autenticado sin token CSRF retorna 403")
    void testPreviewMissingCsrfReturns403() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/leads/import/preview")
                        .file(file))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    @DisplayName("R1/R6: Preview autenticado con CSRF válido retorna 200 y diagnóstico")
    void testPreviewSuccess() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{0x50, 0x4B, 0x03, 0x04});

        when(currentUserProvider.getCurrentUserId()).thenReturn(42L);

        ImportPreviewResponse response = new ImportPreviewResponse(
                "token-preview-id-123",
                1,
                1,
                0,
                true,
                List.of(new ImportRowPreview(2, "VALID", Map.of("name", "Juan"), List.of()))
        );
        when(leadImportService.preview(eq(42L), any())).thenReturn(response);

        mockMvc.perform(multipart("/api/leads/import/preview")
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previewId").value("token-preview-id-123"))
                .andExpect(jsonPath("$.totalRows").value(1))
                .andExpect(jsonPath("$.validRows").value(1))
                .andExpect(jsonPath("$.invalidRows").value(0))
                .andExpect(jsonPath("$.canConfirm").value(true))
                .andExpect(jsonPath("$.rows[0].rowNumber").value(2))
                .andExpect(jsonPath("$.rows[0].status").value("VALID"));
    }

    @Test
    @WithMockUser
    @DisplayName("R1: Preview con archivo corrupto o inválido retorna 400 Bad Request")
    void testPreviewValidationErrorReturns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1, 2, 3});

        when(currentUserProvider.getCurrentUserId()).thenReturn(42L);
        when(leadImportService.preview(eq(42L), any()))
                .thenThrow(new LeadValidationException("Cabeceras de plantilla inválidas"));

        mockMvc.perform(multipart("/api/leads/import/preview")
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Cabeceras de plantilla inválidas"));
    }

    @Test
    @WithAnonymousUser
    @DisplayName("R6: Confirm no autenticado retorna 401")
    void testConfirmUnauthenticatedReturns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/leads/import/confirm")
                        .file(file)
                        .param("previewId", "token-preview-id")
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("No autorizado"));
    }

    @Test
    @WithMockUser
    @DisplayName("R6: Confirm autenticado sin token CSRF retorna 403")
    void testConfirmMissingCsrfReturns403() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/leads/import/confirm")
                        .file(file)
                        .param("previewId", "token-preview-id"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    @DisplayName("R5/R6: Confirm autenticado con CSRF válido retorna 200 y confirmación")
    void testConfirmSuccess() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{0x50, 0x4B, 0x03, 0x04});

        when(currentUserProvider.getCurrentUserId()).thenReturn(42L);

        ImportConfirmResponse response = new ImportConfirmResponse(
                "token-preview-id-123",
                2,
                List.of(101L, 102L)
        );
        when(leadImportService.confirm(eq(42L), any(), eq("token-preview-id-123"))).thenReturn(response);

        mockMvc.perform(multipart("/api/leads/import/confirm")
                        .file(file)
                        .param("previewId", "token-preview-id-123")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previewId").value("token-preview-id-123"))
                .andExpect(jsonPath("$.importedCount").value(2))
                .andExpect(jsonPath("$.leadIds[0]").value(101))
                .andExpect(jsonPath("$.leadIds[1]").value(102));
    }

    @Test
    @WithMockUser
    @DisplayName("R5: Confirm con token de otro propietario retorna 404 seguro")
    void testConfirmOtherOwnerTokenReturns404() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{0x50, 0x4B, 0x03, 0x04});

        when(currentUserProvider.getCurrentUserId()).thenReturn(42L);
        when(leadImportService.confirm(eq(42L), any(), eq("other-user-token")))
                .thenThrow(new ResourceNotFoundException("Vista previa no encontrada o no pertenece al usuario"));

        mockMvc.perform(multipart("/api/leads/import/confirm")
                        .file(file)
                        .param("previewId", "other-user-token")
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Finding 4: Propiedades multipart configuradas con límite de archivo de 5MB y petición de 6MB")
    void testMultipartPropertiesConfigured() {
        org.springframework.core.env.Environment env = mockMvc.getDispatcherServlet().getWebApplicationContext().getEnvironment();
        org.assertj.core.api.Assertions.assertThat(env.getProperty("spring.servlet.multipart.max-file-size")).isEqualTo("5MB");
        org.assertj.core.api.Assertions.assertThat(env.getProperty("spring.servlet.multipart.max-request-size")).isEqualTo("6MB");
    }
}
