package com.urleads.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.urleads.dto.TagRequest;
import com.urleads.dto.TagResponse;
import com.urleads.exception.TagDuplicateException;
import com.urleads.exception.TagNotFoundException;
import com.urleads.security.CurrentUserProvider;
import com.urleads.security.SecurityConfig;
import com.urleads.security.UserDetailsServiceImpl;
import com.urleads.service.TagService;

@WebMvcTest(TagController.class)
@Import(SecurityConfig.class)
@WithMockUser
class TagControllerTest {

    private static final Long USUARIO_ACTUAL = 7L;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TagService tagService;

    @MockBean
    private CurrentUserProvider currentUserProvider;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @BeforeEach
    void setUp() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USUARIO_ACTUAL);
    }

    @Test
    @WithAnonymousUser
    @DisplayName("Petición sin sesión a /api/tags devuelve 401")
    void peticionSinSesion_devuelve401() throws Exception {
        mockMvc.perform(get("/api/tags"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("No autorizado"));
    }

    @Test
    @DisplayName("Mutación sin token CSRF devuelve 403")
    void mutacionSinCsrf_devuelve403() throws Exception {
        mockMvc.perform(post("/api/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Prospecto"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/tags: devuelve 200 con la lista de etiquetas del propietario")
    void listar_devuelve200() throws Exception {
        TagResponse t1 = new TagResponse(1L, "VIP", OffsetDateTime.now());
        when(tagService.listar(USUARIO_ACTUAL)).thenReturn(List.of(t1));

        mockMvc.perform(get("/api/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("VIP"));
    }

    @Test
    @DisplayName("GET /api/tags/{id}: devuelve 200 con la etiqueta propia")
    void obtener_devuelve200() throws Exception {
        TagResponse t1 = new TagResponse(1L, "VIP", OffsetDateTime.now());
        when(tagService.obtener(1L, USUARIO_ACTUAL)).thenReturn(t1);

        mockMvc.perform(get("/api/tags/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("VIP"));
    }

    @Test
    @DisplayName("GET /api/tags/{id}: etiqueta no encontrada devuelve 404")
    void obtener_noEncontrada_devuelve404() throws Exception {
        when(tagService.obtener(999L, USUARIO_ACTUAL))
                .thenThrow(new TagNotFoundException("Etiqueta no encontrada"));

        mockMvc.perform(get("/api/tags/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/tags: crea etiqueta y devuelve 201 Created")
    void crear_devuelve201() throws Exception {
        TagResponse creado = new TagResponse(10L, "Prospecto", OffsetDateTime.now());
        when(tagService.crear(eq(USUARIO_ACTUAL), any(TagRequest.class))).thenReturn(creado);

        mockMvc.perform(post("/api/tags")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  Prospecto  "}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.name").value("Prospecto"));
    }

    @Test
    @DisplayName("POST /api/tags: nombre en blanco devuelve 400 VALIDATION_ERROR")
    void crear_nombreEnBlanco_devuelve400() throws Exception {
        mockMvc.perform(post("/api/tags")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"   "}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.name").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/tags: nombre duplicado devuelve 409 CONFLICT")
    void crear_duplicado_devuelve409() throws Exception {
        when(tagService.crear(eq(USUARIO_ACTUAL), any(TagRequest.class)))
                .thenThrow(new TagDuplicateException("Ya existe una etiqueta con el nombre: Prospecto"));

        mockMvc.perform(post("/api/tags")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Prospecto"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("PUT /api/tags/{id}: renombra etiqueta y devuelve 200 OK")
    void actualizar_devuelve200() throws Exception {
        TagResponse actualizado = new TagResponse(10L, "Cliente", OffsetDateTime.now());
        when(tagService.actualizar(eq(10L), eq(USUARIO_ACTUAL), any(TagRequest.class))).thenReturn(actualizado);

        mockMvc.perform(put("/api/tags/10")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cliente"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.name").value("Cliente"));
    }

    @Test
    @DisplayName("DELETE /api/tags/{id}: elimina etiqueta y devuelve 204 No Content")
    void eliminar_devuelve204() throws Exception {
        mockMvc.perform(delete("/api/tags/10")
                        .with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(tagService).eliminar(10L, USUARIO_ACTUAL);
    }
}
