package com.urleads;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.urleads.repository.NotaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.urleads.repository.LeadAuditLogRepository;
import com.urleads.repository.LeadRepository;
import com.urleads.repository.UsuarioRepository;
import com.urleads.security.CurrentUserProvider;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
})
@AutoConfigureMockMvc
class UrLeadsApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LeadRepository leadRepository;

    @MockBean
    private LeadAuditLogRepository leadAuditLogRepository;

    @MockBean
    private NotaRepository notaRepository;

    @MockBean
    private UsuarioRepository usuarioRepository;

    @MockBean
    private com.urleads.repository.NotificationRepository notificationRepository;

    @MockBean
    private com.urleads.repository.ProcessedEventRepository processedEventRepository;

    @MockBean
    private com.urleads.repository.AgendaTareaRepository agendaTareaRepository;

    @MockBean
    private CurrentUserProvider currentUserProvider;

    @Test
    @WithMockUser
    void contextoArrancaSinBaseDeDatos_yLeadInexistenteDevuelve404Json() throws Exception {
        org.mockito.Mockito.when(currentUserProvider.getCurrentUserId()).thenReturn(1L);

        // El mock devuelve Optional.empty(): atraviesa controlador -> servicio -> handler
        mockMvc.perform(get("/api/leads/{id}", 999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}