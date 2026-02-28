# Day-by-Day Implementation Guide

**Purpose**: Hour-by-hour breakdown of what to implement each day  
**Start Date**: (Choose your start date)  
**Duration**: 5-7 working days

---

## DAY 1: Foundation - Part 1

### Morning (2-3 hours)

#### Task 1.1: Add @Version Annotation
**Time**: 30 minutes

1. Open `domain/entity/BaseEntity.java`
2. Add import: `import jakarta.persistence.Version;`
3. Add field:
```java
@Version
private Long version;
```
4. Save and compile
5. Verify no errors

**Validation**: 
```bash
mvn clean compile
# Should pass with no errors
```

---

#### Task 1.2: Add @Version to All Entities
**Time**: 15 minutes

Since BaseEntity has @Version, all extending entities automatically get it:
- Inventory ✅ (extends BaseEntity)
- StockReservation ✅ (extends BaseEntity)
- StockMovement ✅ (extends BaseEntity)
- Warehouse ✅ (extends BaseEntity)
- WarehouseInventory ✅ (extends BaseEntity)

**Validation**: Run `mvn clean compile` - should pass

---

#### Task 1.3: Add Unique Constraints
**Time**: 1-1.5 hours

**File 1**: `domain/entity/Inventory.java`

Find the `@Table` annotation (around line 20) and update it:

```java
@Table(name = "inventory", 
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_inventory_sku", columnNames = "sku"),
        @UniqueConstraint(name = "uk_inventory_variant", columnNames = {"productId", "variantId"})
    },
    indexes = {
        @Index(name = "idx_inventory_sku", columnList = "sku"),
        @Index(name = "idx_inventory_variant_id", columnList = "variantId"),
        @Index(name = "idx_inventory_product_id", columnList = "productId"),
        @Index(name = "idx_inventory_is_active", columnList = "isActive")
    }
)
```

**File 2**: `domain/entity/Warehouse.java`

Update `@Table` annotation:

```java
@Table(name = "warehouses",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_warehouse_code", columnNames = "warehouseCode")
    },
    indexes = {
        @Index(name = "idx_warehouse_code", columnList = "warehouseCode"),
        @Index(name = "idx_warehouse_is_active", columnList = "isActive")
    }
)
```

**File 3**: `domain/entity/WarehouseInventory.java`

Update `@Table` annotation:

```java
@Table(name = "warehouse_inventory",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_warehouse_inventory", 
                         columnNames = {"warehouseId", "inventoryId"})
    },
    indexes = {
        @Index(name = "idx_wi_warehouse_id", columnList = "warehouseId"),
        @Index(name = "idx_wi_inventory_id", columnList = "inventoryId")
    }
)
```

**Validation**:
```bash
mvn clean compile
# Should pass with no errors
```

---

### Afternoon (2-3 hours)

#### Task 1.4: Create Flyway Migration Directory
**Time**: 15 minutes

1. Create directory: `src/main/resources/db/migration/`
2. Create file: `V1__Initial_Schema.sql`

**Validation**: Directory structure exists

---

#### Task 1.5: Write Flyway Migration Script
**Time**: 2-2.5 hours

**File**: `src/main/resources/db/migration/V1__Initial_Schema.sql`

(See SQL script in inventory-service-complete-plan.md "Database Schema Enhancements")

Copy the complete SQL schema from the planning document into this file.

**Key sections to include**:
- ✅ Inventory table with version, unique constraints, check constraints
- ✅ Stock reservations table
- ✅ Stock movements table
- ✅ Warehouses table
- ✅ Warehouse inventory table

**Validation**:
1. File exists at correct location
2. SQL syntax is valid
3. Contains all 5 tables

---

### End of Day 1

**Checklist**:
- [ ] @Version added to BaseEntity
- [ ] Unique constraints added to Inventory, Warehouse, WarehouseInventory
- [ ] Flyway migration directory created
- [ ] V1__Initial_Schema.sql created with full SQL
- [ ] mvn clean compile passes with no errors

**Blockers**: None

---

## DAY 2: Foundation - Part 2

### Morning (3-4 hours)

#### Task 2.1: Add Atomic SQL Methods to InventoryRepository
**Time**: 1.5 hours

**File**: `domain/repository/InventoryRepository.java`

Add these methods at the end of the interface:

```java
/**
 * Atomic reserve stock - deduct from available, add to reserved.
 * Returns rows updated (1 if successful, 0 if insufficient stock).
 */
@Modifying
@Transactional
@Query("UPDATE Inventory i SET i.availableStock = i.availableStock - :quantity, " +
       "i.reservedStock = i.reservedStock + :quantity " +
       "WHERE i.id = :inventoryId AND i.availableStock >= :quantity")
int atomicReserveStock(@Param("inventoryId") UUID inventoryId, @Param("quantity") int quantity);

/**
 * Atomic release reserved stock - add to available, subtract from reserved.
 */
@Modifying
@Transactional
@Query("UPDATE Inventory i SET i.availableStock = i.availableStock + :quantity, " +
       "i.reservedStock = i.reservedStock - :quantity " +
       "WHERE i.id = :inventoryId")
int atomicReleaseReservation(@Param("inventoryId") UUID inventoryId, @Param("quantity") int quantity);

/**
 * Find by ID with pessimistic WRITE lock.
 */
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT i FROM Inventory i WHERE i.id = :id")
Optional<Inventory> findByIdForUpdate(@Param("id") UUID id);
```

**Required imports**:
```java
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
```

**Validation**:
```bash
mvn clean compile
# Should pass with no errors
```

---

#### Task 2.2: Add Atomic SQL Methods to StockReservationRepository
**Time**: 1.5 hours

**File**: `domain/repository/StockReservationRepository.java`

Add these methods:

```java
/**
 * Find all expired reservations.
 */
@Query("SELECT r FROM StockReservation r WHERE r.expiresAt < :now AND r.status = 'ACTIVE'")
List<StockReservation> findExpiredReservations(@Param("now") Instant now);

/**
 * Find reservations by order ID and status.
 */
List<StockReservation> findByOrderIdAndStatus(String orderId, ReservationStatus status);

/**
 * Atomic confirm reservation (update status).
 */
@Modifying
@Transactional
@Query("UPDATE StockReservation r SET r.status = :newStatus, " +
       "r.fulfilledAt = CURRENT_TIMESTAMP WHERE r.id = :id AND r.status = :currentStatus")
int atomicConfirmReservation(@Param("id") UUID id,
                             @Param("currentStatus") ReservationStatus currentStatus,
                             @Param("newStatus") ReservationStatus newStatus);

/**
 * Atomic release reservation.
 */
@Modifying
@Transactional
@Query("UPDATE StockReservation r SET r.status = :newStatus, " +
       "r.cancelledAt = CURRENT_TIMESTAMP WHERE r.id = :id AND r.status = :currentStatus")
int atomicReleaseReservation(@Param("id") UUID id,
                            @Param("currentStatus") ReservationStatus currentStatus,
                            @Param("newStatus") ReservationStatus newStatus);
```

**Required imports**:
```java
import org.springframework.data.jpa.repository.Modifying;
import com.ecommerce.inventoryservice.domain.enums.ReservationStatus;
import java.time.Instant;
```

**Validation**:
```bash
mvn clean compile
```

---

### Afternoon (2-3 hours)

#### Task 2.3: Create Exception Classes
**Time**: 1 hour

**File 1**: `exception/OptimisticLockException.java`

```java
package com.ecommerce.inventoryservice.exception;

import java.util.UUID;

public class OptimisticLockException extends RuntimeException {
    private final String entityName;
    private final UUID entityId;
    
    public OptimisticLockException(String entityName, UUID entityId, String message) {
        super(message);
        this.entityName = entityName;
        this.entityId = entityId;
    }
    
    public String getEntityName() {
        return entityName;
    }
    
    public UUID getEntityId() {
        return entityId;
    }
}
```

**File 2**: Update `exception/GlobalExceptionHandler.java`

Find the class and add this handler method at the end (before closing brace):

```java
/**
 * Handle optimistic locking failures.
 * Returns 409 Conflict when resource was modified concurrently.
 */
@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
public ResponseEntity<ApiResponse<Void>> handleOptimisticLockingFailure(
        ObjectOptimisticLockingFailureException ex) {
    log.warn("Optimistic locking conflict detected: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiResponse.error("VERSION_CONFLICT", 
                    "Resource was modified by another request. Please retry.")
    );
}
```

**Required imports at top of GlobalExceptionHandler.java**:
```java
import org.springframework.orm.ObjectOptimisticLockingFailureException;
```

**Validation**:
```bash
mvn clean compile
```

---

#### Task 2.4: Test Compilation and Basic Integration
**Time**: 1-1.5 hours

1. Build the project:
```bash
mvn clean install
```

2. If you have Docker MySQL running, test database:
```bash
# Make sure MySQL is running
docker ps | grep mysql

# If running, the migration should auto-apply
# Check logs for Flyway startup
```

3. If build fails:
   - Fix any import errors
   - Check SQL syntax in migration file
   - Review constraint definitions

**Validation**:
```bash
mvn clean install
# Should build successfully
# May have test failures (OK for now, we'll fix those)
```

---

### End of Day 2

**Checklist**:
- [ ] Atomic methods added to InventoryRepository
- [ ] Atomic methods added to StockReservationRepository
- [ ] Exception classes created/updated
- [ ] Exception handler added to GlobalExceptionHandler
- [ ] mvn clean install completes
- [ ] No compilation errors

**Blockers**: None

---

## DAY 3: Concurrency Control & Service Updates

### Full Day (6-8 hours)

#### Task 3.1: Update ReservationService with Atomic Operations
**Time**: 3 hours

**File**: `service/ReservationService.java`

This is where the bulk of the work is. Use the complete implementation from:
→ planner/reservation-service-implementation-guide.md
→ Section: "ReservationService - Complete Implementation"

Key changes:
1. Replace `find() -> modify -> save()` with atomic methods
2. Add version conflict handling
3. Add proper logging
4. Update confirmReservation() to use atomicConfirmReservation()
5. Update releaseReservation() to use atomicReleaseReservation()

**Steps**:
1. Back up current ReservationService.java
2. Replace the service with the complete version from the guide
3. Adjust method names/parameters to match your existing code
4. Update imports as needed
5. Verify all repository methods exist

**Validation**:
```bash
mvn clean compile
# Should compile without errors
```

---

#### Task 3.2: Update InventoryService with Atomic Operations
**Time**: 2 hours

**File**: `service/InventoryService.java`

Look for methods that:
- Call `findBySku()` -> modify stock -> `save()`
- Call `findByVariantId()` -> modify -> `save()`

Replace these with:
- Call to `atomicReserveStock()` or appropriate atomic method
- Check return value (0 = failed, 1 = succeeded)

Key methods to update:
- Any stock reduction operation
- Any stock addition operation
- Reservation-related updates

**Example pattern**:
```java
// OLD - BEFORE
Inventory inventory = repo.findBySku(sku).orElseThrow();
inventory.setAvailableStock(inventory.getAvailableStock() - quantity);
repo.save(inventory);

// NEW - AFTER
int updated = repo.atomicReserveStock(inventory.getId(), quantity);
if (updated == 0) {
    throw new InsufficientStockException(...);
}
```

**Validation**:
```bash
mvn clean compile
```

---

#### Task 3.3: Add Retry Logic (Optional but Recommended)
**Time**: 1-2 hours

If using Spring Retry annotation:

1. Add dependency to pom.xml (if not already there):
```xml
<dependency>
    <groupId>org.springframework.retry</groupId>
    <artifactId>spring-retry</artifactId>
</dependency>
```

2. Add to main application class:
```java
@EnableRetry
public class InventoryServiceApplication {
    // ...
}
```

3. Add to service methods:
```java
@Retryable(
    retryFor = {ObjectOptimisticLockingFailureException.class},
    maxAttempts = 3,
    backoff = @Backoff(delay = 100, multiplier = 2.0)
)
public ReservationResponse confirmReservation(UUID reservationId) {
    // ...
}
```

**Alternative**: Implement manual retry loop (see planner documents)

**Validation**:
```bash
mvn clean compile
```

---

#### Task 3.4: Run Tests
**Time**: 1-2 hours

1. Run unit tests:
```bash
mvn test
```

2. Fix any failures:
   - Update test expectations if behavior changed
   - Fix any mock issues
   - Add missing mocks for new methods

3. Run integration tests:
```bash
mvn verify
```

**Expected**:
- Some tests may fail initially
- Fix them based on error messages
- Document any test updates

**Validation**:
```bash
mvn test
# Should have minimal failures (expect some fixes needed)
```

---

### End of Day 3

**Checklist**:
- [ ] ReservationService updated with atomic operations
- [ ] InventoryService updated with atomic operations
- [ ] Retry logic added (optional)
- [ ] Exception handling works
- [ ] Tests run (some may need fixes)
- [ ] No compilation errors

**Blockers**: None

---

## DAY 4: Testing & Validation

### Full Day (6-8 hours)

#### Task 4.1: Create Concurrency Tests
**Time**: 2-3 hours

**File**: `test/java/com/ecommerce/inventoryservice/service/ConcurrencyTest.java`

```java
package com.ecommerce.inventoryservice.service;

import com.ecommerce.inventoryservice.domain.entity.Inventory;
import com.ecommerce.inventoryservice.domain.repository.InventoryRepository;
import com.ecommerce.inventoryservice.exception.InsufficientStockException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.yml")
class ConcurrencyTest {
    
    @Autowired
    private InventoryRepository inventoryRepository;
    
    @Autowired
    private ReservationService reservationService;
    
    @Test
    void testConcurrentReservationsPrevention() throws InterruptedException {
        // Setup: Create inventory with 100 units
        Inventory inventory = new Inventory();
        inventory.setSku("SKU-CONCURRENT-001");
        inventory.setVariantId(UUID.randomUUID());
        inventory.setProductId(UUID.randomUUID());
        inventory.setAvailableStock(100);
        inventory.setReservedStock(0);
        Inventory saved = inventoryRepository.save(inventory);
        
        // Test: 5 threads try to reserve 30 units each (should prevent 3 from succeeding)
        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<Future<?>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        
        for (int i = 0; i < 5; i++) {
            futures.add(executor.submit(() -> {
                try {
                    int result = inventoryRepository.atomicReserveStock(saved.getId(), 30);
                    if (result > 0) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                }
            }));
        }
        
        // Wait for all to complete
        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();
        
        // Assert: Only 3 should succeed (3*30=90, leaves 10)
        // Last 2 should fail
        assertTrue(successCount.get() <= 3, "Too many reservations succeeded");
        assertTrue(failureCount.get() >= 2, "Not enough reservations failed");
        
        // Verify final state
        Inventory final_inventory = inventoryRepository.findById(saved.getId()).orElseThrow();
        assertEquals(10, final_inventory.getAvailableStock(), 
                    "Available stock should be 10");
        assertEquals(90, final_inventory.getReservedStock(), 
                    "Reserved stock should be 90");
    }
}
```

**Validation**:
```bash
mvn test -Dtest=ConcurrencyTest
# Should pass
```

---

#### Task 4.2: Test Database Constraints
**Time**: 1-2 hours

**File**: `test/java/com/ecommerce/inventoryservice/domain/entity/ConstraintTest.java`

```java
package com.ecommerce.inventoryservice.domain.entity;

import com.ecommerce.inventoryservice.domain.repository.InventoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ConstraintTest {
    
    @Autowired
    private InventoryRepository inventoryRepository;
    
    @Test
    void testUniqueSkuConstraint() {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        
        // Create first inventory
        Inventory inv1 = new Inventory();
        inv1.setSku("SKU-UNIQUE-TEST");
        inv1.setProductId(productId);
        inv1.setVariantId(variantId);
        inventoryRepository.save(inv1);
        
        // Try to create duplicate SKU
        Inventory inv2 = new Inventory();
        inv2.setSku("SKU-UNIQUE-TEST");  // Same SKU
        inv2.setProductId(UUID.randomUUID());
        inv2.setVariantId(UUID.randomUUID());
        
        assertThrows(DataIntegrityViolationException.class, () -> {
            inventoryRepository.save(inv2);
            inventoryRepository.flush();
        }, "Duplicate SKU should violate unique constraint");
    }
    
    @Test
    void testVersionField() {
        Inventory inventory = new Inventory();
        inventory.setSku("SKU-VERSION-TEST");
        inventory.setProductId(UUID.randomUUID());
        inventory.setVariantId(UUID.randomUUID());
        inventory.setAvailableStock(100);
        
        Inventory saved = inventoryRepository.save(inventory);
        
        // Version should be 0 after first save
        assertEquals(0L, saved.getVersion(), "Initial version should be 0");
        
        // Update and save
        saved.setAvailableStock(50);
        Inventory updated = inventoryRepository.save(saved);
        
        // Version should increment
        assertEquals(1L, updated.getVersion(), "Version should increment to 1");
    }
}
```

**Validation**:
```bash
mvn test -Dtest=ConstraintTest
# Should pass
```

---

#### Task 4.3: Integration Test
**Time**: 2-3 hours

**File**: `test/java/com/ecommerce/inventoryservice/integration/ReservationIntegrationTest.java`

Use the complete test from:
→ planner/inventory-service-complete-plan.md
→ Section: "Integration Tests"

Key test scenarios:
- Complete reservation flow (reserve → confirm → fulfill)
- Release reservation flow
- Bulk reservation atomicity
- Expired reservation cleanup
- Concurrent reservations with locking

**Validation**:
```bash
mvn verify -Dtest=ReservationIntegrationTest
# Should pass
```

---

#### Task 4.4: Load Test (Optional but Recommended)
**Time**: 1-2 hours

Quick performance test:

```java
@Test
void loadTestReservations() throws InterruptedException {
    // Setup
    Inventory inventory = // ... create with 10,000 units
    inventoryRepository.save(inventory);
    
    // Load test
    ExecutorService executor = Executors.newFixedThreadPool(50);
    long startTime = System.currentTimeMillis();
    
    for (int i = 0; i < 1000; i++) {
        executor.submit(() -> {
            inventoryRepository.atomicReserveStock(inventory.getId(), 1);
        });
    }
    
    executor.shutdown();
    executor.awaitTermination(5, TimeUnit.MINUTES);
    
    long duration = System.currentTimeMillis() - startTime;
    
    // Assert performance
    assertTrue(duration < 10000, "1000 reservations should complete in < 10 seconds");
}
```

---

### End of Day 4

**Checklist**:
- [ ] Concurrency tests written and passing
- [ ] Constraint tests written and passing
- [ ] Integration tests written and passing
- [ ] Load test executed (performance acceptable)
- [ ] All tests green
- [ ] No critical issues found

**Blockers**: None

---

## DAY 5: Deployment Preparation

### Morning (3-4 hours)

#### Task 5.1: Database Setup Script
**Time**: 1 hour

Create setup documentation:

**File**: `docs/DATABASE_SETUP.md`

```markdown
# Database Setup for Inventory Service

## Prerequisites
- MySQL 8.0+
- Flyway CLI (or let Spring Boot handle it)

## Manual Setup (if needed)
1. Create database:
   ```sql
   CREATE DATABASE inventory_service CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```

2. Create user:
   ```sql
   CREATE USER 'inventory'@'localhost' IDENTIFIED BY 'password';
   GRANT ALL PRIVILEGES ON inventory_service.* TO 'inventory'@'localhost';
   FLUSH PRIVILEGES;
   ```

## Automatic Migration (Spring Boot)
Spring Boot will automatically:
1. Read Flyway migration files from src/main/resources/db/migration/
2. Apply migrations in order (V1__Initial_Schema.sql, etc.)
3. Track migration history in flyway_schema_history table

## Verify Setup
```sql
SHOW TABLES;
-- Should see:
-- - inventory
-- - stock_reservations
-- - stock_movements
-- - warehouses
-- - warehouse_inventory
-- - flyway_schema_history
```
```

---

#### Task 5.2: Update pom.xml
**Time**: 30 minutes

Add/verify these dependencies:

```xml
<!-- Flyway for migrations -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
</dependency>

<!-- Spring Retry (if using @Retryable) -->
<dependency>
    <groupId>org.springframework.retry</groupId>
    <artifactId>spring-retry</artifactId>
</dependency>

<!-- For testing with containers -->
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

**Validation**:
```bash
mvn dependency:tree
# Verify all dependencies present
```

---

#### Task 5.3: Update application.yml
**Time**: 30 minutes

**File**: `src/main/resources/application.yml`

Add Flyway configuration:

```yaml
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
    schemas: inventory_service
```

---

### Afternoon (3-4 hours)

#### Task 5.4: Documentation
**Time**: 2-3 hours

Create/update:

1. **API Documentation** (`docs/API.md`)
   - POST /api/reservations/reserve
   - POST /api/reservations/bulk-reserve
   - POST /api/reservations/check-availability
   - GET /api/reservations/{id}
   - etc.

2. **Architecture Documentation** (`docs/ARCHITECTURE.md`)
   - Database schema explanation
   - Service layer design
   - Controller pattern
   - Concurrency strategy

3. **Deployment Guide** (`docs/DEPLOYMENT.md`)
   - Prerequisites
   - Build steps
   - Database migration
   - Monitoring
   - Troubleshooting

---

#### Task 5.5: Monitoring & Logging
**Time**: 1 hour

Add Micrometer metrics to important methods:

```java
@PostMapping("/reserve")
public ResponseEntity<ApiResponse<ReservationResponse>> reserveStock(
        @Valid @RequestBody ReserveStockRequest request) {
    
    // Add timing metrics
    Timer.Sample sample = Timer.start();
    try {
        ReservationResponse response = reservationService.reserveSingleStock(request);
        sample.stop(Timer.builder("inventory.reservation.reserve")
                .tag("status", "success")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry));
        
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success("201", "Stock reserved successfully", response)
        );
    } catch (Exception e) {
        sample.stop(Timer.builder("inventory.reservation.reserve")
                .tag("status", "failure")
                .tag("error", e.getClass().getSimpleName())
                .register(meterRegistry));
        throw e;
    }
}
```

---

### End of Day 5

**Checklist**:
- [ ] Database setup documentation created
- [ ] pom.xml updated with all required dependencies
- [ ] application.yml configured with Flyway
- [ ] API documentation created
- [ ] Architecture documentation created
- [ ] Deployment guide created
- [ ] Monitoring/metrics added (optional)
- [ ] All tests pass
- [ ] Final mvn clean install successful

---

## Verification Checklist Before Deployment

### Code Quality
- [ ] No compilation warnings
- [ ] All tests passing (100%)
- [ ] No hardcoded values
- [ ] Proper error handling
- [ ] Logging is appropriate

### Database
- [ ] Flyway migration tested
- [ ] Schema creation verified
- [ ] Constraints enforced
- [ ] Indexes created
- [ ] Version field present

### Concurrency
- [ ] @Version annotation present on all entities
- [ ] Atomic methods implemented
- [ ] Pessimistic locks working
- [ ] Exception handlers in place
- [ ] Retry logic tested

### Documentation
- [ ] API documentation complete
- [ ] Database schema documented
- [ ] Deployment guide written
- [ ] Architecture explained
- [ ] Troubleshooting guide included

### Performance
- [ ] Load tests pass
- [ ] Lock wait times acceptable
- [ ] Query performance acceptable
- [ ] Connection pooling configured
- [ ] Metrics configured

### Security
- [ ] @PreAuthorize on admin endpoints
- [ ] Input validation on all endpoints
- [ ] No sensitive data in logs
- [ ] Exception messages don't leak details
- [ ] SQL injection prevention (using @Query)

---

## Post-Deployment Steps

1. **Monitor**
   - Watch application logs for errors
   - Monitor metrics/performance
   - Check database connection pool

2. **Validate**
   - Test reservation flow end-to-end
   - Verify database has correct data
   - Check Flyway migration history

3. **Performance Baseline**
   - Document p50, p95, p99 latencies
   - Document throughput
   - Set up alerting thresholds

4. **Documentation**
   - Update runbooks
   - Document any custom procedures
   - Create troubleshooting guides

---

## Success Criteria

✅ All tasks completed:
- Code compiles without errors
- All tests pass
- Database migrations work
- Concurrency is handled correctly
- Documentation is complete
- Ready for staging deployment

**Estimated Total Time**: 30-40 hours (5-7 days)

**You're ready to build!** 🚀

