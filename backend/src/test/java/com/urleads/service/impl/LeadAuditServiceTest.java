package com.urleads.service.impl;

import com.urleads.dto.CreateLeadRequest;
import com.urleads.dto.LeadAuditResponse;
import com.urleads.dto.PageResponse;
import com.urleads.dto.UpdateLeadRequest;
import com.urleads.entity.Lead;
import com.urleads.entity.LeadAuditLog;
import com.urleads.entity.Usuario;
import com.urleads.exception.LeadNotFoundException;
import com.urleads.exception.LeadValidationException;
import com.urleads.repository.LeadAuditLogRepository;
import com.urleads.repository.LeadRepository;
import com.urleads.repository.UsuarioRepository;
import com.urleads.service.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DSW-49 C09: Pruebas unitarias de auditoría de cambios en leads (LeadServiceImpl)")
class LeadAuditServiceTest {

    private static final Long USUARIO_ID = 42L;
    private static final Long OTRO_USUARIO_ID = 99L;
    private static final Long LEAD_ID = 105L;

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private OutboxService outboxService;

    @Mock
    private LeadAuditLogRepository leadAuditLogRepository;

    @Captor
    private ArgumentCaptor<List<LeadAuditLog>> auditListCaptor;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    private Clock clock;
    private LeadServiceImpl leadService;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-10-18T12:00:00Z"), ZoneOffset.UTC);
        leadService = new LeadServiceImpl(
                leadRepository,
                usuarioRepository,
                outboxService,
                leadAuditLogRepository,
                clock
        );
    }

    // =========================================================================
    // R2: REGISTRO AUTOMÁTICO DE DELTAS EN ACTUALIZACIONES EXITOSAS
    // =========================================================================

    @Test
    @DisplayName("R2: Actualizar múltiples campos editables persiste exactamente un delta por campo cambiado")
    void actualizar_multiplesCamposModificados_persisteDeltasExactos() {
        Lead leadExistente = new Lead();
        leadExistente.setId(LEAD_ID);
        leadExistente.setNombre("Nombre Anterior");
        leadExistente.setTelefono("999111222");
        leadExistente.setCorreo("anterior@mail.com");
        leadExistente.setService("Plan Inicial");
        leadExistente.setEtapa("NUEVO");
        leadExistente.setProximoSeguimiento(LocalDate.of(2026, 10, 20));

        when(leadRepository.findByIdAndUsuarioId(LEAD_ID, USUARIO_ID)).thenReturn(Optional.of(leadExistente));
        when(leadRepository.save(any(Lead.class))).thenAnswer(i -> i.getArgument(0));

        UpdateLeadRequest request = new UpdateLeadRequest();
        request.setNombre("Nombre Nuevo");
        request.setTelefono("999333444");
        request.setCorreo("nuevo@mail.com");
        request.setService("Plan Avanzado");
        request.setEtapa("EN_SEGUIMIENTO");
        request.setProximoSeguimiento(LocalDate.of(2026, 10, 25));

        leadService.actualizar(LEAD_ID, USUARIO_ID, request);

        verify(leadAuditLogRepository).saveAll(auditListCaptor.capture());
        List<LeadAuditLog> deltas = auditListCaptor.getValue();

        assertThat(deltas).hasSize(6);

        // name
        LeadAuditLog deltaNombre = deltas.stream().filter(d -> "name".equals(d.getFieldName())).findFirst().orElseThrow();
        assertThat(deltaNombre.getLeadId()).isEqualTo(LEAD_ID);
        assertThat(deltaNombre.getUsuarioId()).isEqualTo(USUARIO_ID);
        assertThat(deltaNombre.getOldValue()).isEqualTo("Nombre Anterior");
        assertThat(deltaNombre.getNewValue()).isEqualTo("Nombre Nuevo");
        assertThat(deltaNombre.getCreatedAt()).isEqualTo(OffsetDateTime.ofInstant(Instant.parse("2026-10-18T12:00:00Z"), ZoneOffset.UTC));

        // phone
        LeadAuditLog deltaTelefono = deltas.stream().filter(d -> "phone".equals(d.getFieldName())).findFirst().orElseThrow();
        assertThat(deltaTelefono.getOldValue()).isEqualTo("999111222");
        assertThat(deltaTelefono.getNewValue()).isEqualTo("999333444");

        // email
        LeadAuditLog deltaCorreo = deltas.stream().filter(d -> "email".equals(d.getFieldName())).findFirst().orElseThrow();
        assertThat(deltaCorreo.getOldValue()).isEqualTo("anterior@mail.com");
        assertThat(deltaCorreo.getNewValue()).isEqualTo("nuevo@mail.com");

        // service
        LeadAuditLog deltaService = deltas.stream().filter(d -> "service".equals(d.getFieldName())).findFirst().orElseThrow();
        assertThat(deltaService.getOldValue()).isEqualTo("Plan Inicial");
        assertThat(deltaService.getNewValue()).isEqualTo("Plan Avanzado");

        // stage
        LeadAuditLog deltaEtapa = deltas.stream().filter(d -> "stage".equals(d.getFieldName())).findFirst().orElseThrow();
        assertThat(deltaEtapa.getOldValue()).isEqualTo("NUEVO");
        assertThat(deltaEtapa.getNewValue()).isEqualTo("EN_SEGUIMIENTO");

        // nextFollowUp
        LeadAuditLog deltaSeguimiento = deltas.stream().filter(d -> "nextFollowUp".equals(d.getFieldName())).findFirst().orElseThrow();
        assertThat(deltaSeguimiento.getOldValue()).isEqualTo("2026-10-20");
        assertThat(deltaSeguimiento.getNewValue()).isEqualTo("2026-10-25");
    }

    @Test
    @DisplayName("R2: Actualizar un subconjunto de campos omite los campos sin modificaciones")
    void actualizar_subconjuntoModificado_soloRegistraCamposModificados() {
        Lead leadExistente = new Lead();
        leadExistente.setId(LEAD_ID);
        leadExistente.setNombre("Carlos Perez");
        leadExistente.setTelefono("999111222");
        leadExistente.setCorreo("carlos@mail.com");
        leadExistente.setService(null);
        leadExistente.setEtapa("EN_SEGUIMIENTO");
        leadExistente.setProximoSeguimiento(LocalDate.of(2026, 10, 20));

        when(leadRepository.findByIdAndUsuarioId(LEAD_ID, USUARIO_ID)).thenReturn(Optional.of(leadExistente));
        when(leadRepository.save(any(Lead.class))).thenAnswer(i -> i.getArgument(0));

        UpdateLeadRequest request = new UpdateLeadRequest();
        request.setNombre("Carlos Perez"); // Sin cambio
        request.setTelefono("999111222"); // Sin cambio
        request.setCorreo("carlos.nuevo@mail.com"); // CAMBIADO
        request.setService("Consultoría"); // CAMBIADO (de null a valor)
        request.setEtapa("EN_SEGUIMIENTO"); // Sin cambio
        request.setProximoSeguimiento(LocalDate.of(2026, 10, 20)); // Sin cambio

        leadService.actualizar(LEAD_ID, USUARIO_ID, request);

        verify(leadAuditLogRepository).saveAll(auditListCaptor.capture());
        List<LeadAuditLog> deltas = auditListCaptor.getValue();

        assertThat(deltas).hasSize(2);
        assertThat(deltas).extracting(LeadAuditLog::getFieldName).containsExactlyInAnyOrder("email", "service");

        LeadAuditLog deltaCorreo = deltas.stream().filter(d -> "email".equals(d.getFieldName())).findFirst().orElseThrow();
        assertThat(deltaCorreo.getOldValue()).isEqualTo("carlos@mail.com");
        assertThat(deltaCorreo.getNewValue()).isEqualTo("carlos.nuevo@mail.com");

        LeadAuditLog deltaService = deltas.stream().filter(d -> "service".equals(d.getFieldName())).findFirst().orElseThrow();
        assertThat(deltaService.getOldValue()).isNull();
        assertThat(deltaService.getNewValue()).isEqualTo("Consultoría");
    }

    @Test
    @DisplayName("R2: Transición de valor a null persiste oldValue string y newValue null explícito")
    void actualizar_transicionANull_persisteNewValueNull() {
        Lead leadExistente = new Lead();
        leadExistente.setId(LEAD_ID);
        leadExistente.setNombre("Carlos Perez");
        leadExistente.setTelefono("999111222");
        leadExistente.setCorreo("carlos@mail.com");
        leadExistente.setService("Consultoría");
        leadExistente.setEtapa("EN_SEGUIMIENTO");
        leadExistente.setProximoSeguimiento(LocalDate.of(2026, 10, 20));

        when(leadRepository.findByIdAndUsuarioId(LEAD_ID, USUARIO_ID)).thenReturn(Optional.of(leadExistente));
        when(leadRepository.save(any(Lead.class))).thenAnswer(i -> i.getArgument(0));

        UpdateLeadRequest request = new UpdateLeadRequest();
        request.setNombre("Carlos Perez");
        request.setTelefono(null); // CAMBIADO a null (limpieza de teléfono)
        request.setCorreo("carlos@mail.com");
        request.setService(null); // CAMBIADO a null
        request.setEtapa("EN_SEGUIMIENTO");
        request.setProximoSeguimiento(null); // CAMBIADO a null

        leadService.actualizar(LEAD_ID, USUARIO_ID, request);

        verify(leadAuditLogRepository).saveAll(auditListCaptor.capture());
        List<LeadAuditLog> deltas = auditListCaptor.getValue();

        assertThat(deltas).hasSize(3);
        assertThat(deltas).extracting(LeadAuditLog::getFieldName)
                .containsExactlyInAnyOrder("phone", "service", "nextFollowUp");

        LeadAuditLog phoneDelta = deltas.stream().filter(d -> "phone".equals(d.getFieldName())).findFirst().orElseThrow();
        assertThat(phoneDelta.getOldValue()).isEqualTo("999111222");
        assertThat(phoneDelta.getNewValue()).isNull();

        LeadAuditLog serviceDelta = deltas.stream().filter(d -> "service".equals(d.getFieldName())).findFirst().orElseThrow();
        assertThat(serviceDelta.getOldValue()).isEqualTo("Consultoría");
        assertThat(serviceDelta.getNewValue()).isNull();

        LeadAuditLog followUpDelta = deltas.stream().filter(d -> "nextFollowUp".equals(d.getFieldName())).findFirst().orElseThrow();
        assertThat(followUpDelta.getOldValue()).isEqualTo("2026-10-20");
        assertThat(followUpDelta.getNewValue()).isNull();
    }

    @Test
    @DisplayName("R2: Actualización a CERRADO normaliza proximoSeguimiento a null y audita la transición real")
    void actualizar_aCerrado_auditaNormalizacionDeFechaANull() {
        Lead leadExistente = new Lead();
        leadExistente.setId(LEAD_ID);
        leadExistente.setNombre("Carlos Perez");
        leadExistente.setTelefono("999111222");
        leadExistente.setCorreo("carlos@mail.com");
        leadExistente.setEtapa("EN_SEGUIMIENTO");
        leadExistente.setProximoSeguimiento(LocalDate.of(2026, 10, 20));

        when(leadRepository.findByIdAndUsuarioId(LEAD_ID, USUARIO_ID)).thenReturn(Optional.of(leadExistente));
        when(leadRepository.save(any(Lead.class))).thenAnswer(i -> i.getArgument(0));

        UpdateLeadRequest request = new UpdateLeadRequest();
        request.setNombre("Carlos Perez");
        request.setTelefono("999111222");
        request.setCorreo("carlos@mail.com");
        request.setEtapa("CERRADO");
        request.setProximoSeguimiento(LocalDate.of(2026, 12, 31)); // El cliente envió fecha pero CERRADO la anula a null

        leadService.actualizar(LEAD_ID, USUARIO_ID, request);

        verify(leadAuditLogRepository).saveAll(auditListCaptor.capture());
        List<LeadAuditLog> deltas = auditListCaptor.getValue();

        assertThat(deltas).hasSize(2);
        assertThat(deltas).extracting(LeadAuditLog::getFieldName).containsExactlyInAnyOrder("stage", "nextFollowUp");

        LeadAuditLog stageDelta = deltas.stream().filter(d -> "stage".equals(d.getFieldName())).findFirst().orElseThrow();
        assertThat(stageDelta.getOldValue()).isEqualTo("EN_SEGUIMIENTO");
        assertThat(stageDelta.getNewValue()).isEqualTo("CERRADO");

        LeadAuditLog followUpDelta = deltas.stream().filter(d -> "nextFollowUp".equals(d.getFieldName())).findFirst().orElseThrow();
        assertThat(followUpDelta.getOldValue()).isEqualTo("2026-10-20");
        assertThat(followUpDelta.getNewValue()).isNull(); // Normalizado a null
    }

    @Test
    @DisplayName("R2: Actualización de lead ya CERRADO sin cambio en etapa ni fecha no genera falso delta de seguimiento")
    void actualizar_yaCerradoSinCambio_noGeneraFalsoDeltaDeSeguimiento() {
        Lead leadExistente = new Lead();
        leadExistente.setId(LEAD_ID);
        leadExistente.setNombre("Carlos Perez");
        leadExistente.setTelefono("999111222");
        leadExistente.setCorreo("carlos@mail.com");
        leadExistente.setEtapa("CERRADO");
        leadExistente.setProximoSeguimiento(null);

        when(leadRepository.findByIdAndUsuarioId(LEAD_ID, USUARIO_ID)).thenReturn(Optional.of(leadExistente));
        when(leadRepository.save(any(Lead.class))).thenAnswer(i -> i.getArgument(0));

        UpdateLeadRequest request = new UpdateLeadRequest();
        request.setNombre("Carlos Perez Modificado");
        request.setTelefono("999111222");
        request.setCorreo("carlos@mail.com");
        request.setEtapa("CERRADO");
        request.setProximoSeguimiento(null);

        leadService.actualizar(LEAD_ID, USUARIO_ID, request);

        verify(leadAuditLogRepository).saveAll(auditListCaptor.capture());
        List<LeadAuditLog> deltas = auditListCaptor.getValue();

        assertThat(deltas).hasSize(1);
        assertThat(deltas.get(0).getFieldName()).isEqualTo("name");
        assertThat(deltas.get(0).getOldValue()).isEqualTo("Carlos Perez");
        assertThat(deltas.get(0).getNewValue()).isEqualTo("Carlos Perez Modificado");
    }

    // =========================================================================
    // R2b: CREACIÓN, NO-OP Y ERRORES NO PRODUCEN FILAS DE AUDITORÍA
    // =========================================================================

    @Test
    @DisplayName("R2b: Creación de lead no genera filas de auditoría")
    void crear_leadExitoso_noGeneraFilasDeAuditoria() {
        CreateLeadRequest request = new CreateLeadRequest();
        request.setNombre("Nuevo Lead");
        request.setTelefono("999111222");
        request.setEtapa("NUEVO");

        Usuario usuario = new Usuario();
        usuario.setId(USUARIO_ID);

        when(usuarioRepository.findById(USUARIO_ID)).thenReturn(Optional.of(usuario));
        when(leadRepository.save(any(Lead.class))).thenAnswer(i -> {
            Lead l = i.getArgument(0);
            l.setId(201L);
            return l;
        });

        leadService.crear(USUARIO_ID, request);

        verify(leadRepository).save(any(Lead.class));
        verifyNoInteractions(leadAuditLogRepository);
    }

    @Test
    @DisplayName("R2b: Actualización no-op (sin campos cambiados) no persiste filas de auditoría")
    void actualizar_sinCambios_noInvocaSaveAllEnAuditoria() {
        Lead leadExistente = new Lead();
        leadExistente.setId(LEAD_ID);
        leadExistente.setNombre("Carlos Perez");
        leadExistente.setTelefono("999111222");
        leadExistente.setCorreo("carlos@mail.com");
        leadExistente.setService("Consultoría");
        leadExistente.setEtapa("EN_SEGUIMIENTO");
        leadExistente.setProximoSeguimiento(LocalDate.of(2026, 10, 20));

        when(leadRepository.findByIdAndUsuarioId(LEAD_ID, USUARIO_ID)).thenReturn(Optional.of(leadExistente));
        when(leadRepository.save(any(Lead.class))).thenAnswer(i -> i.getArgument(0));

        UpdateLeadRequest request = new UpdateLeadRequest();
        request.setNombre("Carlos Perez");
        request.setTelefono("999111222");
        request.setCorreo("carlos@mail.com");
        request.setService("Consultoría");
        request.setEtapa("EN_SEGUIMIENTO");
        request.setProximoSeguimiento(LocalDate.of(2026, 10, 20));

        leadService.actualizar(LEAD_ID, USUARIO_ID, request);

        verify(leadRepository).save(any(Lead.class));
        verify(leadAuditLogRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("R2b: Error de validación de lead aborta la operación y no registra auditoría")
    void actualizar_fallaValidacion_abortaSinAuditoria() {
        UpdateLeadRequest request = new UpdateLeadRequest();
        request.setNombre("Carlos");
        request.setTelefono(null);
        request.setCorreo(null); // Inválido: sin teléfono ni correo

        assertThatThrownBy(() -> leadService.actualizar(LEAD_ID, USUARIO_ID, request))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(leadRepository, leadAuditLogRepository);
    }

    @Test
    @DisplayName("R2b: Lead no encontrado o de otro propietario aborta sin registrar auditoría")
    void actualizar_leadNoEncontrado_abortaSinAuditoria() {
        when(leadRepository.findByIdAndUsuarioId(LEAD_ID, OTRO_USUARIO_ID)).thenReturn(Optional.empty());

        UpdateLeadRequest request = new UpdateLeadRequest();
        request.setNombre("Carlos");
        request.setTelefono("999111222");
        request.setEtapa("NUEVO");

        assertThatThrownBy(() -> leadService.actualizar(LEAD_ID, OTRO_USUARIO_ID, request))
                .isInstanceOf(LeadNotFoundException.class);

        verify(leadAuditLogRepository, never()).saveAll(any());
    }

    // =========================================================================
    // R3: ATOMICIDAD TRANSACCIONAL Y REGRESIÓN DE OUTBOX
    // =========================================================================

    @Test
    @DisplayName("R3: Falla en persistencia de auditoría propaga excepción para provocar rollback")
    void actualizar_fallaEnAuditoria_propagaExcepcionParaRollback() {
        Lead leadExistente = new Lead();
        leadExistente.setId(LEAD_ID);
        leadExistente.setNombre("Carlos Perez");
        leadExistente.setTelefono("999111222");
        leadExistente.setEtapa("NUEVO");

        when(leadRepository.findByIdAndUsuarioId(LEAD_ID, USUARIO_ID)).thenReturn(Optional.of(leadExistente));
        when(leadRepository.save(any(Lead.class))).thenAnswer(i -> i.getArgument(0));
        when(leadAuditLogRepository.saveAll(any())).thenThrow(new RuntimeException("Falla simulada de BD en auditoría"));

        UpdateLeadRequest request = new UpdateLeadRequest();
        request.setNombre("Carlos Perez Modificado");
        request.setTelefono("999111222");
        request.setEtapa("NUEVO");

        assertThatThrownBy(() -> leadService.actualizar(LEAD_ID, USUARIO_ID, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Falla simulada de BD en auditoría");

        // El outbox no debe publicarse si la auditoría falló
        verify(outboxService, never()).recordLeadUpdated(any(), any());
    }

    // =========================================================================
    // R4 / R5 / R6: CONSULTA PAGINADA, ORDENAMIENTO, AISLAMIENTO Y PRIVACIDAD
    // =========================================================================

    @Test
    @DisplayName("R4: listarAuditoria retorna PageResponse con tamaño fijo 20 y ordenamiento temporal/id descendente")
    void listarAuditoria_leadPropio_retornaPageResponseOrdenado() {
        Lead leadExistente = new Lead();
        leadExistente.setId(LEAD_ID);
        leadExistente.setUsuario(new Usuario());

        when(leadRepository.findByIdAndUsuarioId(LEAD_ID, USUARIO_ID)).thenReturn(Optional.of(leadExistente));

        OffsetDateTime t1 = OffsetDateTime.parse("2026-10-18T10:00:00Z");
        OffsetDateTime t2 = OffsetDateTime.parse("2026-10-18T11:00:00Z");

        LeadAuditLog log1 = new LeadAuditLog(LEAD_ID, USUARIO_ID, "stage", "NUEVO", "EN_SEGUIMIENTO", t2);
        log1.setId(2L);
        LeadAuditLog log2 = new LeadAuditLog(LEAD_ID, USUARIO_ID, "phone", "111", "222", t1);
        log2.setId(1L);

        Page<LeadAuditLog> pageResult = new PageImpl<>(List.of(log1, log2), PageRequest.of(0, 20), 2);
        when(leadAuditLogRepository.findByLeadIdAndUsuarioId(eq(LEAD_ID), eq(USUARIO_ID), any(Pageable.class)))
                .thenReturn(pageResult);

        PageResponse<LeadAuditResponse> response = leadService.listarAuditoria(LEAD_ID, USUARIO_ID, 0);

        assertThat(response).isNotNull();
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.content()).hasSize(2);

        // Verificación de ordenamiento solicitado al repositorio
        verify(leadAuditLogRepository).findByLeadIdAndUsuarioId(eq(LEAD_ID), eq(USUARIO_ID), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        // Verificación R6: DTO solo contiene campos de auditoría
        LeadAuditResponse item0 = response.content().get(0);
        assertThat(item0.id()).isEqualTo(2L);
        assertThat(item0.leadId()).isEqualTo(LEAD_ID);
        assertThat(item0.field()).isEqualTo("stage");
        assertThat(item0.oldValue()).isEqualTo("NUEVO");
        assertThat(item0.newValue()).isEqualTo("EN_SEGUIMIENTO");
        assertThat(item0.createdAt()).isEqualTo(t2);
    }

    @Test
    @DisplayName("R5: listarAuditoria con lead inexistente lanza LeadNotFoundException")
    void listarAuditoria_leadInexistente_lanzaLeadNotFoundException() {
        when(leadRepository.findByIdAndUsuarioId(999L, USUARIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leadService.listarAuditoria(999L, USUARIO_ID, 0))
                .isInstanceOf(LeadNotFoundException.class)
                .hasMessage("Lead no encontrado");

        verifyNoInteractions(leadAuditLogRepository);
    }

    @Test
    @DisplayName("R5: listarAuditoria con lead de otro propietario lanza LeadNotFoundException idéntica")
    void listarAuditoria_otroPropietario_lanzaLeadNotFoundExceptionIdentica() {
        when(leadRepository.findByIdAndUsuarioId(LEAD_ID, OTRO_USUARIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leadService.listarAuditoria(LEAD_ID, OTRO_USUARIO_ID, 0))
                .isInstanceOf(LeadNotFoundException.class)
                .hasMessage("Lead no encontrado");

        verifyNoInteractions(leadAuditLogRepository);
    }

    @Test
    @DisplayName("R4: listarAuditoria con página negativa normaliza a 0")
    void listarAuditoria_paginaNegativa_normalizaACero() {
        Lead leadExistente = new Lead();
        leadExistente.setId(LEAD_ID);

        when(leadRepository.findByIdAndUsuarioId(LEAD_ID, USUARIO_ID)).thenReturn(Optional.of(leadExistente));
        when(leadAuditLogRepository.findByLeadIdAndUsuarioId(eq(LEAD_ID), eq(USUARIO_ID), any(Pageable.class)))
                .thenReturn(Page.empty());

        leadService.listarAuditoria(LEAD_ID, USUARIO_ID, -5);

        verify(leadAuditLogRepository).findByLeadIdAndUsuarioId(eq(LEAD_ID), eq(USUARIO_ID), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
    }
}
