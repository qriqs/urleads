package com.urleads.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urleads.database.TestDatabaseGuardInitializer;
import com.urleads.dto.NotaRequest;
import com.urleads.entity.Lead;
import com.urleads.entity.Usuario;
import com.urleads.repository.LeadRepository;
import com.urleads.repository.NotaRepository;
import com.urleads.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Tag("db-integration")
@ActiveProfiles("db-integration")
@ContextConfiguration(initializers = TestDatabaseGuardInitializer.class)
class NotaControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private NotaRepository notaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private Usuario usuarioA;
    private Usuario usuarioB;
    private Lead leadA;
    private Lead leadB;

    @BeforeEach
    void setUp() {
        notaRepository.deleteAll();
        leadRepository.deleteAll();
        usuarioRepository.deleteAll();

        usuarioA = new Usuario();
        usuarioA.setUsername("userA");
        usuarioA.setPasswordHash(passwordEncoder.encode("Pass123!"));
        usuarioA = usuarioRepository.save(usuarioA);

        usuarioB = new Usuario();
        usuarioB.setUsername("userB");
        usuarioB.setPasswordHash(passwordEncoder.encode("Pass123!"));
        usuarioB = usuarioRepository.save(usuarioB);

        leadA = new Lead();
        leadA.setUsuario(usuarioA);
        leadA.setNombre("Lead Usuario A");
        leadA.setTelefono("+51999999999");
        leadA.setEtapa("NUEVO");
        leadA = leadRepository.save(leadA);

        leadB = new Lead();
        leadB.setUsuario(usuarioB);
        leadB.setNombre("Lead Usuario B");
        leadB.setTelefono("+51888888888");
        leadB.setEtapa("NUEVO");
        leadB = leadRepository.save(leadB);
    }

    private MockHttpSession autenticar(String username, String password) throws Exception {
        String jsonPayload = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password);

        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) res.getRequest().getSession(false);
    }

    @Test
    @DisplayName("POST valido retorna 201, contenido plano y fecha de servidor")
    void crearNota_Valida_Retorna201() throws Exception {
        MockHttpSession sesion = autenticar("userA", "Pass123!");
        NotaRequest request = new NotaRequest("Pidió la propuesta para el viernes.");

        mockMvc.perform(post("/api/leads/" + leadA.getId() + "/notes")
                        .session(sesion)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.content", is("Pidió la propuesta para el viernes.")))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    @DisplayName("GET retorna notas en orden cronologico ascendente")
    void listarNotas_OrdenAscendente() throws Exception {
        MockHttpSession sesion = autenticar("userA", "Pass123!");

        mockMvc.perform(post("/api/leads/" + leadA.getId() + "/notes")
                .session(sesion).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new NotaRequest("Nota 1"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/leads/" + leadA.getId() + "/notes")
                .session(sesion).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new NotaRequest("Nota 2"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/leads/" + leadA.getId() + "/notes").session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].content", is("Nota 1")))
                .andExpect(jsonPath("$[1].content", is("Nota 2")));
    }

    @Test
    @DisplayName("POST con contenido vacio o espacios retorna 400")
    void crearNota_Vacia_Retorna400() throws Exception {
        MockHttpSession sesion = autenticar("userA", "Pass123!");

        mockMvc.perform(post("/api/leads/" + leadA.getId() + "/notes")
                        .session(sesion)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new NotaRequest("    "))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST con mas de 2000 caracteres retorna 400")
    void crearNota_Oversize_Retorna400() throws Exception {
        MockHttpSession sesion = autenticar("userA", "Pass123!");

        mockMvc.perform(post("/api/leads/" + leadA.getId() + "/notes")
                        .session(sesion)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new NotaRequest("A".repeat(2001)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Acceso a lead de otro usuario retorna 404 (Owner Isolation)")
    void accesoLeadAjeno_Retorna404() throws Exception {
        MockHttpSession sesionA = autenticar("userA", "Pass123!");

        mockMvc.perform(post("/api/leads/" + leadB.getId() + "/notes")
                        .session(sesionA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new NotaRequest("Intento en ajeno"))))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/leads/" + leadB.getId() + "/notes")
                        .session(sesionA))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Peticion sin autenticar retorna 401")
    void sinSesion_Retorna401() throws Exception {
        mockMvc.perform(get("/api/leads/" + leadA.getId() + "/notes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST sin CSRF retorna 403")
    void postSinCsrf_Retorna403() throws Exception {
        MockHttpSession sesion = autenticar("userA", "Pass123!");

        mockMvc.perform(post("/api/leads/" + leadA.getId() + "/notes")
                        .session(sesion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new NotaRequest("Texto sin token"))))
                .andExpect(status().isForbidden());
    }
}