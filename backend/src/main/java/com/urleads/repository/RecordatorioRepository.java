package com.urleads.repository;

import com.urleads.entity.Recordatorio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface RecordatorioRepository extends JpaRepository<Recordatorio, Long> {

    Optional<Recordatorio> findByOccurrenceId(String occurrenceId);

    Optional<Recordatorio> findByIdAndUsuarioId(Long id, Long usuarioId);

    List<Recordatorio> findByUsuarioIdAndTargetTypeAndTargetId(Long usuarioId, String targetType, Long targetId);

    Page<Recordatorio> findByUsuarioIdOrderByProgramadoParaAsc(Long usuarioId, Pageable pageable);

    Page<Recordatorio> findByUsuarioIdAndEstadoOrderByProgramadoParaAsc(
            Long usuarioId, String estado, Pageable pageable);

    @Query("SELECT r FROM Recordatorio r WHERE r.estado = 'PENDIENTE' AND r.programadoPara <= :ahora ORDER BY r.programadoPara ASC")
    List<Recordatorio> findPendingRemindersToDispatch(@Param("ahora") Instant ahora);

    @Modifying
    @Query("UPDATE Recordatorio r SET r.estado = :nuevoEstado WHERE r.id = :id AND r.estado = :estadoEsperado")
    int updateEstadoIfCurrent(
            @Param("id") Long id,
            @Param("nuevoEstado") String nuevoEstado,
            @Param("estadoEsperado") String estadoEsperado);
}
