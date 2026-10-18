package com.urleads.service.impl;

import com.urleads.dto.CreateLeadRequest;
import com.urleads.dto.UpdateLeadRequest;
import com.urleads.entity.Lead;
import com.urleads.entity.Usuario;
import com.urleads.exception.LeadNotFoundException;
import com.urleads.exception.LeadValidationException;
import com.urleads.repository.LeadRepository;
import com.urleads.repository.UsuarioRepository;
import com.urleads.service.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("R1/R1b/R3: Pruebas de integración de Outbox con LeadServiceImpl")
class LeadServiceImplOutboxTest {

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private OutboxService outboxService;

    private LeadServiceImpl leadService;

    @BeforeEach
    void setUp() {
        leadService = new LeadServiceImpl(leadRepository, usuarioRepository, outboxService);
    }

    @Test
    @DisplayName("R1: crear lead exitoso invoca outboxService.recordLeadCreated con usuarioId confiable")
    void crear_exitoso_invocaOutboxService() {
        Long usuarioId = 42L;
        CreateLeadRequest request = new CreateLeadRequest();
        request.setNombre("Carlos Perez");
        request.setCorreo("carlos@example.com");
        request.setEtapa("NUEVO");
        request.setProximoSeguimiento(LocalDate.of(2026, 10, 25));

        Usuario usuario = new Usuario();
        usuario.setId(usuarioId);

        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(leadRepository.save(any(Lead.class))).thenAnswer(i -> {
            Lead lead = i.getArgument(0);
            lead.setId(101L);
            return lead;
        });

        leadService.crear(usuarioId, request);

        verify(leadRepository).save(any(Lead.class));
        verify(outboxService).recordLeadCreated(eq(usuarioId), any(Lead.class));
    }

    @Test
    @DisplayName("R1: actualizar lead exitoso invoca outboxService.recordLeadUpdated")
    void actualizar_exitoso_invocaOutboxService() {
        Long usuarioId = 42L;
        Long leadId = 101L;

        Lead existente = new Lead();
        existente.setId(leadId);
        existente.setNombre("Carlos Perez");
        existente.setCorreo("carlos@example.com");
        existente.setEtapa("NUEVO");

        when(leadRepository.findByIdAndUsuarioId(leadId, usuarioId)).thenReturn(Optional.of(existente));
        when(leadRepository.save(any(Lead.class))).thenAnswer(i -> i.getArgument(0));

        UpdateLeadRequest request = new UpdateLeadRequest();
        request.setNombre("Carlos Perez");
        request.setCorreo("carlos@example.com");
        request.setEtapa("EN_SEGUIMIENTO");
        request.setProximoSeguimiento(LocalDate.of(2026, 10, 28));

        leadService.actualizar(leadId, usuarioId, request);

        verify(leadRepository).save(any(Lead.class));
        verify(outboxService).recordLeadUpdated(eq(usuarioId), any(Lead.class));
    }

    @Test
    @DisplayName("R1: eliminar lead captura identificador e invoca outboxService.recordLeadDeleted")
    void eliminar_exitoso_capturaIdEInvocaOutboxService() {
        Long usuarioId = 42L;
        Long leadId = 101L;

        Lead existente = new Lead();
        existente.setId(leadId);

        when(leadRepository.findByIdAndUsuarioId(leadId, usuarioId)).thenReturn(Optional.of(existente));

        leadService.eliminar(leadId, usuarioId);

        verify(leadRepository).delete(existente);
        verify(outboxService).recordLeadDeleted(eq(usuarioId), eq(leadId));
    }

    @Test
    @DisplayName("R1b: Validación fallida en crear NO persiste lead ni evento outbox")
    void crear_validacionFallida_noEncolaOutbox() {
        CreateLeadRequest request = new CreateLeadRequest();
        request.setNombre("Invalido");
        request.setEtapa("ETAPA_INEXISTENTE");

        assertThatThrownBy(() -> leadService.crear(42L, request))
                .isInstanceOf(LeadValidationException.class);

        verifyNoInteractions(leadRepository, outboxService);
    }

    @Test
    @DisplayName("R1b: Lead inexistente o de otro usuario en actualizar NO encola outbox")
    void actualizar_leadNoEncontrado_noEncolaOutbox() {
        UpdateLeadRequest request = new UpdateLeadRequest();
        request.setNombre("Carlos");
        request.setCorreo("carlos@example.com");
        request.setEtapa("EN_SEGUIMIENTO");

        when(leadRepository.findByIdAndUsuarioId(999L, 42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leadService.actualizar(999L, 42L, request))
                .isInstanceOf(LeadNotFoundException.class);

        verify(leadRepository, never()).save(any());
        verifyNoInteractions(outboxService);
    }

    @Test
    @DisplayName("R1b: Lead inexistente o de otro usuario en eliminar NO encola outbox")
    void eliminar_leadNoEncontrado_noEncolaOutbox() {
        when(leadRepository.findByIdAndUsuarioId(999L, 42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leadService.eliminar(999L, 42L))
                .isInstanceOf(LeadNotFoundException.class);

        verify(leadRepository, never()).delete(any());
        verifyNoInteractions(outboxService);
    }

    @Test
    @DisplayName("R1b: Falla en persistencia de outbox propaga excepción para provocar rollback transaccional")
    void crear_cuandoOutboxFalla_propagaExcepcionParaRollback() {
        Long usuarioId = 42L;
        CreateLeadRequest request = new CreateLeadRequest();
        request.setNombre("Carlos Perez");
        request.setCorreo("carlos@example.com");
        request.setEtapa("NUEVO");

        Usuario usuario = new Usuario();
        usuario.setId(usuarioId);

        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(leadRepository.save(any(Lead.class))).thenAnswer(i -> {
            Lead lead = i.getArgument(0);
            lead.setId(101L);
            return lead;
        });
        when(outboxService.recordLeadCreated(eq(usuarioId), any(Lead.class)))
                .thenThrow(new IllegalStateException("Falla de serialización o base de datos en outbox"));

        assertThatThrownBy(() -> leadService.crear(usuarioId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outbox");
    }

    @Test
    @DisplayName("R1b: Falla en guardado de lead no llega a invocar outbox")
    void crear_cuandoLeadRepositoryFalla_noInvocaOutbox() {
        Long usuarioId = 42L;
        CreateLeadRequest request = new CreateLeadRequest();
        request.setNombre("Carlos Perez");
        request.setCorreo("carlos@example.com");
        request.setEtapa("NUEVO");

        Usuario usuario = new Usuario();
        usuario.setId(usuarioId);

        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(leadRepository.save(any(Lead.class))).thenThrow(new RuntimeException("Error en base de datos"));

        assertThatThrownBy(() -> leadService.crear(usuarioId, request))
                .isInstanceOf(RuntimeException.class);

        verifyNoInteractions(outboxService);
    }

    @Test
    @DisplayName("C06: Lead con correo duplicado en crear es rechazado y NO encola outbox")
    void crear_duplicadoCorreo_noEncolaOutboxNiPersisteLead() {
        Long usuarioId = 42L;
        CreateLeadRequest request = new CreateLeadRequest();
        request.setNombre("Carlos Duplicado");
        request.setCorreo("duplicado@example.com");
        request.setEtapa("NUEVO");

        when(leadRepository.existsByUsuarioIdAndCorreoNorm(usuarioId, "duplicado@example.com")).thenReturn(true);

        assertThatThrownBy(() -> leadService.crear(usuarioId, request))
                .isInstanceOf(LeadValidationException.class);

        verify(leadRepository, never()).save(any());
        verifyNoInteractions(outboxService);
    }

    @Test
    @DisplayName("C06: Lead con teléfono duplicado en crear es rechazado y NO encola outbox")
    void crear_duplicadoTelefono_noEncolaOutboxNiPersisteLead() {
        Long usuarioId = 42L;
        CreateLeadRequest request = new CreateLeadRequest();
        request.setNombre("Carlos Duplicado Tel");
        request.setTelefono("999111222");
        request.setEtapa("NUEVO");

        when(leadRepository.existsByUsuarioIdAndTelefonoNorm(usuarioId, "999111222")).thenReturn(true);

        assertThatThrownBy(() -> leadService.crear(usuarioId, request))
                .isInstanceOf(LeadValidationException.class);

        verify(leadRepository, never()).save(any());
        verifyNoInteractions(outboxService);
    }

    @Test
    @DisplayName("C06: Lead con contacto duplicado en actualizar es rechazado y NO encola outbox")
    void actualizar_duplicadoContacto_noEncolaOutboxNiPersisteLead() {
        Long usuarioId = 42L;
        Long leadId = 101L;

        Lead existente = new Lead();
        existente.setId(leadId);
        existente.setNombre("Carlos Perez");
        existente.setCorreo("carlos@example.com");
        existente.setEtapa("NUEVO");

        when(leadRepository.findByIdAndUsuarioId(leadId, usuarioId)).thenReturn(Optional.of(existente));
        when(leadRepository.existsByUsuarioIdAndCorreoNormAndIdNot(usuarioId, "otro@example.com", leadId)).thenReturn(true);

        UpdateLeadRequest request = new UpdateLeadRequest();
        request.setNombre("Carlos Perez");
        request.setCorreo("otro@example.com");
        request.setEtapa("EN_SEGUIMIENTO");

        assertThatThrownBy(() -> leadService.actualizar(leadId, usuarioId, request))
                .isInstanceOf(LeadValidationException.class);

        verify(leadRepository, never()).save(any());
        verifyNoInteractions(outboxService);
    }

    @Test
    @DisplayName("C06: Falla por restricción de unicidad en BD durante carrera transaccional NO encola outbox")
    void crear_carreraBDReduceADataIntegrityViolation_noEncolaOutbox() {
        Long usuarioId = 42L;
        CreateLeadRequest request = new CreateLeadRequest();
        request.setNombre("Carrera Concurrente");
        request.setCorreo("carrera@example.com");
        request.setEtapa("NUEVO");

        Usuario usuario = new Usuario();
        usuario.setId(usuarioId);

        ConstraintViolationException cve = new ConstraintViolationException(
                "duplicate key",
                new SQLException("duplicate"),
                "uq_lead_owner_correo_norm"
        );

        when(leadRepository.existsByUsuarioIdAndCorreoNorm(usuarioId, "carrera@example.com")).thenReturn(false);
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(leadRepository.save(any(Lead.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key violates unique constraint", cve));

        assertThatThrownBy(() -> leadService.crear(usuarioId, request))
                .isInstanceOf(LeadValidationException.class)
                .hasMessage("Ya existe un lead con este correo");

        verifyNoInteractions(outboxService);
    }
}
