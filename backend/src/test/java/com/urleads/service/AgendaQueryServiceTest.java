package com.urleads.service;

import com.urleads.dto.AgendaItemResponse;
import com.urleads.entity.AgendaCita;
import com.urleads.entity.AgendaTarea;
import com.urleads.entity.Usuario;
import com.urleads.repository.AgendaCitaRepository;
import com.urleads.repository.AgendaTareaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AgendaQueryServiceTest {

    private AgendaTareaRepository agendaTareaRepository;
    private AgendaCitaRepository agendaCitaRepository;
    private AgendaQueryService agendaQueryService;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        agendaTareaRepository = mock(AgendaTareaRepository.class);
        agendaCitaRepository = mock(AgendaCitaRepository.class);
        agendaQueryService = new AgendaQueryService(agendaTareaRepository, agendaCitaRepository);

        usuario = new Usuario(42L, "alex", "pass");
    }

    @Test
    @DisplayName("Validación de ventana temporal: rango de 31 días o menor es permitido")
    void validarVentanaTemporal_validWindow_passes() {
        LocalDate desde = LocalDate.of(2026, 10, 1);
        LocalDate hasta31 = LocalDate.of(2026, 10, 31); // 30 días de diferencia
        LocalDate hastaExact31 = LocalDate.of(2026, 11, 1); // 31 días de diferencia

        AgendaQueryService.validarVentanaTemporal(desde, hasta31);
        AgendaQueryService.validarVentanaTemporal(desde, hastaExact31);
    }

    @Test
    @DisplayName("Validación de ventana temporal: rango mayor a 31 días es rechazado con error 400")
    void validarVentanaTemporal_windowOver31Days_throwsIllegalArgument() {
        LocalDate desde = LocalDate.of(2026, 10, 1);
        LocalDate hasta32 = LocalDate.of(2026, 11, 2); // 32 días de diferencia

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                AgendaQueryService.validarVentanaTemporal(desde, hasta32));

        assertThat(ex.getMessage()).contains("El rango de consulta no puede superar los 31 días");
    }

    @Test
    @DisplayName("Validación de ventana temporal: fecha hasta anterior a desde es rechazada")
    void validarVentanaTemporal_endBeforeStart_throwsIllegalArgument() {
        LocalDate desde = LocalDate.of(2026, 10, 15);
        LocalDate hasta = LocalDate.of(2026, 10, 10);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                AgendaQueryService.validarVentanaTemporal(desde, hasta));

        assertThat(ex.getMessage()).contains("La fecha 'hasta' no puede ser anterior a la fecha 'desde'");
    }

    @Test
    @DisplayName("Consulta combinada cronológica de tareas y citas en orden ascendente estable")
    void obtenerAgendaCombinada_mergesAndSortsChronologically() {
        // Tarea para el 25 de octubre a las 14:00 (19:00 UTC)
        AgendaTarea t1 = AgendaTarea.builder()
                .id(1L)
                .usuario(usuario)
                .titulo("Tarea de la tarde")
                .fechaLimite(LocalDate.of(2026, 10, 25))
                .horaLimite(LocalTime.of(14, 0))
                .estado("PENDIENTE")
                .build();

        // Cita para el 25 de octubre a las 10:00 (15:00 UTC)
        AgendaCita c1 = AgendaCita.builder()
                .id(10L)
                .usuario(usuario)
                .titulo("Cita de la mañana")
                .inicio(Instant.parse("2026-10-25T15:00:00Z"))
                .fin(Instant.parse("2026-10-25T16:00:00Z"))
                .zonaHoraria("America/Lima")
                .estado("PROGRAMADA")
                .build();

        // Tarea para el 26 de octubre a las 09:00 (14:00 UTC)
        AgendaTarea t2 = AgendaTarea.builder()
                .id(2L)
                .usuario(usuario)
                .titulo("Tarea día siguiente")
                .fechaLimite(LocalDate.of(2026, 10, 26))
                .horaLimite(LocalTime.of(9, 0))
                .estado("PENDIENTE")
                .build();

        when(agendaTareaRepository.findByUsuarioIdAndFechaLimiteBetweenOrderByFechaLimiteAscHoraLimiteAscIdAsc(
                eq(42L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(t1, t2));

        when(agendaCitaRepository.findByUsuarioIdAndInicioBetweenOrderByInicioAscIdAsc(
                eq(42L), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(c1));

        Pageable pageable = PageRequest.of(0, 10);
        Page<AgendaItemResponse> page = agendaQueryService.obtenerAgendaCombinada(
                42L, "2026-10-25", "2026-10-27", null, null, pageable);

        assertThat(page.getTotalElements()).isEqualTo(3);
        List<AgendaItemResponse> items = page.getContent();

        // Orden cronológico esperado:
        // 1. Cita c1 (15:00 UTC / 10:00 Lima)
        // 2. Tarea t1 (19:00 UTC / 14:00 Lima)
        // 3. Tarea t2 (26 Oct 14:00 UTC / 09:00 Lima)
        assertThat(items.get(0).getTipo()).isEqualTo("CITA");
        assertThat(items.get(0).getId()).isEqualTo(10L);
        assertThat(items.get(0).getTitulo()).isEqualTo("Cita de la mañana");

        assertThat(items.get(1).getTipo()).isEqualTo("TAREA");
        assertThat(items.get(1).getId()).isEqualTo(1L);
        assertThat(items.get(1).getTitulo()).isEqualTo("Tarea de la tarde");

        assertThat(items.get(2).getTipo()).isEqualTo("TAREA");
        assertThat(items.get(2).getId()).isEqualTo(2L);
        assertThat(items.get(2).getTitulo()).isEqualTo("Tarea día siguiente");
    }

    @Test
    @DisplayName("Filtro por tipo=TAREA retorna únicamente tareas excluyendo citas")
    void obtenerAgendaCombinada_filterByTarea_returnsOnlyTasks() {
        AgendaTarea t1 = AgendaTarea.builder()
                .id(1L)
                .usuario(usuario)
                .titulo("Solo tarea")
                .fechaLimite(LocalDate.of(2026, 10, 25))
                .build();

        when(agendaTareaRepository.findByUsuarioIdAndFechaLimiteBetweenOrderByFechaLimiteAscHoraLimiteAscIdAsc(
                eq(42L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(t1));

        Page<AgendaItemResponse> page = agendaQueryService.obtenerAgendaCombinada(
                42L, "2026-10-25", "2026-10-26", null, "TAREA", PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getTipo()).isEqualTo("TAREA");
        verify(agendaCitaRepository, never()).findByUsuarioIdAndInicioBetweenOrderByInicioAscIdAsc(any(), any(), any());
    }

    @Test
    @DisplayName("Filtro por tipo=CITA retorna únicamente citas excluyendo tareas")
    void obtenerAgendaCombinada_filterByCita_returnsOnlyAppointments() {
        AgendaCita c1 = AgendaCita.builder()
                .id(10L)
                .usuario(usuario)
                .titulo("Solo cita")
                .inicio(Instant.parse("2026-10-25T15:00:00Z"))
                .fin(Instant.parse("2026-10-25T16:00:00Z"))
                .build();

        when(agendaCitaRepository.findByUsuarioIdAndInicioBetweenOrderByInicioAscIdAsc(
                eq(42L), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(c1));

        Page<AgendaItemResponse> page = agendaQueryService.obtenerAgendaCombinada(
                42L, "2026-10-25", "2026-10-26", null, "CITA", PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getTipo()).isEqualTo("CITA");
        verify(agendaTareaRepository, never()).findByUsuarioIdAndFechaLimiteBetweenOrderByFechaLimiteAscHoraLimiteAscIdAsc(any(), any(), any());
    }

    @Test
    @DisplayName("Aislamiento por usuario: las consultas a repositorios siempre usan usuarioId")
    void obtenerAgendaCombinada_scopesStrictlyToUserId() {
        agendaQueryService.obtenerAgendaCombinada(
                42L, "2026-10-01", "2026-10-15", null, null, PageRequest.of(0, 10));

        verify(agendaTareaRepository).findByUsuarioIdAndFechaLimiteBetweenOrderByFechaLimiteAscHoraLimiteAscIdAsc(
                eq(42L), eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 15)));

        verify(agendaCitaRepository).findByUsuarioIdAndInicioBetweenOrderByInicioAscIdAsc(
                eq(42L), any(Instant.class), any(Instant.class));
    }
}
