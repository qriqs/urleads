package com.urleads.comercial.repository;

import com.urleads.comercial.entity.QuoteTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QuoteTemplateRepository extends JpaRepository<QuoteTemplate, Long> {

    Optional<QuoteTemplate> findByIdAndOwnerId(Long id, String ownerId);

    Page<QuoteTemplate> findByOwnerId(String ownerId, Pageable pageable);

    boolean existsByOwnerIdAndName(String ownerId, String name);
}
