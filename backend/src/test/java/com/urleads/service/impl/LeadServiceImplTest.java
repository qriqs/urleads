package com.urleads.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;

import org.hibernate.exception.ConstraintViolationException;

import com.urleads.dto.CreateLeadRequest;
import com.urleads.dto.InternalLeadResponse;
import com.urleads.dto.LeadAuditResponse;
import com.urleads.dto.PageResponse;
import com.urleads.dto.LeadResponse;
import com.urleads.dto.UpdateLeadRequest;
import com.urleads.entity.Lead;
import com.urleads.entity.LeadAuditLog;
import com.urleads.entity.Usuario;
import com.urleads.exception.LeadNotFoundException;
import com.urleads.exception.LeadValidationException;
import com.urleads.repository.LeadAuditLogRepository;
import com.urleads.repository.LeadRepository;
import com.urleads.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class LeadServiceImplTest {

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private LeadAuditLogRepository leadAuditLogRepository;

    private LeadServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LeadServiceImpl(leadRepository, usuarioRepository, leadAuditLogRepository);
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
        LeadServiceImpl serviceConReloj = new LeadServiceImpl(leadRepository, usuarioRepository, leadAuditLogRepository, clockFijo);

        when(leadRepository.buscar(eq(1L), eq(""), eq(""), eq(""), eq("%%"), eq("today"), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        serviceConReloj.listarPaginado(1L, null, "today", null, 0);

        ArgumentCaptor<LocalDate> fechaCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(leadRepository).buscar(eq(1L), eq(""), eq(""), eq(""), eq("%%"), eq("today"), fechaCaptor.capture(), any(Pageable.class));

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
    // TESTS C07: PRIORIDAD Y ORIGEN (DSW-47)
    // =========================================================================

    @Test
    @DisplayName("C07: Crear lead con prioridad y origen omitidos asigna valores por defecto MEDIA y MANUAL")
    void crear_conPrioridadYOrigenOmitidos_asignaValoresPorDefecto() {
        CreateLeadRequest request = new CreateLeadRequest();
        request.setNombre("Lead Sin Prioridad");
        request.setTelefono("999888777");
        request.setEtapa("NUEVO");

        Usuario usuario = new Usuario();
        usuario.setId(1L);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(leadRepository.save(any(Lead.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.crear(1L, request);

        ArgumentCaptor<Lead> captor = ArgumentCaptor.forClass(Lead.class);
        verify(leadRepository).save(captor.capture());
        Lead guardado = captor.getValue();

        assertThat(guardado.getPriority()).isEqualTo("MEDIA");
        assertThat(guardado.getOrigin()).isEqualTo("MANUAL");
        assertThat(response.getPriority()).isEqualTo("MEDIA");
        assertThat(response.getOrigin()).isEqualTo("MANUAL");
    }

    @Test
    @DisplayName("C07: Crear lead con prioridad y origen válidos normaliza a mayúsculas y recorta")
    void crear_conPrioridadYOrigenValidos_normalizaAMayusculasYRecorta() {
        CreateLeadRequest request = new CreateLeadRequest();
        request.setNombre("Lead Alta Prioridad");
        request.setTelefono("999888777");
        request.setEtapa("NUEVO");
        request.setPriority("  alta  ");
        request.setOrigin(" referido ");

        Usuario usuario = new Usuario();
        usuario.setId(1L);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(leadRepository.save(any(Lead.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.crear(1L, request);

        ArgumentCaptor<Lead> captor = ArgumentCaptor.forClass(Lead.class);
        verify(leadRepository).save(captor.capture());
        Lead guardado = captor.getValue();

        assertThat(guardado.getPriority()).isEqualTo("ALTA");
        assertThat(guardado.getOrigin()).isEqualTo("REFERIDO");
        assertThat(response.getPriority()).isEqualTo("ALTA");
        assertThat(response.getOrigin()).isEqualTo("REFERIDO");
    }

    @Test
    @DisplayName("C07: Crear lead con prioridad inválida lanza LeadValidationException")
    void crear_conPrioridadInvalida_lanzaLeadValidationException() {
        CreateLeadRequest request = new CreateLeadRequest();
        request.setNombre("Lead Inválido");
        request.setTelefono("999888777");
        request.setEtapa("NUEVO");
        request.setPriority("URGENTE");

        assertThatThrownBy(() -> service.crear(1L, request))
                .isInstanceOf(LeadValidationException.class)
                .hasMessageContaining("Prioridad no válida");

        verifyNoInteractions(usuarioRepository, leadRepository);
    }

    @Test
    @DisplayName("C07: Crear lead con prioridad en blanco lanza LeadValidationException")
    void crear_conPrioridadEnBlanco_lanzaLeadValidationException() {
        CreateLeadRequest request = new CreateLeadRequest();
        request.setNombre("Lead Inválido");
        request.setTelefono("999888777");
        request.setEtapa("NUEVO");
        request.setPriority("   ");

        assertThatThrownBy(() -> service.crear(1L, request))
                .isInstanceOf(LeadValidationException.class)
                .hasMessageContaining("Prioridad no válida");

        verifyNoInteractions(usuarioRepository, leadRepository);
    }

    @Test
    @DisplayName("C07: Crear lead con prioridad nula explícitamente suministrada lanza LeadValidationException")
    void crear_conPrioridadNulaExplicita_lanzaLeadValidationException() {
        CreateLeadRequest request = new CreateLeadRequest();
        request.setNombre("Lead Inválido");
        request.setTelefono("999888777");
        request.setEtapa("NUEVO");
        request.setPriority(null);

        assertThatThrownBy(() -> service.crear(1L, request))
                .isInstanceOf(LeadValidationException.class)
                .hasMessageContaining("Prioridad no válida: no se permite valor nulo");

        verifyNoInteractions(usuarioRepository, leadRepository);
    }

    @Test
    @DisplayName("C07: Crear lead con origen inválido lanza LeadValidationException")
    void crear_conOrigenInvalido_lanzaLeadValidationException() {
        CreateLeadRequest request = new CreateLeadRequest();
        request.setNombre("Lead Inválido");
        request.setTelefono("999888777");
        request.setEtapa("NUEVO");
        request.setOrigin("PARTNER");

        assertThatThrownBy(() -> service.crear(1L, request))
                .isInstanceOf(LeadValidationException.class)
                .hasMessageContaining("Origen no válido");

        verifyNoInteractions(usuarioRepository, leadRepository);
    }

    @Test
    @DisplayName("C07: Crear lead con origen en blanco lanza LeadValidationException")
    void crear_conOrigenEnBlanco_lanzaLeadValidationException() {
        CreateLeadRequest request = new CreateLeadRequest();
        request.setNombre("Lead Inválido");
        request.setTelefono("999888777");
        request.setEtapa("NUEVO");
        request.setOrigin("   ");

        assertThatThrownBy(() -> service.crear(1L, request))
                .isInstanceOf(LeadValidationException.class)
                .hasMessageContaining("Origen no válido");

        verifyNoInteractions(usuarioRepository, leadRepository);
    }

    @Test
    @DisplayName("C07: Crear lead con origen nulo explícitamente suministrado lanza LeadValidationException")
    void crear_conOrigenNuloExplicito_lanzaLeadValidationException() {
        CreateLeadRequest request = new CreateLeadRequest();
        request.setNombre("Lead Inválido");
        request.setTelefono("999888777");
        request.setEtapa("NUEVO");
        request.setOrigin(null);

        assertThatThrownBy(() -> service.crear(1L, request))
                .isInstanceOf(LeadValidationException.class)
                .hasMessageContaining("Origen no válido: no se permite valor nulo");

        verifyNoInteractions(usuarioRepository, leadRepository);
    }

    @Test
    @DisplayName("C07: Actualizar lead con prioridad y origen válidos actualiza correctamente")
    void actualizar_conPrioridadYOrigenValidos_actualizaCorrectamente() {
        Lead existente = new Lead();
        existente.setId(10L);
        existente.setNombre("Lead Existente");
        existente.setTelefono("999111222");
        existente.setEtapa("NUEVO");
        existente.setPriority("MEDIA");
        existente.setOrigin("MANUAL");

        when(leadRepository.findByIdAndUsuarioId(10L, 1L)).thenReturn(Optional.of(existente));
        when(leadRepository.save(any(Lead.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateLeadRequest request = new UpdateLeadRequest();
        request.setNombre("Lead Existente Modificado");
        request.setTelefono("999111222");
        request.setEtapa("EN_SEGUIMIENTO");
        request.setPriority(" baja ");
        request.setOrigin(" web ");

        var response = service.actualizar(10L, 1L, request);

        assertThat(response.getPriority()).isEqualTo("BAJA");
        assertThat(response.getOrigin()).isEqualTo("WEB");
        assertThat(existente.getPriority()).isEqualTo("BAJA");
        assertThat(existente.getOrigin()).isEqualTo("WEB");
    }

    @Test
    @DisplayName("C07: Actualizar lead omitiendo prioridad y origen asigna valores por defecto MEDIA y MANUAL")
    void actualizar_conPrioridadYOrigenOmitidos_asignaValoresPorDefecto() {
        Lead existente = new Lead();
        existente.setId(10L);
        existente.setNombre("Lead Existente");
        existente.setTelefono("999111222");
        existente.setEtapa("NUEVO");
        existente.setPriority("ALTA");
        existente.setOrigin("WEB");

        when(leadRepository.findByIdAndUsuarioId(10L, 1L)).thenReturn(Optional.of(existente));
        when(leadRepository.save(any(Lead.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateLeadRequest request = new UpdateLeadRequest();
        request.setNombre("Lead Modificado");
        request.setTelefono("999111222");
        request.setEtapa("EN_SEGUIMIENTO");

        var response = service.actualizar(10L, 1L, request);

        assertThat(response.getPriority()).isEqualTo("MEDIA");
        assertThat(response.getOrigin()).isEqualTo("MANUAL");
        assertThat(existente.getPriority()).isEqualTo("MEDIA");
        assertThat(existente.getOrigin()).isEqualTo("MANUAL");
    }

    @Test
    @DisplayName("C07: Listar paginado con filtros de prioridad y origen válidos pasa parámetros normalizados al repositorio")
    void listarPaginado_conFiltrosPrioridadYOrigenValidos_pasaParametrosAlRepositorio() {
        when(leadRepository.buscar(eq(1L), eq(""), eq("ALTA"), eq("WEB"), eq("%%"), eq("all"), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.listarPaginado(1L, null, "all", null, " alta ", " web ", 0);

        verify(leadRepository).buscar(eq(1L), eq(""), eq("ALTA"), eq("WEB"), eq("%%"), eq("all"), any(LocalDate.class), any(Pageable.class));
    }

    @Test
    @DisplayName("C07: Listar paginado con prioridad de filtro inválida lanza LeadValidationException")
    void listarPaginado_conFiltroPrioridadInvalido_lanzaLeadValidationException() {
        assertThatThrownBy(() -> service.listarPaginado(1L, null, "all", null, "INVENTADA", null, 0))
                .isInstanceOf(LeadValidationException.class)
                .hasMessageContaining("Prioridad no válida para filtro");

        verifyNoInteractions(leadRepository);
    }

    @Test
    @DisplayName("C07: Listar paginado con origen de filtro inválido lanza LeadValidationException")
    void listarPaginado_conFiltroOrigenInvalido_lanzaLeadValidationException() {
        assertThatThrownBy(() -> service.listarPaginado(1L, null, "all", null, null, "INVENTADO", 0))
                .isInstanceOf(LeadValidationException.class)
                .hasMessageContaining("Origen no válido para filtro");

        verifyNoInteractions(leadRepository);
    }

    @Test
    @DisplayName("C07: Normalización de prioridad y origen es independiente de la configuración regional (Locale.ROOT bajo Locale turco)")
    void normalizacionPrioridadYOrigen_esIndependienteDeLocaleTurco() {
        Locale defaultPrevio = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            // 1. DTO de creación: "referido" con 'i' minúscula debe normalizar a exacto "REFERIDO" (no dotted-I)
            CreateLeadRequest createReq = new CreateLeadRequest();
            createReq.setNombre("Lead Locale");
            createReq.setTelefono("999111222");
            createReq.setEtapa("NUEVO");
            createReq.setPriority("media");
            createReq.setOrigin("referido");

            assertThat(createReq.getPriority()).isEqualTo("MEDIA");
            assertThat(createReq.getOrigin()).isEqualTo("REFERIDO");

            // Validar que LeadValidator lo acepte bajo el catálogo exacto
            com.urleads.service.LeadValidator.validate(createReq);

            // 2. DTO de actualización
            UpdateLeadRequest updateReq = new UpdateLeadRequest();
            updateReq.setNombre("Lead Locale");
            updateReq.setTelefono("999111222");
            updateReq.setEtapa("NUEVO");
            updateReq.setPriority("baja");
            updateReq.setOrigin("referido");

            assertThat(updateReq.getPriority()).isEqualTo("BAJA");
            assertThat(updateReq.getOrigin()).isEqualTo("REFERIDO");
            com.urleads.service.LeadValidator.validate(updateReq);

            // 3. Normalización de filtros en listarPaginado bajo Locale turco
            when(leadRepository.buscar(eq(1L), eq(""), eq("MEDIA"), eq("REFERIDO"), eq("%%"), eq("all"), any(LocalDate.class), any(Pageable.class)))
                    .thenReturn(Page.empty());

            service.listarPaginado(1L, null, "all", null, "media", "referido", 0);

            verify(leadRepository).buscar(eq(1L), eq(""), eq("MEDIA"), eq("REFERIDO"), eq("%%"), eq("all"), any(LocalDate.class), any(Pageable.class));
        } finally {
            Locale.setDefault(defaultPrevio);
        }
    }

    // TESTS C05: REGRESIÓN DE AISLAMIENTO DE PROPIETARIOS EN CRUD (NON-IT)
    // =========================================================================

    @Test
    @DisplayName("C05: Consulta de lead de otro propietario lanza LeadNotFoundException y no invoca findById global")
    void foreignOwnerLead_obtenerComoOwnerA_fallaConLeadNotFoundYNoConsultaGlobal() {
        Long ownerA = 10L;
        Long ownerB = 20L;
        Long leadId = 500L;

        Usuario userB = new Usuario(ownerB, "userB", "hashB");
        Lead leadDeB = new Lead();
        leadDeB.setId(leadId);
        leadDeB.setUsuario(userB);
        leadDeB.setNombre("Lead Privado de B");
        leadDeB.setEtapa("NUEVO");

        // 1. El propietario legítimo ownerB puede consultar el lead exitosamente
        when(leadRepository.findByIdAndUsuarioId(leadId, ownerB)).thenReturn(Optional.of(leadDeB));
        LeadResponse respB = service.obtener(leadId, ownerB);
        assertThat(respB).isNotNull();
        assertThat(respB.getId()).isEqualTo(leadId);

        // 2. Pero cuando el usuario ajeno ownerA intenta consultarlo, el repositorio acotado retorna vacío
        when(leadRepository.findByIdAndUsuarioId(leadId, ownerA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtener(leadId, ownerA))
                .isInstanceOf(LeadNotFoundException.class)
                .hasMessage("Lead no encontrado");

        verify(leadRepository).findByIdAndUsuarioId(leadId, ownerA);
        verify(leadRepository, never()).findById(any());
    }

    @Test
    @DisplayName("C05: Intento de actualizar lead de otro propietario falla con LeadNotFoundException y no muta el registro de B")
    void foreignOwnerLead_actualizarComoOwnerA_fallaConLeadNotFoundYNoMutaRegistroDeOwnerB() {
        Long ownerA = 10L;
        Long ownerB = 20L;
        Long leadId = 500L;

        Usuario userB = new Usuario(ownerB, "userB", "hashB");
        Lead leadDeB = new Lead();
        leadDeB.setId(leadId);
        leadDeB.setUsuario(userB);
        leadDeB.setNombre("Lead Privado de B");
        leadDeB.setEtapa("NUEVO");

        when(leadRepository.findByIdAndUsuarioId(leadId, ownerA)).thenReturn(Optional.empty());

        UpdateLeadRequest updateRequest = new UpdateLeadRequest();
        updateRequest.setNombre("Nombre Alterado");
        updateRequest.setCorreo("alterado@example.com");
        updateRequest.setEtapa("EN_SEGUIMIENTO");
        updateRequest.setProximoSeguimiento(LocalDate.of(2026, 11, 1));

        assertThatThrownBy(() -> service.actualizar(leadId, ownerA, updateRequest))
                .isInstanceOf(LeadNotFoundException.class)
                .hasMessage("Lead no encontrado");

        verify(leadRepository).findByIdAndUsuarioId(leadId, ownerA);
        verify(leadRepository, never()).save(any(Lead.class));
        verify(leadRepository, never()).findById(any());
    }

    @Test
    @DisplayName("C05: Intento de eliminar lead de otro propietario falla con LeadNotFoundException y no elimina el registro de B")
    void foreignOwnerLead_eliminarComoOwnerA_fallaConLeadNotFoundYNoEliminaRegistroDeOwnerB() {
        Long ownerA = 10L;
        Long ownerB = 20L;
        Long leadId = 500L;

        Usuario userB = new Usuario(ownerB, "userB", "hashB");
        Lead leadDeB = new Lead();
        leadDeB.setId(leadId);
        leadDeB.setUsuario(userB);
        leadDeB.setNombre("Lead Privado de B");
        leadDeB.setEtapa("NUEVO");

        when(leadRepository.findByIdAndUsuarioId(leadId, ownerA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.eliminar(leadId, ownerA))
                .isInstanceOf(LeadNotFoundException.class)
                .hasMessage("Lead no encontrado");

        verify(leadRepository).findByIdAndUsuarioId(leadId, ownerA);
        verify(leadRepository, never()).delete(any(Lead.class));
        verify(leadRepository, never()).findById(any());
    }

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

    // C09 audit service regressions

    @Test
    @DisplayName("C09: listarAuditoria con lead inexistente lanza LeadNotFoundException")
    void listarAuditoria_conLeadInexistente_lanzaLeadNotFoundException() {
        when(leadRepository.findByIdAndUsuarioId(999L, 42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listarAuditoria(999L, 42L, 0))
                .isInstanceOf(LeadNotFoundException.class)
                .hasMessage("Lead no encontrado");

        verify(leadRepository).findByIdAndUsuarioId(999L, 42L);
    }

    @Test
    @DisplayName("C09: listarAuditoria con otro propietario lanza LeadNotFoundException sin filtrar existencia")
    void listarAuditoria_conOtroPropietario_lanzaLeadNotFoundException() {
        when(leadRepository.findByIdAndUsuarioId(105L, 88L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listarAuditoria(105L, 88L, 0))
                .isInstanceOf(LeadNotFoundException.class)
                .hasMessage("Lead no encontrado");

        verify(leadRepository).findByIdAndUsuarioId(105L, 88L);
    }

    @Test
    @DisplayName("C09: construccion de LeadServiceImpl requiere obligatoriamente LeadAuditLogRepository")
    void construccion_sinRepositorioAuditoria_lanzaNullPointerException() {
        assertThatThrownBy(() -> new LeadServiceImpl(leadRepository, usuarioRepository, (LeadAuditLogRepository) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("leadAuditLogRepository must not be null");
    }

    @Test
    @DisplayName("C09: listarAuditoria con lead propio delega directamente en LeadAuditLogRepository")
    void listarAuditoria_conLeadPropio_delegaEnRepositorioYRetornaPageResponse() {
        Lead lead = new Lead();
        lead.setId(105L);
        when(leadRepository.findByIdAndUsuarioId(105L, 42L)).thenReturn(Optional.of(lead));

        LeadAuditLog log = new LeadAuditLog(105L, 42L, "stage", "NUEVO", "CERRADO", OffsetDateTime.now());
        log.setId(1L);
        when(leadAuditLogRepository.findByLeadIdAndUsuarioId(eq(105L), eq(42L), any(Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(log), org.springframework.data.domain.PageRequest.of(0, 20), 1));

        PageResponse<LeadAuditResponse> res = service.listarAuditoria(105L, 42L, 0);

        assertThat(res).isNotNull();
        assertThat(res.totalElements()).isEqualTo(1);
        assertThat(res.content()).hasSize(1);
        assertThat(res.content().get(0).field()).isEqualTo("stage");
        verify(leadAuditLogRepository).findByLeadIdAndUsuarioId(eq(105L), eq(42L), any(Pageable.class));
    }

    @Test
    @DisplayName("C09: actualizar con campos modificados siempre persiste deltas en LeadAuditLogRepository")
    void actualizar_conCamposModificados_persisteDeltasObligatoriamente() {
        Lead lead = new Lead();
        lead.setId(10L);
        lead.setNombre("Carlos");
        lead.setTelefono("999111222");
        lead.setEtapa("NUEVO");

        when(leadRepository.findByIdAndUsuarioId(10L, 42L)).thenReturn(Optional.of(lead));
        when(leadRepository.save(any(Lead.class))).thenAnswer(i -> i.getArgument(0));

        UpdateLeadRequest request = new UpdateLeadRequest();
        request.setNombre("Carlos Nuevo");
        request.setTelefono("999111222");
        request.setEtapa("NUEVO");

        var response = service.actualizar(10L, 42L, request);

        assertThat(response.getNombre()).isEqualTo("Carlos Nuevo");
        verify(leadRepository).save(any(Lead.class));
        verify(leadAuditLogRepository).saveAll(any());
    }

    // =========================================================================
    // DSW-52 / C12: Pruebas unitarias de archivo y restauración lógica
    // =========================================================================

    @Test
    @DisplayName("C12: archivar lead activo propio asigna timestamp UTC y persiste sin invocar outbox ni delete")
    void archivar_leadActivoPropio_asignaTimestampYPersiste() {
        Lead lead = new Lead();
        lead.setId(10L);
        lead.setNombre("Lead Activo");
        lead.setTelefono("999111222");
        lead.setEtapa("NUEVO");
        lead.setArchivedAt(null);

        when(leadRepository.findByIdAndUsuarioId(10L, 1L)).thenReturn(Optional.of(lead));
        when(leadRepository.save(any(Lead.class))).thenAnswer(i -> i.getArgument(0));

        LeadResponse response = service.archivar(10L, 1L);

        assertThat(response).isNotNull();
        assertThat(response.getArchivedAt()).isNotNull();
        ArgumentCaptor<Lead> captor = ArgumentCaptor.forClass(Lead.class);
        verify(leadRepository).save(captor.capture());
        assertThat(captor.getValue().getArchivedAt()).isNotNull();
        verify(leadRepository, never()).delete(any());
    }

    @Test
    @DisplayName("C12: archivar lead inexistente lanza LeadNotFoundException")
    void archivar_leadInexistente_lanzaLeadNotFound() {
        when(leadRepository.findByIdAndUsuarioId(999L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.archivar(999L, 1L))
                .isInstanceOf(LeadNotFoundException.class)
                .hasMessage("Lead no encontrado");

        verify(leadRepository, never()).save(any());
    }

    @Test
    @DisplayName("C12: archivar lead ajeno lanza LeadNotFoundException")
    void archivar_leadAjeno_lanzaLeadNotFound() {
        when(leadRepository.findByIdAndUsuarioId(10L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.archivar(10L, 2L))
                .isInstanceOf(LeadNotFoundException.class)
                .hasMessage("Lead no encontrado");

        verify(leadRepository, never()).save(any());
    }

    @Test
    @DisplayName("C12: archivar lead ya archivado lanza LeadNotFoundException (404 preferido)")
    void archivar_leadYaArchivado_lanzaLeadNotFound() {
        Lead yaArchivado = new Lead();
        yaArchivado.setId(10L);
        yaArchivado.setNombre("Ya Archivado");
        yaArchivado.setTelefono("999111222");
        yaArchivado.setEtapa("NUEVO");
        yaArchivado.setArchivedAt(OffsetDateTime.parse("2026-10-18T10:00:00Z"));

        when(leadRepository.findByIdAndUsuarioId(10L, 1L)).thenReturn(Optional.of(yaArchivado));

        assertThatThrownBy(() -> service.archivar(10L, 1L))
                .isInstanceOf(LeadNotFoundException.class)
                .hasMessage("Lead no encontrado");

        verify(leadRepository, never()).save(any());
    }

    @Test
    @DisplayName("C12: restaurar lead archivado propio limpia timestamp archived_at y persiste")
    void restaurar_leadArchivadoPropio_limpiaTimestampYPersiste() {
        Lead leadArchivado = new Lead();
        leadArchivado.setId(10L);
        leadArchivado.setNombre("Lead Archivado");
        leadArchivado.setTelefono("999111222");
        leadArchivado.setEtapa("NUEVO");
        leadArchivado.setArchivedAt(OffsetDateTime.parse("2026-10-18T10:00:00Z"));

        when(leadRepository.findByIdAndUsuarioIdAndArchivedAtIsNotNull(10L, 1L)).thenReturn(Optional.of(leadArchivado));
        when(leadRepository.save(any(Lead.class))).thenAnswer(i -> i.getArgument(0));

        LeadResponse response = service.restaurar(10L, 1L);

        assertThat(response).isNotNull();
        assertThat(response.getArchivedAt()).isNull();
        ArgumentCaptor<Lead> captor = ArgumentCaptor.forClass(Lead.class);
        verify(leadRepository).save(captor.capture());
        assertThat(captor.getValue().getArchivedAt()).isNull();
        verify(leadRepository, never()).delete(any());
    }

    @Test
    @DisplayName("C12: restaurar lead inexistente lanza LeadNotFoundException")
    void restaurar_leadInexistente_lanzaLeadNotFound() {
        when(leadRepository.findByIdAndUsuarioIdAndArchivedAtIsNotNull(999L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.restaurar(999L, 1L))
                .isInstanceOf(LeadNotFoundException.class)
                .hasMessage("Lead no encontrado");

        verify(leadRepository, never()).save(any());
    }

    @Test
    @DisplayName("C12: restaurar lead ajeno lanza LeadNotFoundException")
    void restaurar_leadAjeno_lanzaLeadNotFound() {
        when(leadRepository.findByIdAndUsuarioIdAndArchivedAtIsNotNull(10L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.restaurar(10L, 2L))
                .isInstanceOf(LeadNotFoundException.class)
                .hasMessage("Lead no encontrado");

        verify(leadRepository, never()).save(any());
    }

    @Test
    @DisplayName("C12: restaurar lead activo (no archivado) lanza LeadNotFoundException")
    void restaurar_leadActivo_lanzaLeadNotFound() {
        when(leadRepository.findByIdAndUsuarioIdAndArchivedAtIsNotNull(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.restaurar(10L, 1L))
                .isInstanceOf(LeadNotFoundException.class)
                .hasMessage("Lead no encontrado");

        verify(leadRepository, never()).save(any());
    }

    @Test
    @DisplayName("C12: listarArchivados solicita orden estable createdAt DESC, id DESC")
    void listarArchivados_devuelveSoloArchivadosPaginadosConOrdenCreatedAtIdDesc() {
        Lead lead = new Lead();
        lead.setId(10L);
        lead.setNombre("Archivado");
        lead.setTelefono("999111222");
        lead.setEtapa("NUEVO");
        lead.setArchivedAt(OffsetDateTime.parse("2026-10-18T10:00:00Z"));

        org.springframework.data.domain.Page<Lead> page = new org.springframework.data.domain.PageImpl<>(
                List.of(lead),
                org.springframework.data.domain.PageRequest.of(0, 20),
                1
        );

        when(leadRepository.findArchivedByUsuarioId(eq(1L), any(org.springframework.data.domain.PageRequest.class)))
                .thenReturn(page);
        when(leadRepository.findAllWithTagsByIdIn(eq(List.of(10L)), eq(1L)))
                .thenReturn(List.of(lead));

        PageResponse<LeadResponse> response = service.listarArchivados(1L, 0);

        assertThat(response).isNotNull();
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).getArchivedAt()).isEqualTo(OffsetDateTime.parse("2026-10-18T10:00:00Z"));

        ArgumentCaptor<org.springframework.data.domain.PageRequest> captor =
                ArgumentCaptor.forClass(org.springframework.data.domain.PageRequest.class);
        verify(leadRepository).findArchivedByUsuarioId(eq(1L), captor.capture());
        org.springframework.data.domain.PageRequest pageRequest = captor.getValue();
        assertThat(pageRequest.getSort().getOrderFor("creadoEn")).isNotNull();
        assertThat(pageRequest.getSort().getOrderFor("creadoEn").getDirection()).isEqualTo(org.springframework.data.domain.Sort.Direction.DESC);
        assertThat(pageRequest.getSort().getOrderFor("id")).isNotNull();
        assertThat(pageRequest.getSort().getOrderFor("id").getDirection()).isEqualTo(org.springframework.data.domain.Sort.Direction.DESC);
    }

    @Test
    @DisplayName("C12: obtener lead archivado devuelve 404 LeadNotFoundException")
    void obtener_leadArchivado_lanzaLeadNotFound() {
        Lead archivado = new Lead();
        archivado.setId(10L);
        archivado.setArchivedAt(OffsetDateTime.now());

        when(leadRepository.findByIdAndUsuarioId(10L, 1L)).thenReturn(Optional.of(archivado));

        assertThatThrownBy(() -> service.obtener(10L, 1L))
                .isInstanceOf(LeadNotFoundException.class)
                .hasMessage("Lead no encontrado");
    }

    @Test
    @DisplayName("C12: obtenerInterno lead archivado devuelve 404 LeadNotFoundException")
    void obtenerInterno_leadArchivado_lanzaLeadNotFound() {
        Lead archivado = new Lead();
        archivado.setId(10L);
        archivado.setArchivedAt(OffsetDateTime.now());

        when(leadRepository.findByIdAndUsuarioId(10L, 1L)).thenReturn(Optional.of(archivado));

        assertThatThrownBy(() -> service.obtenerInterno(10L, 1L))
                .isInstanceOf(LeadNotFoundException.class)
                .hasMessage("Lead no encontrado");
    }

    @Test
    @DisplayName("C12: actualizar lead archivado devuelve 404 LeadNotFoundException")
    void actualizar_leadArchivado_lanzaLeadNotFound() {
        Lead archivado = new Lead();
        archivado.setId(10L);
        archivado.setArchivedAt(OffsetDateTime.now());

        when(leadRepository.findByIdAndUsuarioId(10L, 1L)).thenReturn(Optional.of(archivado));

        UpdateLeadRequest request = new UpdateLeadRequest();
        request.setNombre("Nombre");
        request.setTelefono("999111222");
        request.setEtapa("NUEVO");

        assertThatThrownBy(() -> service.actualizar(10L, 1L, request))
                .isInstanceOf(LeadNotFoundException.class)
                .hasMessage("Lead no encontrado");

        verify(leadRepository, never()).save(any());
    }

    @Test
    @DisplayName("C12: listarAuditoria lead archivado devuelve 404 LeadNotFoundException")
    void listarAuditoria_leadArchivado_lanzaLeadNotFound() {
        Lead archivado = new Lead();
        archivado.setId(10L);
        archivado.setArchivedAt(OffsetDateTime.now());

        when(leadRepository.findByIdAndUsuarioId(10L, 1L)).thenReturn(Optional.of(archivado));

        assertThatThrownBy(() -> service.listarAuditoria(10L, 1L, 0))
                .isInstanceOf(LeadNotFoundException.class)
                .hasMessage("Lead no encontrado");
    }

    @Test
    @DisplayName("C12: actualizarTags lead archivado devuelve 404 LeadNotFoundException")
    void actualizarTags_leadArchivado_lanzaLeadNotFound() {
        Lead archivado = new Lead();
        archivado.setId(10L);
        archivado.setArchivedAt(OffsetDateTime.now());

        when(leadRepository.findByIdAndUsuarioId(10L, 1L)).thenReturn(Optional.of(archivado));

        assertThatThrownBy(() -> service.actualizarTags(10L, 1L, List.of(1L)))
                .isInstanceOf(LeadNotFoundException.class)
                .hasMessage("Lead no encontrado");

        verify(leadRepository, never()).save(any());
    }

    @Test
    @DisplayName("C12: eliminar lead archivado devuelve 404 LeadNotFoundException")
    void eliminar_leadArchivado_lanzaLeadNotFound() {
        Lead archivado = new Lead();
        archivado.setId(10L);
        archivado.setArchivedAt(OffsetDateTime.now());

        when(leadRepository.findByIdAndUsuarioId(10L, 1L)).thenReturn(Optional.of(archivado));

        assertThatThrownBy(() -> service.eliminar(10L, 1L))
                .isInstanceOf(LeadNotFoundException.class)
                .hasMessage("Lead no encontrado");

        verify(leadRepository, never()).delete(any());
    }
}
