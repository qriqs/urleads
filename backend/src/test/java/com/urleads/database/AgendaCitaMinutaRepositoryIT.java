package com.urleads.database;

import com.urleads.entity.AgendaCita;
import com.urleads.entity.AgendaCitaMinuta;
import com.urleads.entity.Usuario;
import com.urleads.repository.AgendaCitaMinutaRepository;
import com.urleads.repository.AgendaCitaRepository;
import com.urleads.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ContextConfiguration(initializers = TestDatabaseGuardInitializer.class)
@Transactional
@Tag("db-integration")
@ActiveProfiles("db-integration")
class AgendaCitaMinutaRepositoryIT {

    @Autowired
    private AgendaCitaMinutaRepository agendaCitaMinutaRepository;

    @Autowired
    private AgendaCitaRepository agendaCitaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Test
    @DisplayName("Persistencia y consulta de minuta vinculada a cita con aislamiento estricto por usuario")
    void persistAndFindMinuta_strictUserIsolation() {
        Usuario user1 = usuarioRepository.save(new Usuario(null, "user_minuta_1", "hash1"));
        Usuario user2 = usuarioRepository.save(new Usuario(null, "user_minuta_2", "hash2"));

        AgendaCita cita = agendaCitaRepository.save(AgendaCita.builder()
                .usuario(user1)
                .titulo("Cita de evaluación técnica")
                .inicio(Instant.parse("2026-10-25T15:00:00Z"))
                .fin(Instant.parse("2026-10-25T16:00:00Z"))
                .zonaHoraria("America/Lima")
                .estado("PROGRAMADA")
                .build());

        AgendaCitaMinuta minuta = agendaCitaMinutaRepository.save(AgendaCitaMinuta.builder()
                .cita(cita)
                .usuario(user1)
                .resumen("Demostración completada exitosamente.")
                .acuerdos("Enviar cotización revisada para 10 puestos.")
                .proximasAcciones("Preparar orden comercial.")
                .resultado("REALIZADA")
                .asistencia("ASISTIERON_TODOS")
                .build());

        assertThat(minuta.getId()).isNotNull();

        // 1. Consulta por el propietario devuelve la minuta
        Optional<AgendaCitaMinuta> encontrada = agendaCitaMinutaRepository.findByCitaIdAndUsuarioId(cita.getId(), user1.getId());
        assertThat(encontrada).isPresent();
        assertThat(encontrada.get().getResumen()).isEqualTo("Demostración completada exitosamente.");
        assertThat(encontrada.get().getAcuerdos()).isEqualTo("Enviar cotización revisada para 10 puestos.");
        assertThat(encontrada.get().getProximasAcciones()).isEqualTo("Preparar orden comercial.");
        assertThat(encontrada.get().getResultado()).isEqualTo("REALIZADA");

        // 2. Consulta con otro usuario jamás retorna la minuta
        Optional<AgendaCitaMinuta> ajena = agendaCitaMinutaRepository.findByCitaIdAndUsuarioId(cita.getId(), user2.getId());
        assertThat(ajena).isEmpty();
    }

    @Test
    @DisplayName("Restricción UNIQUE: no se puede duplicar la minuta para una misma cita")
    void uniqueConstraint_duplicateMinutaForSameCitaFails() {
        Usuario user = usuarioRepository.save(new Usuario(null, "user_minuta_unique", "hash"));

        AgendaCita cita = agendaCitaRepository.save(AgendaCita.builder()
                .usuario(user)
                .titulo("Cita única")
                .inicio(Instant.parse("2026-10-25T15:00:00Z"))
                .fin(Instant.parse("2026-10-25T16:00:00Z"))
                .estado("PROGRAMADA")
                .build());

        agendaCitaMinutaRepository.saveAndFlush(AgendaCitaMinuta.builder()
                .cita(cita)
                .usuario(user)
                .resumen("Primera minuta")
                .resultado("REALIZADA")
                .build());

        AgendaCitaMinuta duplicada = AgendaCitaMinuta.builder()
                .cita(cita)
                .usuario(user)
                .resumen("Segunda minuta para la misma cita")
                .resultado("REALIZADA")
                .build();

        assertThatThrownBy(() -> agendaCitaMinutaRepository.saveAndFlush(duplicada))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
