package com.urleads.repository;

import com.urleads.entity.AgendaTarea;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AgendaTareaRepository extends JpaRepository<AgendaTarea, Long> {

    Page<AgendaTarea> findByUsuarioIdOrderByCreadoEnDescIdDesc(Long usuarioId, Pageable pageable);

    Page<AgendaTarea> findByUsuarioIdAndEstadoOrderByCreadoEnDescIdDesc(Long usuarioId, String estado, Pageable pageable);

    Page<AgendaTarea> findByUsuarioIdAndLeadIdOrderByCreadoEnDescIdDesc(Long usuarioId, Long leadId, Pageable pageable);

    Page<AgendaTarea> findByUsuarioIdAndEstadoAndLeadIdOrderByCreadoEnDescIdDesc(Long usuarioId, String estado, Long leadId, Pageable pageable);

    Optional<AgendaTarea> findByIdAndUsuarioId(Long id, Long usuarioId);

    long countByUsuarioIdAndEstado(Long usuarioId, String estado);

    java.util.List<AgendaTarea> findByUsuarioIdAndFechaLimiteBetweenOrderByFechaLimiteAscHoraLimiteAscIdAsc(
            Long usuarioId, java.time.LocalDate desde, java.time.LocalDate hasta);

    java.util.List<AgendaTarea> findByUsuarioIdAndLeadIdAndFechaLimiteBetweenOrderByFechaLimiteAscHoraLimiteAscIdAsc(
            Long usuarioId, Long leadId, java.time.LocalDate desde, java.time.LocalDate hasta);

    java.util.List<AgendaTarea> findByUsuarioIdAndFechaLimiteAndEstadoOrderByHoraLimiteAscIdAsc(
            Long usuarioId, java.time.LocalDate fechaLimite, String estado);
}
