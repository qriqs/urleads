package com.urleads.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.urleads.entity.Lead;

public interface LeadRepository extends JpaRepository<Lead, Long> {

    List<Lead> findByUsuarioId(Long usuarioId);

    Page<Lead> findByUsuarioId(Long usuarioId, Pageable pageable);

    Optional<Lead> findByIdAndUsuarioId(Long leadId, Long usuarioId);

    // ==========================================
    // Detección de duplicados (DSW-46 / C06)
    // ==========================================

    boolean existsByUsuarioIdAndCorreoNorm(Long usuarioId, String correoNorm);

    boolean existsByUsuarioIdAndTelefonoNorm(Long usuarioId, String telefonoNorm);

    boolean existsByUsuarioIdAndCorreoNormAndIdNot(Long usuarioId, String correoNorm, Long id);

    boolean existsByUsuarioIdAndTelefonoNormAndIdNot(Long usuarioId, String telefonoNorm, Long id);

    List<Lead> findByUsuarioIdAndEtapa(Long usuarioId, String etapa);

    List<Lead> findByUsuarioIdAndProximoSeguimiento(Long usuarioId, LocalDate fecha);

    List<Lead> findByUsuarioIdAndProximoSeguimientoBefore(Long usuarioId, LocalDate fecha);

    @Query("""
            SELECT l FROM Lead l
            WHERE l.usuario.id = :usuarioId
              AND (:etapa = '' OR l.etapa = :etapa)
              AND (
                    LOWER(l.nombre) LIKE :patron
                 OR (l.telefono IS NOT NULL AND LOWER(l.telefono) LIKE :patron)
                 OR (l.correo IS NOT NULL AND LOWER(l.correo) LIKE :patron)
              )
              AND (
                    :modo = 'all'
                 OR (:modo = 'today' AND l.proximoSeguimiento = :hoy)
                 OR (:modo = 'overdue' AND l.proximoSeguimiento < :hoy)
              )
            """)
    Page<Lead> buscar(
            @Param("usuarioId") Long usuarioId,
            @Param("etapa") String etapa,
            @Param("patron") String patron,
            @Param("modo") String modo,
            @Param("hoy") LocalDate hoy,
            Pageable pageable);

    // ==========================================
    // Consultas para Dashboard (/api/dashboard)
    // ==========================================

    long countByUsuarioId(Long usuarioId);

    @Query("""
            SELECT l.etapa, COUNT(l)
            FROM Lead l
            WHERE l.usuario.id = :usuarioId
            GROUP BY l.etapa
            """)
    List<Object[]> countLeadsPorEtapa(@Param("usuarioId") Long usuarioId);

    @Query("""
            SELECT l FROM Lead l
            WHERE l.usuario.id = :usuarioId
              AND l.proximoSeguimiento = :hoy
              AND l.etapa != 'CERRADO'
            ORDER BY l.nombre ASC
            """)
    List<Lead> findSeguimientosHoy(
            @Param("usuarioId") Long usuarioId,
            @Param("hoy") LocalDate hoy);

    @Query("""
            SELECT l FROM Lead l
            WHERE l.usuario.id = :usuarioId
              AND l.proximoSeguimiento < :hoy
              AND l.etapa != 'CERRADO'
            ORDER BY l.proximoSeguimiento ASC, l.nombre ASC
            """)
    List<Lead> findSeguimientosAtrasados(
            @Param("usuarioId") Long usuarioId,
            @Param("hoy") LocalDate hoy);
}