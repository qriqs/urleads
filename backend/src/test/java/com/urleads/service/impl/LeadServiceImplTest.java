package com.urleads.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
}