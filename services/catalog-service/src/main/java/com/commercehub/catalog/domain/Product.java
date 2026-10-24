package com.commercehub.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "products")
@Getter
public class Product {

    // 17 integer digits provide a wide retail-price range while preserving two PEN decimals.
    private static final int PRICE_PRECISION = 19;
    private static final int PRICE_SCALE = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64, unique = true)
    private String sku;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 2000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency;

    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    @Column(nullable = false)
    private boolean published;

    protected Product() {
    }

    public Product(String sku, String name, String description, Category category,
                   BigDecimal price, String imageUrl, boolean published) {
        this.sku = requireText(sku, "sku");
        this.name = requireText(name, "name");
        this.description = description;
        this.category = Objects.requireNonNull(category, "category must not be null");
        this.price = requireValidPrice(price);
        this.currency = Currency.PEN;
        this.imageUrl = imageUrl;
        this.published = published;
    }

    public void changePrice(BigDecimal price) {
        this.price = requireValidPrice(price);
    }

    public void publish() {
        this.published = true;
    }

    public void unpublish() {
        this.published = false;
    }

    private static BigDecimal requireValidPrice(BigDecimal value) {
        Objects.requireNonNull(value, "price must not be null");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("price must be positive");
        }
        BigDecimal normalized = value.stripTrailingZeros();
        int integerDigits = Math.max(0, normalized.precision() - normalized.scale());
        if (normalized.scale() > PRICE_SCALE || integerDigits > PRICE_PRECISION - PRICE_SCALE) {
            throw new IllegalArgumentException("price must fit NUMERIC(19,2)");
        }
        return value;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
