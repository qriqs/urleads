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

    @Query("SELECT l FROM Lead l WHERE l.usuario.id = :usuarioId AND l.archivedAt IS NULL")
    List<Lead> findByUsuarioId(@Param("usuarioId") Long usuarioId);

    @Query("SELECT l FROM Lead l WHERE l.usuario.id = :usuarioId AND l.archivedAt IS NULL")
    Page<Lead> findByUsuarioId(@Param("usuarioId") Long usuarioId, Pageable pageable);

    @Query("SELECT l FROM Lead l WHERE l.id = :leadId AND l.usuario.id = :usuarioId AND l.archivedAt IS NULL")
    Optional<Lead> findByIdAndUsuarioId(@Param("leadId") Long leadId, @Param("usuarioId") Long usuarioId);

    @Query("SELECT l FROM Lead l WHERE l.id = :leadId AND l.usuario.id = :usuarioId AND l.archivedAt IS NOT NULL")
    Optional<Lead> findByIdAndUsuarioIdAndArchivedAtIsNotNull(@Param("leadId") Long leadId, @Param("usuarioId") Long usuarioId);

    @Query("SELECT l FROM Lead l WHERE l.usuario.id = :usuarioId AND l.archivedAt IS NOT NULL")
    Page<Lead> findArchivedByUsuarioId(@Param("usuarioId") Long usuarioId, Pageable pageable);

    // ==========================================
    // Detección de duplicados (DSW-46 / C06)
    // ==========================================

    boolean existsByUsuarioIdAndCorreoNorm(Long usuarioId, String correoNorm);

    boolean existsByUsuarioIdAndTelefonoNorm(Long usuarioId, String telefonoNorm);

    boolean existsByUsuarioIdAndCorreoNormAndIdNot(Long usuarioId, String correoNorm, Long id);

    boolean existsByUsuarioIdAndTelefonoNormAndIdNot(Long usuarioId, String telefonoNorm, Long id);

    @Query("SELECT l FROM Lead l WHERE l.usuario.id = :usuarioId AND l.etapa = :etapa AND l.archivedAt IS NULL")
    List<Lead> findByUsuarioIdAndEtapa(@Param("usuarioId") Long usuarioId, @Param("etapa") String etapa);

    @Query("SELECT l FROM Lead l WHERE l.usuario.id = :usuarioId AND l.proximoSeguimiento = :fecha AND l.archivedAt IS NULL")
    List<Lead> findByUsuarioIdAndProximoSeguimiento(@Param("usuarioId") Long usuarioId, @Param("fecha") LocalDate fecha);

    @Query("SELECT l FROM Lead l WHERE l.usuario.id = :usuarioId AND l.proximoSeguimiento < :fecha AND l.archivedAt IS NULL")
    List<Lead> findByUsuarioIdAndProximoSeguimientoBefore(@Param("usuarioId") Long usuarioId, @Param("fecha") LocalDate fecha);

    @Query("""
            SELECT l FROM Lead l
            WHERE l.usuario.id = :usuarioId
              AND l.archivedAt IS NULL
              AND (:etapa = '' OR l.etapa = :etapa)
              AND (:priority = '' OR l.priority = :priority)
              AND (:origin = '' OR l.origin = :origin)
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
            @Param("priority") String priority,
            @Param("origin") String origin,
            @Param("patron") String patron,
            @Param("modo") String modo,
            @Param("hoy") LocalDate hoy,
            Pageable pageable);

    @Query("""
            SELECT l FROM Lead l
            WHERE l.usuario.id = :usuarioId
              AND l.archivedAt IS NULL
              AND (:etapa = '' OR l.etapa = :etapa)
              AND (:priority = '' OR l.priority = :priority)
              AND (:origin = '' OR l.origin = :origin)
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
            @Param("priority") String priority,
            @Param("origin") String origin,
            @Param("patron") String patron,
            @Param("modo") String modo,
            @Param("hoy") LocalDate hoy,
            @Param("tagIds") java.util.Collection<Long> tagIds,
            Pageable pageable);

    @Query("SELECT DISTINCT l FROM Lead l LEFT JOIN FETCH l.tags WHERE l.id IN :leadIds AND l.usuario.id = :usuarioId")
    List<Lead> findAllWithTagsByIdIn(
            @Param("leadIds") java.util.Collection<Long> leadIds,
            @Param("usuarioId") Long usuarioId);

    @Query("SELECT l FROM Lead l LEFT JOIN FETCH l.tags WHERE l.id = :leadId AND l.usuario.id = :usuarioId AND l.archivedAt IS NULL")
    Optional<Lead> findByIdAndUsuarioIdWithTags(@Param("leadId") Long leadId, @Param("usuarioId") Long usuarioId);

    // ==========================================
    // Consultas para Dashboard (/api/dashboard)
    // ==========================================

    @Query("SELECT COUNT(l) FROM Lead l WHERE l.usuario.id = :usuarioId AND l.archivedAt IS NULL")
    long countByUsuarioId(@Param("usuarioId") Long usuarioId);

    @Query("""
            SELECT l.etapa, COUNT(l)
            FROM Lead l
            WHERE l.usuario.id = :usuarioId
              AND l.archivedAt IS NULL
            GROUP BY l.etapa
            """)
    List<Object[]> countLeadsPorEtapa(@Param("usuarioId") Long usuarioId);

    @Query("""
            SELECT l FROM Lead l
            WHERE l.usuario.id = :usuarioId
              AND l.archivedAt IS NULL
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
              AND l.archivedAt IS NULL
              AND l.proximoSeguimiento < :hoy
              AND l.etapa != 'CERRADO'
            ORDER BY l.proximoSeguimiento ASC, l.nombre ASC
            """)
    List<Lead> findSeguimientosAtrasados(
            @Param("usuarioId") Long usuarioId,
            @Param("hoy") LocalDate hoy);
}
