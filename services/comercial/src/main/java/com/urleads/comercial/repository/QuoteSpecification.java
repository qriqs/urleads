package com.urleads.comercial.repository;

import com.urleads.comercial.entity.Quote;
import com.urleads.comercial.entity.QuoteItem;
import com.urleads.comercial.entity.QuoteOutcomeReason;
import com.urleads.comercial.entity.QuoteStatus;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class QuoteSpecification {

    private QuoteSpecification() {}

    public static Specification<Quote> withFilters(
        String ownerId,
        Long leadId,
        QuoteStatus status,
        String currency,
        QuoteOutcomeReason outcomeReason,
        Instant dateFrom,
        Instant dateTo,
        String searchTerm
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Scoping estricto por usuario propietario
            predicates.add(cb.equal(root.get("ownerId"), ownerId));

            // 2. Filtrado por leadId
            if (leadId != null) {
                predicates.add(cb.equal(root.get("leadId"), leadId));
            }

            // 3. Filtrado por estado del ciclo de vida
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            // 4. Filtrado por moneda (case-insensitive)
            if (currency != null && !currency.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("currency")), currency.trim().toUpperCase()));
            }

            // 5. Filtrado por motivo de resultado comercial
            if (outcomeReason != null) {
                predicates.add(cb.equal(root.get("outcomeReason"), outcomeReason));
            }

            // 6. Rango de fechas: dateFrom (createdAt >= dateFrom)
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), dateFrom));
            }

            // 7. Rango de fechas: dateTo (createdAt <= dateTo)
            if (dateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), dateTo));
            }

            // 8. Búsqueda por texto (notas, líneas de detalle o IDs numéricos)
            if (searchTerm != null && !searchTerm.isBlank()) {
                String pattern = "%" + searchTerm.trim().toLowerCase() + "%";

                Subquery<Integer> itemSubquery = query.subquery(Integer.class);
                Root<QuoteItem> itemRoot = itemSubquery.from(QuoteItem.class);
                itemSubquery.select(cb.literal(1));

                Predicate itemMatch = cb.or(
                    cb.like(cb.lower(itemRoot.get("code")), pattern),
                    cb.like(cb.lower(itemRoot.get("name")), pattern),
                    cb.and(
                        cb.isNotNull(itemRoot.get("description")),
                        cb.like(cb.lower(itemRoot.get("description")), pattern)
                    )
                );

                itemSubquery.where(
                    cb.equal(itemRoot.get("quote"), root),
                    itemMatch
                );

                Predicate notesMatch = cb.and(
                    cb.isNotNull(root.get("notes")),
                    cb.like(cb.lower(root.get("notes")), pattern)
                );

                Predicate searchPredicate = cb.or(notesMatch, cb.exists(itemSubquery));

                try {
                    Long numericId = Long.parseLong(searchTerm.trim());
                    searchPredicate = cb.or(
                        searchPredicate,
                        cb.equal(root.get("id"), numericId),
                        cb.equal(root.get("leadId"), numericId)
                    );
                } catch (NumberFormatException ignored) {
                    // Si no es numérico, solo busca en notas e ítems
                }

                predicates.add(searchPredicate);
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
