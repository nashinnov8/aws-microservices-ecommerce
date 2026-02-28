# Inventory Service - Quick Reference Guide

**Status**: Quick Implementation Checklist  
**Last Updated**: February 27, 2026

---

## What You're Missing - Ranked by Priority

### 🔴 CRITICAL (Must Have Before Production)

#### 1. **@Version Annotation (Pessimistic Locking Companion)**
- **Why**: Prevent data loss from concurrent writes
- **Impact**: HIGH - Data consistency issue
- **Effort**: 30 minutes
- **Status**: ❌ NOT IMPLEMENTED

```java
@MappedSuperclass
public abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Version  // ← ADD THIS
    private Long version;
    
    // ... rest of fields
}
```

**Action**: Add `@Version private Long version;` to `BaseEntity.java`

---

#### 2. **Atomic SQL Operations**
- **Why**: Current code uses fetch-modify-save pattern (race condition prone)
- **Impact**: HIGH - Stock can be oversold under concurrent load
- **Effort**: 2-3 hours
- **Status**: ❌ NOT IMPLEMENTED

```java
@Modifying
@Transactional
@Query("UPDATE Inventory i SET i.availableStock = i.availableStock - :quantity " +
       "WHERE i.sku = :sku AND i.availableStock >= :quantity")
int atomicDeductStock(@Param("sku") String sku, @Param("quantity") int quantity);
```

**Action**: Add atomic UPDATE methods to InventoryRepository and StockReservationRepository

---

#### 3. **Unique Constraints**
- **Why**: Database-level enforcement of business rules
- **Impact**: HIGH - Prevent duplicate SKUs, invalid data states
- **Effort**: 1 hour
- **Status**: ❌ PARTIALLY IMPLEMENTED (SKU has unique=true, but needs compound constraints)

```java
@Table(name = "inventory", uniqueConstraints = {
    @UniqueConstraint(name = "uk_inventory_sku", columnNames = "sku"),
    @UniqueConstraint(name = "uk_inventory_variant", columnNames = {"productId", "variantId"}),
}, indexes = { ... })
public class Inventory extends BaseEntity { ... }
```

**Action**: Add `uniqueConstraints` to all entities in `@Table` annotation

---

#### 4. **Flyway Database Migrations**
- **Why**: Version control for database schema, reproducible deployments
- **Impact**: HIGH - Deployment and rollback safety
- **Effort**: 2 hours
- **Status**: ❌ NOT IMPLEMENTED

```sql
-- File: src/main/resources/db/migration/V1__Initial_Schema.sql
CREATE TABLE inventory (
    id BINARY(16) PRIMARY KEY,
    sku VARCHAR(100) NOT NULL UNIQUE,
    version BIGINT NOT NULL DEFAULT 0,
    -- ... more columns
);
```

**Action**: Create `V1__Initial_Schema.sql` with complete schema

---

#### 5. **Exception Handler for Version Conflicts**
- **Why**: Handle OptimisticLockingFailureException gracefully
- **Impact**: MEDIUM - API will crash without proper handling
- **Effort**: 30 minutes
- **Status**: ❌ NOT IMPLEMENTED

```java
@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
public ResponseEntity<ApiResponse<Void>> handleOptimisticLockingFailure(
        ObjectOptimisticLockingFailureException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiResponse.error("VERSION_CONFLICT", "Resource was modified concurrently")
    );
}
```

**Action**: Add handler to `GlobalExceptionHandler.java`

---

### 🟡 HIGH (Important for Production Readiness)

#### 6. **Retry Logic for Optimistic Locks**
- **Why**: Automatically retry on version conflicts
- **Impact**: MEDIUM - Improves reliability under concurrent load
- **Effort**: 1-2 hours
- **Status**: ❌ NOT IMPLEMENTED

```java
@Transactional
public ReservationResponse reserveSingleStock(ReserveStockRequest request) {
    for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
        try {
            return attemptReservation(request);
        } catch (ObjectOptimisticLockingFailureException e) {
            if (attempt == MAX_RETRIES - 1) throw e;
            Thread.sleep((long) Math.pow(2, attempt) * 100);  // Exponential backoff
        }
    }
}
```

**Action**: Add retry logic to service methods or use @Retryable annotation

---

#### 7. **Warehouse Inventory Distribution (Optional but Planned)**
- **Why**: Track stock across multiple warehouses
- **Impact**: MEDIUM - Required for distributed inventory
- **Effort**: 3-4 hours
- **Status**: ✅ PARTIALLY IMPLEMENTED (Entity exists but not integrated into service)

**Action**: Implement WarehouseInventoryService for allocation and transfers

---

#### 8. **Stock Movement Audit Trail**
- **Why**: Track all inventory changes for compliance and debugging
- **Impact**: MEDIUM - Good for audit and troubleshooting
- **Effort**: 2 hours
- **Status**: ⚠️ PARTIAL (Entity exists, but not used consistently)

**Action**: Add StockMovement creation to all stock update operations

---

### 🟢 MEDIUM (Recommended)

#### 9. **Integration with Kafka Events**
- **Why**: Sync with product-service for inventory initialization
- **Impact**: LOW - Fallback is manual inventory creation
- **Effort**: 1 hour
- **Status**: ⚠️ PARTIAL (ProductEventListener exists but may have bugs)

**Verify**:
- ProductEventListener correctly handles VARIANT_CREATED
- Events are published after reservations
- Error handling in event processing

---

#### 10. **Caching Layer**
- **Why**: Reduce database load for stock checks
- **Impact**: LOW - Performance optimization
- **Effort**: 1-2 hours
- **Status**: ❌ NOT IMPLEMENTED

```java
@Cacheable(value = "inventory", key = "#sku", unless = "#result == null")
public Inventory getInventoryBySku(String sku) { ... }

@CacheEvict(value = "inventory", key = "#sku")
public void updateInventory(String sku, ...) { ... }
```

---

#### 11. **Metrics & Monitoring**
- **Why**: Production observability
- **Impact**: LOW - Good to have
- **Effort**: 2-3 hours
- **Status**: ❌ NOT IMPLEMENTED

Add Micrometer metrics:
- Reservation rate
- Lock contention
- Version conflict rate

---

### 🔵 LOW (Nice to Have)

#### 12. **Request/Response Logging**
- **Why**: Debugging and audit
- **Status**: ⚠️ PARTIAL (Basic logging exists)

#### 13. **API Documentation (Swagger/OpenAPI)**
- **Why**: Client integration
- **Status**: ❌ NOT IMPLEMENTED

#### 14. **Rate Limiting**
- **Why**: DDoS protection
- **Status**: ❌ NOT IMPLEMENTED

---

## Implementation Path (Recommended Order)

### Sprint 1: Foundation (Days 1-2) - CRITICAL

- [ ] **Day 1 Morning**: Add @Version to BaseEntity
- [ ] **Day 1 Afternoon**: Add unique constraints to all entities
- [ ] **Day 2 Morning**: Create Flyway migration with full schema
- [ ] **Day 2 Afternoon**: Add atomic UPDATE methods to repositories
- [ ] **End of Day 2**: Verify everything compiles and tests pass

**Estimated**: 12 hours
**Blockers**: None
**Risk**: Low - Changes are additive

---

### Sprint 2: Concurrency & Error Handling (Days 3) - CRITICAL

- [ ] **Morning**: Implement exception handler for version conflicts
- [ ] **Afternoon**: Add retry logic to service methods
- [ ] **Late Afternoon**: Test concurrent reservation scenarios
- [ ] **EOD**: All tests green

**Estimated**: 8 hours
**Blockers**: Completion of Sprint 1
**Risk**: Medium - Concurrency testing needed

---

### Sprint 3: Polish & Testing (Days 4-5) - HIGH

- [ ] **Day 4**: Write integration tests for all scenarios
- [ ] **Day 4**: Load testing for concurrent operations
- [ ] **Day 5**: Database constraint tests
- [ ] **Day 5**: Prepare for staging deployment

**Estimated**: 16 hours
**Blockers**: Completion of Sprints 1-2
**Risk**: Medium - Load testing may reveal issues

---

### Sprint 4: Nice-to-Haves (As Time Allows) - MEDIUM/LOW

- [ ] Add caching layer
- [ ] Implement warehouse distribution
- [ ] Add monitoring/metrics
- [ ] Create API documentation

---

## File Checklist

### Must Modify

- [ ] `domain/entity/BaseEntity.java` - Add @Version
- [ ] `domain/entity/Inventory.java` - Add uniqueConstraints
- [ ] `domain/entity/StockReservation.java` - Add uniqueConstraints
- [ ] `domain/entity/StockMovement.java` - Add uniqueConstraints
- [ ] `domain/entity/Warehouse.java` - Add uniqueConstraints
- [ ] `domain/entity/WarehouseInventory.java` - Add uniqueConstraints
- [ ] `domain/repository/InventoryRepository.java` - Add atomic methods
- [ ] `domain/repository/StockReservationRepository.java` - Add atomic methods
- [ ] `exception/GlobalExceptionHandler.java` - Add version conflict handler
- [ ] `service/ReservationService.java` - Add retry logic
- [ ] `service/InventoryService.java` - Add retry logic

### Must Create

- [ ] `src/main/resources/db/migration/V1__Initial_Schema.sql` - Database schema
- [ ] `test/java/com/ecommerce/inventoryservice/service/ConcurrencyTest.java` - Concurrency tests
- [ ] `test/java/com/ecommerce/inventoryservice/integration/ReservationIntegrationTest.java` - Integration tests

### Optional Enhancements

- [ ] Add caching annotations to service methods
- [ ] Implement WarehouseInventoryService
- [ ] Add Micrometer metrics
- [ ] Create OpenAPI documentation

---

## Code Pattern Examples

### Before (Problematic - Fetch-Modify-Save)

```java
@Transactional
public void reserveStock(String sku, int quantity) {
    // ❌ Problem: Race condition between find and update
    Inventory inventory = repo.findBySku(sku).orElseThrow();
    
    if (inventory.getAvailableStock() < quantity) {
        throw new InsufficientStockException(...);
    }
    
    // ❌ Another thread could have modified this between check and save
    inventory.setAvailableStock(inventory.getAvailableStock() - quantity);
    repo.save(inventory);
}
```

### After (Correct - Atomic SQL)

```java
@Transactional
public void reserveStock(String sku, int quantity) {
    // ✅ Atomic: SQL executes in single database operation
    int updated = repo.atomicReserveStock(sku, quantity);
    
    if (updated == 0) {
        throw new InsufficientStockException(...);
    }
}

// In Repository
@Modifying
@Query("UPDATE Inventory i SET i.availableStock = i.availableStock - :qty " +
       "WHERE i.sku = :sku AND i.availableStock >= :qty")
int atomicReserveStock(@Param("sku") String sku, @Param("qty") int quantity);
```

---

## Testing Priorities

### Unit Tests (High Priority)

```java
✅ InventoryServiceTest
  ├── testInitializeInventory()
  ├── testGetStockBySku()
  ├── testUpdateStock()
  ├── testHandleVersionConflict()
  └── testAtomicOperations()

✅ ReservationServiceTest
  ├── testReserveSingleStock()
  ├── testReserveBulkStock()
  ├── testConfirmReservation()
  ├── testReleaseReservation()
  ├── testCheckAvailability()
  └── testCleanupExpiredReservations()
```

### Integration Tests (High Priority)

```java
✅ ReservationIntegrationTest
  ├── testCompleteReservationFlow()
  ├── testConcurrentReservationsPrevention()
  ├── testBulkReservationAtomicity()
  ├── testVersionConflictHandling()
  └── testExpiredReservationCleanup()
```

### Load Tests (Medium Priority)

```
- 1000 concurrent reservation requests
- 100 parallel stock updates
- Lock contention under high load
- Database connection pool saturation
```

---

## Performance Targets

| Metric | Target | Current | Status |
|--------|--------|---------|--------|
| Single reservation latency (p99) | < 100ms | ? | ❓ |
| Bulk reservation (10 items) latency | < 200ms | ? | ❓ |
| Concurrent reservations/sec | > 1000 | ? | ❓ |
| Lock wait time (p99) | < 10ms | ? | ❓ |
| Version conflict rate | < 1% | ? | ❓ |
| API availability | > 99.9% | ? | ❓ |

---

## Deployment Checklist

Before deploying to production:

- [ ] All CRITICAL items completed
- [ ] Code review passed
- [ ] All tests passing (unit + integration + load)
- [ ] Database migration tested on staging
- [ ] Rollback plan documented
- [ ] Monitoring/alerts configured
- [ ] Documentation updated
- [ ] Performance baseline established
- [ ] Security review completed
- [ ] Backup taken before deployment

---

## Quick Answers to Common Questions

### Q: Why add @Version if I have pessimistic locks?
**A:** Pessimistic locks prevent overselling during reservation, but @Version:
- Catches accidental concurrent modifications to other fields
- Provides defense-in-depth
- Prevents lost updates on metadata changes
- Required for production-grade reliability

### Q: Why atomic SQL instead of Java code?
**A:** Because:
- Database executes atomically in single operation
- No race condition window
- No lock contention issues
- Transactional guarantees from database
- Better performance under load

### Q: Why Flyway migrations?
**A:** Because:
- Schema version control
- Automatic database setup
- Reproducible deployments
- Rollback capability
- Team collaboration on schema changes

### Q: What about the controller business logic issue?
**A:** YOUR CONTROLLER IS ALREADY CORRECT! ✅
- Controllers should NOT have business logic
- Services should handle all business logic
- Your ReservationController delegates to service
- This is the correct architectural pattern

The confusion in your original question came from a misunderstanding - your controller is actually a good example of what NOT to have in controllers!

---

## Support & Escalation

### If You Hit Issues

1. **Compilation errors**: Check dependency versions in pom.xml
2. **Test failures**: Ensure MySQL is running, Flyway migration applied
3. **Performance issues**: Check database indexes, connection pool size
4. **Concurrency issues**: Enable SQL logging, check lock wait times

### Resources

- Spring Data JPA Documentation: https://docs.spring.io/spring-data/jpa/docs/current/reference/html/
- Hibernate Optimistic Locking: https://docs.jboss.org/hibernate/core/current/userguide/html_single/
- Flyway: https://flywaydb.org/
- Testing with Testcontainers: https://www.testcontainers.org/

---

## Summary

**You have a solid foundation!** The main gaps are:

1. ✅ Controllers - Already correct
2. ✅ Services - Already present, needs enhancement
3. ❌ @Version annotation - Easy add
4. ❌ Atomic SQL - Medium effort
5. ❌ Database constraints - Easy add
6. ❌ Flyway migrations - Medium effort
7. ❌ Error handling for concurrency - Easy add
8. ❌ Comprehensive tests - Medium effort

**Total effort to production**: ~40-50 hours
**Recommend splitting across 2 weeks**: Foundation (1 week) + Testing & Polish (1 week)

**Your ReservationController pattern is EXCELLENT** - don't change it!

