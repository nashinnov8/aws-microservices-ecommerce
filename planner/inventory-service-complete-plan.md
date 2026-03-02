# Inventory Service - Complete Implementation Plan

**Date**: February 27, 2026  
**Status**: COMPREHENSIVE ANALYSIS & PLANNING  
**Target**: Production-Ready Microservice  

---

## Table of Contents

1. [Current State Assessment](#current-state-assessment)
2. [Missing Components](#missing-components)
3. [Data Consistency & Locking Strategy](#data-consistency--locking-strategy)
4. [Database Schema Enhancements](#database-schema-enhancements)
5. [Implementation Roadmap](#implementation-roadmap)
6. [Code Quality & Best Practices](#code-quality--best-practices)
7. [Testing Strategy](#testing-strategy)

---

## Current State Assessment

### ✅ What's Already Implemented

1. **Core Entities**
   - `Inventory` - SKU-based inventory tracking with availability and reservation counts
   - `StockReservation` - Order-level stock reservations with expiry management
   - `StockMovement` - Audit trail for all inventory changes
   - `Warehouse` - Physical warehouse locations
   - `WarehouseInventory` - Multi-warehouse inventory distribution (partial)
   - `BaseEntity` - Common audit fields (id, createdAt, updatedAt)

2. **Services**
   - `InventoryService` - Core inventory operations (create, update, check availability)
   - `ReservationService` - Stock reservation management for orders
   - Kafka integration with `ProductEventListener` for product-service events
   - `InventoryEventProducer` for publishing inventory events

3. **REST Controllers**
   - `InventoryController` - Basic CRUD endpoints for inventory
   - `ReservationController` - Reservation management endpoints
   - Security with `@PreAuthorize` annotations
   - Exception handling with `GlobalExceptionHandler`

4. **Concurrency Control**
   - Pessimistic locking with `@Lock(LockModeType.PESSIMISTIC_WRITE)`
   - Repository methods: `findBySkuForUpdate()`, `findByIdForUpdate()`
   - Transactional boundaries with `@Transactional`

5. **Validation & Error Handling**
   - Bean validation annotations (`@NotNull`, `@Min`, etc.)
   - Custom exceptions (InventoryNotFoundException, InsufficientStockException, etc.)
   - Global exception handler with structured error responses

---

## Missing Components

### 🔴 CRITICAL GAPS

#### 1. **Optimistic Locking with @Version Annotation**

**Why It's Needed:**
- Pessimistic locking alone can cause deadlocks and performance issues under high concurrency
- Optimistic locking allows better scalability for frequently-read, rarely-written data
- Prevents concurrent modifications from overwriting each other

**What's Missing:**
- `@Version` annotation on entity fields
- Version increment strategy in update operations
- Handling of `ObjectOptimisticLockingFailureException`

**Files to Modify:**
- `Inventory.java` - Add `@Version private Long version;`
- `StockReservation.java` - Add `@Version private Long version;`
- `StockMovement.java` - Add `@Version private Long version;`
- `Warehouse.java` - Add `@Version private Long version;`
- `WarehouseInventory.java` - Add `@Version private Long version;`

**Implementation Pattern:**
```java
@Entity
public class Inventory extends BaseEntity {
    @Version
    private Long version;
    // ... rest of entity
}
```

**Service Updates:**
- Catch `ObjectOptimisticLockingFailureException` in service methods
- Retry logic with exponential backoff
- Log version conflicts for monitoring

#### 2. **Unique Constraints**

**Current Issues:**
- `sku` has `unique=true` but needs compound constraints
- No unique constraint on `(productId, variantId)` pair
- No unique constraint on warehouse code combinations

**Missing Constraints:**
```java
// Inventory.java
@Table(name = "inventory", uniqueConstraints = {
    @UniqueConstraint(name = "uk_inventory_sku", columnNames = "sku"),
    @UniqueConstraint(name = "uk_inventory_variant", columnNames = {"productId", "variantId"}),
    @UniqueConstraint(name = "uk_inventory_business_key", columnNames = {"sku", "productId"})
}, indexes = { ... })
```

```java
// Warehouse.java
@Table(name = "warehouses", uniqueConstraints = {
    @UniqueConstraint(name = "uk_warehouse_code", columnNames = "warehouseCode")
}, indexes = { ... })
```

```java
// WarehouseInventory.java
@Table(name = "warehouse_inventory", uniqueConstraints = {
    @UniqueConstraint(name = "uk_warehouse_inventory", columnNames = {"warehouseId", "inventoryId"})
}, indexes = { ... })
```

#### 3. **Atomic SQL Operations**

**Current Problem:**
- Stock updates are done in multiple steps (fetch, modify, save)
- Race conditions possible between read and write
- No atomic compare-and-swap operations

**What's Needed:**
- Native SQL queries using `@Query` with UPDATE statements
- Atomic operations for stock adjustments
- Eliminate fetch-modify-save pattern

**Implementation Examples:**

```java
// InventoryRepository.java
@Modifying
@Transactional
@Query("UPDATE Inventory i SET i.availableStock = i.availableStock + :quantity, " +
       "i.reservedStock = i.reservedStock - :reserved WHERE i.sku = :sku")
int atomicAddStock(@Param("sku") String sku, @Param("quantity") int quantity, 
                   @Param("reserved") int reserved);

@Modifying
@Transactional
@Query("UPDATE Inventory i SET i.reservedStock = i.reservedStock + :quantity " +
       "WHERE i.id = :inventoryId AND i.availableStock >= :quantity")
int atomicReserveStock(@Param("inventoryId") UUID inventoryId, @Param("quantity") int quantity);

@Modifying
@Transactional
@Query("UPDATE Inventory i SET i.availableStock = i.availableStock - :quantity " +
       "WHERE i.sku = :sku AND i.availableStock >= :quantity")
int atomicDeductStock(@Param("sku") String sku, @Param("quantity") int quantity);

@Modifying
@Transactional
@Query("UPDATE StockReservation r SET r.status = :newStatus, r.fulfilledAt = CURRENT_TIMESTAMP " +
       "WHERE r.id = :reservationId AND r.status = :currentStatus")
int atomicConfirmReservation(@Param("reservationId") UUID reservationId, 
                             @Param("currentStatus") ReservationStatus currentStatus,
                             @Param("newStatus") ReservationStatus newStatus);
```

#### 4. **Database Migration & Schema Definition**

**Missing:**
- Flyway/Liquibase migration scripts
- Initial schema creation
- Constraints enforcement at DB level

**Create File:** `inventory-service/src/main/resources/db/migration/V1__Initial_Schema.sql`

```sql
-- Inventories table
CREATE TABLE inventory (
    id BINARY(16) PRIMARY KEY COMMENT 'UUID',
    sku VARCHAR(100) NOT NULL UNIQUE COMMENT 'SKU - primary business key',
    variant_id BINARY(16) NOT NULL UNIQUE COMMENT 'Product variant UUID',
    product_id BINARY(16) NOT NULL COMMENT 'Product UUID',
    product_name VARCHAR(255) COMMENT 'Denormalized product name',
    variant_name VARCHAR(255) COMMENT 'Denormalized variant name',
    available_stock INT NOT NULL DEFAULT 0 CHECK (available_stock >= 0),
    reserved_stock INT NOT NULL DEFAULT 0 CHECK (reserved_stock >= 0),
    min_stock_level INT NOT NULL DEFAULT 10,
    max_stock_level INT NOT NULL DEFAULT 1000,
    reorder_point INT NOT NULL DEFAULT 50,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    
    UNIQUE KEY uk_inventory_sku (sku),
    UNIQUE KEY uk_inventory_variant (product_id, variant_id),
    KEY idx_inventory_product_id (product_id),
    KEY idx_inventory_is_active (is_active),
    CONSTRAINT chk_stock_levels CHECK (available_stock + reserved_stock <= max_stock_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Stock reservations table
CREATE TABLE stock_reservations (
    id BINARY(16) PRIMARY KEY COMMENT 'UUID',
    inventory_id BINARY(16) NOT NULL COMMENT 'Reference to inventory',
    order_id VARCHAR(100) NOT NULL COMMENT 'Order UUID or ID',
    quantity INT NOT NULL CHECK (quantity > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, CONFIRMED, RELEASED, FULFILLED',
    expires_at TIMESTAMP(6) NOT NULL COMMENT 'Reservation expiry time',
    fulfilled_at TIMESTAMP(6) COMMENT 'When reservation was fulfilled',
    cancelled_at TIMESTAMP(6) COMMENT 'When reservation was cancelled',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    
    KEY idx_sr_inventory_id (inventory_id),
    KEY idx_sr_order_id (order_id),
    KEY idx_sr_status (status),
    KEY idx_sr_expires_at (expires_at),
    FOREIGN KEY fk_sr_inventory (inventory_id) REFERENCES inventory(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Stock movements table
CREATE TABLE stock_movements (
    id BINARY(16) PRIMARY KEY COMMENT 'UUID',
    inventory_id BINARY(16) NOT NULL COMMENT 'Reference to inventory',
    warehouse_id BINARY(16) COMMENT 'Reference to warehouse (optional)',
    movement_type VARCHAR(20) NOT NULL COMMENT 'RECEIVE, SHIP, ADJUST, etc.',
    quantity INT NOT NULL,
    previous_quantity INT NOT NULL,
    new_quantity INT NOT NULL,
    reason VARCHAR(500) COMMENT 'Movement reason',
    reference_id VARCHAR(100) COMMENT 'External reference (order ID, PO number, etc.)',
    performed_by VARCHAR(100) COMMENT 'User or system that performed movement',
    performed_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    
    KEY idx_sm_inventory_id (inventory_id),
    KEY idx_sm_warehouse_id (warehouse_id),
    KEY idx_sm_movement_type (movement_type),
    KEY idx_sm_performed_at (performed_at),
    FOREIGN KEY fk_sm_inventory (inventory_id) REFERENCES inventory(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Warehouses table
CREATE TABLE warehouses (
    id BINARY(16) PRIMARY KEY COMMENT 'UUID',
    warehouse_code VARCHAR(50) NOT NULL UNIQUE COMMENT 'e.g., WH-SYD-01',
    name VARCHAR(100) NOT NULL COMMENT 'Warehouse name',
    location VARCHAR(255) COMMENT 'Location description',
    address VARCHAR(255),
    city VARCHAR(100),
    state VARCHAR(100),
    country VARCHAR(100),
    zip_code VARCHAR(20),
    capacity INT NOT NULL DEFAULT 10000 CHECK (capacity > 0),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    
    UNIQUE KEY uk_warehouse_code (warehouse_code),
    KEY idx_warehouse_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Warehouse inventory distribution table
CREATE TABLE warehouse_inventory (
    id BINARY(16) PRIMARY KEY COMMENT 'UUID',
    warehouse_id BINARY(16) NOT NULL COMMENT 'Reference to warehouse',
    inventory_id BINARY(16) NOT NULL COMMENT 'Reference to inventory',
    stock_quantity INT NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),
    reserved_quantity INT NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    
    UNIQUE KEY uk_warehouse_inventory (warehouse_id, inventory_id),
    KEY idx_wi_inventory_id (inventory_id),
    FOREIGN KEY fk_wi_warehouse (warehouse_id) REFERENCES warehouses(id) ON DELETE CASCADE,
    FOREIGN KEY fk_wi_inventory (inventory_id) REFERENCES inventory(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## Data Consistency & Locking Strategy

### Pessimistic vs Optimistic Locking Strategy

| Scenario | Approach | Why |
|----------|----------|-----|
| Stock reservation during checkout | **Pessimistic WRITE lock** | High contention, must prevent overselling |
| Inventory metadata updates | **Optimistic version check** | Low contention, high concurrency |
| Warehouse allocation | **Pessimistic WRITE lock** | Critical consistency requirement |
| Stock movement audit | **No lock needed** | Append-only, no conflicts |

### Implementation Strategy

```
┌─────────────────────────────────────────┐
│       Stock Reservation Flow            │
├─────────────────────────────────────────┤
│ 1. Pessimistic WRITE lock on inventory  │
│    @Lock(PESSIMISTIC_WRITE)             │
│ 2. Check available_stock >= quantity    │
│ 3. Atomic UPDATE to reserve stock       │
│ 4. Create reservation record            │
│ 5. Publish event                        │
│ 6. Lock released (transaction commits)  │
└─────────────────────────────────────────┘

┌─────────────────────────────────────────┐
│    Metadata Update Flow                 │
├─────────────────────────────────────────┤
│ 1. Load inventory (no lock)             │
│ 2. Modify productName, variantName      │
│ 3. Save - JPA checks @Version           │
│ 4. On version conflict:                 │
│    - Catch ObjectOptimisticLocking      │
│    - Retry with exponential backoff     │
│    - Max 3 retries                      │
└─────────────────────────────────────────┘
```

---

## Database Schema Enhancements

### 1. **Add @Version to All Entities**

**Files:**
- `BaseEntity.java` - Add version field to parent (preferred approach)

```java
@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version  // ADD THIS
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
```

### 2. **Add Unique Constraints**

**Files to Modify:**
- `Inventory.java`
- `Warehouse.java`
- `WarehouseInventory.java`

### 3. **Add Check Constraints at DB Level**

Constraints to add:
- `available_stock >= 0`
- `reserved_stock >= 0`
- `available_stock + reserved_stock <= max_stock_level`
- `capacity > 0`

### 4. **Create Flyway Migration**

**File:** `src/main/resources/db/migration/V1__Initial_Schema.sql`

(See schema SQL above)

---

## Implementation Roadmap

### Phase 1: Foundation (Days 1-2)

#### Task 1.1: Add @Version Annotation
- [ ] Modify `BaseEntity.java` to include `@Version private Long version;`
- [ ] All entities automatically get versioning
- [ ] Validate compilation
- [ ] **Expected Time:** 30 minutes

#### Task 1.2: Add Unique Constraints
- [ ] `Inventory.java` - Add compound unique constraints
- [ ] `Warehouse.java` - Add unique constraint
- [ ] `WarehouseInventory.java` - Add compound constraint
- [ ] **Expected Time:** 1 hour

#### Task 1.3: Create Flyway Migration
- [ ] Create `V1__Initial_Schema.sql` with complete schema
- [ ] Add check constraints at database level
- [ ] Test migration locally
- [ ] **Expected Time:** 2 hours

#### Task 1.4: Add Atomic SQL Methods to Repositories
- [ ] `InventoryRepository.java` - Add atomic UPDATE methods
- [ ] `StockReservationRepository.java` - Add atomic UPDATE methods
- [ ] Test with integration tests
- [ ] **Expected Time:** 2 hours

### Phase 2: Concurrency Control (Days 2-3)

#### Task 2.1: Implement Optimistic Locking Exception Handling
- [ ] Create `OptimisticLockRetryAspect` or add retry logic to services
- [ ] Catch `ObjectOptimisticLockingFailureException`
- [ ] Implement exponential backoff retry (max 3 attempts)
- [ ] **Expected Time:** 2 hours

#### Task 2.2: Update ReservationService
- [ ] Replace fetch-modify-save with atomic operations
- [ ] Use `findBySkuForUpdate()` (pessimistic)
- [ ] Call atomic UPDATE methods
- [ ] Handle version conflicts
- [ ] **Expected Time:** 2 hours

#### Task 2.3: Update InventoryService
- [ ] Replace all stock updates with atomic methods
- [ ] Handle optimistic lock exceptions
- [ ] Add logging for version conflicts
- [ ] **Expected Time:** 2 hours

#### Task 2.4: Update Exception Handler
- [ ] Add handler for `ObjectOptimisticLockingFailureException`
- [ ] Return appropriate HTTP status (409 Conflict)
- [ ] Log version conflicts for monitoring
- [ ] **Expected Time:** 1 hour

### Phase 3: Testing & Validation (Day 4)

#### Task 3.1: Integration Tests
- [ ] Create `ReservationConcurrencyTest`
- [ ] Test concurrent reservations on same inventory
- [ ] Verify version incrementing
- [ ] **Expected Time:** 2 hours

#### Task 3.2: Load Testing
- [ ] Simulate high concurrent stock reservations
- [ ] Verify pessimistic lock effectiveness
- [ ] Monitor query performance
- [ ] **Expected Time:** 1 hour

#### Task 3.3: Database Tests
- [ ] Verify unique constraints enforced
- [ ] Verify check constraints work
- [ ] Test constraint violation error messages
- [ ] **Expected Time:** 1 hour

---

## Code Quality & Best Practices

### 1. **Controller Best Practices**

Current state is ✅ GOOD:
- Controllers only receive requests and delegate to services
- Thin controllers that don't contain business logic
- Consistent API response format using `ApiResponse`

**Current ReservationController Pattern (CORRECT):**
```java
@PostMapping("/reserve")
public ResponseEntity<ApiResponse<ReservationResponse>> reserveStock(
        @Valid @RequestBody ReserveStockRequest request) {
    // Only HTTP concerns here
    log.info("POST /api/reservations/reserve - Order: {}", request.orderId());
    
    // Delegate to service
    ReservationResponse response = reservationService.reserveSingleStock(request);
    
    return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse.success("201", "Stock reserved successfully", response)
    );
}
```

### 2. **Service Layer Pattern**

**What's Working:**
- Transactional boundaries clearly defined
- Business logic in services, not controllers
- Pessimistic locking in reservation operations

**What Needs Improvement:**
- Add atomic SQL operations to reduce transaction time
- Implement proper exception handling for version conflicts
- Add circuit breaker for Kafka producer failures

### 3. **Repository Pattern**

**Current Implementation:**
- Using Spring Data JPA correctly
- Custom `@Lock` annotations for pessimistic locking
- Named queries for complex operations

**Improvements Needed:**
- Add `@Modifying` methods for atomic updates
- Add batch operations for bulk inserts
- Consider query optimization with projections

### 4. **Entity Design**

**Issues to Address:**
- Add `@Version` for optimistic locking
- Add proper `@UniqueConstraint` annotations
- Consider JPA `@ElementCollection` for denormalized fields

### 5. **Error Handling**

**Create New Exception Class:**
```java
// exception/OptimisticLockException.java
public class OptimisticLockException extends RuntimeException {
    private final String entityName;
    private final UUID entityId;
    
    public OptimisticLockException(String entityName, UUID entityId, String message) {
        super(message);
        this.entityName = entityName;
        this.entityId = entityId;
    }
}
```

**Add Handler to GlobalExceptionHandler:**
```java
@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
public ResponseEntity<ApiResponse<Void>> handleOptimisticLockingFailure(
        ObjectOptimisticLockingFailureException ex) {
    log.warn("Optimistic locking conflict: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiResponse.error("VERSION_CONFLICT", 
                "Resource was modified by another request. Please retry.")
    );
}
```

### 6. **Transaction Management**

**Best Practices to Follow:**
- Keep transactions as short as possible
- Use `@Transactional(readOnly = true)` for read operations
- Use `@Modifying` for update operations to optimize batch operations

```java
// ❌ BAD - Long transaction holding locks
@Transactional
public void reserveStock(String orderId, List<Item> items) {
    for (Item item : items) {
        Inventory inventory = repo.findBySkuForUpdate(item.sku());
        inventory.setAvailableStock(inventory.getAvailableStock() - item.qty);
        repo.save(inventory);  // Each save commits its own lock
        kafkaProducer.sendEvent(...);  // Kafka call inside transaction!
    }
}

// ✅ GOOD - Short transaction, async events
@Transactional
public void reserveStock(String orderId, List<Item> items) {
    List<String> events = new ArrayList<>();
    
    for (Item item : items) {
        // Lock, check, update atomically
        int updated = repo.atomicReserveStock(item.sku(), item.qty);
        if (updated == 0) throw new InsufficientStockException(...);
        events.add(createEvent(item));
    }
    
    // Publish events after transaction commits
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void publishEvents(List<String> events) {
        events.forEach(kafkaProducer::send);
    }
}
```

---

## Testing Strategy

### 1. **Unit Tests**

Files to create:
- `test/java/com/ecommerce/inventoryservice/service/InventoryServiceTest.java`
- `test/java/com/ecommerce/inventoryservice/service/ReservationServiceTest.java`

```java
class ReservationServiceTest {
    
    @Test
    @DisplayName("Should reserve stock and update inventory atomically")
    void testAtomicReservation() {
        // Given
        Inventory inventory = new Inventory("SKU-001", variant, product, "Product", "Variant");
        inventory.setAvailableStock(100);
        
        // When
        reservationService.reserveSingleStock(
            new ReserveStockRequest("SKU-001", "ORDER-123", 50)
        );
        
        // Then
        Inventory updated = inventoryRepo.findBySku("SKU-001").get();
        assertEquals(50, updated.getAvailableStock());
        assertEquals(50, updated.getReservedStock());
    }
}
```

### 2. **Concurrency Tests**

File: `test/java/com/ecommerce/inventoryservice/service/ConcurrencyTest.java`

```java
class ConcurrencyTest {
    
    @Test
    @DisplayName("Should prevent overselling under concurrent reservations")
    void testConcurrentReservationsPrevention() throws InterruptedException {
        // Given
        Inventory inventory = new Inventory(...);
        inventory.setAvailableStock(100);
        
        // When - 3 threads try to reserve 50 each
        ExecutorService executor = Executors.newFixedThreadPool(3);
        List<Future<?>> futures = new ArrayList<>();
        
        for (int i = 0; i < 3; i++) {
            futures.add(executor.submit(() -> {
                reservationService.reserveSingleStock(
                    new ReserveStockRequest("SKU-001", "ORDER-" + i, 50)
                );
            }));
        }
        
        // Then - At least one should fail
        int successCount = 0;
        for (Future<?> future : futures) {
            try {
                future.get();
                successCount++;
            } catch (ExecutionException e) {
                assertTrue(e.getCause() instanceof InsufficientStockException);
            }
        }
        
        assertTrue(successCount <= 2);
    }
}
```

### 3. **Integration Tests**

File: `test/java/com/ecommerce/inventoryservice/integration/ReservationIntegrationTest.java`

```java
@SpringBootTest
@Testcontainers
class ReservationIntegrationTest {
    
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));
    
    @Test
    void testCompleteReservationFlow() {
        // Given
        initializeInventory("SKU-001", 100);
        
        // When
        ReservationResponse reservation = controller.reserveStock(
            new ReserveStockRequest("SKU-001", "ORDER-001", 50)
        );
        
        // Then
        assertEquals(ReservationStatus.ACTIVE, reservation.status());
        
        // And when
        controller.confirmReservation(reservation.reservationId());
        
        // Then
        StockReservation confirmed = repo.findById(reservation.reservationId()).get();
        assertEquals(ReservationStatus.CONFIRMED, confirmed.getStatus());
    }
}
```

---

## Remaining DTOs & Models

### DTOs to Verify/Create

```java
// dto/inventory/StockInfoResponse.java
public record StockInfoResponse(
    UUID id,
    String sku,
    UUID variantId,
    UUID productId,
    String productName,
    String variantName,
    int availableStock,
    int reservedStock,
    int totalStock,  // availableStock + reservedStock
    boolean isLowStock,
    int minStockLevel,
    int maxStockLevel,
    int reorderPoint,
    boolean isActive,
    Instant createdAt,
    Instant updatedAt
) {}

// dto/inventory/CreateInventoryRequest.java
public record CreateInventoryRequest(
    @NotBlank String sku,
    @NotNull UUID variantId,
    @NotNull UUID productId,
    @NotBlank String productName,
    @NotBlank String variantName,
    @Min(0) int initialStock
) {}

// dto/inventory/UpdateStockRequest.java
public record UpdateStockRequest(
    @NotNull StockOperation operation,  // ADD, SUBTRACT, SET
    @Min(0) int quantity,
    String reason,
    @Email String performedBy
) {}

// dto/inventory/BulkStockCheckRequest.java
public record BulkStockCheckRequest(
    List<StockCheckItem> items
) {
    public record StockCheckItem(
        @NotBlank String sku,
        @Min(1) int requestedQuantity
    ) {}
}

// dto/inventory/BulkStockCheckResponse.java
public record BulkStockCheckResponse(
    List<AvailabilityResult> results,
    boolean allAvailable
) {
    public record AvailabilityResult(
        String sku,
        int requestedQuantity,
        int availableQuantity,
        boolean isAvailable,
        int shortfall  // If not available
    ) {}
}

// dto/reservation/ReserveStockRequest.java
public record ReserveStockRequest(
    @NotBlank String sku,
    @NotBlank String orderId,
    @Min(1) int quantity
) {}

// dto/reservation/ReservationResponse.java
public record ReservationResponse(
    UUID reservationId,
    String sku,
    String orderId,
    int quantity,
    ReservationStatus status,
    Instant expiresAt,
    Instant createdAt
) {}

// dto/reservation/BulkReserveStockRequest.java
public record BulkReserveStockRequest(
    @NotBlank String orderId,
    @NotEmpty List<ReservationItem> items
) {
    public record ReservationItem(
        @NotBlank String sku,
        @Min(1) int quantity
    ) {}
}

// dto/reservation/BulkReservationResponse.java
public record BulkReservationResponse(
    String orderId,
    List<ReservationResponse> reservations,
    boolean allSuccessful,
    Instant expiresAt
) {}

// dto/reservation/UpdateReservationRequest.java
public record UpdateReservationRequest(
    @NotBlank String action,  // "confirm", "release", "fulfill"
    String reason
) {}

// dto/warehouse/WarehouseResponse.java
public record WarehouseResponse(
    UUID id,
    String warehouseCode,
    String name,
    String location,
    String address,
    String city,
    String state,
    String country,
    String zipCode,
    int capacity,
    boolean isActive,
    Instant createdAt
) {}

// dto/event/InventoryEvent.java
public record InventoryEvent(
    String eventType,  // STOCK_RESERVED, STOCK_CONFIRMED, STOCK_RELEASED, etc.
    UUID inventoryId,
    String sku,
    UUID variantId,
    UUID productId,
    int quantity,
    String orderId,
    Instant timestamp
) {}

// dto/event/ProductEvent.java
public record ProductEvent(
    String eventType,  // VARIANT_CREATED, VARIANT_UPDATED, VARIANT_DELETED, etc.
    UUID productId,
    UUID variantId,
    String sku,
    String productName,
    String variantName,
    Instant timestamp
) {}
```

---

## Missing Service Methods

### InventoryService - Methods to Implement/Verify

```java
// Query operations
public Inventory getInventoryBySku(String sku);
public List<Inventory> getInventoriesByProductId(UUID productId);
public StockInfoResponse getStockBySku(String sku);
public List<StockInfoResponse> getStocksByProductId(UUID productId);
public List<LowStockItemResponse> getLowStockItems();

// Create operations
public Inventory createInventory(CreateInventoryRequest request);
public void initializeInventory(String sku, UUID variantId, UUID productId, 
                                String productName, String variantName, int initialStock);

// Update operations
public StockInfoResponse updateStock(String sku, UpdateStockRequest request);
public Inventory updateInventoryMetadata(UUID variantId, String productName, String variantName);

// Deactivation
public void deactivateInventory(String sku);

// Availability checks
public BulkStockCheckResponse checkAvailability(BulkStockCheckRequest request);

// Warehouse operations
public void allocateToWarehouse(UUID inventoryId, UUID warehouseId, int quantity);
public void transferBetweenWarehouses(UUID inventoryId, UUID fromWarehouse, 
                                      UUID toWarehouse, int quantity);
```

### ReservationService - Methods to Implement/Verify

```java
// Single and bulk reservations
public ReservationResponse reserveSingleStock(ReserveStockRequest request);
public BulkReservationResponse reserveBulkStock(BulkReserveStockRequest request);

// Reservation status changes
public ReservationResponse confirmReservation(UUID reservationId);
public ReservationResponse releaseReservation(UUID reservationId, String reason);
public ReservationResponse fulfillReservation(UUID reservationId);

// Queries
public ReservationResponse getReservationById(UUID reservationId);
public List<ReservationResponse> getOrderReservationResponses(String orderId);
public List<ReservationResponse> getActiveReservations();
public List<ReservationResponse> getExpiredReservations();

// Cleanup
public void cleanupExpiredReservations();

// Availability checks
public BulkStockCheckResponse checkAvailability(BulkStockCheckRequest request);
```

---

## Kafka Integration Points

### Events Inventory-Service Produces

1. **StockReserved**
   - Topics: `inventory-events`
   - Consumers: order-service, notification-service

2. **StockReleased**
   - When reservation cancelled or expires
   
3. **StockMovement**
   - Recording all stock changes for audit

4. **LowStockAlert**
   - When inventory falls below reorder point

### Events Inventory-Service Consumes

1. **ProductEvent** (from product-service)
   - VARIANT_CREATED → Initialize inventory
   - VARIANT_UPDATED → Update metadata
   - VARIANT_DELETED → Deactivate inventory

2. **OrderEvent** (from order-service - optional)
   - ORDER_CANCELLED → Release reservations

---

## Configuration & Deployment

### pom.xml Dependencies to Add/Verify

```xml
<!-- Flyway for database migrations -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
</dependency>

<!-- For version conflict handling -->
<dependency>
    <groupId>org.springframework.retry</groupId>
    <artifactId>spring-retry</artifactId>
</dependency>

<!-- For concurrent testing -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mysql</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>
```

### Application Configuration

```yaml
# application.yml
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # Don't auto-create, use Flyway
    properties:
      hibernate:
        jdbc:
          batch_size: 20
          fetch_size: 50
        order_inserts: true
        order_updates: true
  
  flyway:
    enabled: true
    locations: classpath:db/migration
    baselineOnMigrate: true
    
  retry:
    enabled: true
```

---

## Security Considerations

1. **Role-Based Access Control**
   - ✅ Already implemented with `@PreAuthorize`
   - Admin/InventoryManager for creation/deletion
   - Warehouse staff for stock updates

2. **Audit Trail**
   - ✅ StockMovement table tracks all changes
   - Include `performedBy` user info

3. **Data Isolation**
   - Ensure users can only access their warehouses
   - Add tenant-awareness if multi-tenant

4. **API Rate Limiting**
   - Consider adding rate limits to prevent abuse
   - Especially for bulk operations

---

## Performance Optimization

### Query Optimization

1. **Stock Check Query - Optimize with Projection**
```java
@Query("SELECT new com.ecommerce.inventoryservice.dto.inventory.AvailabilityResult(" +
       "i.sku, i.variantId, i.availableStock, i.maxStockLevel) " +
       "FROM Inventory i WHERE i.sku IN :skus AND i.isActive = true")
List<AvailabilityResult> checkAvailabilityOptimized(@Param("skus") List<String> skus);
```

2. **Caching Strategy**
```java
@Cacheable(value = "inventory", key = "#sku", unless = "#result == null")
public Inventory getInventoryBySku(String sku) { ... }

// Invalidate on updates
@CacheEvict(value = "inventory", key = "#sku")
public void updateInventory(String sku, ...) { ... }
```

3. **Connection Pooling**
   - HikariCP (default in Spring Boot)
   - Configure pool size based on concurrent reservations

### Index Strategy

Indexes to create (in Flyway migration):
```sql
CREATE INDEX idx_inventory_sku ON inventory(sku);
CREATE INDEX idx_inventory_is_active ON inventory(is_active);
CREATE INDEX idx_sr_inventory_id ON stock_reservations(inventory_id);
CREATE INDEX idx_sr_order_id ON stock_reservations(order_id);
CREATE INDEX idx_sr_expires_at ON stock_reservations(expires_at);
CREATE INDEX idx_sm_inventory_id ON stock_movements(inventory_id);
CREATE INDEX idx_sm_performed_at ON stock_movements(performed_at);
```

---

## Monitoring & Alerting

### Metrics to Track

1. **Stock Reservation Metrics**
   - Reservations per minute
   - Reservation success rate
   - Reservation expiry rate
   - Average reservation duration

2. **Lock Contention**
   - Pessimistic lock wait times
   - Optimistic lock retry rate
   - Lock timeout rate

3. **Business Metrics**
   - Low stock alerts
   - Overselling incidents
   - Stock movement count
   - Inventory accuracy

### Logging Strategy

```java
log.info("Stock reserved: sku={}, quantity={}, orderId={}, reservationId={}", 
         sku, quantity, orderId, reservationId);
         
log.warn("Version conflict on inventory: {}, retrying... (attempt {})", 
         inventoryId, attempt);
         
log.error("Failed to reserve stock after retries: sku={}, orderId={}", 
         sku, orderId, exception);
```

---

## Migration Path from Current State

### If Database Already Exists

1. **Backup current database**
   ```bash
   mysqldump -u user -p db_name > backup.sql
   ```

2. **Create Flyway migrations incrementally**
   - V1__Initial_Schema.sql - Current state
   - V2__Add_Versions.sql - Add @Version column
   - V3__Add_Constraints.sql - Add unique/check constraints
   - V4__Add_Indexes.sql - Add performance indexes

3. **Gradual Rollout**
   - Deploy read-only endpoints first
   - Deploy atomic update methods
   - Enable optimistic locking gradually
   - Monitor for issues

---

## Summary Checklist

### Phase 1: Foundation ✅
- [ ] Add @Version to BaseEntity
- [ ] Add unique constraints to entities
- [ ] Create Flyway migration script
- [ ] Add atomic SQL methods to repositories

### Phase 2: Concurrency Control ✅
- [ ] Implement optimistic lock retry logic
- [ ] Update ReservationService with atomic operations
- [ ] Update InventoryService with atomic operations
- [ ] Add exception handler for version conflicts

### Phase 3: Testing ✅
- [ ] Create concurrency tests
- [ ] Create integration tests
- [ ] Load testing
- [ ] Database constraint tests

### Phase 4: Deployment ✅
- [ ] Update pom.xml with new dependencies
- [ ] Update application configuration
- [ ] Test in staging environment
- [ ] Monitor metrics after deployment

---

## Additional Resources

### Spring Data JPA Locking
- https://docs.spring.io/spring-data/jpa/docs/current/reference/html/#reference.repositories.query-methods

### Concurrency Control Patterns
- https://en.wikipedia.org/wiki/Optimistic_concurrency_control
- https://en.wikipedia.org/wiki/Pessimistic_concurrency_control

### Flyway Migrations
- https://flywaydb.org/

### Testing with Testcontainers
- https://www.testcontainers.org/

---

## Contact & Support

For questions or clarifications about this plan, refer to:
- Spring Boot Documentation
- Project Architecture ADRs
- Team Technical Guidelines

**Last Updated:** February 27, 2026

