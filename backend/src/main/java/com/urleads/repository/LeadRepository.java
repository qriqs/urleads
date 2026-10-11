package com.urleads.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.urleads.entity.Lead;

public interface LeadRepository
        extends JpaRepository<Lead, Long> {

    List<Lead> findByUsuarioId(Long usuarioId);

    Optional<Lead> findByIdAndUsuarioId(
            Long leadId,
            Long usuarioId);
}