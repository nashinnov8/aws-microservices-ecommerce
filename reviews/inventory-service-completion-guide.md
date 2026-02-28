# Inventory Service - Completion Guide

**Status**: PARTIALLY IMPLEMENTED
**Last Updated**: February 2026
**Overall Completion**: ~60%

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [What's Implemented](#whats-implemented)
3. [What's Missing](#whats-missing)
4. [Architecture Overview](#architecture-overview)
5. [Implementation Roadmap](#implementation-roadmap)
6. [Known Issues & Recommendations](#known-issues--recommendations)

---

## Executive Summary

The Inventory Service is a core microservice responsible for managing stock levels across product variants in the e-commerce platform. The architecture is well-designed with proper separation of concerns, but several critical components are still missing.

### Current Status:
- ✅ **Domain Layer**: Fully designed (Entities, Repositories)
- ✅ **Service Layer**: Core service implemented with basic operations
- ✅ **Kafka Integration**: Producer configured, event schema defined
- ❌ **REST Controller**: Missing (no API endpoints)
- ❌ **Kafka Consumer**: Missing (doesn't listen to product events)
- ❌ **Reservation Service**: Missing (no order fulfillment support)
- ❌ **Warehouse Management**: Infrastructure incomplete
- ❌ **Configuration**: Missing application-dev.yml, application-prod.yml
- ❌ **Error Handling**: No GlobalExceptionHandler
- ❌ **Testing**: No unit/integration tests

---

## What's Implemented

### 1. **Domain Model** ✅
**Location**: `src/main/java/com/ecommerce/inventoryservice/domain/`

#### Entities:
- **Inventory.java**: Core inventory tracking entity
  - SKU as primary business key
  - Variant and Product references
  - Stock levels (available + reserved)
  - Threshold configurations (min, max, reorder point)
  - Status tracking (active/inactive)

- **StockMovement.java**: Audit trail for all stock changes
  - Movement type (RECEIVE, ADJUST, RESERVE, RELEASE)
  - Previous and new stock levels
  - Reason and performer tracking

- **StockReservation.java**: Order fulfillment tracking
  - Status management (PENDING, CONFIRMED, RELEASED, EXPIRED)
  - Order reference
  - Reservation details

- **Warehouse.java**: Physical warehouse locations
- **WarehouseInventory.java**: Multi-warehouse inventory distribution

#### Repositories:
- **InventoryRepository**: Well-designed with pessimistic locking for updates
  - `findBySku()`, `findBySkuForUpdate()` - pessimistic locking
  - `findByProductId()` - bulk variant lookup
  - `findLowStockItems()` - inventory alerts
  - `findByVariantIdForUpdate()` - variant-based updates

- **StockMovementRepository**: Audit trail queries
- **StockReservationRepository**: Reservation state queries
- **WarehouseRepository**: Warehouse management
- **WarehouseInventoryRepository**: Multi-location inventory

### 2. **Service Layer** ✅ (Partial)
**Location**: `src/main/java/com/ecommerce/inventoryservice/service/`

#### InventoryService:
- `initializeInventory()`: Create inventory for new variants
- `getStocksByProductId()`: Retrieve all variant stock levels
- `updateStock()`: Add/subtract/set stock with event publishing
  - Low stock alerts
  - Out of stock detection
  - Back in stock recovery

**Missing Services**:
- ReservationService (order fulfillment)
- WarehouseService (multi-location management)
- StockAllocationService (warehouse distribution)
- SyncService (Kafka event handling)

### 3. **Event Infrastructure** ✅ (Producer Only)
**Location**: `src/main/java/com/ecommerce/inventoryservice/kafka/`

#### InventoryEventProducer:
- Publishes stock updates to Kafka
- Sends low stock alerts
- Notifies out of stock/back in stock events
- Uses SKU as partition key for order guarantee

**Missing**:
- Event consumer for ProductEvent (from product-service)
- Event consumer for OrderEvent (from order-service)
- Dead-letter queue handling
- Event replay mechanism

### 4. **Configuration** ✅ (Partial)
- **KafkaConfig.java**: Producer/Consumer factory setup
- **SecurityConfig.java**: Basic Spring Security

**Missing**:
- application-dev.yml configuration
- application-prod.yml configuration
- Database connection pooling settings
- Actuator metrics configuration

### 5. **Exception Handling** ✅
**Location**: `src/main/java/com/ecommerce/inventoryservice/exception/`

Defined Exceptions:
- `InventoryNotFoundException`
- `InsufficientStockException`
- `InvalidStockOperationException`
- `DuplicateInventoryException`
- `ReservationNotFoundException`
- `WarehouseNotFoundException`
- `WarehouseCapacityExceededException`

**Missing**: GlobalExceptionHandler implementation

### 6. **DTOs** ✅
**Location**: `src/main/java/com/ecommerce/inventoryservice/dto/`

- **inventory/**: Stock operations (Create, Update, Response)
- **event/**: Kafka event schemas
- **reservation/**: Reservation requests/responses
- **warehouse/**: Warehouse operations
- **movement/**: Stock audit trails

---

## What's Missing

### 🔴 CRITICAL (Blocks functionality)

#### 1. **REST Controller** 
**Priority**: P0 - BLOCKING
**Location**: `src/main/java/com/ecommerce/inventoryservice/controller/`

**Required Endpoints**:
```
GET    /api/v1/inventory/{sku}              → Get stock info
GET    /api/v1/inventory/product/{productId} → Get all variant stocks
POST   /api/v1/inventory                    → Create inventory
PUT    /api/v1/inventory/{sku}/stock       → Update stock levels

POST   /api/v1/reservations                 → Reserve stock
PUT    /api/v1/reservations/{id}           → Confirm/Release reservation
GET    /api/v1/reservations/order/{orderId} → Get reservation

GET    /api/v1/inventory/alerts/low-stock   → Get low stock items
POST   /api/v1/inventory/sync               → Manual sync trigger
```

**Implementation Complexity**: HIGH (5-6 hours)

---

#### 2. **Kafka Event Consumer** 
**Priority**: P0 - BLOCKING
**Location**: `src/main/java/com/ecommerce/inventoryservice/kafka/InventoryEventListener.java`

**Events to Handle**:
- `ProductVariantCreated` → Initialize inventory
- `ProductVariantUpdated` → Update denormalized fields
- `ProductVariantDeleted` → Soft delete inventory
- `OrderPlaced` → Create reservation
- `OrderFulfilled` → Confirm reservation
- `OrderCancelled` → Release reservation

**Implementation Complexity**: MEDIUM (4-5 hours)

---

#### 3. **Reservation Service** 
**Priority**: P0 - BLOCKING
**Location**: `src/main/java/com/ecommerce/inventoryservice/service/ReservationService.java`

**Methods Needed**:
```java
// Reserve stock for order
StockReservation reserveStock(BulkReserveStockRequest request)

// Confirm reservation (on payment)
void confirmReservation(UUID reservationId, String orderId)

// Release reservation (on cancellation/timeout)
void releaseReservation(UUID reservationId, String reason)

// Get order reservations
List<StockReservation> getReservationsByOrderId(String orderId)

// Check if stock can be reserved
BulkStockCheckResponse checkAvailability(BulkStockCheckRequest request)
```

**Complexity**: HIGH (with concurrency handling)

---

### 🟡 HIGH PRIORITY (Recommended before production)

#### 4. **GlobalExceptionHandler** 
**Priority**: P1
**Location**: `src/main/java/com/ecommerce/inventoryservice/exception/GlobalExceptionHandler.java`

**Should Handle**:
- `InventoryNotFoundException` → 404
- `InsufficientStockException` → 400
- `InvalidStockOperationException` → 400
- `DuplicateInventoryException` → 409
- `ReservationNotFoundException` → 404
- `WarehouseCapacityExceededException` → 400
- Generic exceptions → 500

---

#### 5. **Configuration Files** 
**Priority**: P1
**Files Missing**:
- `application-dev.yml`
- `application-prod.yml`

**Required Configurations**:
```yaml
# Database
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/inventory_db
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: validate

# Kafka
kafka:
  bootstrap-servers: kafka:9092
  topics:
    inventory-events: inventory-events
    product-events: product-events
    order-events: order-events

# Actuator
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus

# Logging
logging:
  level:
    com.ecommerce: DEBUG
```

---

#### 6. **Kafka Consumer Configuration** 
**Priority**: P1

**Missing Classes**:
- `InventoryEventListener.java` - Consume product events
- `OrderEventListener.java` - Consume order events
- Dead-letter topic configuration
- Retry policy implementation

---

### 🟢 MEDIUM PRIORITY (Nice to have)

#### 7. **Warehouse Management Service** 
**Priority**: P2
**Location**: `src/main/java/com/ecommerce/inventoryservice/service/WarehouseService.java`

**Methods**:
```java
// Warehouse operations
Warehouse createWarehouse(CreateWarehouseRequest request)
List<Warehouse> getAllWarehouses()
void updateWarehouseCapacity(UUID warehouseId, int newCapacity)

// Multi-location inventory
void distributeStock(UUID inventoryId, int quantity, UUID primaryWarehouse)
List<WarehouseInventory> getWarehouseInventories(UUID inventoryId)
void transferStock(UUID fromWarehouse, UUID toWarehouse, String sku, int quantity)
```

---

#### 8. **Testing** 
**Priority**: P2
**Location**: `src/test/`

**Required Tests**:
- **InventoryRepositoryTests**: JpaRepository methods
- **InventoryServiceTests**: Business logic
- **ReservationServiceTests**: Stock reservation logic
- **InventoryControllerTests**: API endpoints
- **KafkaIntegrationTests**: Event flow
- **ConcurrencyTests**: Pessimistic locking behavior

---

#### 9. **Documentation & Swagger** 
**Priority**: P2

**Missing**:
- OpenAPI/Swagger configuration
- API documentation
- Database schema documentation
- Integration guide with other services

---

## Architecture Overview

### System Design

```
┌─────────────────────────────────────────────────────────────┐
│                     API GATEWAY                             │
└──────────────────────┬──────────────────────────────────────┘
                       │
        ┌──────────────┴──────────────┐
        │                             │
┌───────▼────────┐          ┌────────▼────────┐
│ Order Service  │          │ Product Service │
│ (Order Events) │          │ (Product Events)│
└────────┬───────┘          └────────┬────────┘
         │                           │
         │  Kafka Topic             │  Kafka Topic
         │  order-events            │  product-events
         │                          │
         └──────────────┬───────────┘
                        │
                ┌───────▼──────────┐
                │  KAFKA BROKER    │
                │ (Message Bus)    │
                └───────┬──────────┘
                        │
        ┌───────────────┴───────────────┐
        │                               │
┌───────▼──────────────────────────────▼───────┐
│     INVENTORY SERVICE (THIS SERVICE)         │
│                                              │
│  REST API (MISSING)                         │
│  ├── Inventory Endpoints                    │
│  ├── Reservation Endpoints                  │
│  └── Warehouse Endpoints                    │
│                                              │
│  Service Layer                              │
│  ├── InventoryService (80% done)           │
│  ├── ReservationService (MISSING)          │
│  ├── WarehouseService (MISSING)            │
│  └── SyncService (MISSING)                 │
│                                              │
│  Domain Layer                               │
│  ├── Inventory Entity ✓                    │
│  ├── StockMovement Entity ✓                │
│  ├── StockReservation Entity ✓             │
│  ├── Warehouse Entity ✓                    │
│  └── Repositories with Locking ✓           │
│                                              │
│  Kafka Producer (COMPLETED)                │
│  └── Publishes inventory events            │
│                                              │
│  Kafka Consumer (MISSING)                  │
│  ├── ProductEventListener                  │
│  └── OrderEventListener                    │
└────────────────┬─────────────────────────────┘
                 │
        ┌────────▼────────┐
        │   MySQL DB      │
        │ (inventory_db)  │
        └─────────────────┘
```

### Entity Relationships

```
Product (External)
    │
    ├── ProductVariant (External)
    │       │
    │       └─→ Inventory (This Service)
    │           ├── availableStock (can be sold)
    │           ├── reservedStock (pending orders)
    │           └── Warehouse Locations
    │               └── WarehouseInventory (distributed)
    │
    └─→ StockMovement (audit trail)
        └── For all inventory changes
        
Order (External)
    │
    └─→ StockReservation
        ├── PENDING (stock reserved)
        ├── CONFIRMED (payment received)
        ├── RELEASED (order fulfilled)
        └── EXPIRED (timeout)
```

---

## Implementation Roadmap

### Phase 1: Foundation (1-2 days) 🚀

#### Step 1: Create REST Controller
- **File**: `src/main/java/com/ecommerce/inventoryservice/controller/InventoryController.java`
- **Methods**: 
  - GET `/api/v1/inventory/{sku}` - Get stock
  - GET `/api/v1/inventory/product/{productId}` - Get variants
  - POST `/api/v1/inventory` - Create inventory
  - PUT `/api/v1/inventory/{sku}/stock` - Update stock
- **Time**: 3 hours
- **Dependencies**: InventoryService (done)

#### Step 2: Create ReservationController
- **File**: `src/main/java/com/ecommerce/inventoryservice/controller/ReservationController.java`
- **Methods**:
  - POST `/api/v1/reservations` - Reserve stock
  - PUT `/api/v1/reservations/{id}` - Manage reservation
  - GET `/api/v1/reservations/order/{orderId}` - Get reservations
- **Time**: 2 hours
- **Dependencies**: ReservationService (next step)

#### Step 3: Implement ReservationService
- **File**: `src/main/java/com/ecommerce/inventoryservice/service/ReservationService.java`
- **Key Methods**:
  - `reserveStock()` - Check availability, create reservation, deduct from available
  - `confirmReservation()` - Move to confirmed state
  - `releaseReservation()` - Return to available pool
  - `checkAvailability()` - Bulk stock check
- **Time**: 4 hours
- **Considerations**: Concurrency, order consistency, timeout handling
- **Dependencies**: InventoryRepository with locking

#### Step 4: Create GlobalExceptionHandler
- **File**: `src/main/java/com/ecommerce/inventoryservice/exception/GlobalExceptionHandler.java`
- **Time**: 1 hour
- **Dependencies**: None

---

### Phase 2: Event Integration (1-2 days) 🔄

#### Step 5: Create Kafka Event Listeners
- **Files**: 
  - `ProductEventListener.java`
  - `OrderEventListener.java`
- **Time**: 3 hours
- **Events Handled**:
  - ProductVariantCreated → Initialize inventory
  - ProductVariantUpdated → Update metadata
  - OrderPlaced → Create reservation
  - OrderCancelled → Release reservation

#### Step 6: Create SyncService
- **File**: `src/main/java/com/ecommerce/inventoryservice/service/SyncService.java`
- **Purpose**: Handle Kafka events and database sync
- **Time**: 2 hours
- **Methods**:
  - `handleProductVariantCreated()`
  - `handleProductVariantUpdated()`
  - `handleOrderPlaced()`
  - `handleOrderCancelled()`

#### Step 7: Add Configuration Files
- **Files**:
  - `application-dev.yml`
  - `application-prod.yml`
- **Time**: 1 hour
- **Contents**: Database, Kafka, Logging, Security

---

### Phase 3: Warehouse & Advanced Features (Optional - 1 week) 📦

#### Step 8: Implement WarehouseService
- **File**: `src/main/java/com/ecommerce/inventoryservice/service/WarehouseService.java`
- **Time**: 4 hours
- **Features**: Multi-location inventory, distribution, transfers

#### Step 9: Create WarehouseController
- **File**: `src/main/java/com/ecommerce/inventoryservice/controller/WarehouseController.java`
- **Time**: 2 hours

#### Step 10: Add StockAllocationService
- **File**: `src/main/java/com/ecommerce/inventoryservice/service/StockAllocationService.java`
- **Purpose**: Intelligent warehouse selection
- **Time**: 3 hours

---

### Phase 4: Testing & Documentation (1 week) 🧪

#### Step 11: Unit Tests
- Test repositories with locking
- Test service business logic
- Test reservation logic
- Test exception scenarios

#### Step 12: Integration Tests
- Kafka event processing
- Database transactions
- Concurrency scenarios

#### Step 13: API Documentation
- Swagger/OpenAPI
- Integration guide
- Database schema

---

## Implementation Checklist

### Phase 1: Foundation
- [ ] Create InventoryController.java
- [ ] Create ReservationController.java
- [ ] Implement ReservationService.java
- [ ] Create GlobalExceptionHandler.java
- [ ] Test basic endpoints

### Phase 2: Events
- [ ] Create ProductEventListener.java
- [ ] Create OrderEventListener.java
- [ ] Create SyncService.java
- [ ] Create application-dev.yml
- [ ] Create application-prod.yml
- [ ] Test event flow

### Phase 3: Warehouse
- [ ] Implement WarehouseService.java
- [ ] Create WarehouseController.java
- [ ] Implement StockAllocationService.java
- [ ] Test warehouse operations

### Phase 4: Testing
- [ ] Unit tests (80% coverage)
- [ ] Integration tests
- [ ] Concurrency tests
- [ ] API documentation

---

## Known Issues & Recommendations

### Issues Found

#### 1. **Missing Timeout Handling for Reservations**
- **Problem**: StockReservation has no expiration mechanism
- **Impact**: Reserved stock could be locked indefinitely
- **Solution**: Add scheduled job to expire old reservations
```java
@Scheduled(cron = "0 0 * * * ?")
public void expireOldReservations() {
    LocalDateTime expiryTime = LocalDateTime.now().minusHours(24);
    List<StockReservation> expired = 
        reservationRepository.findByStatusAndCreatedAtBefore(
            ReservationStatus.PENDING, expiryTime
        );
    expired.forEach(r -> releaseReservation(r.getId(), "EXPIRED"));
}
```

#### 2. **No Deadlock Prevention**
- **Problem**: Concurrent updates on multiple SKUs could cause deadlock
- **Solution**: Always acquire locks in consistent order (SKU alphabetical)
```java
public void reserveMultipleSkus(List<String> skus) {
    List<String> sortedSkus = skus.stream().sorted().collect(toList());
    for (String sku : sortedSkus) {
        inventoryRepository.findBySkuForUpdate(sku);
    }
}
```

#### 3. **Missing Async Processing**
- **Problem**: Stock updates are synchronous (could be slow)
- **Solution**: Make Kafka producer async:
```java
kafkaTemplate.send(topic, key, event).addCallback(
    result -> log.info("Event sent"),
    ex -> log.error("Event failed", ex)
);
```

#### 4. **No Metrics/Monitoring**
- **Missing**: Stock level metrics, reservation metrics
- **Recommendation**: Add Micrometer metrics:
```java
@Autowired
private MeterRegistry meterRegistry;

// Track available stock
meterRegistry.gauge("inventory.available", 
    () -> inventory.getAvailableStock());
```

#### 5. **Insufficient Logging**
- **Problem**: Hard to debug stock issues
- **Solution**: Add detailed structured logging:
```java
log.info("Stock operation", 
    kv("sku", sku),
    kv("operation", "RESERVE"),
    kv("quantity", qty),
    kv("previousStock", previous)
);
```

---

### Recommendations Before Production

#### 🔴 Critical
1. ✅ Implement REST Controller
2. ✅ Implement ReservationService
3. ✅ Add Kafka Event Listeners
4. ✅ Add GlobalExceptionHandler
5. ✅ Create configuration files

#### 🟡 Important
6. Add comprehensive error handling
7. Add unit tests (70%+ coverage)
8. Add integration tests for Kafka
9. Add concurrency tests
10. Add API documentation

#### 🟢 Nice-to-Have
11. Implement WarehouseService
12. Add distributed tracing
13. Add metrics/monitoring
14. Add performance optimization
15. Add data migration utilities

---

## Testing Strategy

### Unit Tests (Priority: HIGH)
```
InventoryRepositoryTests
├── testFindBySkuForUpdate() - Locking verification
├── testFindByProductId() - Bulk operations
├── testFindLowStockItems() - Alert queries
└── testConcurrentUpdates() - Race condition detection

InventoryServiceTests
├── testInitializeInventory() - Creation logic
├── testUpdateStock() - All operations (ADD, SUBTRACT, SET)
├── testLowStockAlert() - Event publishing
└── testStockMovementRecording() - Audit trail

ReservationServiceTests
├── testReserveStock() - Happy path
├── testInsufficientStock() - Failure case
├── testConfirmReservation() - State transition
└── testConcurrentReservations() - Race conditions
```

### Integration Tests (Priority: HIGH)
```
KafkaIntegrationTests
├── testProductVariantCreatedEvent() - Sync creation
├── testProductVariantUpdatedEvent() - Metadata update
├── testOrderPlacedEvent() - Reservation creation
└── testDeadLetterQueue() - Error handling

ControllerIntegrationTests
├── testGetStockBySkuSuccess()
├── testUpdateStockWithValidation()
├── testReserveStockFlow()
└── testConcurrentRequests()
```

### Concurrency Tests (Priority: MEDIUM)
```
ConcurrencyTests
├── testPessimisticLocking() - Deadlock prevention
├── testMultipleReservations() - Concurrent reserves
├── testRaceCondition() - Stock consistency
└── testLockTimeout() - Deadlock recovery
```

---

## Database Schema Summary

```sql
-- Main inventory table
CREATE TABLE inventory (
    id CHAR(36) PRIMARY KEY,
    sku VARCHAR(100) UNIQUE NOT NULL,
    variant_id CHAR(36) UNIQUE NOT NULL,
    product_id CHAR(36) NOT NULL,
    product_name VARCHAR(255),
    variant_name VARCHAR(255),
    available_stock INT DEFAULT 0,
    reserved_stock INT DEFAULT 0,
    min_stock_level INT DEFAULT 10,
    max_stock_level INT DEFAULT 1000,
    reorder_point INT DEFAULT 20,
    is_active BOOLEAN DEFAULT TRUE,
    created_at DATETIME,
    updated_at DATETIME,
    INDEX idx_sku (sku),
    INDEX idx_variant_id (variant_id),
    INDEX idx_product_id (product_id),
    INDEX idx_is_active (is_active)
);

-- Audit trail
CREATE TABLE stock_movement (
    id CHAR(36) PRIMARY KEY,
    inventory_id CHAR(36) NOT NULL,
    movement_type ENUM('RECEIVE', 'ADJUST', 'RESERVE', 'RELEASE'),
    quantity INT NOT NULL,
    previous_stock INT,
    new_stock INT,
    reason VARCHAR(500),
    performed_by VARCHAR(100),
    created_at DATETIME,
    FOREIGN KEY (inventory_id) REFERENCES inventory(id),
    INDEX idx_inventory_id (inventory_id)
);

-- Reservations for orders
CREATE TABLE stock_reservation (
    id CHAR(36) PRIMARY KEY,
    inventory_id CHAR(36) NOT NULL,
    order_id VARCHAR(100) NOT NULL,
    quantity INT NOT NULL,
    status ENUM('PENDING', 'CONFIRMED', 'RELEASED', 'EXPIRED'),
    created_at DATETIME,
    confirmed_at DATETIME,
    released_at DATETIME,
    FOREIGN KEY (inventory_id) REFERENCES inventory(id),
    INDEX idx_order_id (order_id),
    INDEX idx_status (status)
);

-- Warehouse locations
CREATE TABLE warehouse (
    id CHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    location VARCHAR(255),
    capacity INT NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at DATETIME,
    updated_at DATETIME
);

-- Multi-location inventory
CREATE TABLE warehouse_inventory (
    id CHAR(36) PRIMARY KEY,
    warehouse_id CHAR(36) NOT NULL,
    inventory_id CHAR(36) NOT NULL,
    stock_quantity INT DEFAULT 0,
    created_at DATETIME,
    updated_at DATETIME,
    FOREIGN KEY (warehouse_id) REFERENCES warehouse(id),
    FOREIGN KEY (inventory_id) REFERENCES inventory(id),
    UNIQUE KEY unique_warehouse_inventory (warehouse_id, inventory_id)
);
```

---

## API Endpoint Specification

### Inventory Endpoints

#### GET `/api/v1/inventory/{sku}` - Get Stock Info
```json
Request:
{
  "sku": "SKU-001"
}

Response (200):
{
  "id": "uuid",
  "sku": "SKU-001",
  "variantId": "uuid",
  "productId": "uuid",
  "productName": "T-Shirt",
  "variantName": "Red / L",
  "availableStock": 100,
  "reservedStock": 20,
  "totalStock": 120,
  "isLowStock": false,
  "reorderPoint": 20,
  "minStockLevel": 10,
  "maxStockLevel": 1000,
  "isActive": true,
  "createdAt": "2026-02-22T...",
  "updatedAt": "2026-02-22T..."
}

Error (404):
{
  "status": "NOT_FOUND",
  "message": "Inventory not found for SKU: SKU-001",
  "timestamp": "2026-02-22T..."
}
```

#### GET `/api/v1/inventory/product/{productId}` - Get All Variants
```json
Response (200):
{
  "data": [
    { /* stock info */ },
    { /* stock info */ }
  ],
  "total": 2
}
```

#### POST `/api/v1/inventory` - Create Inventory
```json
Request:
{
  "sku": "SKU-002",
  "variantId": "uuid",
  "productId": "uuid",
  "productName": "T-Shirt",
  "variantName": "Blue / L",
  "initialStock": 50,
  "minStockLevel": 10,
  "reorderPoint": 20
}

Response (201):
{ /* stock info */ }
```

#### PUT `/api/v1/inventory/{sku}/stock` - Update Stock
```json
Request:
{
  "operation": "ADD",  // ADD | SUBTRACT | SET
  "quantity": 50,
  "reason": "New delivery from supplier",
  "performedBy": "WAREHOUSE_STAFF"
}

Response (200):
{ /* updated stock info */ }
```

---

## Kafka Topics & Events

### Topic: `inventory-events`
```json
// Stock Updated
{
  "eventId": "uuid",
  "eventType": "STOCK_UPDATED",
  "timestamp": "2026-02-22T...",
  "inventoryId": "uuid",
  "sku": "SKU-001",
  "variantId": "uuid",
  "productId": "uuid",
  "previousQuantity": 100,
  "newQuantity": 120,
  "reservedQuantity": 20,
  "reason": "Goods receipt"
}

// Low Stock Alert
{
  "eventType": "LOW_STOCK_ALERT",
  "inventoryId": "uuid",
  "sku": "SKU-001",
  "availableStock": 15,
  "reorderPoint": 20
}

// Out of Stock
{
  "eventType": "OUT_OF_STOCK",
  "inventoryId": "uuid",
  "sku": "SKU-001"
}

// Stock Reserved
{
  "eventType": "STOCK_RESERVED",
  "reservationId": "uuid",
  "inventoryId": "uuid",
  "orderId": "ORDER-123",
  "quantity": 5
}

// Stock Released
{
  "eventType": "STOCK_RELEASED",
  "reservationId": "uuid",
  "inventoryId": "uuid",
  "quantity": 5,
  "reason": "Order cancelled"
}
```

### Topic: `product-events` (Consumer)
```json
// Product Variant Created
{
  "eventType": "PRODUCT_VARIANT_CREATED",
  "variantId": "uuid",
  "productId": "uuid",
  "sku": "SKU-001",
  "productName": "T-Shirt",
  "variantName": "Red / L"
}

// Product Variant Updated
{
  "eventType": "PRODUCT_VARIANT_UPDATED",
  "variantId": "uuid",
  "productName": "T-Shirt",
  "variantName": "Red / L"
}

// Product Variant Deleted
{
  "eventType": "PRODUCT_VARIANT_DELETED",
  "variantId": "uuid"
}
```

### Topic: `order-events` (Consumer)
```json
// Order Placed
{
  "eventType": "ORDER_PLACED",
  "orderId": "ORDER-123",
  "items": [
    {
      "sku": "SKU-001",
      "quantity": 2
    }
  ]
}

// Order Cancelled
{
  "eventType": "ORDER_CANCELLED",
  "orderId": "ORDER-123"
}

// Order Fulfilled
{
  "eventType": "ORDER_FULFILLED",
  "orderId": "ORDER-123"
}
```

---

## Next Steps

1. **Immediate (This Week)**:
   - [ ] Create InventoryController
   - [ ] Create ReservationController
   - [ ] Implement ReservationService
   - [ ] Add GlobalExceptionHandler

2. **Short Term (Next Week)**:
   - [ ] Create Kafka listeners
   - [ ] Add configuration files
   - [ ] Implement SyncService
   - [ ] Write unit tests

3. **Medium Term (2-3 Weeks)**:
   - [ ] Add integration tests
   - [ ] Add concurrency tests
   - [ ] Add API documentation
   - [ ] Add monitoring/metrics

4. **Long Term (Before Production)**:
   - [ ] Implement WarehouseService
   - [ ] Add distributed tracing
   - [ ] Load testing
   - [ ] Performance optimization
   - [ ] Data migration tools

---

## Questions & Support

For questions about specific components, refer to:
- **Architecture**: Review the domain entities and relationships
- **Configuration**: Check application-dev.yml once created
- **Kafka Integration**: Review InventoryEventProducer.java
- **Database**: Check schema in `init-db/01-init-databases.sql`

---

**Document Version**: 1.0  
**Last Updated**: February 22, 2026  
**Status**: Ready for Development

