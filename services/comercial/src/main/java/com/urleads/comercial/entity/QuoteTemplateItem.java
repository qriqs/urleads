package com.urleads.comercial.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "quote_template_items")
public class QuoteTemplateItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private QuoteTemplate template;

    @Column(name = "catalog_item_id")
    private Long catalogItemId;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    public QuoteTemplateItem() {}

    public QuoteTemplateItem(
        QuoteTemplate template,
        Long catalogItemId,
        String code,
        String name,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice
    ) {
        this.template = template;
        this.catalogItemId = catalogItemId;
        this.code = code;
        this.name = name;
        this.description = description;
        this.quantity = quantity != null ? quantity.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ONE;
        this.unitPrice = unitPrice != null ? unitPrice.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        this.subtotal = this.quantity.multiply(this.unitPrice).setScale(2, RoundingMode.HALF_UP);
    }

    public static QuoteTemplateItem fromCatalogSnapshot(
        QuoteTemplate template,
        CatalogItem catalogItem,
        BigDecimal quantity,
        BigDecimal overrideUnitPrice
    ) {
        BigDecimal price = overrideUnitPrice != null ? overrideUnitPrice : catalogItem.getUnitPrice();
        return new QuoteTemplateItem(
            template,
            catalogItem.getId(),
            catalogItem.getCode(),
            catalogItem.getName(),
            catalogItem.getDescription(),
            quantity,
            price
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public QuoteTemplate getTemplate() {
        return template;
    }

    public void setTemplate(QuoteTemplate template) {
        this.template = template;
    }

    public Long getCatalogItemId() {
        return catalogItemId;
    }

    public void setCatalogItemId(Long catalogItemId) {
        this.catalogItemId = catalogItemId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
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

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity != null ? quantity.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ONE;
        recalculateSubtotal();
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice != null ? unitPrice.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        recalculateSubtotal();
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal != null ? subtotal.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    public void recalculateSubtotal() {
        if (this.quantity != null && this.unitPrice != null) {
            this.subtotal = this.quantity.multiply(this.unitPrice).setScale(2, RoundingMode.HALF_UP);
        }
    }
}
