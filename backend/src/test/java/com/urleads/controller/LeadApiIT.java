package com.urleads.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.contains;
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
import org.springframework.jdbc.core.JdbcTemplate;
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
    // TESTS C07: PRIORIDAD, ORIGEN Y FILTROS COMBINADOS (DSW-47)
    // =========================================================================

    @Test
    void crear_conPrioridadYOrigenOmitidos_persisteValoresPorDefecto() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/leads")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Sin Prioridad","phone":"999111222","stage":"NUEVO"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Sin Prioridad"))
                .andExpect(jsonPath("$.priority").value("MEDIA"))
                .andExpect(jsonPath("$.origin").value("MANUAL"))
                .andReturn();

        Long id = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
        flushYLimpiar();

        Lead guardado = leadRepository.findById(id).orElseThrow();
        assertThat(guardado.getPriority()).isEqualTo("MEDIA");
        assertThat(guardado.getOrigin()).isEqualTo("MANUAL");
    }

    @Test
    void crear_conPrioridadYOrigenValidos_normalizaMayusculasYRecorta() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/leads")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Con Prioridad","phone":"999111222","stage":"NUEVO",
                                 "priority":"  alta  ","origin":" referido "}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority").value("ALTA"))
                .andExpect(jsonPath("$.origin").value("REFERIDO"))
                .andReturn();

        Long id = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
        flushYLimpiar();

        Lead guardado = leadRepository.findById(id).orElseThrow();
        assertThat(guardado.getPriority()).isEqualTo("ALTA");
        assertThat(guardado.getOrigin()).isEqualTo("REFERIDO");
    }

    @Test
    void crear_conPrioridadInvalidaOEnBlanco_devuelve400() throws Exception {
        // Prioridad desconocida
        mockMvc.perform(post("/api/leads")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Invalido","phone":"999111222","stage":"NUEVO","priority":"URGENTE"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // Prioridad con solo espacios en blanco
        mockMvc.perform(post("/api/leads")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Invalido","phone":"999111222","stage":"NUEVO","priority":"   "}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // Prioridad explícitamente nula
        mockMvc.perform(post("/api/leads")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Invalido","phone":"999111222","stage":"NUEVO","priority":null}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void crear_conOrigenInvalidoOEnBlanco_devuelve400() throws Exception {
        // Origen desconocido
        mockMvc.perform(post("/api/leads")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Invalido","phone":"999111222","stage":"NUEVO","origin":"PARTNER"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // Origen con solo espacios en blanco
        mockMvc.perform(post("/api/leads")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Invalido","phone":"999111222","stage":"NUEVO","origin":"   "}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // Origen explícitamente nulo
        mockMvc.perform(post("/api/leads")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Invalido","phone":"999111222","stage":"NUEVO","origin":null}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void actualizar_conPrioridadYOrigen_persisteNuevosValores() throws Exception {
        Lead lead = crearLead(owner, "Lead Actualizable", "999111222", null, "Web");
        lead.setPriority("MEDIA");
        lead.setOrigin("MANUAL");
        leadRepository.saveAndFlush(lead);
        flushYLimpiar();

        mockMvc.perform(put("/api/leads/{id}", lead.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Lead Actualizado","phone":"999111222","stage":"EN_SEGUIMIENTO",
                                 "priority":" baja ","origin":" web "}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("BAJA"))
                .andExpect(jsonPath("$.origin").value("WEB"));

        flushYLimpiar();

        Lead actualizado = leadRepository.findById(lead.getId()).orElseThrow();
        assertThat(actualizado.getPriority()).isEqualTo("BAJA");
        assertThat(actualizado.getOrigin()).isEqualTo("WEB");
    }

    @Test
    void actualizar_omitiendoPrioridadYOrigen_asignaValoresPorDefecto() throws Exception {
        Lead lead = crearLead(owner, "Lead Con Datos", "999111222", null, "Web");
        lead.setPriority("ALTA");
        lead.setOrigin("WEB");
        leadRepository.saveAndFlush(lead);
        flushYLimpiar();

        mockMvc.perform(put("/api/leads/{id}", lead.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Lead Sin Campos Nuevos","phone":"999111222","stage":"NUEVO"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("MEDIA"))
                .andExpect(jsonPath("$.origin").value("MANUAL"));

        flushYLimpiar();

        Lead actualizado = leadRepository.findById(lead.getId()).orElseThrow();
        assertThat(actualizado.getPriority()).isEqualTo("MEDIA");
        assertThat(actualizado.getOrigin()).isEqualTo("MANUAL");
    }

    @Test
    void listar_filtrosPriorityYOrigin_combinadosConEtapaYSeguimiento_conAislamientoEstricto() throws Exception {
        LocalDate hoyLima = LocalDate.now(java.time.ZoneId.of("America/Lima"));

        // 1. Lead owner: ALTA, REFERIDO, EN_SEGUIMIENTO, hoy
        Lead l1 = crearLead(owner, "Lead A1", "901000001", null, null);
        l1.setPriority("ALTA");
        l1.setOrigin("REFERIDO");
        l1.setEtapa("EN_SEGUIMIENTO");
        l1.setProximoSeguimiento(hoyLima);
        leadRepository.saveAndFlush(l1);

        // 2. Lead owner: ALTA, WEB, NUEVO, hoy
        Lead l2 = crearLead(owner, "Lead A2", "901000002", null, null);
        l2.setPriority("ALTA");
        l2.setOrigin("WEB");
        l2.setEtapa("NUEVO");
        l2.setProximoSeguimiento(hoyLima);
        leadRepository.saveAndFlush(l2);

        // 3. Lead owner: MEDIA, REFERIDO, EN_SEGUIMIENTO, hoy
        Lead l3 = crearLead(owner, "Lead A3", "901000003", null, null);
        l3.setPriority("MEDIA");
        l3.setOrigin("REFERIDO");
        l3.setEtapa("EN_SEGUIMIENTO");
        l3.setProximoSeguimiento(hoyLima);
        leadRepository.saveAndFlush(l3);

        // 4. Lead owner: BAJA, MANUAL, CERRADO, sin fecha
        Lead l4 = crearLead(owner, "Lead A4", "901000004", null, null);
        l4.setPriority("BAJA");
        l4.setOrigin("MANUAL");
        l4.setEtapa("CERRADO");
        l4.setProximoSeguimiento(null);
        leadRepository.saveAndFlush(l4);

        // 5. Lead ajeno (other): ALTA, REFERIDO, EN_SEGUIMIENTO, hoy (idéntico a l1)
        Lead lAjeno = crearLead(other, "Ajeno Gemelo", "901000099", null, null);
        lAjeno.setPriority("ALTA");
        lAjeno.setOrigin("REFERIDO");
        lAjeno.setEtapa("EN_SEGUIMIENTO");
        lAjeno.setProximoSeguimiento(hoyLima);
        leadRepository.saveAndFlush(lAjeno);

        flushYLimpiar();

        // Filtro solo priority=alta (case insensitive, trimmed) -> l1 y l2 (ajeno excluido)
        mockMvc.perform(get("/api/leads").param("priority", "  alta  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].name").value(org.hamcrest.Matchers.containsInAnyOrder("Lead A1", "Lead A2")));

        // Filtro solo origin=referido (case insensitive) -> l1 y l3 (ajeno excluido)
        mockMvc.perform(get("/api/leads").param("origin", "referido"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].name").value(org.hamcrest.Matchers.containsInAnyOrder("Lead A1", "Lead A3")));

        // Filtro combinado priority=alta AND origin=referido -> solo l1
        mockMvc.perform(get("/api/leads")
                        .param("priority", "alta")
                        .param("origin", "referido"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Lead A1"));

        // Filtro combinado total: priority=alta AND origin=referido AND stage=EN_SEGUIMIENTO AND followUp=today -> solo l1
        mockMvc.perform(get("/api/leads")
                        .param("priority", "alta")
                        .param("origin", "referido")
                        .param("stage", "EN_SEGUIMIENTO")
                        .param("followUp", "today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Lead A1"));

        // Filtro combinado sin coincidencia: priority=baja AND stage=EN_SEGUIMIENTO -> 0
        mockMvc.perform(get("/api/leads")
                        .param("priority", "baja")
                        .param("stage", "EN_SEGUIMIENTO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // Parámetro vacío/en blanco no aplica filtro
        mockMvc.perform(get("/api/leads")
                        .param("priority", "   ")
                        .param("origin", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    void listar_conFiltrosPriorityYOriginInvalidos_devuelve400() throws Exception {
        mockMvc.perform(get("/api/leads").param("priority", "URGENTE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/leads").param("origin", "PARTNER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // C06 duplicate-detection regressions

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

    // C09 audit API integration regressions

    @Test
    void actualizar_persisteDeltasEnBaseDeDatosYExponeAuditoriaPaginadaConAislamiento() throws Exception {
        // 1. Crear lead propio en estado inicial
        Lead lead = crearLead(owner, "Carlos Inicial", "999111222", "carlos@test.com", "Consultoria");
        lead.setEtapa("EN_SEGUIMIENTO");
        lead.setProximoSeguimiento(LocalDate.of(2026, 11, 20));
        leadRepository.saveAndFlush(lead);
        flushYLimpiar();

        // 2. Asociar nota privada para verificar estricta separación de notas (R6 / Req 3)
        jdbcTemplate.update("INSERT INTO nota (lead_id, contenido) VALUES (?, ?)", lead.getId(), "Nota privada confidencial");
        assertThat(contarNotas(lead.getId())).isEqualTo(1);

        // 3. Actualizar lead vía PUT /api/leads/{id} modificando nombre, pasando service a null (transición a null)
        // y pasando etapa a CERRADO (lo que normaliza proximoSeguimiento a null). Telefono y correo se conservan.
        mockMvc.perform(put("/api/leads/{id}", lead.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Carlos Actualizado","phone":"999111222","email":"carlos@test.com",
                                 "stage":"CERRADO","service":null,"nextFollowUp":"2026-12-01"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Carlos Actualizado"))
                .andExpect(jsonPath("$.service").value(nullValue()))
                .andExpect(jsonPath("$.stage").value("CERRADO"))
                .andExpect(jsonPath("$.nextFollowUp").value(nullValue()));

        flushYLimpiar();

        // 4. Verificación directa en base de datos PostgreSQL: se deben haber persistido exactamente 4 deltas
        // (name, service, stage, nextFollowUp) con usuario_id del propietario autenticado
        Integer auditRowsCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM lead_audit_log WHERE lead_id = ? AND usuario_id = ?",
                Integer.class, lead.getId(), owner.getId());
        assertThat(auditRowsCount).isEqualTo(4);

        // La cantidad y contenido de notas privadas no fueron alterados
        assertThat(contarNotas(lead.getId())).isEqualTo(1);

        // 5. Verificación vía endpoint GET /api/leads/{id}/audit: formato paginado, tamaño 20, deltas exactos y sin notas
        mockMvc.perform(get("/api/leads/{id}/audit", lead.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content.length()").value(4))
                .andExpect(jsonPath("$.content[?(@.field == 'name')].oldValue").value("Carlos Inicial"))
                .andExpect(jsonPath("$.content[?(@.field == 'name')].newValue").value("Carlos Actualizado"))
                .andExpect(jsonPath("$.content[?(@.field == 'service')].oldValue").value("Consultoria"))
                .andExpect(jsonPath("$.content[?(@.field == 'service')].newValue").value(contains(nullValue())))
                .andExpect(jsonPath("$.content[?(@.field == 'stage')].oldValue").value("EN_SEGUIMIENTO"))
                .andExpect(jsonPath("$.content[?(@.field == 'stage')].newValue").value("CERRADO"))
                .andExpect(jsonPath("$.content[?(@.field == 'nextFollowUp')].oldValue").value("2026-11-20"))
                .andExpect(jsonPath("$.content[?(@.field == 'nextFollowUp')].newValue").value(contains(nullValue())))
                // R6: No expone contenido de notas privadas
                .andExpect(jsonPath("$.content[*].contenido").doesNotExist())
                .andExpect(jsonPath("$.content[*].content").doesNotExist());
    }

    @Test
    void auditoriaDeOtroUsuario_devuelve404_eIgnoraOwnerIdSpoofing() throws Exception {
        // 1. Crear lead perteneciente a 'other' y persistir un delta de auditoría en base de datos
        Lead leadAjeno = crearLead(other, "Cliente Ajeno", "987654321", "ajeno@test.com", "Plan X");
        flushYLimpiar();

        jdbcTemplate.update("""
                INSERT INTO lead_audit_log (lead_id, usuario_id, field_name, old_value, new_value)
                VALUES (?, ?, 'stage', 'NUEVO', 'EN_SEGUIMIENTO')
                """, leadAjeno.getId(), other.getId());

        // 2. El usuario autenticado actual ('owner') consulta el lead de 'other': responde 404 indistinguible
        mockMvc.perform(get("/api/leads/{id}/audit", leadAjeno.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Lead no encontrado"));

        // 3. Intento de manipulación de parámetro ?ownerId= no altera el aislamiento: responde 404
        mockMvc.perform(get("/api/leads/{id}/audit", leadAjeno.getId())
                        .param("ownerId", String.valueOf(other.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // 4. Confirmar que el registro ajeno en base de datos no fue modificado ni expuesto
        Integer otherAuditRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM lead_audit_log WHERE lead_id = ? AND usuario_id = ?",
                Integer.class, leadAjeno.getId(), other.getId());
        assertThat(otherAuditRows).isEqualTo(1);
    }

    // =========================================================================
    // TESTS C12: ARCHIVO Y RESTAURACION LOGICA (DSW-52)
    // =========================================================================

    @Test
    void archivar_leadActivoPropio_asignaTimestampEnBd_yExcluyeDeConsultasActivas() throws Exception {
        LocalDate hoyLima = LocalDate.now(java.time.ZoneId.of("America/Lima"));
        Lead lead = crearLead(owner, "Lead Para Archivar", "999000111", "activo@test.com", "Web");
        lead.setEtapa("EN_SEGUIMIENTO");
        lead.setProximoSeguimiento(hoyLima);
        leadRepository.saveAndFlush(lead);
        flushYLimpiar();

        // 1. Archivar con sesión y CSRF
        mockMvc.perform(post("/api/leads/{id}/archive", lead.getId())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(lead.getId()))
                .andExpect(jsonPath("$.archivedAt").isNotEmpty());

        flushYLimpiar();

        // 2. Comprobación directa en PostgreSQL: archived_at no es nulo
        java.sql.Timestamp archivedAt = jdbcTemplate.queryForObject(
                "SELECT archived_at FROM lead WHERE id = ?",
                java.sql.Timestamp.class,
                lead.getId()
        );
        assertThat(archivedAt).isNotNull();

        // 3. Excluido del detalle estándar (404)
        mockMvc.perform(get("/api/leads/{id}", lead.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // 4. Excluido del listado activo
        mockMvc.perform(get("/api/leads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // 5. Presente en el listado de archivados
        mockMvc.perform(get("/api/leads/archived"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(lead.getId()))
                .andExpect(jsonPath("$.content[0].archivedAt").isNotEmpty());
    }

    @Test
    void archivar_repetido_oAjeno_devuelve404() throws Exception {
        // Lead propio archivado
        Lead propioArchivado = crearLead(owner, "Ya Archivado", "999000222", null, null);
        propioArchivado.setArchivedAt(java.time.OffsetDateTime.now());
        leadRepository.saveAndFlush(propioArchivado);

        // Lead ajeno activo
        Lead ajenoActivo = crearLead(other, "Ajeno", "999000333", null, null);
        flushYLimpiar();

        // Archivar un lead que ya está archivado devuelve 404
        mockMvc.perform(post("/api/leads/{id}/archive", propioArchivado.getId())
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // Archivar un lead de otro propietario devuelve 404
        mockMvc.perform(post("/api/leads/{id}/archive", ajenoActivo.getId())
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void restaurar_leadArchivado_limpiaTimestampEnBd_yVuelveAparecerEnActivos() throws Exception {
        Lead lead = crearLead(owner, "Lead Restaurable", "999000444", "restaurable@test.com", "SEO");
        lead.setArchivedAt(java.time.OffsetDateTime.now());
        leadRepository.saveAndFlush(lead);
        flushYLimpiar();

        // 1. Restaurar lead archivado
        mockMvc.perform(post("/api/leads/{id}/restore", lead.getId())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(lead.getId()))
                .andExpect(jsonPath("$.archivedAt").doesNotExist());

        flushYLimpiar();

        // 2. Comprobación directa en PostgreSQL: archived_at es NULL
        java.sql.Timestamp archivedAt = jdbcTemplate.queryForObject(
                "SELECT archived_at FROM lead WHERE id = ?",
                java.sql.Timestamp.class,
                lead.getId()
        );
        assertThat(archivedAt).isNull();

        // 3. Vuelve a ser visible en detalle estándar
        mockMvc.perform(get("/api/leads/{id}", lead.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(lead.getId()));

        // 4. Vuelve a aparecer en el listado activo
        mockMvc.perform(get("/api/leads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        // 5. Ya no aparece en listado de archivados
        mockMvc.perform(get("/api/leads/archived"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // 6. Restauración repetida sobre lead ya activo devuelve 404
        mockMvc.perform(post("/api/leads/{id}/restore", lead.getId())
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // 7. Restauración sobre lead de otro usuario devuelve 404
        Lead ajenoArchivado = crearLead(other, "Ajeno", "999000555", null, null);
        ajenoArchivado.setArchivedAt(java.time.OffsetDateTime.now());
        leadRepository.saveAndFlush(ajenoArchivado);
        flushYLimpiar();

        mockMvc.perform(post("/api/leads/{id}/restore", ajenoArchivado.getId())
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void archivoYRestauracion_preservanNotasAuditoriaEtiquetasYOutbox_sinEventosInventados() throws Exception {
        // 1. Crear lead con notas, auditoría, etiqueta y registro de outbox inicial
        Lead lead = crearLead(owner, "Lead Completo Ciclo", "999000666", "completo@test.com", "Plan Pro");
        flushYLimpiar();

        // Crear etiqueta propia y asociar
        Long tagId = jdbcTemplate.queryForObject(
                "INSERT INTO tag (usuario_id, nombre) VALUES (?, ?) RETURNING id",
                Long.class, owner.getId(), "Tag C12"
        );
        jdbcTemplate.update("INSERT INTO lead_tag (lead_id, tag_id) VALUES (?, ?)", lead.getId(), tagId);

        // Crear nota privada
        jdbcTemplate.update("INSERT INTO nota (lead_id, contenido) VALUES (?, ?)", lead.getId(), "Nota historica importante");

        // Crear registro de auditoría
        jdbcTemplate.update("""
                INSERT INTO lead_audit_log (lead_id, usuario_id, field_name, old_value, new_value)
                VALUES (?, ?, 'stage', 'NUEVO', 'EN_SEGUIMIENTO')
                """, lead.getId(), owner.getId());

        // Conteo inicial de registros en BD
        Integer notesBefore = jdbcTemplate.queryForObject("SELECT count(*) FROM nota WHERE lead_id = ?", Integer.class, lead.getId());
        Integer auditBefore = jdbcTemplate.queryForObject("SELECT count(*) FROM lead_audit_log WHERE lead_id = ?", Integer.class, lead.getId());
        Integer tagsBefore = jdbcTemplate.queryForObject("SELECT count(*) FROM lead_tag WHERE lead_id = ?", Integer.class, lead.getId());
        Integer outboxBefore = jdbcTemplate.queryForObject("SELECT count(*) FROM outbox", Integer.class);

        assertThat(notesBefore).isEqualTo(1);
        assertThat(auditBefore).isEqualTo(1);
        assertThat(tagsBefore).isEqualTo(1);

        // 2. Archivar lead vía API
        mockMvc.perform(post("/api/leads/{id}/archive", lead.getId()).with(csrf()))
                .andExpect(status().isOk());
        flushYLimpiar();

        // 3. Comprobar que en SQL las notas, auditoría y tags permanecen intactos (sin borrado en cascada)
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM nota WHERE lead_id = ?", Integer.class, lead.getId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM lead_audit_log WHERE lead_id = ?", Integer.class, lead.getId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM lead_tag WHERE lead_id = ?", Integer.class, lead.getId())).isEqualTo(1);
        // Ningún evento de outbox inventado para archive
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM outbox", Integer.class)).isEqualTo(outboxBefore);

        // 4. Restaurar lead vía API
        mockMvc.perform(post("/api/leads/{id}/restore", lead.getId()).with(csrf()))
                .andExpect(status().isOk());
        flushYLimpiar();

        // 5. Comprobar que tras restaurar, todo el historial sigue exactamente presente
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM nota WHERE lead_id = ?", Integer.class, lead.getId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM lead_audit_log WHERE lead_id = ?", Integer.class, lead.getId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM lead_tag WHERE lead_id = ?", Integer.class, lead.getId())).isEqualTo(1);
        // Ningún evento de outbox inventado para restore
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM outbox", Integer.class)).isEqualTo(outboxBefore);
    }

    @Test
    void leadArchivado_retieneReservaDeUnicidadDeContactoNormalizadoC06() throws Exception {
        // 1. Crear y archivar lead con contacto determinado
        Lead lead = crearLead(owner, "Lead Reservado", "(999) 777-888", "reservado@test.com", null);
        flushYLimpiar();

        mockMvc.perform(post("/api/leads/{id}/archive", lead.getId()).with(csrf()))
                .andExpect(status().isOk());
        flushYLimpiar();

        // 2. Intentar crear un nuevo lead con el mismo correo normalizado debe fallar por duplicado (400)
        mockMvc.perform(post("/api/leads")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Intento Correo Duplicado","email":"  RESERVADO@test.com  ","stage":"NUEVO"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Ya existe un lead con este correo"));

        // 3. Intentar crear un nuevo lead con el mismo teléfono normalizado debe fallar por duplicado (400)
        mockMvc.perform(post("/api/leads")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Intento Tel Duplicado","phone":"999.777.888","stage":"NUEVO"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Ya existe un lead con este teléfono"));
    }

    @Test
    void dashboard_excluyeLeadsArchivadosDeTotalesEtapasYSeguimientos() throws Exception {
        LocalDate hoyLima = LocalDate.now(java.time.ZoneId.of("America/Lima"));

        // 1. Lead activo para hoy
        Lead activo = crearLead(owner, "Lead Activo Hoy", "999888001", null, null);
        activo.setEtapa("EN_SEGUIMIENTO");
        activo.setProximoSeguimiento(hoyLima);
        leadRepository.saveAndFlush(activo);

        // 2. Lead archivado para hoy
        Lead archivado = crearLead(owner, "Lead Archivado Hoy", "999888002", null, null);
        archivado.setEtapa("EN_SEGUIMIENTO");
        archivado.setProximoSeguimiento(hoyLima);
        archivado.setArchivedAt(java.time.OffsetDateTime.now());
        leadRepository.saveAndFlush(archivado);
        flushYLimpiar();

        // 3. Consultar dashboard: métricas deben reflejar solo el lead activo
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLeads").value(1))
                .andExpect(jsonPath("$.leadsPorEtapa.EN_SEGUIMIENTO").value(1))
                .andExpect(jsonPath("$.seguimientos.hoy").value(1))
                .andExpect(jsonPath("$.seguimientos.leadsHoy.length()").value(1))
                .andExpect(jsonPath("$.seguimientos.leadsHoy[0].id").value(activo.getId()))
                .andExpect(jsonPath("$.seguimientos.leadsHoy[0].nombre").value("Lead Activo Hoy"));
    }
}
