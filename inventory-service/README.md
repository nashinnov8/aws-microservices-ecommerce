# Inventory Service

## Overview
The Inventory Service is a microservice responsible for managing stock levels, warehouse locations, and inventory operations for the e-commerce platform. It works in conjunction with the Product Service to provide real-time inventory data.

## Architecture

### Technology Stack
- **Compute**: Spring Boot (containerized on AWS ECS or local Docker)
- **Database**: PostgreSQL (for ACID compliance and complex queries)
- **Message Queue**: Amazon SQS (for event-driven communication)
- **Build Tool**: Maven
- **Framework**: Spring Boot 3.x

### Service Architecture
```
┌─────────────────┐
│ Product Service │
│   (Lambda)      │
└────┬──────┬─────┘
     │      │
     │      └──────────────┐
     │                     │ (SQS Events)
     │                     ▼
     │            ┌─────────────────┐
     │            │   Amazon SQS    │
     │            │ (Event Queue)   │
     │            └────────┬────────┘
     │                     │
     │ (REST API)          │ (Poll)
     │                     ▼
     │            ┌─────────────────┐
     │            │ Inventory Service│
     │            │  (Spring Boot)  │
     │            └────────┬────────┘
     │                     │
     └────────────────────>│
                           ▼
                  ┌─────────────────┐
                  │   PostgreSQL    │
                  │ (Stock Database)│
                  └─────────────────┘
```

### Microservices Integration
- **Product Service**: Sends product lifecycle events via SQS; requests stock data via REST API
- **Order Service**: Requests stock reservation and deduction when orders are placed
- **Auth Service**: Validates JWT tokens for authenticated requests
- **Inventory Service**: (This service) Manages all inventory and warehouse operations

## Database Design

### PostgreSQL Schema

#### 1. Inventory Table
```sql
CREATE TABLE inventory (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL UNIQUE,
    sku VARCHAR(100) NOT NULL UNIQUE,
    available_stock INTEGER NOT NULL DEFAULT 0,
    reserved_stock INTEGER NOT NULL DEFAULT 0,
    total_stock INTEGER GENERATED ALWAYS AS (available_stock + reserved_stock) STORED,
    min_stock_level INTEGER DEFAULT 10,
    max_stock_level INTEGER DEFAULT 1000,
    reorder_point INTEGER DEFAULT 20,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    
    CONSTRAINT check_stock_positive CHECK (available_stock >= 0 AND reserved_stock >= 0)
);

CREATE INDEX idx_inventory_product_id ON inventory(product_id);
CREATE INDEX idx_inventory_sku ON inventory(sku);
CREATE INDEX idx_inventory_low_stock ON inventory(available_stock) WHERE available_stock <= reorder_point;
```

#### 2. Warehouse Table
```sql
CREATE TABLE warehouse (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    warehouse_code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    location VARCHAR(500) NOT NULL,
    address TEXT,
    city VARCHAR(100),
    state VARCHAR(100),
    country VARCHAR(100),
    zip_code VARCHAR(20),
    capacity INTEGER DEFAULT 10000,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_warehouse_code ON warehouse(warehouse_code);
CREATE INDEX idx_warehouse_location ON warehouse(city, state, country);
```

#### 3. Warehouse Inventory Table
```sql
CREATE TABLE warehouse_inventory (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    warehouse_id UUID NOT NULL REFERENCES warehouse(id),
    inventory_id UUID NOT NULL REFERENCES inventory(id),
    quantity INTEGER NOT NULL DEFAULT 0,
    aisle VARCHAR(20),
    rack VARCHAR(20),
    shelf VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(warehouse_id, inventory_id),
    CONSTRAINT check_quantity_positive CHECK (quantity >= 0)
);

CREATE INDEX idx_warehouse_inventory_warehouse ON warehouse_inventory(warehouse_id);
CREATE INDEX idx_warehouse_inventory_inventory ON warehouse_inventory(inventory_id);
```

#### 4. Stock Movement Table (Audit Trail)
```sql
CREATE TABLE stock_movement (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inventory_id UUID NOT NULL REFERENCES inventory(id),
    warehouse_id UUID REFERENCES warehouse(id),
    movement_type VARCHAR(50) NOT NULL, -- RECEIVE, SHIP, ADJUST, RESERVE, RELEASE
    quantity INTEGER NOT NULL,
    previous_quantity INTEGER NOT NULL,
    new_quantity INTEGER NOT NULL,
    reason TEXT,
    reference_id UUID, -- Order ID, Transfer ID, etc.
    performed_by UUID, -- User ID
    performed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_stock_movement_inventory ON stock_movement(inventory_id);
CREATE INDEX idx_stock_movement_date ON stock_movement(performed_at);
CREATE INDEX idx_stock_movement_type ON stock_movement(movement_type);
```

#### 5. Stock Reservation Table
```sql
CREATE TABLE stock_reservation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inventory_id UUID NOT NULL REFERENCES inventory(id),
    order_id UUID NOT NULL,
    quantity INTEGER NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, FULFILLED, CANCELLED, EXPIRED
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT check_reservation_quantity_positive CHECK (quantity > 0)
);

CREATE INDEX idx_stock_reservation_inventory ON stock_reservation(inventory_id);
CREATE INDEX idx_stock_reservation_order ON stock_reservation(order_id);
CREATE INDEX idx_stock_reservation_status ON stock_reservation(status);
CREATE INDEX idx_stock_reservation_expires ON stock_reservation(expires_at);
```

## API Endpoints

### 1. Get Product Stock
**Endpoint**: `GET /api/v1/inventory/products/{productId}/stock`
**Description**: Retrieves current stock information for a product

**Response**:
```json
{
  "success": true,
  "data": {
    "productId": "uuid",
    "sku": "PROD-001",
    "availableStock": 50,
    "reservedStock": 10,
    "totalStock": 60,
    "minStockLevel": 10,
    "reorderPoint": 20,
    "isLowStock": false,
    "warehouseLocations": [
      {
        "warehouseId": "uuid",
        "warehouseCode": "WH-NYC",
        "location": "New York",
        "quantity": 40,
        "position": "A-12-3"
      },
      {
        "warehouseId": "uuid",
        "warehouseCode": "WH-LA",
        "location": "Los Angeles",
        "quantity": 20,
        "position": "B-5-7"
      }
    ]
  }
}
```

### 2. Initialize Inventory
**Endpoint**: `POST /api/v1/inventory`
**Description**: Creates inventory record for a new product (called by SQS event)

**Request**:
```json
{
  "productId": "uuid",
  "sku": "PROD-001",
  "initialStock": 0,
  "minStockLevel": 10,
  "maxStockLevel": 1000,
  "reorderPoint": 20
}
```

### 3. Update Stock
**Endpoint**: `PATCH /api/v1/inventory/{inventoryId}/stock`
**Description**: Updates stock quantity for a product

**Request**:
```json
{
  "warehouseId": "uuid",
  "operation": "ADD | SUBTRACT | SET",
  "quantity": 50,
  "reason": "Stock adjustment",
  "performedBy": "user-id"
}
```

### 4. Reserve Stock
**Endpoint**: `POST /api/v1/inventory/reserve`
**Description**: Reserves stock for an order

**Request**:
```json
{
  "productId": "uuid",
  "orderId": "uuid",
  "quantity": 5,
  "expiresInMinutes": 15
}
```

**Response**:
```json
{
  "success": true,
  "data": {
    "reservationId": "uuid",
    "productId": "uuid",
    "quantity": 5,
    "expiresAt": "2026-01-21T10:15:00Z"
  }
}
```

### 5. Release/Fulfill Reservation
**Endpoint**: `PATCH /api/v1/inventory/reservations/{reservationId}`
**Description**: Fulfills or releases a stock reservation

**Request**:
```json
{
  "action": "FULFILL | RELEASE",
  "reason": "Order completed"
}
```

### 6. Get Low Stock Items
**Endpoint**: `GET /api/v1/inventory/low-stock`
**Description**: Lists all products with stock below reorder point

**Response**:
```json
{
  "success": true,
  "data": [
    {
      "productId": "uuid",
      "sku": "PROD-001",
      "availableStock": 8,
      "reorderPoint": 20,
      "recommendedOrderQuantity": 42
    }
  ]
}
```

### 7. Get Stock Movement History
**Endpoint**: `GET /api/v1/inventory/{inventoryId}/movements`
**Description**: Retrieves audit trail of stock movements

**Query Parameters**:
- `startDate`: Start date for filtering
- `endDate`: End date for filtering
- `movementType`: Filter by movement type
- `page`: Page number
- `size`: Page size

### 8. Transfer Stock Between Warehouses
**Endpoint**: `POST /api/v1/inventory/transfer`
**Description**: Transfers stock between warehouses

**Request**:
```json
{
  "productId": "uuid",
  "fromWarehouseId": "uuid",
  "toWarehouseId": "uuid",
  "quantity": 20,
  "reason": "Rebalancing inventory"
}
```

## Project Structure
```
inventory-service/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── ecommerce/
│   │   │           └── inventory/
│   │   │               ├── InventoryServiceApplication.java
│   │   │               ├── config/
│   │   │               │   ├── SecurityConfig.java
│   │   │               │   ├── SqsConfig.java
│   │   │               │   └── DatabaseConfig.java
│   │   │               ├── controller/
│   │   │               │   ├── InventoryController.java
│   │   │               │   ├── WarehouseController.java
│   │   │               │   └── ReservationController.java
│   │   │               ├── domain/
│   │   │               │   ├── entity/
│   │   │               │   │   ├── Inventory.java
│   │   │               │   │   ├── Warehouse.java
│   │   │               │   │   ├── WarehouseInventory.java
│   │   │               │   │   ├── StockMovement.java
│   │   │               │   │   └── StockReservation.java
│   │   │               │   ├── enums/
│   │   │               │   │   ├── MovementType.java
│   │   │               │   │   └── ReservationStatus.java
│   │   │               │   └── repository/
│   │   │               │       ├── InventoryRepository.java
│   │   │               │       ├── WarehouseRepository.java
│   │   │               │       ├── WarehouseInventoryRepository.java
│   │   │               │       ├── StockMovementRepository.java
│   │   │               │       └── StockReservationRepository.java
│   │   │               ├── dto/
│   │   │               │   ├── StockInfoResponse.java
│   │   │               │   ├── CreateInventoryRequest.java
│   │   │               │   ├── UpdateStockRequest.java
│   │   │               │   ├── ReserveStockRequest.java
│   │   │               │   └── ProductEventMessage.java
│   │   │               ├── service/
│   │   │               │   ├── InventoryService.java
│   │   │               │   ├── WarehouseService.java
│   │   │               │   ├── ReservationService.java
│   │   │               │   ├── StockMovementService.java
│   │   │               │   └── SqsListenerService.java
│   │   │               ├── exception/
│   │   │               │   ├── InsufficientStockException.java
│   │   │               │   ├── InventoryNotFoundException.java
│   │   │               │   └── GlobalExceptionHandler.java
│   │   │               └── scheduler/
│   │   │                   └── ReservationExpirationScheduler.java
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-prod.yml
│   │       └── db/
│   │           └── migration/
│   │               ├── V1__create_inventory_tables.sql
│   │               └── V2__create_indexes.sql
│   └── test/
│       └── java/
├── Dockerfile
├── pom.xml
└── README.md
```

## Setup and Configuration

### Environment Variables
```bash
# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/inventory_db
SPRING_DATASOURCE_USERNAME=inventory_user
SPRING_DATASOURCE_PASSWORD=your_password

# SQS Configuration
AWS_SQS_QUEUE_URL=https://sqs.us-east-1.amazonaws.com/your-account-id/product-events-queue
AWS_REGION=us-east-1

# JWT Configuration (for authentication)
JWT_SECRET=your-jwt-secret-key
JWT_ISSUER=auth-service

# Product Service Integration
PRODUCT_SERVICE_URL=https://your-product-service-api-gateway-url

# Server Configuration
SERVER_PORT=8081
```

### Maven Dependencies (pom.xml)
```xml
<dependencies>
    <!-- Spring Boot Starter Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- Spring Boot Starter Data JPA -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>

    <!-- PostgreSQL Driver -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>

    <!-- AWS SQS -->
    <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>sqs</artifactId>
        <version>2.21.0</version>
    </dependency>

    <!-- Spring Cloud AWS -->
    <dependency>
        <groupId>io.awspring.cloud</groupId>
        <artifactId>spring-cloud-aws-messaging</artifactId>
        <version>3.0.1</version>
    </dependency>

    <!-- JWT -->
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>0.12.3</version>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
        <version>0.12.3</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId>
        <version>0.12.3</version>
        <scope>runtime</scope>
    </dependency>

    <!-- Flyway for Database Migration -->
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-core</artifactId>
    </dependency>

    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <scope>provided</scope>
    </dependency>

    <!-- Validation -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>

    <!-- Testing -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## SQS Event Listener

### Processing Product Events
The Inventory Service listens to SQS for product lifecycle events:

```java
@Service
@Slf4j
public class SqsListenerService {
    
    private final InventoryService inventoryService;
    
    @SqsListener("${aws.sqs.queue.url}")
    public void processProductEvent(ProductEventMessage message) {
        log.info("Received product event: {}", message);
        
        switch (message.getEventType()) {
            case "PRODUCT_CREATED":
                handleProductCreated(message);
                break;
            case "PRODUCT_UPDATED":
                handleProductUpdated(message);
                break;
            case "PRODUCT_DELETED":
                handleProductDeleted(message);
                break;
            default:
                log.warn("Unknown event type: {}", message.getEventType());
        }
    }
    
    private void handleProductCreated(ProductEventMessage message) {
        inventoryService.initializeInventory(
            message.getProductId(),
            message.getSku(),
            0 // Initial stock
        );
    }
    
    private void handleProductUpdated(ProductEventMessage message) {
        inventoryService.updateInventoryMetadata(
            message.getProductId(),
            message.getSku()
        );
    }
    
    private void handleProductDeleted(ProductEventMessage message) {
        inventoryService.archiveInventory(message.getProductId());
    }
}
```

## Stock Reservation Flow

### Reservation Lifecycle
1. **Order Initiated**: Reserve stock when order is placed
2. **Reservation Active**: Stock is reserved for 15 minutes
3. **Order Confirmed**: Convert reservation to actual deduction
4. **Order Cancelled**: Release reserved stock back to available
5. **Reservation Expired**: Automatically release after timeout

### Scheduled Cleanup
```java
@Component
@Slf4j
public class ReservationExpirationScheduler {
    
    private final StockReservationRepository reservationRepository;
    private final InventoryService inventoryService;
    
    @Scheduled(fixedDelay = 60000) // Run every minute
    public void releaseExpiredReservations() {
        List<StockReservation> expired = reservationRepository
            .findByStatusAndExpiresAtBefore(
                ReservationStatus.ACTIVE,
                Instant.now()
            );
        
        expired.forEach(reservation -> {
            inventoryService.releaseReservation(reservation.getId());
            log.info("Released expired reservation: {}", reservation.getId());
        });
    }
}
```

## Docker Setup

### Dockerfile
```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### docker-compose.yml (for local development)
```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15
    container_name: inventory-postgres
    environment:
      POSTGRES_DB: inventory_db
      POSTGRES_USER: inventory_user
      POSTGRES_PASSWORD: inventory_pass
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  inventory-service:
    build: .
    container_name: inventory-service
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/inventory_db
      SPRING_DATASOURCE_USERNAME: inventory_user
      SPRING_DATASOURCE_PASSWORD: inventory_pass
      AWS_SQS_QUEUE_URL: ${AWS_SQS_QUEUE_URL}
      JWT_SECRET: ${JWT_SECRET}
    ports:
      - "8081:8081"
    depends_on:
      - postgres

volumes:
  postgres_data:
```

## Monitoring and Alerts

### Key Metrics to Monitor
- **Stock Levels**: Alert when products reach reorder point
- **Reservation Expiration Rate**: High rate may indicate checkout issues
- **SQS Queue Depth**: Monitor for processing delays
- **Database Connection Pool**: Ensure optimal performance
- **API Response Times**: Track performance degradation

### Sample Alert Configuration
```yaml
alerts:
  - name: LowStockAlert
    condition: available_stock <= reorder_point
    action: send_notification_to_purchasing_team
  
  - name: HighReservationExpiration
    condition: expired_reservations_last_hour > 50
    action: send_notification_to_dev_team
  
  - name: SQSProcessingDelay
    condition: messages_in_queue > 100
    action: scale_up_consumer_instances
```

## API Integration Examples

### Reserve Stock for Order
```bash
curl -X POST https://inventory-service-url/api/v1/inventory/reserve \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "productId": "uuid",
    "orderId": "uuid",
    "quantity": 5,
    "expiresInMinutes": 15
  }'
```

### Get Product Stock
```bash
curl -X GET https://inventory-service-url/api/v1/inventory/products/{productId}/stock \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### Update Stock
```bash
curl -X PATCH https://inventory-service-url/api/v1/inventory/{inventoryId}/stock \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "warehouseId": "uuid",
    "operation": "ADD",
    "quantity": 100,
    "reason": "New shipment received"
  }'
```

## Best Practices

### 1. Transactional Consistency
Always use database transactions for stock updates to ensure ACID compliance:
```java
@Transactional
public void reserveStock(UUID productId, int quantity) {
    // Lock the row for update
    Inventory inventory = inventoryRepository
        .findByProductIdForUpdate(productId)
        .orElseThrow();
    
    if (inventory.getAvailableStock() < quantity) {
        throw new InsufficientStockException();
    }
    
    inventory.setAvailableStock(inventory.getAvailableStock() - quantity);
    inventory.setReservedStock(inventory.getReservedStock() + quantity);
    
    inventoryRepository.save(inventory);
}
```

### 2. Audit Trail
Always log stock movements for compliance and troubleshooting:
```java
private void recordStockMovement(Inventory inventory, MovementType type, 
                                  int quantity, String reason) {
    StockMovement movement = new StockMovement();
    movement.setInventoryId(inventory.getId());
    movement.setMovementType(type);
    movement.setQuantity(quantity);
    movement.setReason(reason);
    movement.setPerformedAt(Instant.now());
    
    stockMovementRepository.save(movement);
}
```

### 3. Idempotency
Ensure SQS message processing is idempotent to handle duplicate messages:
```java
@Transactional
public void initializeInventory(UUID productId, String sku, int initialStock) {
    // Check if already exists
    if (inventoryRepository.existsByProductId(productId)) {
        log.warn("Inventory already exists for product: {}", productId);
        return; // Idempotent - don't create duplicate
    }
    
    // Create new inventory record
    Inventory inventory = new Inventory();
    inventory.setProductId(productId);
    inventory.setSku(sku);
    inventory.setAvailableStock(initialStock);
    
    inventoryRepository.save(inventory);
}
```

## Testing

### Unit Tests Example
```java
@Test
void testReserveStock_Success() {
    Inventory inventory = new Inventory();
    inventory.setAvailableStock(100);
    
    when(inventoryRepository.findByProductIdForUpdate(any()))
        .thenReturn(Optional.of(inventory));
    
    inventoryService.reserveStock(productId, orderId, 10);
    
    assertEquals(90, inventory.getAvailableStock());
    assertEquals(10, inventory.getReservedStock());
}

@Test
void testReserveStock_InsufficientStock() {
    Inventory inventory = new Inventory();
    inventory.setAvailableStock(5);
    
    when(inventoryRepository.findByProductIdForUpdate(any()))
        .thenReturn(Optional.of(inventory));
    
    assertThrows(InsufficientStockException.class, () -> {
        inventoryService.reserveStock(productId, orderId, 10);
    });
}
```

## License
MIT

## Support
For issues and questions, please contact: nashnguyen1002@gmail.com

