# Inventory Service - Quick Reference

**For**: Quick lookups during development  
**Status**: Use with main implementation guide

---

## File Creation Checklist

```
✅ EXISTING:
├── src/main/java/com/ecommerce/inventoryservice/
│   ├── InventoryServiceApplication.java
│   ├── config/
│   │   ├── KafkaConfig.java
│   │   └── SecurityConfig.java
│   ├── domain/
│   │   ├── entity/
│   │   │   ├── BaseEntity.java
│   │   │   ├── Inventory.java
│   │   │   ├── StockMovement.java
│   │   │   ├── StockReservation.java
│   │   │   ├── Warehouse.java
│   │   │   └── WarehouseInventory.java
│   │   ├── enums/
│   │   │   ├── MovementType.java
│   │   │   ├── ReservationStatus.java
│   │   │   └── StockOperation.java
│   │   └── repository/
│   │       ├── InventoryRepository.java
│   │       ├── StockMovementRepository.java
│   │       ├── StockReservationRepository.java
│   │       ├── WarehouseInventoryRepository.java
│   │       └── WarehouseRepository.java
│   ├── dto/ (EXISTING - mostly complete)
│   ├── exception/
│   │   ├── DuplicateInventoryException.java
│   │   ├── GlobalExceptionHandler.java [NEEDS IMPL]
│   │   ├── InsufficientStockException.java
│   │   ├── InvalidStockOperationException.java
│   │   ├── InventoryNotFoundException.java
│   │   ├── ReservationNotFoundException.java
│   │   ├── WarehouseCapacityExceededException.java
│   │   └── WarehouseNotFoundException.java
│   ├── kafka/
│   │   ├── InventoryEventProducer.java ✅
│   │   ├── ProductEventListener.java [NEW]
│   │   └── OrderEventListener.java [NEW]
│   └── service/
│       ├── InventoryService.java ✅
│       ├── ReservationService.java [NEW - PRIORITY]
│       └── WarehouseService.java [PHASE 3]
│
❌ MISSING - CREATE THESE:
├── src/main/java/com/ecommerce/inventoryservice/controller/
│   ├── InventoryController.java [PRIORITY - Phase 1]
│   ├── ReservationController.java [PRIORITY - Phase 1]
│   └── WarehouseController.java [PHASE 3]
│
├── src/main/resources/
│   ├── application.yml (EXISTS)
│   ├── application-dev.yml [PRIORITY]
│   └── application-prod.yml [PRIORITY]
│
└── src/test/java/com/ecommerce/inventoryservice/
    ├── controller/
    │   ├── InventoryControllerTest.java
    │   └── ReservationControllerTest.java
    ├── service/
    │   ├── InventoryServiceTest.java
    │   ├── ReservationServiceTest.java
    │   └── WarehouseServiceTest.java
    ├── integration/
    │   ├── KafkaIntegrationTest.java
    │   └── RepositoryIntegrationTest.java
    └── concurrency/
        └── ConcurrencyTest.java
```

---

## Phase 1: Foundation (Do First - 2 Days)

### 1. InventoryController.java
**File Path**: `src/main/java/com/ecommerce/inventoryservice/controller/InventoryController.java`

**Key Endpoints**:
```
GET    /api/v1/inventory/{sku}              → getStockBySku
GET    /api/v1/inventory/product/{productId} → getStocksByProductId
POST   /api/v1/inventory                    → createInventory
PUT    /api/v1/inventory/{sku}/stock       → updateStock
GET    /api/v1/inventory/alerts/low-stock   → getLowStockItems
POST   /api/v1/inventory/check-availability → checkAvailability
POST   /api/v1/inventory/sync               → triggerSync
```

**Key Methods in InventoryService to Call**:
- `getStockBySku(sku)` - NEW, needs implementation
- `getStocksByProductId(productId)` - EXISTS ✅
- `createInventory(request)` - NEW, wrapper for initializeInventory
- `updateStock(sku, request)` - EXISTS ✅
- `getLowStockItems()` - NEW, needs implementation
- `checkAvailability(request)` - NEW, delegate to ReservationService
- `triggerManualSync()` - NEW, for manual sync

**Dependencies**: 
- InventoryService (mostly done)
- DTOs (mostly done)

**Time**: 3 hours

---

### 2. ReservationController.java
**File Path**: `src/main/java/com/ecommerce/inventoryservice/controller/ReservationController.java`

**Key Endpoints**:
```
POST   /api/v1/reservations                 → reserveStock
PUT    /api/v1/reservations/{id}           → updateReservation (confirm/release)
GET    /api/v1/reservations/order/{orderId} → getReservationsByOrderId
```

**Key Methods in ReservationService to Call**:
- `reserveStock(request)` - PRIMARY
- `confirmReservation(id, orderId)` - SECONDARY
- `releaseReservation(id, reason)` - SECONDARY
- `getReservationsByOrderId(orderId)` - SECONDARY

**Dependencies**: 
- ReservationService (next step)

**Time**: 2 hours

---

### 3. ReservationService.java [CRITICAL]
**File Path**: `src/main/java/com/ecommerce/inventoryservice/service/ReservationService.java`

**Key Methods** (in order of importance):
```java
1. checkAvailability(request)              // Read-only stock check
2. reserveStock(request)                   // Create reservations, deduct stock
3. releaseReservation(id, reason)          // Return stock to available
4. confirmReservation(id, orderId)         // Change to CONFIRMED
5. getReservationsByOrderId(orderId)       // Lookup
6. expireOldReservations()                 // Scheduled cleanup
```

**Core Logic**:
- Always lock SKUs in alphabetical order (deadlock prevention)
- Check all items BEFORE updating any (atomicity)
- Deduct from `availableStock`, add to `reservedStock`
- Publish Kafka events for each operation
- Idempotent operations where possible

**Concurrency Considerations**:
- Use pessimistic locking: `findBySkuForUpdate()`
- All-or-nothing semantics with `@Transactional`
- Never hold locks across multiple transactions

**Dependencies**:
- InventoryRepository (with pessimistic locking)
- StockReservationRepository
- InventoryEventProducer

**Time**: 4-5 hours
**Complexity**: HIGH - Concurrency handling critical

---

### 4. GlobalExceptionHandler.java
**File Path**: `src/main/java/com/ecommerce/inventoryservice/exception/GlobalExceptionHandler.java`

**Exceptions to Handle**:
```
InventoryNotFoundException       → 404 NOT_FOUND
InsufficientStockException       → 400 BAD_REQUEST
InvalidStockOperationException   → 400 BAD_REQUEST
DuplicateInventoryException      → 409 CONFLICT
ReservationNotFoundException     → 404 NOT_FOUND
WarehouseNotFoundException       → 404 NOT_FOUND
WarehouseCapacityExceededException → 400 BAD_REQUEST
MethodArgumentNotValidException  → 400 BAD_REQUEST (validation errors)
Exception (generic)              → 500 INTERNAL_SERVER_ERROR
```

**Response Format**:
```json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "Error message",
    "details": {} // Optional
  }
}
```

**Dependencies**:
- Exception classes (all exist)
- ApiResponse from common

**Time**: 1 hour

---

### 5. application-dev.yml & application-prod.yml
**Files**:
- `src/main/resources/application-dev.yml`
- `src/main/resources/application-prod.yml`

**Essential Configurations**:
```yaml
# Database
spring.datasource.url
spring.datasource.username
spring.datasource.password

# Kafka
spring.kafka.bootstrap-servers
kafka.topics.inventory-events
kafka.topics.product-events
kafka.topics.order-events

# JPA
spring.jpa.hibernate.ddl-auto
spring.jpa.properties.hibernate.dialect

# Logging
logging.level.com.ecommerce
```

**Dev vs Prod Differences**:
- Dev: localhost, DDL=validate, DEBUG logging
- Prod: env variables, DDL=validate, WARN logging

**Time**: 1 hour

---

## Phase 2: Events (1-2 Days)

### 6. ProductEventListener.java
**File Path**: `src/main/java/com/ecommerce/inventoryservice/kafka/ProductEventListener.java`

**Events to Handle**:
- `PRODUCT_VARIANT_CREATED` → Initialize inventory
- `PRODUCT_VARIANT_UPDATED` → Update denormalized fields
- `PRODUCT_VARIANT_DELETED` → Soft delete (mark inactive)

**Methods in InventoryService to Add**:
```java
void updateInventoryMetadata(String sku, String productName, String variantName)
void deactivateInventory(UUID variantId)
void triggerManualSync()  // For admin endpoints
```

**Kafka Configuration Needed**:
- Consumer factory for ProductEvent deserialization
- Topic: `product-events`
- Group ID: `inventory-service`

**Time**: 2 hours

---

### 7. OrderEventListener.java
**File Path**: `src/main/java/com/ecommerce/inventoryservice/kafka/OrderEventListener.java`

**Events to Handle**:
- `ORDER_PLACED` → Log/analytics (reservations created via REST)
- `ORDER_CANCELLED` → Release reservations
- `ORDER_FULFILLED` → Confirm reservations

**Topic**: `order-events`  
**Group ID**: `inventory-service`

**Time**: 1 hour

---

## Repository Methods to Add

### InventoryRepository

```java
// Add these missing methods:

@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT i FROM Inventory i WHERE i.id = :id")
Optional<Inventory> findByIdForUpdate(@Param("id") UUID id);

List<Inventory> findByIsActiveTrue();

Optional<Inventory> findBySkuAndIsActiveTrue(String sku);
```

### StockReservationRepository

```java
// Add these for expiration job:

List<StockReservation> findByStatusAndCreatedAtBefore(
    ReservationStatus status,
    LocalDateTime before
);

List<StockReservation> findByOrderId(String orderId);
```

---

## Environment Variables Needed

**For Production (application-prod.yml)**:

```bash
# Database
DB_URL=jdbc:mysql://your-rds-endpoint:3306/inventory_db
DB_USERNAME=admin
DB_PASSWORD=secure_password

# Kafka
KAFKA_BOOTSTRAP_SERVERS=kafka-broker-1:9092,kafka-broker-2:9092

# Optional - for monitoring
PROMETHEUS_PORT=9090
```

---

## Testing Order

### 1. Unit Tests (Test individually)
```
[2 hours]
- InventoryRepositoryTest (locking behavior)
- ReservationServiceTest (logic, not DB)
- InventoryControllerTest (endpoints)
```

### 2. Integration Tests (Test with DB)
```
[2 hours]
- RepositoryIntegrationTest (DB + locking)
- KafkaIntegrationTest (event flow)
- ControllerIntegrationTest (full requests)
```

### 3. Concurrency Tests (Race conditions)
```
[1 hour]
- ConcurrencyTest (pessimistic locking)
- StressTest (high volume)
```

---

## Kafka Topics Required

Must exist before deployment:

```
Topic: inventory-events
- Partitions: 3+
- Replication: 2+
- Key: SKU (for order guarantee)

Topic: product-events
- Partitions: 2+
- Replication: 2+
- Key: variant ID

Topic: order-events
- Partitions: 2+
- Replication: 2+
- Key: order ID
```

---

## Database Schema Validation

Run before deployment:

```sql
-- Check indexes exist
SHOW INDEXES FROM inventory;
SHOW INDEXES FROM stock_reservation;
SHOW INDEXES FROM stock_movement;

-- Check foreign keys
SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE 
WHERE TABLE_NAME = 'stock_reservation' AND COLUMN_NAME = 'inventory_id';

-- Check required columns
DESCRIBE inventory;
DESCRIBE stock_reservation;
DESCRIBE stock_movement;
```

---

## API Testing with cURL

### Test Stock Lookup
```bash
curl -X GET http://localhost:8083/api/v1/inventory/SKU-001 \
  -H "Authorization: Bearer <token>"
```

### Test Create Inventory
```bash
curl -X POST http://localhost:8083/api/v1/inventory \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "sku": "SKU-001",
    "variantId": "uuid-here",
    "productId": "uuid-here",
    "productName": "T-Shirt",
    "variantName": "Red / L",
    "initialStock": 100
  }'
```

### Test Reserve Stock
```bash
curl -X POST http://localhost:8083/api/v1/reservations \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORDER-123",
    "items": [
      {"sku": "SKU-001", "quantity": 2}
    ]
  }'
```

### Test Update Stock
```bash
curl -X PUT http://localhost:8083/api/v1/inventory/SKU-001/stock \
  -H "Content-Type: application/json" \
  -d '{
    "operation": "ADD",
    "quantity": 50,
    "reason": "New delivery",
    "performedBy": "WAREHOUSE"
  }'
```

---

## Performance Tuning

### Connection Pool (Dev)
```yaml
hikari:
  maximum-pool-size: 5
  minimum-idle: 2
```

### Connection Pool (Prod)
```yaml
hikari:
  maximum-pool-size: 20
  minimum-idle: 5
  max-lifetime: 1800000  # 30 minutes
  idle-timeout: 600000   # 10 minutes
```

### Kafka Batch Settings (Prod)
```yaml
producer:
  batch-size: 32768
  linger-ms: 10
```

### Query Hints
```java
// For bulk operations
hibernateProperties.put("hibernate.jdbc.batch_size", 20);
hibernateProperties.put("hibernate.jdbc.fetch_size", 50);
```

---

## Common Issues & Fixes

### Issue: Deadlock on Concurrent Reservations
**Fix**: Always acquire locks in consistent order
```java
// WRONG - can deadlock
reserveStock(["SKU-B", "SKU-A"])
reserveStock(["SKU-A", "SKU-B"])

// RIGHT - always sort
List<String> skus = request.items().stream()
    .map(i -> i.sku())
    .sorted()  // ← Critical!
    .collect(toList());
```

### Issue: Stock Not Updated After Event
**Causes**:
1. Kafka topic doesn't exist
2. Consumer not started (check logs)
3. Event has wrong format
4. Exception in listener

**Debug**:
```bash
# Check topic
kafka-topics.sh --list --bootstrap-server localhost:9092

# Check consumer group lag
kafka-consumer-groups.sh --bootstrap-servers localhost:9092 \
  --group inventory-service --describe

# Check logs
docker logs inventory-service | grep ERROR
```

### Issue: Optimistic Lock Failures
**Fix**: Use pessimistic locking instead
```java
// WRONG - optimistic locking
Optional<Inventory> inv = inventoryRepository.findBySku(sku);

// RIGHT - pessimistic locking
Optional<Inventory> inv = inventoryRepository.findBySkuForUpdate(sku);
```

---

## Deployment Checklist

- [ ] All Phase 1 code complete
- [ ] All unit tests passing
- [ ] All integration tests passing
- [ ] Configuration files created (dev + prod)
- [ ] Kafka topics created
- [ ] Database schema validated
- [ ] Environment variables configured
- [ ] Docker image builds successfully
- [ ] Container starts without errors
- [ ] Healthcheck endpoint responds
- [ ] API endpoints respond to requests
- [ ] Kafka events processed correctly
- [ ] Concurrent reservations work
- [ ] Load test passed (100+ RPS)

---

## Quick Debug Commands

```bash
# Check if service is running
curl http://localhost:8083/actuator/health

# Check database connection
curl http://localhost:8083/actuator/health/db

# Get metrics
curl http://localhost:8083/actuator/metrics

# Check specific metric
curl http://localhost:8083/actuator/metrics/inventory.available

# View logs
docker logs -f inventory-service

# Check Kafka connectivity
kafka-broker-api-versions.sh --bootstrap-server localhost:9092
```

---

## References

- Inventory Entity: `domain/entity/Inventory.java`
- Repositories: `domain/repository/`
- Existing Service: `service/InventoryService.java`
- Event Producer: `kafka/InventoryEventProducer.java`
- Exception Classes: `exception/`
- DTOs: `dto/`

---

## Contact & Support

For questions about:
- **Implementation Details**: See `inventory-service-implementation-guide.md`
- **Architecture**: See `inventory-service-completion-guide.md`
- **Quick Issues**: Check "Common Issues" section above

---

**Last Updated**: February 22, 2026  
**Estimated Total Dev Time**: 3-4 days (Phase 1 + 2)

