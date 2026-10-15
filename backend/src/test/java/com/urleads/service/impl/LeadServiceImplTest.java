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
import com.urleads.dto.UpdateLeadRequest;
import com.urleads.entity.Lead;
import com.urleads.entity.Usuario;
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
}