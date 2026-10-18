package com.urleads.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.urleads.database.TestDatabaseGuardInitializer;
import com.urleads.entity.Lead;
import com.urleads.entity.Tag;
import com.urleads.entity.Usuario;
import com.urleads.repository.LeadRepository;
import com.urleads.repository.TagRepository;
import com.urleads.repository.UsuarioRepository;
import com.urleads.security.CurrentUserProvider;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestDatabaseGuardInitializer.class)
@Transactional
@WithMockUser(username = "owner")
class TagApiIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @MockBean
    private CurrentUserProvider currentUserProvider;

    private Usuario owner;
    private Usuario other;

    @BeforeEach
    void setUp() {
        owner = crearUsuario("owner");
        other = crearUsuario("other");
        when(currentUserProvider.getCurrentUserId()).thenReturn(owner.getId());
    }

    private Usuario crearUsuario(String prefijo) {
        Usuario u = new Usuario();
        u.setUsername(prefijo + "_" + UUID.randomUUID());
        u.setPasswordHash("hash-sintetico");
        return usuarioRepository.saveAndFlush(u);
    }

    private static final java.util.concurrent.atomic.AtomicLong TELEFONO_SEQ =
            new java.util.concurrent.atomic.AtomicLong(990000000L);

    private Lead crearLead(Usuario dueno, String nombre) {
        Lead lead = new Lead();
        lead.setUsuario(dueno);
        lead.setNombre(nombre);
        lead.setTelefono(String.valueOf(TELEFONO_SEQ.incrementAndGet()));
        lead.setEtapa("NUEVO");
        return leadRepository.saveAndFlush(lead);
    }

    private Tag crearTag(Usuario dueno, String nombre) {
        Tag tag = new Tag(dueno, nombre);
        return tagRepository.saveAndFlush(tag);
    }

    private void flushYLimpiar() {
        entityManager.flush();
        entityManager.clear();
    }

    // ---------- Seguridad y Autenticación (REG2) ----------

    @Test
    @WithAnonymousUser
    @DisplayName("Petición sin sesión a /api/tags devuelve 401")
    void peticionSinSesion_devuelve401() throws Exception {
        mockMvc.perform(get("/api/tags"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("No autorizado"));
    }

    @Test
    @DisplayName("Mutación sin CSRF en /api/tags devuelve 403")
    void mutacionSinCsrf_devuelve403() throws Exception {
        mockMvc.perform(post("/api/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Prospecto"}"""))
                .andExpect(status().isForbidden());
    }

    // ---------- R2: CRUD de Etiquetas y Aislamiento por Propietario ----------

    @Test
    @DisplayName("R2a: Owner A CRUD tag y lista; Owner B puede usar el mismo nombre; cada uno ve solo su tag")
    void crudTag_aislamientoEntrePropietarios() throws Exception {
        // Tag existente de otro usuario con el mismo nombre
        crearTag(other, "Prospecto");
        flushYLimpiar();

        // 1. Crear tag para owner (debe permitir el mismo nombre)
        MvcResult postResult = mockMvc.perform(post("/api/tags")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  Prospecto  "}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Prospecto"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn();

        Long tagId = ((Number) JsonPath.read(postResult.getResponse().getContentAsString(), "$.id")).longValue();
        flushYLimpiar();

        // Verificar persistencia y propietario en BD
        Tag guardado = tagRepository.findById(tagId).orElseThrow();
        assertThat(guardado.getUsuario().getId()).isEqualTo(owner.getId());
        assertThat(guardado.getNombre()).isEqualTo("Prospecto");

        // 2. Listar: solo debe ver su propio tag
        mockMvc.perform(get("/api/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(tagId))
                .andExpect(jsonPath("$[0].name").value("Prospecto"));

        // 3. Actualizar
        mockMvc.perform(put("/api/tags/{id}", tagId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  Cliente VIP  "}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cliente VIP"));

        flushYLimpiar();
        assertThat(tagRepository.findById(tagId).orElseThrow().getNombre()).isEqualTo("Cliente VIP");

        // 4. Eliminar
        mockMvc.perform(delete("/api/tags/{id}", tagId)
                        .with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        flushYLimpiar();
        assertThat(tagRepository.findById(tagId)).isEmpty();
        // El tag del otro usuario continúa intacto
        assertThat(tagRepository.findByUsuarioIdOrderByNombreAsc(other.getId())).hasSize(1);
    }

    @Test
    @DisplayName("R2b: Validación de nombre (recorta espacios, rechaza blanco y duplicados case-insensitive)")
    void validacionNombreEtiqueta() throws Exception {
        crearTag(owner, "Prospecto");
        flushYLimpiar();

        // Nombre en blanco
        mockMvc.perform(post("/api/tags")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"   "}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // Duplicado case-insensitive para el mismo propietario
        mockMvc.perform(post("/api/tags")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  PROSPECTO  "}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("R2c: Tag ajeno devuelve 404 indistinguible en GET, PUT y DELETE sin mutación")
    void tagAjeno_devuelve404SinMutacion() throws Exception {
        Tag tagAjeno = crearTag(other, "Tag Ajeno");
        flushYLimpiar();

        mockMvc.perform(get("/api/tags/{id}", tagAjeno.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mockMvc.perform(put("/api/tags/{id}", tagAjeno.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Hackeado"}"""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mockMvc.perform(delete("/api/tags/{id}", tagAjeno.getId())
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        flushYLimpiar();
        assertThat(tagRepository.findById(tagAjeno.getId()).orElseThrow().getNombre()).isEqualTo("Tag Ajeno");
    }

    // ---------- R3: Reemplazo Atómico de Asociaciones ----------

    @Test
    @DisplayName("R3a: PUT /api/leads/{id}/tags reemplaza atómicamente, [] limpia y repetidos no duplican")
    void actualizarTagsLead_reemplazoAtomico() throws Exception {
        Lead lead = crearLead(owner, "Lead A");
        Tag t1 = crearTag(owner, "Alfa");
        Tag t2 = crearTag(owner, "Beta");
        flushYLimpiar();

        // Asociar t1 y t2 con IDs duplicados en request
        mockMvc.perform(put("/api/leads/{id}/tags", lead.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tagIds":[%d, %d, %d]}""".formatted(t1.getId(), t2.getId(), t1.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags.length()").value(2))
                .andExpect(jsonPath("$.tags[0].name").value("Alfa"))
                .andExpect(jsonPath("$.tags[1].name").value("Beta"));

        flushYLimpiar();
        Integer joinCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM lead_tag WHERE lead_id = ?", Integer.class, lead.getId());
        assertThat(joinCount).isEqualTo(2);

        // Reemplazar asociando únicamente t2
        mockMvc.perform(put("/api/leads/{id}/tags", lead.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tagIds":[%d]}""".formatted(t2.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags.length()").value(1))
                .andExpect(jsonPath("$.tags[0].name").value("Beta"));

        flushYLimpiar();
        joinCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM lead_tag WHERE lead_id = ?", Integer.class, lead.getId());
        assertThat(joinCount).isEqualTo(1);

        // Limpiar todas con []
        mockMvc.perform(put("/api/leads/{id}/tags", lead.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tagIds":[]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags.length()").value(0));

        flushYLimpiar();
        joinCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM lead_tag WHERE lead_id = ?", Integer.class, lead.getId());
        assertThat(joinCount).isZero();
    }

    @Test
    @DisplayName("R3b: Asignar tag ajeno o asociar en lead ajeno devuelve 404 sin cambios parciales")
    void actualizarTagsLead_ajenosFallanCon404SinMutacionParcial() throws Exception {
        Lead leadPropio = crearLead(owner, "Lead Propio");
        Tag tagPropio = crearTag(owner, "Tag Propio");

        Lead leadAjeno = crearLead(other, "Lead Ajeno");
        Tag tagAjeno = crearTag(other, "Tag Ajeno");

        // Ya tenía asociado su propio tag
        jdbcTemplate.update("INSERT INTO lead_tag (lead_id, tag_id) VALUES (?, ?)",
                leadPropio.getId(), tagPropio.getId());
        flushYLimpiar();

        // Intentar asignar tag ajeno a lead propio
        mockMvc.perform(put("/api/leads/{id}/tags", leadPropio.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tagIds":[%d, %d]}""".formatted(tagPropio.getId(), tagAjeno.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        flushYLimpiar();
        // La asociación previa debe estar intacta (sin mutación parcial)
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM lead_tag WHERE lead_id = ? AND tag_id = ?",
                Integer.class, leadPropio.getId(), tagPropio.getId());
        assertThat(count).isEqualTo(1);

        // Intentar actualizar tags de lead ajeno
        mockMvc.perform(put("/api/leads/{id}/tags", leadAjeno.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tagIds":[%d]}""".formatted(tagPropio.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        flushYLimpiar();
        Integer ajenoCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM lead_tag WHERE lead_id = ?",
                Integer.class, leadAjeno.getId());
        assertThat(ajenoCount).isZero();
    }

    // ---------- R4: Filtro de Leads por Múltiples Tags (Semántica OR) ----------

    @Test
    @DisplayName("R4a: Filtro con tagIds usa semántica OR, no duplica filas y respeta paginación")
    void filtrarLeadsPorTags_semanticaOr() throws Exception {
        Tag t1 = crearTag(owner, "Tag1");
        Tag t2 = crearTag(owner, "Tag2");
        Tag t3 = crearTag(owner, "Tag3");

        // Lead 1: tiene Tag1 y Tag2 (coincide con ambos)
        Lead l1 = crearLead(owner, "Lead Con 1 y 2");
        jdbcTemplate.update("INSERT INTO lead_tag (lead_id, tag_id) VALUES (?, ?)", l1.getId(), t1.getId());
        jdbcTemplate.update("INSERT INTO lead_tag (lead_id, tag_id) VALUES (?, ?)", l1.getId(), t2.getId());

        // Lead 2: tiene solo Tag2
        Lead l2 = crearLead(owner, "Lead Con 2");
        jdbcTemplate.update("INSERT INTO lead_tag (lead_id, tag_id) VALUES (?, ?)", l2.getId(), t2.getId());

        // Lead 3: tiene solo Tag3 (no coincide con el filtro de 1 o 2)
        Lead l3 = crearLead(owner, "Lead Con 3");
        jdbcTemplate.update("INSERT INTO lead_tag (lead_id, tag_id) VALUES (?, ?)", l3.getId(), t3.getId());

        // Lead de otro usuario con Tag1 para verificar aislamiento
        Lead lAjeno = crearLead(other, "Lead Ajeno");
        Tag tAjeno = crearTag(other, "Tag Ajeno");
        jdbcTemplate.update("INSERT INTO lead_tag (lead_id, tag_id) VALUES (?, ?)", lAjeno.getId(), tAjeno.getId());

        flushYLimpiar();

        // Filtrar por tagIds=t1 & tagIds=t2 (OR)
        mockMvc.perform(get("/api/leads")
                        .param("tagIds", String.valueOf(t1.getId()))
                        .param("tagIds", String.valueOf(t2.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    @DisplayName("R4b: Filtro con IDs ajenos o inexistentes devuelve 0 coincidencias sin revelar existencia")
    void filtrarLeads_tagsAjenosOInexistentes_devuelveListaVacia() throws Exception {
        Tag tagAjeno = crearTag(other, "Tag Ajeno");
        Lead leadPropio = crearLead(owner, "Lead Propio");
        Tag tagPropio = crearTag(owner, "Tag Propio");
        jdbcTemplate.update("INSERT INTO lead_tag (lead_id, tag_id) VALUES (?, ?)",
                leadPropio.getId(), tagPropio.getId());
        flushYLimpiar();

        // Tag inexistente
        mockMvc.perform(get("/api/leads").param("tagIds", "999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));

        // Tag de otro usuario
        mockMvc.perform(get("/api/leads").param("tagIds", String.valueOf(tagAjeno.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    // ---------- R1b: Cascada y Limpieza de Relaciones ----------

    @Test
    @DisplayName("R1b: Eliminar lead con notas y tags elimina sus notas y filas de lead_tag conservando las etiquetas")
    void eliminarLead_limpiaRelacionesConservandoEtiquetas() throws Exception {
        Lead lead = crearLead(owner, "Lead Borrar");
        Tag tag = crearTag(owner, "Tag Conservar");

        jdbcTemplate.update("INSERT INTO nota (lead_id, contenido) VALUES (?, ?)", lead.getId(), "Nota lead");
        jdbcTemplate.update("INSERT INTO lead_tag (lead_id, tag_id) VALUES (?, ?)", lead.getId(), tag.getId());
        flushYLimpiar();

        mockMvc.perform(delete("/api/leads/{id}", lead.getId())
                        .with(csrf()))
                .andExpect(status().isNoContent());

        flushYLimpiar();

        assertThat(leadRepository.findById(lead.getId())).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM nota WHERE lead_id = ?", Integer.class, lead.getId())).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM lead_tag WHERE lead_id = ?", Integer.class, lead.getId())).isZero();
        // La etiqueta debe seguir existiendo
        assertThat(tagRepository.findById(tag.getId())).isPresent();
    }
}
