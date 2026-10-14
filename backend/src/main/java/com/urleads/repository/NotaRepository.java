package com.urleads.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.urleads.entity.Nota;

public interface NotaRepository
        extends JpaRepository<Nota, Long> {

    List<Nota> findByLeadId(Long leadId);
}