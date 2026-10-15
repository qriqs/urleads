package com.urleads.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
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
import com.urleads.entity.Usuario;
import com.urleads.repository.LeadRepository;
import com.urleads.repository.UsuarioRepository;
import com.urleads.security.CurrentUserProvider;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestDatabaseGuardInitializer.class)
@Transactional // cada test hace rollback: no deja datos en la BD
@WithMockUser(username = "owner")
class LeadApiIT {

    @Autowired
    private MockMvc mockMvc;

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

    // ---------- helpers ----------

    private Usuario crearUsuario(String prefijo) {
        Usuario u = new Usuario();
        u.setUsername(prefijo + "_" + UUID.randomUUID());
        u.setPasswordHash("hash-sintetico");
        return usuarioRepository.saveAndFlush(u);
    }

    private Lead crearLead(Usuario dueno, String nombre, String telefono, String correo, String service) {
        Lead lead = new Lead();
        lead.setUsuario(dueno);
        lead.setNombre(nombre);
        lead.setTelefono(telefono);
        lead.setCorreo(correo);
        lead.setService(service);
        lead.setEtapa("NUEVO");
        return leadRepository.saveAndFlush(lead);
    }

    private void flushYLimpiar() {
        entityManager.flush();
        entityManager.clear();
    }

    private long contarNotas(Long leadId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM nota WHERE lead_id = ?", Long.class, leadId);
    }

    // ---------- tests de seguridad y aislamiento ----------

    @Test
    @WithAnonymousUser
    void peticionSinSesion_devuelve401() throws Exception {
        mockMvc.perform(get("/api/leads"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("No autorizado"));
    }

    @Test
    void mutacionSinCsrf_devuelve403() throws Exception {
        mockMvc.perform(post("/api/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ana Torres","phone":"999111222","stage":"NUEVO"}"""))
                .andExpect(status().isForbidden());
    }

    // ---------- tests funcionales de la API ----------

    @Test
    void crear_persisteConService_conOwnerDelServidor() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/leads")
                        .with(csrf())
                        .param("usuarioId", String.valueOf(other.getId())) // el cliente intenta elegir owner
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  Ana Torres  ","phone":"999111222","email":"ana@mail.com",
                                 "stage":"NUEVO","nextFollowUp":"2026-11-01","service":"Web"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Ana Torres"))
                .andExpect(jsonPath("$.service").value("Web"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn();

        Long id = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();

        flushYLimpiar();

        Lead guardado = leadRepository.findById(id).orElseThrow();
        assertThat(guardado.getUsuario().getId()).isEqualTo(owner.getId());
        assertThat(guardado.getNombre()).isEqualTo("Ana Torres");
        assertThat(guardado.getService()).isEqualTo("Web");
        assertThat(guardado.getProximoSeguimiento()).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void actualizar_reemplazaCampos_yLimpiaServiceYFechaCuandoVienenNull() throws Exception {
        Lead lead = crearLead(owner, "Beto", "999", null, "SEO");
        lead.setProximoSeguimiento(LocalDate.of(2026, 12, 1));
        leadRepository.saveAndFlush(lead);
        flushYLimpiar();

        mockMvc.perform(put("/api/leads/{id}", lead.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Beto R","phone":"999","stage":"EN_SEGUIMIENTO"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Beto R"))
                .andExpect(jsonPath("$.service").value(nullValue()))
                .andExpect(jsonPath("$.nextFollowUp").value(nullValue()));

        flushYLimpiar();

        Lead actualizado = leadRepository.findById(lead.getId()).orElseThrow();
        assertThat(actualizado.getNombre()).isEqualTo("Beto R");
        assertThat(actualizado.getEtapa()).isEqualTo("EN_SEGUIMIENTO");
        assertThat(actualizado.getService()).isNull();
        assertThat(actualizado.getProximoSeguimiento()).isNull();
    }

    @Test
    void leadDeOtroUsuario_devuelve404EnGetPutDelete_ySigueIntacto() throws Exception {
        Lead ajeno = crearLead(other, "Ajeno", "111", null, "X");
        flushYLimpiar();

        mockMvc.perform(get("/api/leads/{id}", ajeno.getId()))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/leads/{id}", ajeno.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Hackeado","phone":"000","stage":"NUEVO"}"""))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/leads/{id}", ajeno.getId())
                        .with(csrf()))
                .andExpect(status().isNotFound());

        flushYLimpiar();

        Lead sigue = leadRepository.findById(ajeno.getId()).orElseThrow();
        assertThat(sigue.getNombre()).isEqualTo("Ajeno");
        assertThat(sigue.getService()).isEqualTo("X");
    }

    @Test
    void leadInexistente_devuelve404() throws Exception {
        mockMvc.perform(get("/api/leads/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void eliminar_borraElLeadYSusNotas() throws Exception {
        Lead lead = crearLead(owner, "ConNotas", "999", null, null);
        flushYLimpiar();

        jdbcTemplate.update("INSERT INTO nota (lead_id, contenido) VALUES (?, ?)", lead.getId(), "Nota 1");
        jdbcTemplate.update("INSERT INTO nota (lead_id, contenido) VALUES (?, ?)", lead.getId(), "Nota 2");
        assertThat(contarNotas(lead.getId())).isEqualTo(2);

        mockMvc.perform(delete("/api/leads/{id}", lead.getId())
                        .with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        flushYLimpiar();

        assertThat(leadRepository.findById(lead.getId())).isEmpty();
        assertThat(contarNotas(lead.getId())).isZero();
    }

    @Test
    void listar_busquedaYPaginacion_conTamanoFijoDelServidor() throws Exception {
        for (int i = 0; i < 25; i++) {
            crearLead(owner, "Cliente %02d".formatted(i), "9000000%02d".formatted(i), null, null);
        }
        crearLead(owner, "Zoila Quispe", null, "zoila@mail.com", null);
        crearLead(other, "Cliente Ajeno", "999", null, null);
        flushYLimpiar();

        // Página 0: 20 de 26 (el lead de otro usuario no cuenta)
        mockMvc.perform(get("/api/leads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(20))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(26))
                .andExpect(jsonPath("$.totalPages").value(2));

        // Página 1: los 6 restantes
        mockMvc.perform(get("/api/leads").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(6))
                .andExpect(jsonPath("$.page").value(1));

        // El cliente no puede ampliar el tamaño
        mockMvc.perform(get("/api/leads").param("size", "1000"))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content.length()").value(20));

        // search por nombre, sin distinguir mayúsculas
        mockMvc.perform(get("/api/leads").param("search", "ZOILA"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Zoila Quispe"));

        // search por correo
        mockMvc.perform(get("/api/leads").param("search", "zoila@mail"))
                .andExpect(jsonPath("$.totalElements").value(1));

        // search por teléfono
        mockMvc.perform(get("/api/leads").param("search", "9000000"))
                .andExpect(jsonPath("$.totalElements").value(25));

        // No encuentra leads de otro usuario
        mockMvc.perform(get("/api/leads").param("search", "ajeno"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}