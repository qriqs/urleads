package com.urleads.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.urleads.dto.CreateLeadRequest;
import com.urleads.dto.InternalLeadResponse;
import com.urleads.dto.UpdateLeadRequest;
import com.urleads.entity.Lead;
import com.urleads.entity.Usuario;
import com.urleads.exception.LeadNotFoundException;
import com.urleads.exception.LeadValidationException;
import com.urleads.repository.LeadRepository;
import com.urleads.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class LeadServiceImplTest {

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    private LeadServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LeadServiceImpl(leadRepository, usuarioRepository);
    }

    @Test
    void crear_sinTelefonoNiCorreo_lanzaIllegalArgument() {
        CreateLeadRequest request = new CreateLeadRequest();
        request.setNombre("Ana");
        request.setEtapa("NUEVO");

        assertThatThrownBy(() -> service.crear(1L, request))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(leadRepository, usuarioRepository);
    }

    @Test
    void crear_conContactosEnBlanco_lanzaIllegalArgument() {
        CreateLeadRequest request = new CreateLeadRequest();
        request.setNombre("Ana");
        request.setEtapa("NUEVO");
        request.setTelefono("   ");
        request.setCorreo("  ");

        assertThatThrownBy(() -> service.crear(1L, request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void actualizar_sinTelefonoNiCorreo_lanzaIllegalArgument() {
        UpdateLeadRequest request = new UpdateLeadRequest();
        request.setNombre("Ana");
        request.setEtapa("NUEVO");

        assertThatThrownBy(() -> service.actualizar(10L, 1L, request))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(leadRepository);
    }

    // =========================================================================
    // TESTS UL-17: REGLAS DE ETAPAS, SEGUIMIENTO Y FECHAS
    // =========================================================================

    @Test
    @DisplayName("UL-17: Crear lead como CERRADO con fecha debe limpiar automáticamente la fecha a null")
    void crear_conEtapaCerradaYFecha_limpiaFechaANull() {
        CreateLeadRequest request = new CreateLeadRequest();
        request.setNombre("Lead Cerrado");
        request.setTelefono("999888777");
        request.setEtapa("CERRADO");
        request.setProximoSeguimiento(LocalDate.of(2026, 11, 15));

        Usuario usuario = new Usuario();
        usuario.setId(1L);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(leadRepository.save(any(Lead.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.crear(1L, request);

        ArgumentCaptor<Lead> captor = ArgumentCaptor.forClass(Lead.class);
        verify(leadRepository).save(captor.capture());

        Lead guardado = captor.getValue();
        assertThat(guardado.getEtapa()).isEqualTo("CERRADO");
        assertThat(guardado.getProximoSeguimiento()).isNull();
    }

    @Test
    @DisplayName("UL-17: Actualizar lead a CERRADO con fecha debe limpiar automáticamente la fecha a null")
    void actualizar_conEtapaCerradaYFecha_limpiaFechaANull() {
        Lead existente = new Lead();
        existente.setId(10L);
        existente.setNombre("Lead Anterior");
        existente.setTelefono("999888777");
        existente.setEtapa("EN_SEGUIMIENTO");
        existente.setProximoSeguimiento(LocalDate.of(2026, 10, 20));

        when(leadRepository.findByIdAndUsuarioId(10L, 1L)).thenReturn(Optional.of(existente));
        when(leadRepository.save(any(Lead.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateLeadRequest request = new UpdateLeadRequest();
        request.setNombre("Lead Anterior");
        request.setTelefono("999888777");
        request.setEtapa("CERRADO");
        request.setProximoSeguimiento(LocalDate.of(2026, 12, 1));

        service.actualizar(10L, 1L, request);

        ArgumentCaptor<Lead> captor = ArgumentCaptor.forClass(Lead.class);
        verify(leadRepository).save(captor.capture());

        Lead actualizado = captor.getValue();
        assertThat(actualizado.getEtapa()).isEqualTo("CERRADO");
        assertThat(actualizado.getProximoSeguimiento()).isNull();
    }

    @Test
    @DisplayName("UL-17: Reabrir un lead cerrado a EN_SEGUIMIENTO o NUEVO permite programar una nueva fecha")
    void actualizar_reabrirLeadCerradoConFecha_persisteNuevaEtapaYFecha() {
        Lead cerrado = new Lead();
        cerrado.setId(15L);
        cerrado.setNombre("Lead Reabierto");
        cerrado.setCorreo("reabierto@mail.com");
        cerrado.setEtapa("CERRADO");
        cerrado.setProximoSeguimiento(null);

        when(leadRepository.findByIdAndUsuarioId(15L, 1L)).thenReturn(Optional.of(cerrado));
        when(leadRepository.save(any(Lead.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateLeadRequest request = new UpdateLeadRequest();
        request.setNombre("Lead Reabierto");
        request.setCorreo("reabierto@mail.com");
        request.setEtapa("EN_SEGUIMIENTO");
        request.setProximoSeguimiento(LocalDate.of(2026, 11, 25));

        service.actualizar(15L, 1L, request);

        ArgumentCaptor<Lead> captor = ArgumentCaptor.forClass(Lead.class);
        verify(leadRepository).save(captor.capture());

        Lead actualizado = captor.getValue();
        assertThat(actualizado.getEtapa()).isEqualTo("EN_SEGUIMIENTO");
        assertThat(actualizado.getProximoSeguimiento()).isEqualTo(LocalDate.of(2026, 11, 25));
    }

    @Test
    @DisplayName("UL-17: Crear con etapa inválida lanza LeadValidationException")
    void crear_conEtapaInvalida_lanzaLeadValidationException() {
        CreateLeadRequest request = new CreateLeadRequest();
        request.setNombre("Carlos");
        request.setTelefono("999111222");
        request.setEtapa("INVALIDA");

        assertThatThrownBy(() -> service.crear(1L, request))
                .isInstanceOf(LeadValidationException.class)
                .hasMessageContaining("Etapa no válida");

        verifyNoInteractions(usuarioRepository, leadRepository);
    }

    @Test
    @DisplayName("UL-17: Actualizar con etapa inválida lanza LeadValidationException")
    void actualizar_conEtapaInvalida_lanzaLeadValidationException() {
        UpdateLeadRequest request = new UpdateLeadRequest();
        request.setNombre("Carlos");
        request.setTelefono("999111222");
        request.setEtapa("OTRA_ETAPA");

        assertThatThrownBy(() -> service.actualizar(10L, 1L, request))
                .isInstanceOf(LeadValidationException.class)
                .hasMessageContaining("Etapa no válida");

        verifyNoInteractions(leadRepository);
    }

    @Test
    @DisplayName("UL-17: Filtrar con followUp inválido lanza LeadValidationException")
    void listarPaginado_conFiltroFollowUpInvalido_lanzaLeadValidationException() {
        assertThatThrownBy(() -> service.listarPaginado(1L, null, "invalido", null, 0))
                .isInstanceOf(LeadValidationException.class)
                .hasMessageContaining("Filtro followUp no válido");

        verifyNoInteractions(leadRepository);
    }

    @Test
    @DisplayName("UL-17: Filtrar con stage inválido lanza LeadValidationException")
    void listarPaginado_conFiltroStageInvalido_lanzaLeadValidationException() {
        assertThatThrownBy(() -> service.listarPaginado(1L, null, "all", "ETAPA_DESCONOCIDA", 0))
                .isInstanceOf(LeadValidationException.class)
                .hasMessageContaining("Etapa no válida para filtro");

        verifyNoInteractions(leadRepository);
    }

    @Test
    @DisplayName("UL-17: Clasificación de fechas evalúa estrictamente America/Lima (borde UTC vs Lima)")
    void listarPaginado_casoBordeLimaVsUtc_clasificaSegunLima() {
        // En instante 2026-10-12T02:00:00Z:
        // En UTC la fecha es 2026-10-12.
        // En Lima (UTC-5) son las 21:00 del 2026-10-11.
        Clock clockFijo = Clock.fixed(Instant.parse("2026-10-12T02:00:00Z"), ZoneOffset.UTC);
        LeadServiceImpl serviceConReloj = new LeadServiceImpl(leadRepository, usuarioRepository, clockFijo);

        when(leadRepository.buscar(eq(1L), eq(""), eq("%%"), eq("today"), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        serviceConReloj.listarPaginado(1L, null, "today", null, 0);

        ArgumentCaptor<LocalDate> fechaCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(leadRepository).buscar(eq(1L), eq(""), eq("%%"), eq("today"), fechaCaptor.capture(), any(Pageable.class));

        // Debe ser 2026-10-11 (Lima), NO 2026-10-12 (UTC)
        assertThat(fechaCaptor.getValue()).isEqualTo(LocalDate.of(2026, 10, 11));
    }

    // =========================================================================
    // TESTS C03: CONSULTA INTERNA DE LEADS (obtenerInterno)
    // =========================================================================

    @Test
    @DisplayName("C03: obtenerInterno con lead existente y propietario correcto retorna DTO mínimo sin PII")
    void obtenerInterno_conLeadExistenteYPropietarioCorrecto_retornaDtoMinimoSinPii() {
        Lead lead = new Lead();
        lead.setId(105L);
        lead.setNombre("Lead Privado Con PII");
        lead.setTelefono("+51999888777");
        lead.setCorreo("privado@example.com");
        lead.setService("Plan Premium");
        lead.setEtapa("EN_SEGUIMIENTO");
        lead.setProximoSeguimiento(LocalDate.of(2026, 10, 25));

        when(leadRepository.findByIdAndUsuarioId(105L, 42L)).thenReturn(Optional.of(lead));

        InternalLeadResponse response = service.obtenerInterno(105L, 42L);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(105L);
        assertThat(response.stage()).isEqualTo("EN_SEGUIMIENTO");
        assertThat(response.nextFollowUp()).isEqualTo(LocalDate.of(2026, 10, 25));
        verify(leadRepository).findByIdAndUsuarioId(105L, 42L);
    }

    @Test
    @DisplayName("C03: obtenerInterno con lead inexistente lanza LeadNotFoundException")
    void obtenerInterno_conLeadInexistente_lanzaLeadNotFoundException() {
        when(leadRepository.findByIdAndUsuarioId(999L, 42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenerInterno(999L, 42L))
                .isInstanceOf(LeadNotFoundException.class)
                .hasMessage("Lead no encontrado");

        verify(leadRepository).findByIdAndUsuarioId(999L, 42L);
    }

    @Test
    @DisplayName("C03: obtenerInterno con otro propietario lanza LeadNotFoundException sin filtrar existencia")
    void obtenerInterno_conOtroPropietario_lanzaLeadNotFoundException() {
        when(leadRepository.findByIdAndUsuarioId(105L, 88L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenerInterno(105L, 88L))
                .isInstanceOf(LeadNotFoundException.class)
                .hasMessage("Lead no encontrado");

        verify(leadRepository).findByIdAndUsuarioId(105L, 88L);
    }

    @Test
    @DisplayName("C03: obtenerInterno con lead CERRADO retorna DTO con nextFollowUp null")
    void obtenerInterno_conLeadCerradoSinSeguimiento_retornaDtoConProximoSeguimientoNull() {
        Lead leadCerrado = new Lead();
        leadCerrado.setId(200L);
        leadCerrado.setNombre("Lead Cerrado");
        leadCerrado.setEtapa("CERRADO");
        leadCerrado.setProximoSeguimiento(null);

        when(leadRepository.findByIdAndUsuarioId(200L, 42L)).thenReturn(Optional.of(leadCerrado));

        InternalLeadResponse response = service.obtenerInterno(200L, 42L);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(200L);
        assertThat(response.stage()).isEqualTo("CERRADO");
        assertThat(response.nextFollowUp()).isNull();
        verify(leadRepository).findByIdAndUsuarioId(200L, 42L);
    }

    // =========================================================================
    // TESTS C06: DETECCIÓN DE DUPLICADOS POR PROPIETARIO (DSW-46)
    // =========================================================================

    @Test
    @DisplayName("C06: Crear lead rechaza correo duplicado del mismo propietario con LeadValidationException")
    void crear_conCorreoDuplicadoMismoPropietario_lanzaLeadValidationException() {
        CreateLeadRequest request = new CreateLeadRequest();
        request.setNombre("Ana Repetida");
        request.setCorreo("  Ana.Torres@Example.com  ");
        request.setEtapa("NUEVO");

        when(leadRepository.existsByUsuarioIdAndCorreoNorm(1L, "ana.torres@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.crear(1L, request))
                .isInstanceOf(LeadValidationException.class)
                .hasMessage("Ya existe un lead con este correo");

        verify(leadRepository, never()).save(any());
    }

    @Test
    @DisplayName("C06: Crear lead rechaza teléfono duplicado del mismo propietario con LeadValidationException")
    void crear_conTelefonoDuplicadoMismoPropietario_lanzaLeadValidationException() {
        CreateLeadRequest request = new CreateLeadRequest();
        request.setNombre("Beto Repetido");
        request.setTelefono("+51 (999) 111-222");
        request.setEtapa("NUEVO");

        when(leadRepository.existsByUsuarioIdAndTelefonoNorm(1L, "51999111222")).thenReturn(true);

        assertThatThrownBy(() -> service.crear(1L, request))
                .isInstanceOf(LeadValidationException.class)
                .hasMessage("Ya existe un lead con este teléfono");

        verify(leadRepository, never()).save(any());
    }

    @Test
    @DisplayName("C06: Crear lead permite mismo correo en propietarios distintos")
    void crear_conMismoCorreoEnDistintoPropietario_sePermiteYPersiste() {
        CreateLeadRequest request = new CreateLeadRequest();
        request.setNombre("Carlos Otro Dueño");
        request.setCorreo("compartido@example.com");
        request.setEtapa("NUEVO");

        Usuario usuario = new Usuario();
        usuario.setId(2L);

        when(leadRepository.existsByUsuarioIdAndCorreoNorm(2L, "compartido@example.com")).thenReturn(false);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(usuario));
        when(leadRepository.save(any(Lead.class))).thenAnswer(i -> i.getArgument(0));

        service.crear(2L, request);

        verify(leadRepository).save(any(Lead.class));
    }

    @Test
    @DisplayName("C06: Crear lead permite mismo teléfono en propietarios distintos")
    void crear_conMismoTelefonoEnDistintoPropietario_sePermiteYPersiste() {
        CreateLeadRequest request = new CreateLeadRequest();
        request.setNombre("Diana Otro Dueño");
        request.setTelefono("999111222");
        request.setEtapa("NUEVO");

        Usuario usuario = new Usuario();
        usuario.setId(3L);

        when(leadRepository.existsByUsuarioIdAndTelefonoNorm(3L, "999111222")).thenReturn(false);
        when(usuarioRepository.findById(3L)).thenReturn(Optional.of(usuario));
        when(leadRepository.save(any(Lead.class))).thenAnswer(i -> i.getArgument(0));

        service.crear(3L, request);

        verify(leadRepository).save(any(Lead.class));
    }

    @Test
    @DisplayName("C06: Crear lead con teléfono sin dígitos y correo único se permite y no compara teléfono nulo")
    void crear_conTelefonoSinDigitosPeroCorreoUnico_sePermiteYPersiste() {
        CreateLeadRequest request = new CreateLeadRequest();
        request.setNombre("Elena Sin Digitos");
        request.setTelefono("---");
        request.setCorreo("elena@example.com");
        request.setEtapa("NUEVO");

        Usuario usuario = new Usuario();
        usuario.setId(1L);

        when(leadRepository.existsByUsuarioIdAndCorreoNorm(1L, "elena@example.com")).thenReturn(false);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(leadRepository.save(any(Lead.class))).thenAnswer(i -> i.getArgument(0));

        service.crear(1L, request);

        verify(leadRepository).existsByUsuarioIdAndCorreoNorm(1L, "elena@example.com");
        verify(leadRepository, never()).existsByUsuarioIdAndTelefonoNorm(any(), any());
        verify(leadRepository).save(any(Lead.class));
    }

    @Test
    @DisplayName("C06: Actualizar lead permite auto-exclusión conservando su propio correo y teléfono")
    void actualizar_conMismoCorreoYTelefonoDelMismoLead_permiteAutoExclusion() {
        Lead existente = new Lead();
        existente.setId(10L);
        existente.setNombre("Original");
        existente.setCorreo("original@mail.com");
        existente.setTelefono("999111222");
        existente.setEtapa("NUEVO");

        when(leadRepository.findByIdAndUsuarioId(10L, 1L)).thenReturn(Optional.of(existente));
        when(leadRepository.existsByUsuarioIdAndCorreoNormAndIdNot(1L, "original@mail.com", 10L)).thenReturn(false);
        when(leadRepository.existsByUsuarioIdAndTelefonoNormAndIdNot(1L, "999111222", 10L)).thenReturn(false);
        when(leadRepository.save(any(Lead.class))).thenAnswer(i -> i.getArgument(0));

        UpdateLeadRequest request = new UpdateLeadRequest();
        request.setNombre("Original Actualizado");
        request.setCorreo("ORIGINAL@mail.com");
        request.setTelefono(" 999-111-222 ");
        request.setEtapa("EN_SEGUIMIENTO");

        service.actualizar(10L, 1L, request);

        verify(leadRepository).save(any(Lead.class));
    }

    @Test
    @DisplayName("C06: Actualizar lead rechaza correo que ya pertenece a otro lead del mismo propietario")
    void actualizar_conCorreoDeOtroLeadDelMismoPropietario_lanzaLeadValidationException() {
        Lead existente = new Lead();
        existente.setId(10L);
        existente.setNombre("Lead Diez");
        existente.setCorreo("diez@mail.com");
        existente.setEtapa("NUEVO");

        when(leadRepository.findByIdAndUsuarioId(10L, 1L)).thenReturn(Optional.of(existente));
        when(leadRepository.existsByUsuarioIdAndCorreoNormAndIdNot(1L, "veinte@mail.com", 10L)).thenReturn(true);

        UpdateLeadRequest request = new UpdateLeadRequest();
        request.setNombre("Lead Diez Modificado");
        request.setCorreo("veinte@mail.com");
        request.setEtapa("NUEVO");

        assertThatThrownBy(() -> service.actualizar(10L, 1L, request))
                .isInstanceOf(LeadValidationException.class)
                .hasMessage("Ya existe un lead con este correo");

        verify(leadRepository, never()).save(any());
    }

    @Test
    @DisplayName("C06: Actualizar lead rechaza teléfono que ya pertenece a otro lead del mismo propietario")
    void actualizar_conTelefonoDeOtroLeadDelMismoPropietario_lanzaLeadValidationException() {
        Lead existente = new Lead();
        existente.setId(10L);
        existente.setNombre("Lead Diez");
        existente.setTelefono("999111222");
        existente.setEtapa("NUEVO");

        when(leadRepository.findByIdAndUsuarioId(10L, 1L)).thenReturn(Optional.of(existente));
        when(leadRepository.existsByUsuarioIdAndTelefonoNormAndIdNot(1L, "888222333", 10L)).thenReturn(true);

        UpdateLeadRequest request = new UpdateLeadRequest();
        request.setNombre("Lead Diez Modificado");
        request.setTelefono("(888) 222-333");
        request.setEtapa("NUEVO");

        assertThatThrownBy(() -> service.actualizar(10L, 1L, request))
                .isInstanceOf(LeadValidationException.class)
                .hasMessage("Ya existe un lead con este teléfono");

        verify(leadRepository, never()).save(any());
    }

    @Test
    @DisplayName("C06: Conflicto de carrera en BD para correo se mapea a LeadValidationException con mensaje específico")
    void crear_conflictoConcurrenteBDCorreo_seMapeaALeadValidationException() {
        CreateLeadRequest request = new CreateLeadRequest();
        request.setNombre("Concurrente Correo");
        request.setCorreo("concurrente@example.com");
        request.setEtapa("NUEVO");

        Usuario usuario = new Usuario();
        usuario.setId(1L);

        ConstraintViolationException cve = new ConstraintViolationException(
                "duplicate key",
                new SQLException("duplicate"),
                "uq_lead_owner_correo_norm"
        );

        when(leadRepository.existsByUsuarioIdAndCorreoNorm(1L, "concurrente@example.com")).thenReturn(false);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(leadRepository.save(any(Lead.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint", cve));

        assertThatThrownBy(() -> service.crear(1L, request))
                .isInstanceOf(LeadValidationException.class)
                .hasMessage("Ya existe un lead con este correo");
    }

    @Test
    @DisplayName("C06: Conflicto de carrera en BD para teléfono se mapea a LeadValidationException con mensaje específico")
    void crear_conflictoConcurrenteBDTelefono_seMapeaALeadValidationException() {
        CreateLeadRequest request = new CreateLeadRequest();
        request.setNombre("Concurrente Tel");
        request.setTelefono("999111222");
        request.setEtapa("NUEVO");

        Usuario usuario = new Usuario();
        usuario.setId(1L);

        ConstraintViolationException cve = new ConstraintViolationException(
                "duplicate key",
                new SQLException("duplicate"),
                "uq_lead_owner_telefono_norm"
        );

        when(leadRepository.existsByUsuarioIdAndTelefonoNorm(1L, "999111222")).thenReturn(false);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(leadRepository.save(any(Lead.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint", cve));

        assertThatThrownBy(() -> service.crear(1L, request))
                .isInstanceOf(LeadValidationException.class)
                .hasMessage("Ya existe un lead con este teléfono");
    }

    @Test
    @DisplayName("C06: Violación de integridad de BD ajena a duplicados (ej. FK) NO se reescribe como duplicado en crear")
    void crear_conflictoBDRestriccionAjena_noSeMapeaAYPropagaExcepcionOriginal() {
        CreateLeadRequest request = new CreateLeadRequest();
        request.setNombre("Fk Fallido");
        request.setCorreo("fk@example.com");
        request.setEtapa("NUEVO");

        Usuario usuario = new Usuario();
        usuario.setId(1L);

        ConstraintViolationException cve = new ConstraintViolationException(
                "fk error",
                new SQLException("fk"),
                "fk_lead_usuario"
        );
        DataIntegrityViolationException originalEx = new DataIntegrityViolationException("FK failure", cve);

        when(leadRepository.existsByUsuarioIdAndCorreoNorm(1L, "fk@example.com")).thenReturn(false);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(leadRepository.save(any(Lead.class))).thenThrow(originalEx);

        assertThatThrownBy(() -> service.crear(1L, request))
                .isSameAs(originalEx)
                .isNotInstanceOf(LeadValidationException.class);
    }

    @Test
    @DisplayName("C06: Conflicto de carrera en BD en actualizar se mapea a LeadValidationException para correo")
    void actualizar_conflictoConcurrenteBDCorreo_seMapeaALeadValidationException() {
        Lead existente = new Lead();
        existente.setId(10L);
        existente.setNombre("Lead Diez");
        existente.setCorreo("diez@mail.com");
        existente.setEtapa("NUEVO");

        ConstraintViolationException cve = new ConstraintViolationException(
                "duplicate key",
                new SQLException("duplicate"),
                "uq_lead_owner_correo_norm"
        );

        when(leadRepository.findByIdAndUsuarioId(10L, 1L)).thenReturn(Optional.of(existente));
        when(leadRepository.existsByUsuarioIdAndCorreoNormAndIdNot(1L, "nuevo@mail.com", 10L)).thenReturn(false);
        when(leadRepository.save(any(Lead.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint", cve));

        UpdateLeadRequest request = new UpdateLeadRequest();
        request.setNombre("Lead Diez Modificado");
        request.setCorreo("nuevo@mail.com");
        request.setEtapa("NUEVO");

        assertThatThrownBy(() -> service.actualizar(10L, 1L, request))
                .isInstanceOf(LeadValidationException.class)
                .hasMessage("Ya existe un lead con este correo");
    }

    @Test
    @DisplayName("C06: Conflicto de carrera en BD en actualizar se mapea a LeadValidationException para teléfono")
    void actualizar_conflictoConcurrenteBDTelefono_seMapeaALeadValidationException() {
        Lead existente = new Lead();
        existente.setId(10L);
        existente.setNombre("Lead Diez");
        existente.setTelefono("999111222");
        existente.setEtapa("NUEVO");

        ConstraintViolationException cve = new ConstraintViolationException(
                "duplicate key",
                new SQLException("duplicate"),
                "uq_lead_owner_telefono_norm"
        );

        when(leadRepository.findByIdAndUsuarioId(10L, 1L)).thenReturn(Optional.of(existente));
        when(leadRepository.existsByUsuarioIdAndTelefonoNormAndIdNot(1L, "888111222", 10L)).thenReturn(false);
        when(leadRepository.save(any(Lead.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint", cve));

        UpdateLeadRequest request = new UpdateLeadRequest();
        request.setNombre("Lead Diez Modificado");
        request.setTelefono("888111222");
        request.setEtapa("NUEVO");

        assertThatThrownBy(() -> service.actualizar(10L, 1L, request))
                .isInstanceOf(LeadValidationException.class)
                .hasMessage("Ya existe un lead con este teléfono");
    }

    @Test
    @DisplayName("C06: Violación de integridad de BD ajena a duplicados (ej. CHECK) NO se reescribe como duplicado en actualizar")
    void actualizar_conflictoBDRestriccionAjena_noSeMapeaAYPropagaExcepcionOriginal() {
        Lead existente = new Lead();
        existente.setId(10L);
        existente.setNombre("Lead Diez");
        existente.setCorreo("diez@mail.com");
        existente.setEtapa("NUEVO");

        ConstraintViolationException cve = new ConstraintViolationException(
                "check error",
                new SQLException("check"),
                "chk_lead_nombre"
        );
        DataIntegrityViolationException originalEx = new DataIntegrityViolationException("Check failure", cve);

        when(leadRepository.findByIdAndUsuarioId(10L, 1L)).thenReturn(Optional.of(existente));
        when(leadRepository.existsByUsuarioIdAndCorreoNormAndIdNot(1L, "diez@mail.com", 10L)).thenReturn(false);
        when(leadRepository.save(any(Lead.class))).thenThrow(originalEx);

        UpdateLeadRequest request = new UpdateLeadRequest();
        request.setNombre("Modificado");
        request.setCorreo("diez@mail.com");
        request.setEtapa("NUEVO");

        assertThatThrownBy(() -> service.actualizar(10L, 1L, request))
                .isSameAs(originalEx)
                .isNotInstanceOf(LeadValidationException.class);
    }
}