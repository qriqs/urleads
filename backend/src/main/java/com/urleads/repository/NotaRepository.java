package com.urleads.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.urleads.entity.Nota;

@Repository
public interface NotaRepository extends JpaRepository<Nota, Long> {

    List<Nota> findByLeadIdOrderByCreadoEnAsc(Long leadId);
}	