package com.urleads.comercial.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "quotes")
public class Quote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lead_id", nullable = false)
    private Long leadId;

    @Column(name = "owner_id", nullable = false, length = 50)
    private String ownerId;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuoteStatus status = QuoteStatus.DRAFT;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal taxRate = new BigDecimal("0.1800");

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal tax = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome_reason", length = 50)
    private QuoteOutcomeReason outcomeReason;

    @Column(name = "outcome_notes", columnDefinition = "TEXT")
    private String outcomeNotes;

    @Column(name = "outcome_date")
    private Instant outcomeDate;

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<QuoteItem> items = new ArrayList<>();

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "root_quote_id")
    private Long rootQuoteId;

    @Column(name = "parent_quote_id")
    private Long parentQuoteId;

    @Column(name = "revision_number", nullable = false)
    private Integer revisionNumber = 1;

    @Column(name = "is_latest", nullable = false)
    private Boolean isLatest = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Quote() {}

    public Quote(Long leadId, String ownerId, String currency, BigDecimal taxRate, String notes) {
        this.leadId = leadId;
        this.ownerId = ownerId;
        this.currency = currency;
        if (taxRate != null) {
            this.taxRate = taxRate.setScale(4, RoundingMode.HALF_UP);
        }
        this.notes = notes;
        this.status = QuoteStatus.DRAFT;
    }

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (status == null) {
            status = QuoteStatus.DRAFT;
        }
        if (version == null) {
            version = 0L;
        }
        if (revisionNumber == null) {
            revisionNumber = 1;
        }
        if (isLatest == null) {
            isLatest = true;
        }
        if (items != null && !items.isEmpty()) {
            recalculateTotals();
        } else if (subtotal == null) {
            subtotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            tax = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public void recalculateTotals() {
        BigDecimal sum = BigDecimal.ZERO;
        for (QuoteItem item : items) {
            if (item.getQuantity() != null && item.getUnitPrice() != null) {
                BigDecimal lineSubtotal = item.getQuantity()
                    .multiply(item.getUnitPrice())
                    .setScale(2, RoundingMode.HALF_UP);
                item.setSubtotal(lineSubtotal);
                sum = sum.add(lineSubtotal);
            }
        }
        this.subtotal = sum.setScale(2, RoundingMode.HALF_UP);
        BigDecimal rate = this.taxRate != null ? this.taxRate : BigDecimal.ZERO;
        this.tax = this.subtotal.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        this.total = this.subtotal.add(this.tax).setScale(2, RoundingMode.HALF_UP);
    }

    public void addItem(QuoteItem item) {
        items.add(item);
        item.setQuote(this);
        recalculateTotals();
    }

    public void removeItem(QuoteItem item) {
        items.remove(item);
        item.setQuote(null);
        recalculateTotals();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getLeadId() {
        return leadId;
    }

    public void setLeadId(Long leadId) {
        this.leadId = leadId;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public QuoteStatus getStatus() {
        return status;
    }

    public void setStatus(QuoteStatus status) {
        this.status = status;
    }

    public boolean isFinal() {
        return status != null && status.isFinal();
    }

    public void transitionTo(QuoteStatus newStatus) {
        if (this.status == newStatus) {
            return;
        }
        if (this.status != null && !this.status.canTransitionTo(newStatus)) {
            throw new IllegalStateException("Transición inválida de estado: no se puede pasar de " + this.status + " a " + newStatus);
        }
        this.status = newStatus;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(BigDecimal taxRate) {
        this.taxRate = taxRate != null ? taxRate.setScale(4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    public BigDecimal getTax() {
        return tax;
    }

    public void setTax(BigDecimal tax) {
        this.tax = tax;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public List<QuoteItem> getItems() {
        return items;
    }

    public void setItems(List<QuoteItem> items) {
        this.items = items != null ? items : new ArrayList<>();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public QuoteOutcomeReason getOutcomeReason() {
        return outcomeReason;
    }

    public void setOutcomeReason(QuoteOutcomeReason outcomeReason) {
        this.outcomeReason = outcomeReason;
    }

    public String getOutcomeNotes() {
        return outcomeNotes;
    }

    public void setOutcomeNotes(String outcomeNotes) {
        this.outcomeNotes = outcomeNotes;
    }

    public Instant getOutcomeDate() {
        return outcomeDate;
    }

    public void setOutcomeDate(Instant outcomeDate) {
        this.outcomeDate = outcomeDate;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Long getRootQuoteId() {
        return rootQuoteId != null ? rootQuoteId : id;
    }

    public void setRootQuoteId(Long rootQuoteId) {
        this.rootQuoteId = rootQuoteId;
    }

    public Long getParentQuoteId() {
        return parentQuoteId;
    }

    public void setParentQuoteId(Long parentQuoteId) {
        this.parentQuoteId = parentQuoteId;
    }

    public Integer getRevisionNumber() {
        return revisionNumber != null ? revisionNumber : 1;
    }

    public void setRevisionNumber(Integer revisionNumber) {
        this.revisionNumber = revisionNumber;
    }

    public Boolean isLatest() {
        return isLatest != null ? isLatest : true;
    }

    public void setLatest(Boolean latest) {
        isLatest = latest;
    }

    /**
     * Genera una nueva revisión inmutable vinculada (v(N+1)) a partir de esta cotización.
     * Bloquea la versión actual desmarcándola como la última (isLatest = false).
     */
    public Quote createNextRevision(String newNotes, BigDecimal newTaxRate) {
        this.isLatest = false;

        BigDecimal tax = newTaxRate != null ? newTaxRate : this.taxRate;
        String notesText = newNotes != null ? newNotes : this.notes;

        Quote nextRevision = new Quote(
            this.leadId,
            this.ownerId,
            this.currency,
            tax,
            notesText
        );

        nextRevision.setRootQuoteId(this.getRootQuoteId());
        nextRevision.setParentQuoteId(this.getId());
        nextRevision.setRevisionNumber(this.getRevisionNumber() + 1);
        nextRevision.setLatest(true);
        nextRevision.setStatus(QuoteStatus.DRAFT);

        // Copia profunda de ítems
        if (this.items != null) {
            for (QuoteItem item : this.items) {
                QuoteItem cloned = new QuoteItem(
                    nextRevision,
                    item.getCatalogItemId(),
                    item.getCode(),
                    item.getName(),
                    item.getDescription(),
                    item.getQuantity(),
                    item.getUnitPrice()
                );
                nextRevision.addItem(cloned);
            }
        }

        nextRevision.recalculateTotals();
        return nextRevision;
    }
}
