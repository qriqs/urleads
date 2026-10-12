package com.urleads.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.urleads.entity.Lead;
import java.time.LocalDate;
//Paginado
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface LeadRepository
        extends JpaRepository<Lead, Long> {

    List<Lead> findByUsuarioId(Long usuarioId);

    Optional<Lead> findByIdAndUsuarioId(
            Long leadId,
            Long usuarioId);
    
    //Paginado
    Page<Lead> findByUsuarioId(
            Long usuarioId,
            Pageable pageable);
    List<Lead> findByUsuarioIdAndEtapa(
            Long usuarioId,
            String etapa);

    List<Lead> findByUsuarioIdAndProximoSeguimiento(
            Long usuarioId,
            LocalDate fecha);

    List<Lead> findByUsuarioIdAndProximoSeguimientoBefore(
            Long usuarioId,
            LocalDate fecha);

}