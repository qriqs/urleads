package com.urleads.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import jakarta.persistence.RollbackException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.TransactionSystemException;

import com.urleads.dto.CreateLeadRequest;
import com.urleads.dto.LeadResponse;
import com.urleads.dto.PageResponse;
import com.urleads.dto.UpdateLeadRequest;
import com.urleads.exception.LeadNotFoundException;
import com.urleads.exception.LeadValidationException;
import com.urleads.security.CurrentUserProvider;
import com.urleads.security.SecurityConfig;
import com.urleads.security.UserDetailsServiceImpl;
import com.urleads.service.LeadService;

@WebMvcTest(LeadController.class)
@Import(SecurityConfig.class)
@WithMockUser
class LeadControllerTest {

    private static final Long USUARIO_ACTUAL = 7L;

    private static final String BODY_VALIDO = """
            {"name":"Ana Torres","phone":"999111222","email":"ana@mail.com",
             "stage":"NUEVO","nextFollowUp":"2026-11-01","service":"Web"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LeadService leadService;

    @MockBean
    private CurrentUserProvider currentUserProvider;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @BeforeEach
    void setUp() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USUARIO_ACTUAL);
    }

    private LeadResponse respuesta() {
        return new LeadResponse(10L, "Ana Torres", "999111222", "ana@mail.com", "NUEVO",
                LocalDate.of(2026, 11, 1),
                OffsetDateTime.parse("2026-10-14T00:00:00Z"),
                OffsetDateTime.parse("2026-10-14T00:00:00Z"),
                "Web");
    }

    private ResultActions enviar(String metodo, String url, String json) throws Exception {
        var builder = switch (metodo) {
            case "POST" -> post(url);
            case "PUT" -> put(url);
            default -> throw new IllegalArgumentException(metodo);
        };
        return mockMvc.perform(builder
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    // ---------- SEGURIDAD Y CSRF ----------

    @Test
    @WithAnonymousUser
    void peticionSinSesion_devuelve401() throws Exception {
        mockMvc.perform(get("/api/leads"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("No autorizado"));
    }

    @Test
    void postSinCsrf_devuelve403() throws Exception {
        mockMvc.perform(post("/api/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isForbidden());
    }

    @Test
    void putSinCsrf_devuelve403() throws Exception {
        mockMvc.perform(put("/api/leads/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteSinCsrf_devuelve403() throws Exception {
        mockMvc.perform(delete("/api/leads/10"))
                .andExpect(status().isForbidden());
    }

    // ---------- POST ----------

    @Test
    void crear_devuelve201ConPayloadDelContrato_eIgnoraUsuarioIdDelCliente() throws Exception {
        when(leadService.crear(eq(USUARIO_ACTUAL), any(CreateLeadRequest.class))).thenReturn(respuesta());

        mockMvc.perform(post("/api/leads")
                        .with(csrf())
                        .param("usuarioId", "999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.name").value("Ana Torres"))
                .andExpect(jsonPath("$.phone").value("999111222"))
                .andExpect(jsonPath("$.email").value("ana@mail.com"))
                .andExpect(jsonPath("$.stage").value("NUEVO"))
                .andExpect(jsonPath("$.nextFollowUp").value("2026-11-01"))
                .andExpect(jsonPath("$.service").value("Web"))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.updatedAt").isString())
                .andExpect(jsonPath("$.nombre").doesNotExist());

        verify(leadService).crear(eq(USUARIO_ACTUAL), any(CreateLeadRequest.class));
        verify(leadService, never()).crear(eq(999L), any(CreateLeadRequest.class));
    }

    @Test
    void crear_mapeaCamposPublicosYRecortaElNombre() throws Exception {
        when(leadService.crear(eq(USUARIO_ACTUAL), any(CreateLeadRequest.class))).thenReturn(respuesta());

        enviar("POST", "/api/leads", """
                {"name":"  Ana Torres  ","phone":"999","stage":"NUEVO","service":"Web"}""")
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateLeadRequest> captor = ArgumentCaptor.forClass(CreateLeadRequest.class);
        verify(leadService).crear(eq(USUARIO_ACTUAL), captor.capture());
        assertThat(captor.getValue().getNombre()).isEqualTo("Ana Torres");
        assertThat(captor.getValue().getTelefono()).isEqualTo("999");
        assertThat(captor.getValue().getEtapa()).isEqualTo("NUEVO");
        assertThat(captor.getValue().getService()).isEqualTo("Web");
    }

    @Test
    void crear_emailInvalido_devuelve400ConNombrePublico() throws Exception {
        enviar("POST", "/api/leads", """
                {"name":"Ana","phone":"999","email":"no-es-email","stage":"NUEVO"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.email").exists());

        verifyNoInteractions(leadService);
    }

    @Test
    void crear_sinNombre_devuelve400() throws Exception {
        enviar("POST", "/api/leads", """
                {"phone":"999","stage":"NUEVO"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.nombre").doesNotExist());

        verifyNoInteractions(leadService);
    }

    @Test
    void crear_nombreConEspaciosQueQuedaEnUnCaracter_devuelve400() throws Exception {
        enviar("POST", "/api/leads", """
                {"name":" a ","phone":"999","stage":"NUEVO"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());

        verifyNoInteractions(leadService);
    }

    @Test
    void crear_nombreDe121Caracteres_devuelve400() throws Exception {
        enviar("POST", "/api/leads", """
                {"name":"%s","phone":"999","stage":"NUEVO"}""".formatted("a".repeat(121)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void crear_etapaInvalida_devuelve400() throws Exception {
        enviar("POST", "/api/leads", """
                {"name":"Ana","phone":"999","stage":"INVENTADA"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.stage").exists());
    }

    @Test
    void crear_serviceMasLargoQue255_devuelve400() throws Exception {
        enviar("POST", "/api/leads", """
                {"name":"Ana","phone":"999","stage":"NUEVO","service":"%s"}""".formatted("s".repeat(256)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.service").exists());
    }

    @Test
    void crear_sinTelefonoNiCorreo_devuelve400() throws Exception {
        when(leadService.crear(eq(USUARIO_ACTUAL), any(CreateLeadRequest.class)))
                .thenThrow(new IllegalArgumentException("Debe registrar teléfono o correo"));

        enviar("POST", "/api/leads", """
                {"name":"Ana","stage":"NUEVO"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void crear_conCorreoDuplicado_devuelve400ConErrorSeguroSinFugas() throws Exception {
        when(leadService.crear(eq(USUARIO_ACTUAL), any(CreateLeadRequest.class)))
                .thenThrow(new LeadValidationException("Ya existe un lead con este correo"));

        enviar("POST", "/api/leads", BODY_VALIDO)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Ya existe un lead con este correo"))
                .andExpect(jsonPath("$.sql").doesNotExist())
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Test
    void crear_conTelefonoDuplicado_devuelve400ConErrorSeguroSinFugas() throws Exception {
        when(leadService.crear(eq(USUARIO_ACTUAL), any(CreateLeadRequest.class)))
                .thenThrow(new LeadValidationException("Ya existe un lead con este teléfono"));

        enviar("POST", "/api/leads", BODY_VALIDO)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Ya existe un lead con este teléfono"));
    }

    @Test
    void actualizar_conContactoDuplicado_devuelve400ConErrorSeguro() throws Exception {
        when(leadService.actualizar(eq(10L), eq(USUARIO_ACTUAL), any(UpdateLeadRequest.class)))
                .thenThrow(new LeadValidationException("Ya existe un lead con este correo"));

        enviar("PUT", "/api/leads/10", BODY_VALIDO)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Ya existe un lead con este correo"));
    }

    @Test
    void crear_dataIntegrityViolationConConstraintCorreo_devuelve400ConMensajeCorreo() throws Exception {
        ConstraintViolationException cve = new ConstraintViolationException(
                "duplicate key",
                new SQLException("duplicate"),
                "uq_lead_owner_correo_norm"
        );
        when(leadService.crear(eq(USUARIO_ACTUAL), any(CreateLeadRequest.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate error", cve));

        enviar("POST", "/api/leads", BODY_VALIDO)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Ya existe un lead con este correo"));
    }

    @Test
    void crear_dataIntegrityViolationConConstraintTelefono_devuelve400ConMensajeTelefono() throws Exception {
        ConstraintViolationException cve = new ConstraintViolationException(
                "duplicate key",
                new SQLException("duplicate"),
                "uq_lead_owner_telefono_norm"
        );
        when(leadService.crear(eq(USUARIO_ACTUAL), any(CreateLeadRequest.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate error", cve));

        enviar("POST", "/api/leads", BODY_VALIDO)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Ya existe un lead con este teléfono"));
    }

    @Test
    void crear_dataIntegrityViolationAjena_devuelve500SinFugaDeSqlNiMensajesInternos() throws Exception {
        ConstraintViolationException cve = new ConstraintViolationException(
                "foreign key violation in schema internal detail",
                new SQLException("insert into lead ... violates foreign key"),
                "fk_lead_usuario"
        );
        when(leadService.crear(eq(USUARIO_ACTUAL), any(CreateLeadRequest.class)))
                .thenThrow(new DataIntegrityViolationException("FK failure", cve));

        enviar("POST", "/api/leads", BODY_VALIDO)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("Error interno del servidor."))
                .andExpect(jsonPath("$.sql").doesNotExist())
                .andExpect(jsonPath("$.constraint").doesNotExist())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void actualizar_transactionSystemExceptionConConstraintTelefono_devuelve400ConMensajeTelefono() throws Exception {
        ConstraintViolationException cve = new ConstraintViolationException(
                "duplicate key",
                new SQLException("duplicate"),
                "uq_lead_owner_telefono_norm"
        );
        RollbackException rollbackEx = new RollbackException("commit failed", cve);
        TransactionSystemException tse = new TransactionSystemException("JPA commit error", rollbackEx);

        when(leadService.actualizar(eq(10L), eq(USUARIO_ACTUAL), any(UpdateLeadRequest.class)))
                .thenThrow(tse);

        enviar("PUT", "/api/leads/10", BODY_VALIDO)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Ya existe un lead con este teléfono"));
    }

    @Test
    void actualizar_transactionSystemExceptionAjena_devuelve500SinFuga() throws Exception {
        RollbackException rollbackEx = new RollbackException("commit failed due to timeout");
        TransactionSystemException tse = new TransactionSystemException("JPA commit error", rollbackEx);

        when(leadService.actualizar(eq(10L), eq(USUARIO_ACTUAL), any(UpdateLeadRequest.class)))
                .thenThrow(tse);

        enviar("PUT", "/api/leads/10", BODY_VALIDO)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("Error interno del servidor."))
                .andExpect(jsonPath("$.sql").doesNotExist())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    // ---------- GET ----------

    @Test
    void obtener_devuelve200() throws Exception {
        when(leadService.obtener(10L, USUARIO_ACTUAL)).thenReturn(respuesta());

        mockMvc.perform(get("/api/leads/{id}", 10))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ana Torres"))
                .andExpect(jsonPath("$.service").value("Web"));
    }

    @Test
    void obtener_inexistente_devuelve404() throws Exception {
        when(leadService.obtener(99L, USUARIO_ACTUAL))
                .thenThrow(new LeadNotFoundException("Lead no encontrado"));

        mockMvc.perform(get("/api/leads/{id}", 99))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void listar_pasaSearchYPageAlServicio_yDevuelveFormatoPaginado() throws Exception {
        when(leadService.listarPaginado(USUARIO_ACTUAL, "ana", "all", null, 1))
                .thenReturn(new PageResponse<>(List.of(respuesta()), 1, 20, 21, 2));

        mockMvc.perform(get("/api/leads").param("search", "ana").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Ana Torres"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(21))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    // ---------- PUT ----------

    @Test
    void actualizar_devuelve200() throws Exception {
        when(leadService.actualizar(eq(10L), eq(USUARIO_ACTUAL), any(UpdateLeadRequest.class)))
                .thenReturn(respuesta());

        enviar("PUT", "/api/leads/10", BODY_VALIDO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ana Torres"));
    }

    @Test
    void actualizar_inexistente_devuelve404() throws Exception {
        when(leadService.actualizar(eq(99L), eq(USUARIO_ACTUAL), any(UpdateLeadRequest.class)))
                .thenThrow(new LeadNotFoundException("Lead no encontrado"));

        enviar("PUT", "/api/leads/99", BODY_VALIDO)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void actualizar_datosInvalidos_devuelve400() throws Exception {
        enviar("PUT", "/api/leads/10", """
                {"name":" a ","email":"mal","phone":"999","stage":"NUEVO"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists());

        verifyNoInteractions(leadService);
    }

    // ---------- DELETE ----------

    @Test
    void eliminar_devuelve204SinCuerpo() throws Exception {
        mockMvc.perform(delete("/api/leads/{id}", 10).with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(leadService).eliminar(10L, USUARIO_ACTUAL);
    }

    @Test
    void eliminar_inexistente_devuelve404() throws Exception {
        doThrow(new LeadNotFoundException("Lead no encontrado"))
                .when(leadService).eliminar(99L, USUARIO_ACTUAL);

        mockMvc.perform(delete("/api/leads/{id}", 99).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // =========================================================================
    // TESTS UL-17: ETAPAS Y FILTROS
    // =========================================================================


    @Test
    void actualizar_etapaInvalida_devuelve400() throws Exception {
        enviar("PUT", "/api/leads/10", """
                {"name":"Test","phone":"999111222","stage":"INVALIDA"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.stage").exists());

        verifyNoInteractions(leadService);
    }

    @Test
    void listar_pasaStageYFollowUpAlServicio() throws Exception {
        when(leadService.listarPaginado(USUARIO_ACTUAL, null, "today", "NUEVO", 0))
                .thenReturn(new PageResponse<>(List.of(respuesta()), 0, 20, 1, 1));

        mockMvc.perform(get("/api/leads")
                        .param("stage", "NUEVO")
                        .param("followUp", "today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(leadService).listarPaginado(USUARIO_ACTUAL, null, "today", "NUEVO", 0);
    }

    // =========================================================================
    // TESTS C08: ETIQUETAS Y RELACIONES (DSW-48)
    // =========================================================================

    @Test
    void actualizarTags_devuelve200() throws Exception {
        when(leadService.actualizarTags(eq(10L), eq(USUARIO_ACTUAL), eq(List.of(1L, 2L))))
                .thenReturn(respuesta());

        mockMvc.perform(put("/api/leads/10/tags")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tagIds":[1, 2]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ana Torres"));

        verify(leadService).actualizarTags(10L, USUARIO_ACTUAL, List.of(1L, 2L));
    }

    @Test
    void actualizarTags_sinCsrf_devuelve403() throws Exception {
        mockMvc.perform(put("/api/leads/10/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tagIds":[1, 2]}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void actualizarTags_leadOEtiquetaInexistente_devuelve404() throws Exception {
        when(leadService.actualizarTags(eq(10L), eq(USUARIO_ACTUAL), any()))
                .thenThrow(new com.urleads.exception.TagNotFoundException("Etiqueta no encontrada"));

        mockMvc.perform(put("/api/leads/10/tags")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tagIds":[999]}"""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void actualizarTags_cuerpoNulo_devuelve400() throws Exception {
        mockMvc.perform(put("/api/leads/10/tags")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void listar_conTagIds_pasaFiltroAlServicio() throws Exception {
        when(leadService.listarPaginado(USUARIO_ACTUAL, null, "all", null, null, null, List.of(1L, 2L), 0))
                .thenReturn(new PageResponse<>(List.of(respuesta()), 0, 20, 1, 1));

        mockMvc.perform(get("/api/leads")
                        .param("tagIds", "1")
                        .param("tagIds", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(leadService).listarPaginado(USUARIO_ACTUAL, null, "all", null, null, null, List.of(1L, 2L), 0);
    }
}
