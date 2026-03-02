package com.ecommerce.inventoryservice.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Base entity class providing common fields for all entities.
 * Uses JPA callbacks for automatic timestamp management.
 * Includes @Version for optimistic locking on metadata updates.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Optimistic locking version field.
     * JPA automatically increments this on every save().
     * Prevents stale metadata overwrites (e.g., concurrent productName updates).
     * Note: Atomic @Modifying queries bypass this — they handle concurrency via SQL WHERE clauses.
     */
    @Version
    private Long version;

    @Column(updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
