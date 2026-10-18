package com.urleads.comercial.repository;

import com.urleads.comercial.entity.CatalogItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CatalogItemRepository extends JpaRepository<CatalogItem, Long> {

    Optional<CatalogItem> findByIdAndOwnerId(Long id, String ownerId);

    boolean existsByOwnerIdAndCode(String ownerId, String code);

    boolean existsByOwnerIdAndCodeAndIdNot(String ownerId, String code, Long id);

    @Query("SELECT c FROM CatalogItem c WHERE c.ownerId = :ownerId " +
           "AND (:currency IS NULL OR c.currency = :currency) " +
           "AND (:active IS NULL OR c.active = :active)")
    Page<CatalogItem> findByOwnerIdWithFilters(
        @Param("ownerId") String ownerId,
        @Param("currency") String currency,
        @Param("active") Boolean active,
        Pageable pageable
    );
}
