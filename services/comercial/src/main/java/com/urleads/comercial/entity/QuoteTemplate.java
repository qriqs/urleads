package com.urleads.comercial.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "quote_templates")
public class QuoteTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false, length = 50)
    private String ownerId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal taxRate = new BigDecimal("0.1800");

    @Column(columnDefinition = "TEXT")
    private String notes;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<QuoteTemplateItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public QuoteTemplate() {}

    public QuoteTemplate(
        String ownerId,
        String name,
        String description,
        String currency,
        BigDecimal taxRate,
        String notes
    ) {
        this.ownerId = ownerId;
        this.name = name;
        this.description = description;
        this.currency = currency;
        if (taxRate != null) {
            this.taxRate = taxRate.setScale(4, RoundingMode.HALF_UP);
        }
        this.notes = notes;
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
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public void addItem(QuoteTemplateItem item) {
        items.add(item);
        item.setTemplate(this);
    }

    public void removeItem(QuoteTemplateItem item) {
        items.remove(item);
        item.setTemplate(null);
    }

    public BigDecimal getSubtotal() {
        BigDecimal sum = BigDecimal.ZERO;
        for (QuoteTemplateItem item : items) {
            if (item.getSubtotal() != null) {
                sum = sum.add(item.getSubtotal());
            }
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getTax() {
        BigDecimal rate = this.taxRate != null ? this.taxRate : BigDecimal.ZERO;
        return getSubtotal().multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getTotal() {
        return getSubtotal().add(getTax()).setScale(2, RoundingMode.HALF_UP);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(BigDecimal taxRate) {
        this.taxRate = taxRate != null ? taxRate.setScale(4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public List<QuoteTemplateItem> getItems() {
        return items;
    }

    public void setItems(List<QuoteTemplateItem> items) {
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
}
