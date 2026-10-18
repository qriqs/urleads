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
              AND EXISTS (
                    SELECT 1 FROM l.tags t
                    WHERE t.id IN :tagIds
                      AND t.usuario.id = :usuarioId
              )
            """)
    Page<Lead> buscarConTags(
            @Param("usuarioId") Long usuarioId,
            @Param("etapa") String etapa,
            @Param("patron") String patron,
            @Param("modo") String modo,
            @Param("hoy") LocalDate hoy,
            @Param("tagIds") java.util.Collection<Long> tagIds,
            Pageable pageable);

    @Query("SELECT DISTINCT l FROM Lead l LEFT JOIN FETCH l.tags WHERE l.id IN :leadIds AND l.usuario.id = :usuarioId")
    List<Lead> findAllWithTagsByIdIn(
            @Param("leadIds") java.util.Collection<Long> leadIds,
            @Param("usuarioId") Long usuarioId);

    @Query("SELECT l FROM Lead l LEFT JOIN FETCH l.tags WHERE l.id = :leadId AND l.usuario.id = :usuarioId")
    Optional<Lead> findByIdAndUsuarioIdWithTags(@Param("leadId") Long leadId, @Param("usuarioId") Long usuarioId);

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