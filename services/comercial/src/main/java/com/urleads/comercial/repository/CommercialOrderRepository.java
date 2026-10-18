package com.urleads.comercial.repository;

import com.urleads.comercial.entity.CommercialOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CommercialOrderRepository extends JpaRepository<CommercialOrder, Long> {

    Optional<CommercialOrder> findByIdAndOwnerId(Long id, String ownerId);

    Optional<CommercialOrder> findByQuoteId(Long quoteId);

    Optional<CommercialOrder> findByOwnerIdAndQuoteId(String ownerId, Long quoteId);

    boolean existsByQuoteId(Long quoteId);

    Page<CommercialOrder> findByOwnerId(String ownerId, Pageable pageable);
}
