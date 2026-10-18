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

    // =========================================================================
    // TESTS UL-17: REGLAS DE ETAPAS, SEGUIMIENTO Y FILTROS EN BASE DE DATOS
    // =========================================================================

    @Test
    void crear_conEtapaCerradaYFecha_persisteCerradoSinFecha() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/leads")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cerrado Directo","phone":"999111222",
                                 "stage":"CERRADO","nextFollowUp":"2026-12-01"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stage").value("CERRADO"))
                .andExpect(jsonPath("$.nextFollowUp").value(nullValue()))
                .andReturn();

        Long id = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
        flushYLimpiar();

        Lead guardado = leadRepository.findById(id).orElseThrow();
        assertThat(guardado.getEtapa()).isEqualTo("CERRADO");
        assertThat(guardado.getProximoSeguimiento()).isNull();
    }

    @Test
    void actualizar_aCerradoConFecha_persisteCerradoSinFecha() throws Exception {
        Lead lead = crearLead(owner, "Lead Activo", "999111222", null, "Web");
        lead.setProximoSeguimiento(LocalDate.of(2026, 11, 20));
        leadRepository.saveAndFlush(lead);
        flushYLimpiar();

        mockMvc.perform(put("/api/leads/{id}", lead.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Lead Activo","phone":"999111222",
                                 "stage":"CERRADO","nextFollowUp":"2026-12-05"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("CERRADO"))
                .andExpect(jsonPath("$.nextFollowUp").value(nullValue()));

        flushYLimpiar();

        Lead actualizado = leadRepository.findById(lead.getId()).orElseThrow();
        assertThat(actualizado.getEtapa()).isEqualTo("CERRADO");
        assertThat(actualizado.getProximoSeguimiento()).isNull();
    }

    @Test
    void actualizar_reabrirCerradoConNuevaFecha_persisteNuevaEtapaYFecha() throws Exception {
        Lead cerrado = crearLead(owner, "Lead Cerrado", "999111222", null, "SEO");
        cerrado.setEtapa("CERRADO");
        cerrado.setProximoSeguimiento(null);
        leadRepository.saveAndFlush(cerrado);
        flushYLimpiar();

        LocalDate nuevaFecha = LocalDate.of(2026, 12, 15);
        mockMvc.perform(put("/api/leads/{id}", cerrado.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Lead Reabierto","phone":"999111222",
                                 "stage":"EN_SEGUIMIENTO","nextFollowUp":"2026-12-15"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("EN_SEGUIMIENTO"))
                .andExpect(jsonPath("$.nextFollowUp").value("2026-12-15"));

        flushYLimpiar();

        Lead actualizado = leadRepository.findById(cerrado.getId()).orElseThrow();
        assertThat(actualizado.getEtapa()).isEqualTo("EN_SEGUIMIENTO");
        assertThat(actualizado.getProximoSeguimiento()).isEqualTo(nuevaFecha);
    }

    @Test
    void crear_conFechaPasada_esValidoYPersiste() throws Exception {
        LocalDate fechaPasada = LocalDate.of(2025, 5, 10);
        MvcResult result = mockMvc.perform(post("/api/leads")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Lead Pasado","phone":"999111222",
                                 "stage":"EN_SEGUIMIENTO","nextFollowUp":"2025-05-10"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nextFollowUp").value("2025-05-10"))
                .andReturn();

        Long id = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
        flushYLimpiar();

        Lead guardado = leadRepository.findById(id).orElseThrow();
        assertThat(guardado.getProximoSeguimiento()).isEqualTo(fechaPasada);
    }

    @Test
    void crear_conEtapaInvalida_devuelve400() throws Exception {
        mockMvc.perform(post("/api/leads")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Invalido","phone":"999111222",
                                 "stage":"PENDIENTE"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void actualizar_conEtapaInvalida_devuelve400() throws Exception {
        Lead lead = crearLead(owner, "Valido", "999111222", null, null);
        flushYLimpiar();

        mockMvc.perform(put("/api/leads/{id}", lead.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Valido","phone":"999111222",
                                 "stage":"GANADO"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void actualizar_conEtapaPerdido_devuelve400() throws Exception {
        Lead lead = crearLead(owner, "Valido", "999111222", null, null);
        flushYLimpiar();

        mockMvc.perform(put("/api/leads/{id}", lead.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Valido","phone":"999111222",
                                 "stage":"PERDIDO"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listar_filtrosStageYFollowUp_clasificacionCorrectaLima() throws Exception {
        LocalDate hoyLima = LocalDate.now(java.time.ZoneId.of("America/Lima"));
        LocalDate ayerLima = hoyLima.minusDays(1);
        LocalDate mananaLima = hoyLima.plusDays(1);

        // 1. Lead de hoy en EN_SEGUIMIENTO
        Lead lHoy = crearLead(owner, "Lead De Hoy", "999000001", null, null);
        lHoy.setEtapa("EN_SEGUIMIENTO");
        lHoy.setProximoSeguimiento(hoyLima);
        leadRepository.saveAndFlush(lHoy);

        // 2. Lead atrasado en NUEVO
        Lead lAyer = crearLead(owner, "Lead Atrasado", "999000002", null, null);
        lAyer.setEtapa("NUEVO");
        lAyer.setProximoSeguimiento(ayerLima);
        leadRepository.saveAndFlush(lAyer);

        // 3. Lead futuro en EN_SEGUIMIENTO
        Lead lFuturo = crearLead(owner, "Lead Futuro", "999000003", null, null);
        lFuturo.setEtapa("EN_SEGUIMIENTO");
        lFuturo.setProximoSeguimiento(mananaLima);
        leadRepository.saveAndFlush(lFuturo);

        // 4. Lead cerrado (sin fecha)
        Lead lCerrado = crearLead(owner, "Lead Cerrado", "999000004", null, null);
        lCerrado.setEtapa("CERRADO");
        lCerrado.setProximoSeguimiento(null);
        leadRepository.saveAndFlush(lCerrado);

        // 5. Lead ajeno (de other) con fecha de hoy para probar aislamiento
        Lead lAjeno = crearLead(other, "Ajeno De Hoy", "999000005", null, null);
        lAjeno.setEtapa("EN_SEGUIMIENTO");
        lAjeno.setProximoSeguimiento(hoyLima);
        leadRepository.saveAndFlush(lAjeno);

        flushYLimpiar();

        // followUp=today -> solo lHoy (el ajeno no)
        mockMvc.perform(get("/api/leads").param("followUp", "today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Lead De Hoy"));

        // followUp=overdue -> solo lAyer
        mockMvc.perform(get("/api/leads").param("followUp", "overdue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Lead Atrasado"));

        // stage=CERRADO -> solo lCerrado
        mockMvc.perform(get("/api/leads").param("stage", "CERRADO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Lead Cerrado"));

        // stage=EN_SEGUIMIENTO -> lHoy y lFuturo (2)
        mockMvc.perform(get("/api/leads").param("stage", "EN_SEGUIMIENTO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        // Combinado: stage=EN_SEGUIMIENTO y followUp=today -> solo lHoy
        mockMvc.perform(get("/api/leads")
                        .param("stage", "EN_SEGUIMIENTO")
                        .param("followUp", "today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Lead De Hoy"));

        // Combinado: stage=EN_SEGUIMIENTO y followUp=overdue -> 0 (lAyer es NUEVO)
        mockMvc.perform(get("/api/leads")
                        .param("stage", "EN_SEGUIMIENTO")
                        .param("followUp", "overdue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // Combinado con search:
        mockMvc.perform(get("/api/leads")
                        .param("search", "Futuro")
                        .param("stage", "EN_SEGUIMIENTO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Lead Futuro"));
    }

    @Test
    void listar_conFiltrosInvalidos_devuelve400() throws Exception {
        mockMvc.perform(get("/api/leads").param("followUp", "proximamente"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/leads").param("stage", "INVALIDA"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // =========================================================================
    // TESTS C06: DETECCIÓN DE DUPLICADOS EN API REST (DSW-46)
    // =========================================================================

    @Test
    void crear_conCorreoDuplicadoMismoPropietario_devuelve400() throws Exception {
        crearLead(owner, "Ana Original", null, "ana@mail.com", "Web");
        flushYLimpiar();

        mockMvc.perform(post("/api/leads")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ana Copia","email":"  ANA@Mail.COM  ","stage":"NUEVO"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Ya existe un lead con este correo"));
    }

    @Test
    void crear_conTelefonoDuplicadoMismoPropietario_devuelve400() throws Exception {
        crearLead(owner, "Beto Original", "999-111-222", null, "SEO");
        flushYLimpiar();

        mockMvc.perform(post("/api/leads")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Beto Copia","phone":"(999) 111.222","stage":"NUEVO"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Ya existe un lead con este teléfono"));
    }

    @Test
    void crear_conMismoContactoEnOtroPropietario_devuelve201() throws Exception {
        crearLead(other, "Ajeno", "999-111-222", "compartido@mail.com", null);
        flushYLimpiar();

        mockMvc.perform(post("/api/leads")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Mio","phone":"(999) 111-222","email":"COMPARTIDO@mail.com","stage":"NUEVO"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Mio"))
                .andExpect(jsonPath("$.phone").value("(999) 111-222"))
                .andExpect(jsonPath("$.email").value("COMPARTIDO@mail.com"));
    }

    @Test
    void actualizar_conservandoMismosDatosDelLead_devuelve200() throws Exception {
        Lead lead = crearLead(owner, "Original", "999-111-222", "propio@mail.com", null);
        flushYLimpiar();

        mockMvc.perform(put("/api/leads/{id}", lead.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Original Renombrado","phone":"999111222","email":"PROPIO@mail.com","stage":"EN_SEGUIMIENTO"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Original Renombrado"))
                .andExpect(jsonPath("$.stage").value("EN_SEGUIMIENTO"));
    }

    @Test
    void actualizar_colisionConOtroLeadDelMismoPropietario_devuelve400() throws Exception {
        Lead l1 = crearLead(owner, "Primero", "999-111-222", "primero@mail.com", null);
        Lead l2 = crearLead(owner, "Segundo", "888-222-333", "segundo@mail.com", null);
        flushYLimpiar();

        mockMvc.perform(put("/api/leads/{id}", l1.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Primero Modificado","email":"segundo@mail.com","stage":"NUEVO"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Ya existe un lead con este correo"));
    }
}