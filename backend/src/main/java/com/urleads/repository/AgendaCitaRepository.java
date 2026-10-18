package com.urleads.repository;

import com.urleads.entity.AgendaCita;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface AgendaCitaRepository extends JpaRepository<AgendaCita, Long> {

    Optional<AgendaCita> findByIdAndUsuarioId(Long id, Long usuarioId);

    void deleteByIdAndUsuarioId(Long id, Long usuarioId);

    Page<AgendaCita> findByUsuarioIdOrderByInicioAscIdAsc(Long usuarioId, Pageable pageable);

    Page<AgendaCita> findByUsuarioIdAndEstadoOrderByInicioAscIdAsc(Long usuarioId, String estado, Pageable pageable);

    Page<AgendaCita> findByUsuarioIdAndLeadIdOrderByInicioAscIdAsc(Long usuarioId, Long leadId, Pageable pageable);

    Page<AgendaCita> findByUsuarioIdAndEstadoAndLeadIdOrderByInicioAscIdAsc(
            Long usuarioId, String estado, Long leadId, Pageable pageable);

    Page<AgendaCita> findByUsuarioIdAndInicioBetweenOrderByInicioAscIdAsc(
            Long usuarioId, Instant desde, Instant hasta, Pageable pageable);

    List<AgendaCita> findByUsuarioIdAndInicioBetweenOrderByInicioAscIdAsc(
            Long usuarioId, Instant desde, Instant hasta);

    List<AgendaCita> findByUsuarioIdAndLeadIdAndInicioBetweenOrderByInicioAscIdAsc(
            Long usuarioId, Long leadId, Instant desde, Instant hasta);

    @Query("SELECT c FROM AgendaCita c WHERE c.usuario.id = :usuarioId AND c.estado <> 'CANCELADA' AND c.inicio < :fin AND c.fin > :inicio")
    List<AgendaCita> findOverlapping(
            @Param("usuarioId") Long usuarioId,
            @Param("inicio") Instant inicio,
            @Param("fin") Instant fin);

    @Query("SELECT c FROM AgendaCita c WHERE c.usuario.id = :usuarioId AND c.id <> :excludeId AND c.estado <> 'CANCELADA' AND c.inicio < :fin AND c.fin > :inicio")
    List<AgendaCita> findOverlappingExcludingId(
            @Param("usuarioId") Long usuarioId,
            @Param("excludeId") Long excludeId,
            @Param("inicio") Instant inicio,
            @Param("fin") Instant fin);
}
