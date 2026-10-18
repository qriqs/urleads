package com.urleads.comercial.repository;

import com.urleads.comercial.entity.Quote;
import com.urleads.comercial.entity.QuoteStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QuoteRepository extends JpaRepository<Quote, Long>, JpaSpecificationExecutor<Quote> {

    Optional<Quote> findByIdAndOwnerId(Long id, String ownerId);

    @Query("SELECT q FROM Quote q WHERE q.ownerId = :ownerId " +
           "AND (:leadId IS NULL OR q.leadId = :leadId) " +
           "AND (:status IS NULL OR q.status = :status) " +
           "AND (:currency IS NULL OR q.currency = :currency)")
    Page<Quote> findByOwnerIdWithFilters(
        @Param("ownerId") String ownerId,
        @Param("leadId") Long leadId,
        @Param("status") QuoteStatus status,
        @Param("currency") String currency,
        Pageable pageable
    );

    @Query("SELECT q FROM Quote q WHERE q.ownerId = :ownerId AND (q.rootQuoteId = :rootId OR q.id = :rootId) ORDER BY q.revisionNumber ASC")
    java.util.List<Quote> findRevisionsByRootId(@Param("ownerId") String ownerId, @Param("rootId") Long rootId);

    @Query("SELECT q.currency AS currency, q.status AS status, COUNT(q) AS count, " +
           "COALESCE(SUM(q.subtotal), 0) AS subtotal, COALESCE(SUM(q.tax), 0) AS tax, COALESCE(SUM(q.total), 0) AS total " +
           "FROM Quote q WHERE q.ownerId = :ownerId AND q.isLatest = true " +
           "GROUP BY q.currency, q.status")
    java.util.List<com.urleads.comercial.repository.projection.QuoteCurrencyStatusAggregation> aggregateMetricsByOwner(@Param("ownerId") String ownerId);
}
