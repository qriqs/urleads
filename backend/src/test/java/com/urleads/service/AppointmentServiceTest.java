package com.urleads.service;

import com.urleads.dto.AppointmentOutcomeRequest;
import com.urleads.dto.AppointmentOutcomeResponse;
import com.urleads.dto.AppointmentResponse;
import com.urleads.dto.CreateAppointmentRequest;
import com.urleads.dto.UpdateAppointmentRequest;
import com.urleads.entity.AgendaCita;
import com.urleads.entity.AgendaCitaMinuta;
import com.urleads.entity.Usuario;
import com.urleads.exception.InvalidLeadAssociationException;
import com.urleads.exception.ResourceNotFoundException;
import com.urleads.repository.AgendaCitaMinutaRepository;
import com.urleads.repository.AgendaCitaRepository;
import com.urleads.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AppointmentServiceTest {

    private AgendaCitaRepository agendaCitaRepository;
    private UsuarioRepository usuarioRepository;
    private LeadValidationService leadValidationService;
    private AgendaCitaMinutaRepository agendaCitaMinutaRepository;
    private AppointmentService appointmentService;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        agendaCitaRepository = mock(AgendaCitaRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        leadValidationService = mock(LeadValidationService.class);
        agendaCitaMinutaRepository = mock(AgendaCitaMinutaRepository.class);

        appointmentService = new AppointmentService(agendaCitaRepository, usuarioRepository, leadValidationService, agendaCitaMinutaRepository);

        usuario = new Usuario(42L, "alex", "secret");
        when(usuarioRepository.findById(42L)).thenReturn(Optional.of(usuario));
    }

    @Test
    @DisplayName("Parseo de fechas ISO-8601 con offset de Lima (-05:00) y conversión exacta a UTC")
    void parseIsoDateTime_withLimaOffset_convertsCorrectlyToUtc() {
        ZoneId limaZone = ZoneId.of("America/Lima");
        // 10:00 AM en Lima es 15:00 UTC
        Instant instant = AppointmentService.parseIsoDateTime("2026-10-25T10:00:00-05:00", limaZone);
        assertThat(instant).isEqualTo(Instant.parse("2026-10-25T15:00:00Z"));
    }

    @Test
    @DisplayName("Parseo de fechas ISO-8601 en UTC con sufijo Z")
    void parseIsoDateTime_withUtcZ_parsesDirectly() {
        ZoneId limaZone = ZoneId.of("America/Lima");
        Instant instant = AppointmentService.parseIsoDateTime("2026-10-25T15:00:00Z", limaZone);
        assertThat(instant).isEqualTo(Instant.parse("2026-10-25T15:00:00Z"));
    }

    @Test
    @DisplayName("Parseo de fecha local sin offset asumiendo la zona horaria indicada")
    void parseIsoDateTime_withoutOffset_usesFallbackZone() {
        ZoneId limaZone = ZoneId.of("America/Lima");
        Instant instant = AppointmentService.parseIsoDateTime("2026-10-25T10:00:00", limaZone);
        assertThat(instant).isEqualTo(Instant.parse("2026-10-25T15:00:00Z"));
    }

    @Test
    @DisplayName("Caso borde: cambio de fecha / medianoche con zona horaria de Lima")
    void parseIsoDateTime_midnightCrossing_preservesCorrectUtcDay() {
        ZoneId limaZone = ZoneId.of("America/Lima");
        // 23:30 del 25 de octubre en Lima (-05:00) corresponde a 04:30 del 26 de octubre en UTC
        Instant inicio = AppointmentService.parseIsoDateTime("2026-10-25T23:30:00-05:00", limaZone);
        Instant fin = AppointmentService.parseIsoDateTime("2026-10-26T00:30:00-05:00", limaZone);

        assertThat(inicio).isEqualTo(Instant.parse("2026-10-26T04:30:00Z"));
        assertThat(fin).isEqualTo(Instant.parse("2026-10-26T05:30:00Z"));
        assertThat(fin).isAfter(inicio);
    }

    @Test
    @DisplayName("Rechazo de formato de fecha inválido")
    void parseIsoDateTime_invalidFormat_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () ->
                AppointmentService.parseIsoDateTime("fecha-invalida", ZoneId.of("America/Lima")));
    }

    @Test
    @DisplayName("Rechazo de zona horaria no existente")
    void parseZoneId_invalidZone_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () ->
                AppointmentService.parseZoneId("Zona/Inexistente_123"));
    }

    @Test
    @DisplayName("Validación de horario: rechaza cuando fin es igual a inicio")
    void validarHorario_equalTimes_throwsIllegalArgument() {
        Instant t = Instant.parse("2026-10-25T15:00:00Z");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                AppointmentService.validarHorario(t, t));
        assertThat(ex.getMessage()).contains("La fecha de fin debe ser posterior a la de inicio");
    }

    @Test
    @DisplayName("Validación de horario: rechaza cuando fin es anterior a inicio (duración negativa)")
    void validarHorario_endBeforeStart_throwsIllegalArgument() {
        Instant inicio = Instant.parse("2026-10-25T16:00:00Z");
        Instant fin = Instant.parse("2026-10-25T15:00:00Z");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                AppointmentService.validarHorario(inicio, fin));
        assertThat(ex.getMessage()).contains("La fecha de fin debe ser posterior a la de inicio");
    }

    @Test
    @DisplayName("Validación de horario entre zonas horarias distintas: hora local mayor pero UTC menor es rechazada")
    void validarHorario_crossTimezones_detectsTrueChronologicalOrder() {
        // Inicio en Lima: 11:00 -05:00 -> 16:00 UTC
        // Fin enviado en Madrid (+02:00): 17:00 +02:00 -> 15:00 UTC (1 hora ANTES en tiempo absoluto)
        Instant inicio = AppointmentService.parseIsoDateTime("2026-10-25T11:00:00-05:00", ZoneId.of("America/Lima"));
        Instant fin = AppointmentService.parseIsoDateTime("2026-10-25T17:00:00+02:00", ZoneId.of("Europe/Madrid"));

        assertThat(fin.isBefore(inicio)).isTrue();
        assertThrows(IllegalArgumentException.class, () -> AppointmentService.validarHorario(inicio, fin));
    }

    @Test
    @DisplayName("Validación de estados de cita: PROGRAMADA, REALIZADA, CANCELADA son válidos")
    void validarEstado_validStates_normalizedCorrectly() {
        assertThat(AppointmentService.validarEstado("PROGRAMADA")).isEqualTo("PROGRAMADA");
        assertThat(AppointmentService.validarEstado("realizada")).isEqualTo("REALIZADA");
        assertThat(AppointmentService.validarEstado("Cancelada")).isEqualTo("CANCELADA");
        assertThat(AppointmentService.validarEstado(null)).isEqualTo("PROGRAMADA");
    }

    @Test
    @DisplayName("Validación de estados de cita: estados no permitidos son rechazados")
    void validarEstado_invalidState_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> AppointmentService.validarEstado("PENDIENTE"));
        assertThrows(IllegalArgumentException.class, () -> AppointmentService.validarEstado("COMPLETADA"));
        assertThrows(IllegalArgumentException.class, () -> AppointmentService.validarEstado("INVENTADO"));
    }

    @Test
    @DisplayName("Crear cita válida con lead autorizado retorna 201 y persiste correctamente")
    void crearCita_valid_savesAndReturnsResponse() {
        CreateAppointmentRequest request = CreateAppointmentRequest.builder()
                .titulo("Demostración de CRM UrLeads")
                .descripcion("Reunión virtual con el tomador de decisiones")
                .leadId(105L)
                .inicio("2026-10-25T10:00:00-05:00")
                .fin("2026-10-25T11:00:00-05:00")
                .zonaHoraria("America/Lima")
                .estado("PROGRAMADA")
                .build();

        when(agendaCitaRepository.save(any(AgendaCita.class))).thenAnswer(inv -> {
            AgendaCita c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });

        AppointmentResponse response = appointmentService.crearCita(42L, request, "Bearer test-token");

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getTitulo()).isEqualTo("Demostración de CRM UrLeads");
        assertThat(response.getEstado()).isEqualTo("PROGRAMADA");
        assertThat(response.getZonaHoraria()).isEqualTo("America/Lima");
        assertThat(response.getInicio()).isEqualTo(Instant.parse("2026-10-25T15:00:00Z"));
        assertThat(response.getFin()).isEqualTo(Instant.parse("2026-10-25T16:00:00Z"));
        assertThat(response.getInicioLocal()).contains("2026-10-25T10:00:00-05:00");
        assertThat(response.getFinLocal()).contains("2026-10-25T11:00:00-05:00");

        verify(leadValidationService).validarLeadPerteneceAUsuario(105L, "Bearer test-token");
        verify(agendaCitaRepository).save(any(AgendaCita.class));
    }

    @Test
    @DisplayName("Crear cita con lead ajeno propaga InvalidLeadAssociationException")
    void crearCita_foreignLead_throwsException() {
        CreateAppointmentRequest request = CreateAppointmentRequest.builder()
                .titulo("Cita con lead ajeno")
                .leadId(999L)
                .inicio("2026-10-25T10:00:00-05:00")
                .fin("2026-10-25T11:00:00-05:00")
                .build();

        doThrow(new InvalidLeadAssociationException("El lead no existe o no pertenece al usuario autenticado"))
                .when(leadValidationService).validarLeadPerteneceAUsuario(eq(999L), any());

        assertThrows(InvalidLeadAssociationException.class, () ->
                appointmentService.crearCita(42L, request, "Bearer test-token"));

        verify(agendaCitaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Actualizar estado de cita modifica el estado y actualiza el timestamp")
    void actualizarEstadoCita_updatesStatusSuccessfully() {
        AgendaCita cita = AgendaCita.builder()
                .id(1L)
                .usuario(usuario)
                .titulo("Cita existente")
                .inicio(Instant.parse("2026-10-25T15:00:00Z"))
                .fin(Instant.parse("2026-10-25T16:00:00Z"))
                .zonaHoraria("America/Lima")
                .estado("PROGRAMADA")
                .build();

        when(agendaCitaRepository.findByIdAndUsuarioId(1L, 42L)).thenReturn(Optional.of(cita));
        when(agendaCitaRepository.save(any(AgendaCita.class))).thenAnswer(inv -> inv.getArgument(0));

        AppointmentResponse response = appointmentService.actualizarEstadoCita(1L, 42L, "REALIZADA");

        assertThat(response.getEstado()).isEqualTo("REALIZADA");
        verify(agendaCitaRepository).save(argThat(c -> c.getEstado().equals("REALIZADA")));
    }

    @Test
    @DisplayName("Eliminar cita de otro usuario lanza ResourceNotFoundException")
    void eliminarCita_foreignUser_throwsNotFound() {
        when(agendaCitaRepository.findByIdAndUsuarioId(99L, 42L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                appointmentService.eliminarCita(99L, 42L));
    }

    @Test
    @DisplayName("Detección de solapamientos: casos borde entre intervalos de citas")
    void verificarSolapamientos_handlesEdgeCases() {
        Instant inicio = Instant.parse("2026-10-25T15:00:00Z"); // 10:00 Lima
        Instant fin = Instant.parse("2026-10-25T16:00:00Z");    // 11:00 Lima

        AgendaCita solapada = AgendaCita.builder()
                .id(2L)
                .usuario(usuario)
                .titulo("Cita en conflicto")
                .inicio(Instant.parse("2026-10-25T15:30:00Z"))
                .fin(Instant.parse("2026-10-25T16:30:00Z"))
                .zonaHoraria("America/Lima")
                .estado("PROGRAMADA")
                .build();

        when(agendaCitaRepository.findOverlapping(42L, inicio, fin)).thenReturn(List.of(solapada));

        List<AppointmentResponse> overlaps = appointmentService.verificarSolapamientos(42L, inicio, fin, null);
        assertThat(overlaps).hasSize(1);
        assertThat(overlaps.get(0).getId()).isEqualTo(2L);
        assertThat(appointmentService.tieneSolapamiento(42L, inicio, fin, null)).isTrue();
    }

    @Test
    @DisplayName("Detección de solapamientos: citas adyacentes que se tocan en el borde no se consideran solapadas")
    void verificarSolapamientos_touchingBoundaries_noOverlap() {
        Instant inicio = Instant.parse("2026-10-25T16:00:00Z"); // Empieza exactamente cuando la anterior termina
        Instant fin = Instant.parse("2026-10-25T17:00:00Z");

        when(agendaCitaRepository.findOverlapping(42L, inicio, fin)).thenReturn(List.of());

        List<AppointmentResponse> overlaps = appointmentService.verificarSolapamientos(42L, inicio, fin, null);
        assertThat(overlaps).isEmpty();
        assertThat(appointmentService.tieneSolapamiento(42L, inicio, fin, null)).isFalse();
    }

    @Test
    @DisplayName("Registrar resultado de cita: persiste minuta y actualiza estado a REALIZADA")
    void registrarResultadoCita_validRequest_persistsMinutaAndUpdatesStatus() {
        AgendaCita cita = AgendaCita.builder()
                .id(10L)
                .usuario(usuario)
                .titulo("Demostración con cliente")
                .inicio(Instant.parse("2026-10-25T15:00:00Z"))
                .fin(Instant.parse("2026-10-25T16:00:00Z"))
                .estado("PROGRAMADA")
                .build();

        when(agendaCitaRepository.findByIdAndUsuarioId(10L, 42L)).thenReturn(Optional.of(cita));
        when(agendaCitaMinutaRepository.findByCitaIdAndUsuarioId(10L, 42L)).thenReturn(Optional.empty());
        when(agendaCitaMinutaRepository.save(any(AgendaCitaMinuta.class))).thenAnswer(invocation -> {
            AgendaCitaMinuta m = invocation.getArgument(0);
            m.setId(100L);
            return m;
        });

        AppointmentOutcomeRequest request = AppointmentOutcomeRequest.builder()
                .resumen("Se presentaron las características del plan enterprise.")
                .acuerdos("Enviar cotización ajustada antes del viernes.")
                .proximasAcciones("Preparar cotización de 15 licencias.")
                .resultado("REALIZADA")
                .asistencia("ASISTIERON_TODOS")
                .build();

        AppointmentOutcomeResponse response = appointmentService.registrarResultadoCita(10L, 42L, request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getCitaId()).isEqualTo(10L);
        assertThat(response.getUsuarioId()).isEqualTo(42L);
        assertThat(response.getResumen()).isEqualTo("Se presentaron las características del plan enterprise.");
        assertThat(response.getMinuta()).isEqualTo("Se presentaron las características del plan enterprise.");
        assertThat(response.getAcuerdos()).isEqualTo("Enviar cotización ajustada antes del viernes.");
        assertThat(response.getProximasAcciones()).isEqualTo("Preparar cotización de 15 licencias.");
        assertThat(response.getResultado()).isEqualTo("REALIZADA");
        assertThat(response.getAsistencia()).isEqualTo("ASISTIERON_TODOS");

        // Verifica que la cita transitó a REALIZADA
        assertThat(cita.getEstado()).isEqualTo("REALIZADA");
        verify(agendaCitaRepository).save(cita);
        verify(agendaCitaMinutaRepository).save(any(AgendaCitaMinuta.class));
    }

    @Test
    @DisplayName("Registrar resultado de cita: actualización idempotente sobre minuta existente")
    void registrarResultadoCita_idempotentUpdate_updatesExistingMinuta() {
        AgendaCita cita = AgendaCita.builder()
                .id(10L)
                .usuario(usuario)
                .titulo("Cita previa")
                .estado("REALIZADA")
                .build();

        AgendaCitaMinuta minutaExistente = AgendaCitaMinuta.builder()
                .id(100L)
                .cita(cita)
                .usuario(usuario)
                .resumen("Minuta anterior")
                .acuerdos("Acuerdo previo")
                .resultado("REALIZADA")
                .build();

        when(agendaCitaRepository.findByIdAndUsuarioId(10L, 42L)).thenReturn(Optional.of(cita));
        when(agendaCitaMinutaRepository.findByCitaIdAndUsuarioId(10L, 42L)).thenReturn(Optional.of(minutaExistente));
        when(agendaCitaMinutaRepository.save(any(AgendaCitaMinuta.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AppointmentOutcomeRequest updateRequest = AppointmentOutcomeRequest.builder()
                .resumen("Minuta corregida con nuevos detalles.")
                .acuerdos("Acuerdo ampliado.")
                .build();

        AppointmentOutcomeResponse response = appointmentService.registrarResultadoCita(10L, 42L, updateRequest);

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getResumen()).isEqualTo("Minuta corregida con nuevos detalles.");
        assertThat(response.getAcuerdos()).isEqualTo("Acuerdo ampliado.");
        verify(agendaCitaMinutaRepository).save(minutaExistente);
    }

    @Test
    @DisplayName("Registrar resultado de cita: contenido vacío lanza IllegalArgumentException")
    void registrarResultadoCita_emptyContent_throwsIllegalArgument() {
        AppointmentOutcomeRequest emptyRequest = AppointmentOutcomeRequest.builder()
                .resumen("   ")
                .acuerdos(null)
                .proximasAcciones("")
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                appointmentService.registrarResultadoCita(10L, 42L, emptyRequest));

        assertThat(ex.getMessage()).contains("Debe proporcionar al menos un resumen, acuerdo o próxima acción");
    }

    @Test
    @DisplayName("Registrar resultado de cita: cita no encontrada o de otro usuario lanza ResourceNotFoundException")
    void registrarResultadoCita_foreignOrNotFound_throwsResourceNotFound() {
        when(agendaCitaRepository.findByIdAndUsuarioId(999L, 42L)).thenReturn(Optional.empty());

        AppointmentOutcomeRequest request = AppointmentOutcomeRequest.builder()
                .resumen("Minuta de cita ajena")
                .build();

        assertThrows(ResourceNotFoundException.class, () ->
                appointmentService.registrarResultadoCita(999L, 42L, request));
    }

    @Test
    @DisplayName("Aislamiento de CRM: registrar resultado de cita nunca interactúa con servicios ni notas de CRM")
    void registrarResultadoCita_strictIsolation_noCrmInteraction() {
        AgendaCita cita = AgendaCita.builder()
                .id(10L)
                .leadId(105L) // vinculada a lead
                .usuario(usuario)
                .titulo("Cita con lead")
                .estado("PROGRAMADA")
                .build();

        when(agendaCitaRepository.findByIdAndUsuarioId(10L, 42L)).thenReturn(Optional.of(cita));
        when(agendaCitaMinutaRepository.findByCitaIdAndUsuarioId(10L, 42L)).thenReturn(Optional.empty());
        when(agendaCitaMinutaRepository.save(any(AgendaCitaMinuta.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AppointmentOutcomeRequest request = AppointmentOutcomeRequest.builder()
                .resumen("Acuerdo alcanzado.")
                .build();

        appointmentService.registrarResultadoCita(10L, 42L, request);

        // Verifica que LeadValidationService nunca fue invocado para registrar notas en CRM
        verifyNoInteractions(leadValidationService);
    }

    @Test
    @DisplayName("Consultar resultado de cita: retorna minuta existente")
    void obtenerResultadoCita_found_returnsOutcome() {
        AgendaCita cita = AgendaCita.builder().id(10L).usuario(usuario).estado("REALIZADA").build();
        AgendaCitaMinuta minuta = AgendaCitaMinuta.builder()
                .id(100L)
                .cita(cita)
                .usuario(usuario)
                .resumen("Minuta registrada")
                .acuerdos("Acuerdo 1")
                .build();

        when(agendaCitaRepository.findByIdAndUsuarioId(10L, 42L)).thenReturn(Optional.of(cita));
        when(agendaCitaMinutaRepository.findByCitaIdAndUsuarioId(10L, 42L)).thenReturn(Optional.of(minuta));

        AppointmentOutcomeResponse response = appointmentService.obtenerResultadoCita(10L, 42L);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getResumen()).isEqualTo("Minuta registrada");
    }

    @Test
    @DisplayName("Consultar resultado de cita: si no existe minuta lanza ResourceNotFoundException")
    void obtenerResultadoCita_notFound_throwsResourceNotFound() {
        AgendaCita cita = AgendaCita.builder().id(10L).usuario(usuario).build();
        when(agendaCitaRepository.findByIdAndUsuarioId(10L, 42L)).thenReturn(Optional.of(cita));
        when(agendaCitaMinutaRepository.findByCitaIdAndUsuarioId(10L, 42L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                appointmentService.obtenerResultadoCita(10L, 42L));
    }
}
