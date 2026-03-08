# Order Service - Complete Implementation Guide (Kafka Saga Pattern)

## Overview

The **order-service** manages the full lifecycle of customer orders using an **event-driven choreography saga** pattern. All inter-service communication uses **Kafka only** — no synchronous REST calls between services.

### Key Design Decisions
- **No REST calls** between order-service and inventory-service
- **Eventual consistency** — `createOrder` returns `PENDING`, client polls for status
- **Choreography saga** — Services react to events, no central orchestrator
- **Compensation events** — `ORDER_CANCELLED` / `ORDER_PAYMENT_FAILED` trigger stock release

### Architecture

```
┌──────────────┐                              ┌───────────────────┐
│              │   Kafka: order-events         │                   │
│ order-service│ ────────────────────────────► │ inventory-service  │
│              │   (ORDER_CREATED,             │                   │
│  Port: 8083  │    ORDER_COMPLETED,           │   Port: 8082      │
│              │    ORDER_CANCELLED,            │                   │
│              │    ORDER_PAYMENT_FAILED)       │                   │
│              │                               │                   │
│              │   Kafka: inventory-events      │                   │
│              │ ◄──────────────────────────── │                   │
│              │   (STOCK_RESERVED,             │                   │
│              │    STOCK_RESERVATION_FAILED,   │                   │
│              │    STOCK_RELEASED)             │                   │
└──────────────┘                               └───────────────────┘
       │                                              │
       │ MySQL (order_db)                             │ MySQL (inventory_db)
       ▼                                              ▼
┌──────────────┐                              ┌───────────────────┐
│   orders     │                              │   inventory       │
│   order_items│                              │   stock_reserv... │
└──────────────┘                              └───────────────────┘
```

### Saga Event Flow

```
Happy Path:
═══════════
1. Client  → POST /api/orders         → order-service saves Order (PENDING)
2. order-service  → Kafka ORDER_CREATED         → inventory-service
3. inventory-service reserves stock             → Kafka STOCK_RESERVED → order-service
4. order-service updates Order to STOCK_RESERVED
5. Client  → POST /api/orders/{id}/confirm     → order-service
6. order-service  → Kafka ORDER_COMPLETED       → inventory-service fulfills reservations
7. order-service updates Order to CONFIRMED

Failure Path (insufficient stock):
═══════════════════════════════════
1. Client  → POST /api/orders         → order-service saves Order (PENDING)
2. order-service  → Kafka ORDER_CREATED         → inventory-service
3. inventory-service cannot reserve stock       → Kafka STOCK_RESERVATION_FAILED → order-service
4. order-service updates Order to FAILED

Cancellation Path:
══════════════════
1. Client  → POST /api/orders/{id}/cancel      → order-service
2. order-service  → Kafka ORDER_CANCELLED       → inventory-service releases reservations
3. order-service updates Order to CANCELLED
```

---

## Project Structure

```
order-service/
├── Dockerfile
├── pom.xml
└── src/
    └── main/
        ├── java/
        │   └── com/
        │       └── ecommerce/
        │           └── orderservice/
        │               ├── OrderServiceApplication.java
        │               ├── config/
        │               │   ├── KafkaConfig.java
        │               │   └── SecurityConfig.java
        │               ├── controller/
        │               │   └── OrderController.java
        │               ├── domain/
        │               │   ├── entity/
        │               │   │   ├── BaseEntity.java
        │               │   │   ├── Order.java
        │               │   │   └── OrderItem.java
        │               │   ├── enums/
        │               │   │   └── OrderStatus.java
        │               │   └── repository/
        │               │       ├── OrderRepository.java
        │               │       └── OrderItemRepository.java
        │               ├── dto/
        │               │   ├── event/
        │               │   │   ├── InventoryEvent.java
        │               │   │   └── OrderEvent.java
        │               │   └── order/
        │               │       ├── CreateOrderRequest.java
        │               │       ├── OrderItemRequest.java
        │               │       ├── OrderItemResponse.java
        │               │       ├── OrderResponse.java
        │               │       └── OrderSummaryResponse.java
        │               ├── exception/
        │               │   ├── GlobalExceptionHandler.java
        │               │   ├── InsufficientStockException.java
        │               │   ├── InvalidOrderStateException.java
        │               │   └── OrderNotFoundException.java
        │               ├── kafka/
        │               │   ├── InventoryEventListener.java
        │               │   └── OrderEventProducer.java
        │               └── service/
        │                   └── OrderService.java
        └── resources/
            ├── application.yml
            ├── application-dev.yml
            ├── application-prod.yml
            └── db/
                └── migration/
                    └── V1__Initial_Schema.sql
```

> **Note:** No `RestClientConfig.java` — all communication is Kafka-based.

---

## Implementation Plan

### Phase 1: Infrastructure & Configuration
1. `pom.xml` — Maven project with Spring Boot 3.4.1, common module, Kafka, Flyway, MySQL, JPA, Security
2. `Dockerfile` — Multi-stage Maven build
3. `application.yml`, `application-dev.yml`, `application-prod.yml`
4. Database init script additions
5. Docker-compose additions

### Phase 2: Domain Layer
6. `BaseEntity.java` — UUID + version + timestamps
7. `OrderStatus.java` — Enum for order lifecycle
8. `Order.java` — Core order entity with `@OneToMany` OrderItems
9. `OrderItem.java` — Line items with SKU, quantity, price
10. `OrderRepository.java` — JPA repository with pagination
11. `OrderItemRepository.java` — JPA repository

### Phase 3: DTO Layer
12. `OrderEvent.java` — **Now includes `ORDER_CREATED`** event type
13. `InventoryEvent.java` — **Now includes `STOCK_RESERVATION_FAILED`** event type
14. `CreateOrderRequest.java` — Request DTO
15. `OrderItemRequest.java` — Item request DTO
16. `OrderResponse.java` — Full order response
17. `OrderItemResponse.java` — Item response
18. `OrderSummaryResponse.java` — Lightweight response for lists

### Phase 4: Exception Handling
19. `OrderNotFoundException.java`
20. `InvalidOrderStateException.java`
21. `InsufficientStockException.java`
22. `GlobalExceptionHandler.java`

### Phase 5: Configuration
23. `SecurityConfig.java` — Gateway-trusted, permit all
24. `KafkaConfig.java` — Producer for OrderEvent, consumer for InventoryEvent

### Phase 6: Kafka Integration (Saga Core)
25. `OrderEventProducer.java` — Publishes `ORDER_CREATED`, `ORDER_COMPLETED`, `ORDER_CANCELLED`, `ORDER_PAYMENT_FAILED`
26. `InventoryEventListener.java` — **Critical: consumes `STOCK_RESERVED` / `STOCK_RESERVATION_FAILED`** to update order status

### Phase 7: Service & Controller
27. `OrderService.java` — Business logic (**async create, no REST**)
28. `OrderController.java` — REST endpoints

### Phase 8: Database
29. `V1__Initial_Schema.sql` — Flyway migration

### Phase 9: Inventory-Service Changes (Required)
30. Update `OrderEvent.java` — Add `ORDER_CREATED` constant
31. Update `OrderEventListener.java` — Handle `ORDER_CREATED` event
32. Update `InventoryEvent.java` — Add `STOCK_RESERVATION_FAILED` constant + factory
33. Update `InventoryEventProducer.java` — Add `publishStockReservationFailed()` method

---

## Complete Source Code

### 1. `pom.xml`

**File:** `order-service/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.1</version>
        <relativePath/>
    </parent>
    <groupId>com.ecommerce</groupId>
    <artifactId>order-service</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>order-service</name>
    <description>Order management microservice (Kafka saga pattern)</description>
    <url/>
    <licenses>
        <license/>
    </licenses>
    <developers>
        <developer/>
    </developers>
    <scm>
        <connection/>
        <developerConnection/>
        <tag/>
        <url/>
    </scm>
    <properties>
        <java.version>21</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>com.ecommerce</groupId>
            <artifactId>common</artifactId>
            <version>0.0.1-SNAPSHOT</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>

        <!-- Kafka -->
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka</artifactId>
        </dependency>

        <!-- Runtime dependencies -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-devtools</artifactId>
            <scope>runtime</scope>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Flyway for database migrations -->
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-mysql</artifactId>
        </dependency>

        <!-- Test dependencies -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>

</project>
```

---

### 2. `Dockerfile`

**File:** `order-service/Dockerfile`

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

COPY . .

RUN mvn clean package -DskipTests -pl order-service -am

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=builder /app/order-service/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

### 3. `application.yml`

**File:** `order-service/src/main/resources/application.yml`

```yaml
spring:
  application:
    name: order-service
  jpa:
    show-sql: false
    open-in-view: false
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
    baseline-on-migrate: true

server:
  port: 8083

# Kafka topic configuration
kafka:
  topics:
    order-events: order-events
    inventory-events: inventory-events

management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      probes:
        enabled: true
      show-details: never
```

> **Note:** No `services.inventory-service.url` — Kafka-only communication.

---

### 4. `application-dev.yml`

**File:** `order-service/src/main/resources/application-dev.yml`

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:mysql://localhost:3306/order_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh}
    username: ${SPRING_DATASOURCE_USERNAME:auth}
    password: ${SPRING_DATASOURCE_PASSWORD:auth}

  jpa:
    hibernate:
      ddl-auto: update
    database-platform: org.hibernate.dialect.MySQLDialect
    show-sql: true
    properties:
      hibernate:
        format_sql: true

  flyway:
    enabled: true

  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:29092}
    consumer:
      group-id: order-service
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer

logging:
  level:
    root: INFO
    com.ecommerce.orderservice: DEBUG
    org.springframework.kafka: INFO
```

---

### 5. `application-prod.yml`

**File:** `order-service/src/main/resources/application-prod.yml`

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL}
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000

  jpa:
    hibernate:
      ddl-auto: validate
    database-platform: org.hibernate.dialect.MySQLDialect
    show-sql: false

  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
    consumer:
      group-id: order-service
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer

  config:
    import:
      - aws-secretsmanager:micro-ecommerce/order/db-configuration
  cloud:
    aws:
      cloudwatch:
        region: ap-southeast-2

logging:
  level:
    root: WARN
    com.ecommerce.orderservice: INFO
```

---

### 6. `V1__Initial_Schema.sql`

**File:** `order-service/src/main/resources/db/migration/V1__Initial_Schema.sql`

```sql
-- =====================================================
-- V1: Initial schema for order-service
-- =====================================================

-- Orders table: Core order tracking
CREATE TABLE IF NOT EXISTS orders (
    id BINARY(16) PRIMARY KEY COMMENT 'UUID',
    order_number VARCHAR(50) NOT NULL COMMENT 'Human-readable order number (e.g., ORD-20260304-XXXX)',
    user_id VARCHAR(100) NOT NULL COMMENT 'User ID from auth-service',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING' COMMENT 'Order status',
    total_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT 'Total order amount',
    currency VARCHAR(3) NOT NULL DEFAULT 'USD' COMMENT 'Currency code',
    shipping_address_line1 VARCHAR(255) COMMENT 'Shipping address line 1',
    shipping_address_line2 VARCHAR(255) COMMENT 'Shipping address line 2',
    shipping_city VARCHAR(100) COMMENT 'Shipping city',
    shipping_state VARCHAR(100) COMMENT 'Shipping state/province',
    shipping_zip_code VARCHAR(20) COMMENT 'Shipping zip/postal code',
    shipping_country VARCHAR(100) COMMENT 'Shipping country',
    notes TEXT COMMENT 'Order notes from customer',
    cancelled_reason VARCHAR(500) COMMENT 'Reason for cancellation',
    version BIGINT NOT NULL DEFAULT 0 COMMENT 'Optimistic locking version',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    -- Unique constraints
    UNIQUE KEY uk_order_number (order_number),

    -- Indexes for query performance
    KEY idx_orders_user_id (user_id),
    KEY idx_orders_status (status),
    KEY idx_orders_created_at (created_at),
    KEY idx_orders_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Order items table: Line items for each order
CREATE TABLE IF NOT EXISTS order_items (
    id BINARY(16) PRIMARY KEY COMMENT 'UUID',
    order_id BINARY(16) NOT NULL COMMENT 'Reference to orders table',
    sku VARCHAR(100) NOT NULL COMMENT 'SKU from inventory-service',
    product_id BINARY(16) NOT NULL COMMENT 'Product UUID from product-service',
    variant_id BINARY(16) NOT NULL COMMENT 'Variant UUID from product-service',
    product_name VARCHAR(255) NOT NULL COMMENT 'Denormalized product name at time of order',
    variant_name VARCHAR(255) COMMENT 'Denormalized variant name (e.g., Red / XL)',
    quantity INT NOT NULL COMMENT 'Quantity ordered',
    unit_price DECIMAL(12, 2) NOT NULL COMMENT 'Price per unit at time of order',
    subtotal DECIMAL(12, 2) NOT NULL COMMENT 'quantity * unit_price',
    version BIGINT NOT NULL DEFAULT 0 COMMENT 'Optimistic locking version',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    -- Foreign keys
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,

    -- Indexes
    KEY idx_oi_order_id (order_id),
    KEY idx_oi_sku (sku),
    KEY idx_oi_product_id (product_id),

    -- Check constraints
    CONSTRAINT chk_oi_quantity CHECK (quantity > 0),
    CONSTRAINT chk_oi_unit_price CHECK (unit_price >= 0),
    CONSTRAINT chk_oi_subtotal CHECK (subtotal >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

### 7. `OrderServiceApplication.java`

**File:** `order-service/src/main/java/com/ecommerce/orderservice/OrderServiceApplication.java`

```java
package com.ecommerce.orderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
```

---

### 8. `BaseEntity.java`

**File:** `order-service/src/main/java/com/ecommerce/orderservice/domain/entity/BaseEntity.java`

```java
package com.ecommerce.orderservice.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

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
```

---

### 9. `OrderStatus.java`

**File:** `order-service/src/main/java/com/ecommerce/orderservice/domain/enums/OrderStatus.java`

```java
package com.ecommerce.orderservice.domain.enums;

/**
 * Order lifecycle status enum.
 *
 * Saga Flow:
 *   PENDING → (await inventory-service Kafka response)
 *          → STOCK_RESERVED (success) → CONFIRMED → PROCESSING → SHIPPED → DELIVERED
 *          → FAILED (stock reservation failed via Kafka)
 *   Any non-terminal → CANCELLED (with reason)
 */
public enum OrderStatus {
    /** Order created, awaiting async stock reservation via Kafka. */
    PENDING,

    /** Stock reserved by inventory-service (confirmed via Kafka STOCK_RESERVED event). */
    STOCK_RESERVED,

    /** Order confirmed (payment received or COD accepted). */
    CONFIRMED,

    /** Order is being processed for shipping. */
    PROCESSING,

    /** Order has been shipped. */
    SHIPPED,

    /** Order delivered to customer. */
    DELIVERED,

    /** Order cancelled by customer or admin. */
    CANCELLED,

    /** Order failed (stock reservation failed via Kafka, or payment failed). */
    FAILED;

    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED || this == FAILED;
    }

    public boolean isCancellable() {
        return this == PENDING || this == STOCK_RESERVED || this == CONFIRMED || this == PROCESSING;
    }
}
```

---

### 10. `Order.java`

**File:** `order-service/src/main/java/com/ecommerce/orderservice/domain/entity/Order.java`

```java
package com.ecommerce.orderservice.domain.entity;

import com.ecommerce.orderservice.domain.enums.OrderStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_order_number", columnNames = "orderNumber")
    },
    indexes = {
        @Index(name = "idx_orders_user_id", columnList = "userId"),
        @Index(name = "idx_orders_status", columnList = "status"),
        @Index(name = "idx_orders_created_at", columnList = "createdAt"),
        @Index(name = "idx_orders_user_status", columnList = "userId, status")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class Order extends BaseEntity {

    @NotBlank(message = "Order number is required")
    @Column(nullable = false, unique = true, length = 50)
    private String orderNumber;

    @NotBlank(message = "User ID is required")
    @Column(nullable = false, length = 100)
    private String userId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Column(length = 255)
    private String shippingAddressLine1;

    @Column(length = 255)
    private String shippingAddressLine2;

    @Column(length = 100)
    private String shippingCity;

    @Column(length = 100)
    private String shippingState;

    @Column(length = 20)
    private String shippingZipCode;

    @Column(length = 100)
    private String shippingCountry;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(length = 500)
    private String cancelledReason;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItem> items = new ArrayList<>();

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public void removeItem(OrderItem item) {
        items.remove(item);
        item.setOrder(null);
    }

    public void recalculateTotal() {
        this.totalAmount = items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
```

---

### 11. `OrderItem.java`

**File:** `order-service/src/main/java/com/ecommerce/orderservice/domain/entity/OrderItem.java`

```java
package com.ecommerce.orderservice.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_items",
    indexes = {
        @Index(name = "idx_oi_order_id", columnList = "order_id"),
        @Index(name = "idx_oi_sku", columnList = "sku"),
        @Index(name = "idx_oi_product_id", columnList = "productId")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class OrderItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @NotBlank(message = "SKU is required")
    @Column(nullable = false, length = 100)
    private String sku;

    @NotNull(message = "Product ID is required")
    @Column(nullable = false)
    private UUID productId;

    @NotNull(message = "Variant ID is required")
    @Column(nullable = false)
    private UUID variantId;

    @NotBlank(message = "Product name is required")
    @Column(nullable = false, length = 255)
    private String productName;

    @Column(length = 255)
    private String variantName;

    @Min(value = 1, message = "Quantity must be at least 1")
    @Column(nullable = false)
    private int quantity;

    @NotNull(message = "Unit price is required")
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @NotNull
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    public OrderItem(String sku, UUID productId, UUID variantId,
                     String productName, String variantName,
                     int quantity, BigDecimal unitPrice) {
        this.sku = sku;
        this.productId = productId;
        this.variantId = variantId;
        this.productName = productName;
        this.variantName = variantName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
```

---

### 12. `OrderRepository.java`

**File:** `order-service/src/main/java/com/ecommerce/orderservice/domain/repository/OrderRepository.java`

```java
package com.ecommerce.orderservice.domain.repository;

import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByOrderNumber(String orderNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);

    Page<Order> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    Page<Order> findByUserIdAndStatusOrderByCreatedAtDesc(String userId, OrderStatus status, Pageable pageable);

    Page<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);

    boolean existsByOrderNumber(String orderNumber);
}
```

---

### 13. `OrderItemRepository.java`

**File:** `order-service/src/main/java/com/ecommerce/orderservice/domain/repository/OrderItemRepository.java`

```java
package com.ecommerce.orderservice.domain.repository;

import com.ecommerce.orderservice.domain.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    List<OrderItem> findByOrderId(UUID orderId);
}
```

---

### 14. `OrderEvent.java` ⭐ CHANGED

**File:** `order-service/src/main/java/com/ecommerce/orderservice/dto/event/OrderEvent.java`

> **KEY CHANGE:** Added `ORDER_CREATED` event type and factory method.
> Inventory-service must also add this constant to its `OrderEvent.java`.

```java
package com.ecommerce.orderservice.dto.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO for order events published to Kafka (order-events topic).
 * Consumed by inventory-service for saga-based stock management.
 *
 * Event types:
 * - ORDER_CREATED        → inventory-service reserves stock (NEW - saga trigger)
 * - ORDER_COMPLETED      → inventory-service fulfills reservations
 * - ORDER_CANCELLED      → inventory-service releases reservations (compensation)
 * - ORDER_PAYMENT_FAILED → inventory-service releases reservations (compensation)
 */
public record OrderEvent(
        String eventId,
        String eventType,
        String orderId,
        List<OrderEventItem> items,
        Instant timestamp
) {
    public static final String ORDER_CREATED = "ORDER_CREATED";
    public static final String ORDER_COMPLETED = "ORDER_COMPLETED";
    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";
    public static final String ORDER_PAYMENT_FAILED = "ORDER_PAYMENT_FAILED";

    /**
     * Order line item — matches inventory-service's expected format.
     */
    public record OrderEventItem(
            String sku,
            UUID variantId,
            int quantity
    ) {}

    /** Factory: saga trigger — request stock reservation. */
    public static OrderEvent orderCreated(String orderId, List<OrderEventItem> items) {
        return new OrderEvent(
                UUID.randomUUID().toString(),
                ORDER_CREATED,
                orderId,
                items,
                Instant.now()
        );
    }

    /** Factory: saga success — fulfill reservations. */
    public static OrderEvent orderCompleted(String orderId, List<OrderEventItem> items) {
        return new OrderEvent(
                UUID.randomUUID().toString(),
                ORDER_COMPLETED,
                orderId,
                items,
                Instant.now()
        );
    }

    /** Factory: saga compensation — release reservations. */
    public static OrderEvent orderCancelled(String orderId, List<OrderEventItem> items) {
        return new OrderEvent(
                UUID.randomUUID().toString(),
                ORDER_CANCELLED,
                orderId,
                items,
                Instant.now()
        );
    }

    /** Factory: saga compensation — release reservations due to payment failure. */
    public static OrderEvent orderPaymentFailed(String orderId, List<OrderEventItem> items) {
        return new OrderEvent(
                UUID.randomUUID().toString(),
                ORDER_PAYMENT_FAILED,
                orderId,
                items,
                Instant.now()
        );
    }
}
```

---

### 15. `InventoryEvent.java` ⭐ CHANGED

**File:** `order-service/src/main/java/com/ecommerce/orderservice/dto/event/InventoryEvent.java`

> **KEY CHANGE:** Added `STOCK_RESERVATION_FAILED` constant.
> Inventory-service must also add this constant + factory to its `InventoryEvent.java`.

```java
package com.ecommerce.orderservice.dto.event;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for events consumed from inventory-service.
 * Used by order-service to react to saga responses.
 *
 * Saga-relevant events:
 * - STOCK_RESERVED            → Order transitions PENDING → STOCK_RESERVED
 * - STOCK_RESERVATION_FAILED  → Order transitions PENDING → FAILED
 * - STOCK_RELEASED            → Logged (compensation confirmed)
 */
public record InventoryEvent(
        String eventId,
        String eventType,
        UUID inventoryId,
        String sku,
        UUID variantId,
        UUID productId,
        int previousQuantity,
        int newQuantity,
        int reservedQuantity,
        String reason,
        String referenceId,
        Instant timestamp
) {
    public static final String STOCK_UPDATED = "STOCK_UPDATED";
    public static final String LOW_STOCK_ALERT = "LOW_STOCK_ALERT";
    public static final String STOCK_RESERVED = "STOCK_RESERVED";
    public static final String STOCK_RELEASED = "STOCK_RELEASED";
    public static final String STOCK_RESERVATION_FAILED = "STOCK_RESERVATION_FAILED";
    public static final String OUT_OF_STOCK = "OUT_OF_STOCK";
    public static final String BACK_IN_STOCK = "BACK_IN_STOCK";
}
```

---

### 16–20. DTOs (Unchanged from previous)

The following DTOs remain the same as the previous guide:
- `CreateOrderRequest.java`
- `OrderItemRequest.java`
- `OrderResponse.java`
- `OrderItemResponse.java`
- `OrderSummaryResponse.java`

See the existing files already implemented in the project.

---

### 21–24. Exceptions (Unchanged from previous)

The following exception classes remain the same:
- `OrderNotFoundException.java`
- `InvalidOrderStateException.java`
- `InsufficientStockException.java`
- `GlobalExceptionHandler.java`

See the existing files already implemented in the project.

---

### 25. `SecurityConfig.java` (Unchanged)

**File:** `order-service/src/main/java/com/ecommerce/orderservice/config/SecurityConfig.java`

```java
package com.ecommerce.orderservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().permitAll()
                );

        return http.build();
    }
}
```

> **Note:** `RestClientConfig.java` is **REMOVED** — not needed in Kafka saga pattern.

---

### 26. `KafkaConfig.java`

**File:** `order-service/src/main/java/com/ecommerce/orderservice/config/KafkaConfig.java`

```java
package com.ecommerce.orderservice.config;

import com.ecommerce.orderservice.dto.event.OrderEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:29092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:order-service}")
    private String groupId;

    @Bean
    public ObjectMapper kafkaObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    // ==================== CUSTOM SERIALIZER ====================

    public static class OrderEventSerializer implements Serializer<OrderEvent> {
        private final ObjectMapper objectMapper;

        public OrderEventSerializer() {
            this.objectMapper = new ObjectMapper();
            this.objectMapper.registerModule(new JavaTimeModule());
        }

        public OrderEventSerializer(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public byte[] serialize(String topic, OrderEvent data) {
            if (data == null) return null;
            try {
                return objectMapper.writeValueAsBytes(data);
            } catch (Exception e) {
                throw new RuntimeException("Error serializing OrderEvent", e);
            }
        }
    }

    // ==================== PRODUCER ====================

    @Bean
    public ProducerFactory<String, OrderEvent> orderEventProducerFactory(ObjectMapper kafkaObjectMapper) {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        return new DefaultKafkaProducerFactory<>(
                configProps,
                new StringSerializer(),
                new OrderEventSerializer(kafkaObjectMapper)
        );
    }

    @Bean
    public KafkaTemplate<String, OrderEvent> kafkaTemplate(
            ProducerFactory<String, OrderEvent> orderEventProducerFactory) {
        return new KafkaTemplate<>(orderEventProducerFactory);
    }

    // ==================== CONSUMER ====================

    @Bean
    public ConsumerFactory<String, String> inventoryEventConsumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        return new DefaultKafkaConsumerFactory<>(configProps, new StringDeserializer(), new StringDeserializer());
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> inventoryEventListenerContainerFactory(
            ConsumerFactory<String, String> inventoryEventConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(inventoryEventConsumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                (consumerRecord, exception) -> {
                    // Runs after all retries exhausted — send to DLQ in production
                },
                new FixedBackOff(1000L, 3L)
        );
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }
}
```

---

### 27. `OrderEventProducer.java` ⭐ CHANGED

**File:** `order-service/src/main/java/com/ecommerce/orderservice/kafka/OrderEventProducer.java`

> **KEY CHANGE:** Added `publishOrderCreated()` — the saga trigger event.

```java
package com.ecommerce.orderservice.kafka;

import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.dto.event.OrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Kafka producer for publishing order events.
 *
 * Saga events published:
 * - ORDER_CREATED        → triggers stock reservation in inventory-service
 * - ORDER_COMPLETED      → triggers reservation fulfillment in inventory-service
 * - ORDER_CANCELLED      → triggers reservation release (compensation)
 * - ORDER_PAYMENT_FAILED → triggers reservation release (compensation)
 *
 * Uses orderId as message key for partition consistency.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventProducer {

    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    @Value("${kafka.topics.order-events:order-events}")
    private String orderEventsTopic;

    /**
     * Publish ORDER_CREATED event — SAGA TRIGGER.
     * Inventory-service will attempt to reserve stock for all items.
     * Response comes back via inventory-events topic (STOCK_RESERVED or STOCK_RESERVATION_FAILED).
     */
    public void publishOrderCreated(Order order) {
        List<OrderEvent.OrderEventItem> items = mapOrderItems(order);
        OrderEvent event = OrderEvent.orderCreated(order.getId().toString(), items);
        sendEvent(order.getId().toString(), event);
        log.info("Published ORDER_CREATED for order: {} with {} items",
                order.getOrderNumber(), items.size());
    }

    /**
     * Publish ORDER_COMPLETED event.
     * Inventory-service will fulfill the reservations (stock leaves the system).
     */
    public void publishOrderCompleted(Order order) {
        List<OrderEvent.OrderEventItem> items = mapOrderItems(order);
        OrderEvent event = OrderEvent.orderCompleted(order.getId().toString(), items);
        sendEvent(order.getId().toString(), event);
    }

    /**
     * Publish ORDER_CANCELLED event — SAGA COMPENSATION.
     * Inventory-service will release reservations and restore stock.
     */
    public void publishOrderCancelled(Order order) {
        List<OrderEvent.OrderEventItem> items = mapOrderItems(order);
        OrderEvent event = OrderEvent.orderCancelled(order.getId().toString(), items);
        sendEvent(order.getId().toString(), event);
    }

    /**
     * Publish ORDER_PAYMENT_FAILED event — SAGA COMPENSATION.
     * Inventory-service will release reservations and restore stock.
     */
    public void publishOrderPaymentFailed(Order order) {
        List<OrderEvent.OrderEventItem> items = mapOrderItems(order);
        OrderEvent event = OrderEvent.orderPaymentFailed(order.getId().toString(), items);
        sendEvent(order.getId().toString(), event);
    }

    private List<OrderEvent.OrderEventItem> mapOrderItems(Order order) {
        return order.getItems().stream()
                .map(item -> new OrderEvent.OrderEventItem(
                        item.getSku(),
                        item.getVariantId(),
                        item.getQuantity()
                ))
                .collect(Collectors.toList());
    }

    private void sendEvent(String orderId, OrderEvent event) {
        log.debug("Publishing {} event for order: {}, eventId: {}",
                event.eventType(), orderId, event.eventId());

        CompletableFuture<SendResult<String, OrderEvent>> future =
                kafkaTemplate.send(orderEventsTopic, orderId, event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Successfully published {} event for order: {} to partition {} offset {}",
                        event.eventType(), orderId,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("Failed to publish {} event for order: {}: {}",
                        event.eventType(), orderId, ex.getMessage(), ex);
            }
        });
    }
}
```

---

### 28. `InventoryEventListener.java` ⭐ COMPLETELY REWRITTEN

**File:** `order-service/src/main/java/com/ecommerce/orderservice/kafka/InventoryEventListener.java`

> **KEY CHANGE:** This is now the SAGA RESPONSE HANDLER. It updates order status
> based on `STOCK_RESERVED` / `STOCK_RESERVATION_FAILED` events from inventory-service.

```java
package com.ecommerce.orderservice.kafka;

import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import com.ecommerce.orderservice.domain.repository.OrderRepository;
import com.ecommerce.orderservice.dto.event.InventoryEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * SAGA RESPONSE HANDLER — consumes inventory events from inventory-service.
 *
 * This listener is CRITICAL for the saga pattern. It receives the async response
 * from inventory-service after ORDER_CREATED was published:
 *
 * - STOCK_RESERVED            → Order PENDING → STOCK_RESERVED (saga success)
 * - STOCK_RESERVATION_FAILED  → Order PENDING → FAILED (saga failure)
 * - STOCK_RELEASED            → Logged (compensation confirmed)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventListener {

    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "inventory-events",
            groupId = "order-service",
            containerFactory = "inventoryEventListenerContainerFactory"
    )
    @Transactional
    public void handleInventoryEvent(String message) {
        try {
            InventoryEvent event = objectMapper.readValue(message, InventoryEvent.class);
            log.info("Received inventory event: {} for SKU: {}, referenceId (orderId): {}",
                    event.eventType(), event.sku(), event.referenceId());

            switch (event.eventType()) {
                case InventoryEvent.STOCK_RESERVED -> handleStockReserved(event);
                case InventoryEvent.STOCK_RESERVATION_FAILED -> handleStockReservationFailed(event);
                case InventoryEvent.STOCK_RELEASED -> handleStockReleased(event);
                case InventoryEvent.OUT_OF_STOCK -> handleOutOfStock(event);
                case InventoryEvent.BACK_IN_STOCK -> handleBackInStock(event);
                default -> log.debug("Ignoring inventory event type: {}", event.eventType());
            }
        } catch (Exception e) {
            log.error("Error processing inventory event: {}", message, e);
            throw new RuntimeException("Failed to process inventory event", e);
        }
    }

    /**
     * SAGA SUCCESS — Stock was reserved for this order.
     * Transition: PENDING → STOCK_RESERVED.
     *
     * Note: inventory-service publishes one STOCK_RESERVED event per SKU.
     * For multi-item orders, we receive multiple events. The first one
     * transitions the order; subsequent ones are idempotent.
     */
    private void handleStockReserved(InventoryEvent event) {
        String orderId = event.referenceId();
        if (orderId == null) {
            log.debug("STOCK_RESERVED event without referenceId (not order-related), skipping");
            return;
        }

        log.info("Stock reserved for order: {}, SKU: {}, quantity: {}",
                orderId, event.sku(), event.newQuantity());

        Optional<Order> orderOpt = findOrder(orderId);
        if (orderOpt.isEmpty()) return;

        Order order = orderOpt.get();
        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.STOCK_RESERVED);
            orderRepository.save(order);
            log.info("Order {} transitioned PENDING → STOCK_RESERVED", order.getOrderNumber());
        } else {
            log.debug("Order {} already in status {}, ignoring STOCK_RESERVED",
                    order.getOrderNumber(), order.getStatus());
        }
    }

    /**
     * SAGA FAILURE — Stock reservation failed for this order.
     * Transition: PENDING → FAILED.
     *
     * Inventory-service was unable to reserve stock for one or more items.
     * The order cannot proceed.
     */
    private void handleStockReservationFailed(InventoryEvent event) {
        String orderId = event.referenceId();
        if (orderId == null) {
            log.warn("STOCK_RESERVATION_FAILED event without referenceId, skipping");
            return;
        }

        log.warn("Stock reservation FAILED for order: {}, SKU: {}, reason: {}",
                orderId, event.sku(), event.reason());

        Optional<Order> orderOpt = findOrder(orderId);
        if (orderOpt.isEmpty()) return;

        Order order = orderOpt.get();
        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.FAILED);
            order.setCancelledReason("Stock reservation failed: " + event.reason());
            orderRepository.save(order);
            log.info("Order {} transitioned PENDING → FAILED due to stock reservation failure",
                    order.getOrderNumber());
        } else {
            log.debug("Order {} already in status {}, ignoring STOCK_RESERVATION_FAILED",
                    order.getOrderNumber(), order.getStatus());
        }
    }

    /**
     * Stock released — compensation confirmed.
     * This event confirms that inventory-service has released stock
     * after an ORDER_CANCELLED or ORDER_PAYMENT_FAILED event.
     */
    private void handleStockReleased(InventoryEvent event) {
        log.info("Stock released for order: {}, SKU: {}, quantity: {}, reason: {}",
                event.referenceId(), event.sku(), event.newQuantity(), event.reason());
    }

    private void handleOutOfStock(InventoryEvent event) {
        log.warn("OUT OF STOCK: SKU {} is now out of stock", event.sku());
    }

    private void handleBackInStock(InventoryEvent event) {
        log.info("BACK IN STOCK: SKU {} now has {} units", event.sku(), event.newQuantity());
    }

    /**
     * Helper to find order by referenceId (orderId string).
     * Handles UUID parsing and not-found gracefully.
     */
    private Optional<Order> findOrder(String orderId) {
        try {
            UUID uuid = UUID.fromString(orderId);
            Optional<Order> orderOpt = orderRepository.findByIdForUpdate(uuid);
            if (orderOpt.isEmpty()) {
                log.warn("Order not found for referenceId: {}", orderId);
            }
            return orderOpt;
        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID in referenceId: {}", orderId);
            return Optional.empty();
        }
    }
}
```

---

### 29. `OrderService.java` ⭐ COMPLETELY REWRITTEN

**File:** `order-service/src/main/java/com/ecommerce/orderservice/service/OrderService.java`

> **KEY CHANGE:** `createOrder` no longer calls REST. It saves the order as PENDING
> and publishes `ORDER_CREATED` via Kafka. The status transition happens
> asynchronously when `InventoryEventListener` receives the response.

```java
package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.entity.OrderItem;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import com.ecommerce.orderservice.domain.repository.OrderRepository;
import com.ecommerce.orderservice.dto.order.*;
import com.ecommerce.orderservice.exception.InvalidOrderStateException;
import com.ecommerce.orderservice.exception.OrderNotFoundException;
import com.ecommerce.orderservice.kafka.OrderEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing orders using the Kafka choreography saga pattern.
 *
 * Saga Flow:
 * 1. createOrder → save as PENDING → publish ORDER_CREATED to Kafka
 * 2. InventoryEventListener receives STOCK_RESERVED → update to STOCK_RESERVED
 *    OR receives STOCK_RESERVATION_FAILED → update to FAILED
 * 3. confirmOrder → publish ORDER_COMPLETED → update to CONFIRMED
 * 4. cancelOrder → publish ORDER_CANCELLED → update to CANCELLED
 *
 * NO synchronous REST calls — fully event-driven.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderEventProducer orderEventProducer;

    private static final SecureRandom RANDOM = new SecureRandom();

    // ==================== CREATE ORDER (SAGA TRIGGER) ====================

    /**
     * Create a new order and trigger the saga.
     *
     * Steps:
     * 1. Generate order number
     * 2. Build Order entity with items
     * 3. Save order as PENDING
     * 4. Publish ORDER_CREATED to Kafka → inventory-service will attempt reservation
     *
     * The order stays in PENDING until inventory-service responds via Kafka:
     * - STOCK_RESERVED → InventoryEventListener updates to STOCK_RESERVED
     * - STOCK_RESERVATION_FAILED → InventoryEventListener updates to FAILED
     *
     * Client should poll GET /api/orders/{id} to check status.
     *
     * @param userId User ID from X-User-Id header
     * @param request CreateOrderRequest with items and shipping info
     * @return OrderResponse with status PENDING
     */
    @Transactional
    public OrderResponse createOrder(String userId, CreateOrderRequest request) {
        log.info("Creating order for user: {} with {} items", userId, request.items().size());

        // 1. Generate unique order number
        String orderNumber = generateOrderNumber();

        // 2. Build order entity
        Order order = new Order();
        order.setOrderNumber(orderNumber);
        order.setUserId(userId);
        order.setStatus(OrderStatus.PENDING);
        order.setCurrency(request.currency() != null ? request.currency() : "USD");
        order.setShippingAddressLine1(request.shippingAddressLine1());
        order.setShippingAddressLine2(request.shippingAddressLine2());
        order.setShippingCity(request.shippingCity());
        order.setShippingState(request.shippingState());
        order.setShippingZipCode(request.shippingZipCode());
        order.setShippingCountry(request.shippingCountry());
        order.setNotes(request.notes());

        // 3. Add items
        for (OrderItemRequest itemReq : request.items()) {
            OrderItem item = new OrderItem(
                    itemReq.sku(),
                    itemReq.productId(),
                    itemReq.variantId(),
                    itemReq.productName(),
                    itemReq.variantName(),
                    itemReq.quantity(),
                    itemReq.unitPrice()
            );
            order.addItem(item);
        }
        order.recalculateTotal();

        // 4. Save order as PENDING
        Order savedOrder = orderRepository.save(order);
        log.info("Order saved as PENDING: {}, ID: {}", orderNumber, savedOrder.getId());

        // 5. Publish ORDER_CREATED → inventory-service will reserve stock asynchronously
        orderEventProducer.publishOrderCreated(savedOrder);

        return mapToOrderResponse(savedOrder);
    }

    // ==================== GET ORDERS ====================

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> OrderNotFoundException.byId(orderId));
        return mapToOrderResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderByOrderNumber(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> OrderNotFoundException.byOrderNumber(orderNumber));
        return mapToOrderResponse(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderSummaryResponse> getOrdersByUserId(String userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::mapToOrderSummaryResponse);
    }

    @Transactional(readOnly = true)
    public Page<OrderSummaryResponse> getOrdersByUserIdAndStatus(
            String userId, OrderStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return orderRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status, pageable)
                .map(this::mapToOrderSummaryResponse);
    }

    @Transactional(readOnly = true)
    public Page<OrderSummaryResponse> getOrdersByStatus(OrderStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return orderRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
                .map(this::mapToOrderSummaryResponse);
    }

    // ==================== CONFIRM ORDER (SAGA SUCCESS) ====================

    /**
     * Confirm an order after payment success.
     * Publishes ORDER_COMPLETED → inventory-service fulfills reservations.
     *
     * Pre-condition: Order must be in STOCK_RESERVED status.
     */
    @Transactional
    public OrderResponse confirmOrder(UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> OrderNotFoundException.byId(orderId));

        if (order.getStatus() != OrderStatus.STOCK_RESERVED) {
            throw new InvalidOrderStateException(order.getStatus(), "confirm");
        }

        order.setStatus(OrderStatus.CONFIRMED);
        Order saved = orderRepository.save(order);

        // Publish ORDER_COMPLETED → inventory-service fulfills reservations
        orderEventProducer.publishOrderCompleted(saved);

        log.info("Order confirmed: {}", saved.getOrderNumber());
        return mapToOrderResponse(saved);
    }

    // ==================== STATUS TRANSITIONS (ADMIN) ====================

    @Transactional
    public OrderResponse markAsProcessing(UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> OrderNotFoundException.byId(orderId));

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new InvalidOrderStateException(order.getStatus(), "mark as processing");
        }

        order.setStatus(OrderStatus.PROCESSING);
        Order saved = orderRepository.save(order);
        log.info("Order marked as processing: {}", saved.getOrderNumber());
        return mapToOrderResponse(saved);
    }

    @Transactional
    public OrderResponse markAsShipped(UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> OrderNotFoundException.byId(orderId));

        if (order.getStatus() != OrderStatus.PROCESSING) {
            throw new InvalidOrderStateException(order.getStatus(), "mark as shipped");
        }

        order.setStatus(OrderStatus.SHIPPED);
        Order saved = orderRepository.save(order);
        log.info("Order shipped: {}", saved.getOrderNumber());
        return mapToOrderResponse(saved);
    }

    @Transactional
    public OrderResponse markAsDelivered(UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> OrderNotFoundException.byId(orderId));

        if (order.getStatus() != OrderStatus.SHIPPED) {
            throw new InvalidOrderStateException(order.getStatus(), "mark as delivered");
        }

        order.setStatus(OrderStatus.DELIVERED);
        Order saved = orderRepository.save(order);
        log.info("Order delivered: {}", saved.getOrderNumber());
        return mapToOrderResponse(saved);
    }

    // ==================== CANCEL ORDER (SAGA COMPENSATION) ====================

    /**
     * Cancel an order. If stock was reserved, publishes ORDER_CANCELLED
     * so inventory-service releases the reservations (compensation).
     */
    @Transactional
    public OrderResponse cancelOrder(UUID orderId, String reason) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> OrderNotFoundException.byId(orderId));

        if (!order.getStatus().isCancellable()) {
            throw new InvalidOrderStateException(order.getStatus(), "cancel");
        }

        OrderStatus previousStatus = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledReason(reason);
        Order saved = orderRepository.save(order);

        // Publish compensation event only if stock was already reserved
        if (previousStatus == OrderStatus.STOCK_RESERVED
                || previousStatus == OrderStatus.CONFIRMED
                || previousStatus == OrderStatus.PROCESSING) {
            orderEventProducer.publishOrderCancelled(saved);
            log.info("Published ORDER_CANCELLED for order: {} (compensation)", saved.getOrderNumber());
        }

        log.info("Order cancelled: {} (was: {}), reason: {}",
                saved.getOrderNumber(), previousStatus, reason);
        return mapToOrderResponse(saved);
    }

    // ==================== HELPER METHODS ====================

    private String generateOrderNumber() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomPart = String.format("%04X", RANDOM.nextInt(0xFFFF));
        String orderNumber = "ORD-" + datePart + "-" + randomPart;

        while (orderRepository.existsByOrderNumber(orderNumber)) {
            randomPart = String.format("%04X", RANDOM.nextInt(0xFFFF));
            orderNumber = "ORD-" + datePart + "-" + randomPart;
        }

        return orderNumber;
    }

    private OrderResponse mapToOrderResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> new OrderItemResponse(
                        item.getId(),
                        item.getSku(),
                        item.getProductId(),
                        item.getVariantId(),
                        item.getProductName(),
                        item.getVariantName(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getSubtotal()
                ))
                .collect(Collectors.toList());

        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getUserId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getShippingAddressLine1(),
                order.getShippingAddressLine2(),
                order.getShippingCity(),
                order.getShippingState(),
                order.getShippingZipCode(),
                order.getShippingCountry(),
                order.getNotes(),
                order.getCancelledReason(),
                itemResponses,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    private OrderSummaryResponse mapToOrderSummaryResponse(Order order) {
        return new OrderSummaryResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getUserId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getItems().size(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
```

---

### 30. `OrderController.java`

**File:** `order-service/src/main/java/com/ecommerce/orderservice/controller/OrderController.java`

```java
package com.ecommerce.orderservice.controller;

import com.ecommerce.orderservice.domain.enums.OrderStatus;
import com.ecommerce.orderservice.dto.order.*;
import com.ecommerce.orderservice.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import response.ApiResponse;
import response.PageResponse;

import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for order operations.
 *
 * IMPORTANT: createOrder returns status PENDING (not STOCK_RESERVED).
 * Client must poll GET /api/orders/{id} to check if stock was reserved.
 *
 * Endpoints:
 * - POST   /api/orders                     - Create order (returns PENDING)
 * - GET    /api/orders/{id}                 - Get order by ID (poll for status)
 * - GET    /api/orders/number/{orderNumber} - Get order by order number
 * - GET    /api/orders/my-orders            - Get current user's orders (paginated)
 * - POST   /api/orders/{id}/confirm         - Confirm order (requires STOCK_RESERVED)
 * - POST   /api/orders/{id}/cancel          - Cancel order
 * - POST   /api/orders/{id}/processing      - Mark as processing (admin)
 * - POST   /api/orders/{id}/ship            - Mark as shipped (admin)
 * - POST   /api/orders/{id}/deliver         - Mark as delivered (admin)
 * - GET    /api/orders/admin/by-status       - Get orders by status (admin)
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;

    // ==================== CREATE ====================

    /**
     * Create a new order (async saga).
     * Returns immediately with status PENDING.
     * Client should poll GET /api/orders/{id} to check status transition:
     *   PENDING → STOCK_RESERVED (success) or PENDING → FAILED (insufficient stock)
     */
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId,
            @Valid @RequestBody CreateOrderRequest request) {
        log.info("POST /api/orders - User: {}, Items: {}", userId, request.items().size());

        OrderResponse response = orderService.createOrder(userId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
                ApiResponse.success("202", "Order created, awaiting stock reservation", response)
        );
    }

    // ==================== READ ====================

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(@PathVariable UUID id) {
        log.info("GET /api/orders/{}", id);
        OrderResponse response = orderService.getOrderById(id);
        return ResponseEntity.ok(
                ApiResponse.success("200", "Order retrieved successfully", response)
        );
    }

    @GetMapping("/number/{orderNumber}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderByOrderNumber(
            @PathVariable String orderNumber) {
        log.info("GET /api/orders/number/{}", orderNumber);
        OrderResponse response = orderService.getOrderByOrderNumber(orderNumber);
        return ResponseEntity.ok(
                ApiResponse.success("200", "Order retrieved successfully", response)
        );
    }

    @GetMapping("/my-orders")
    public ResponseEntity<ApiResponse<PageResponse<OrderSummaryResponse>>> getMyOrders(
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) OrderStatus status) {
        log.info("GET /api/orders/my-orders - User: {}, Page: {}, Size: {}, Status: {}",
                userId, page, size, status);

        Page<OrderSummaryResponse> orders;
        if (status != null) {
            orders = orderService.getOrdersByUserIdAndStatus(userId, status, page, size);
        } else {
            orders = orderService.getOrdersByUserId(userId, page, size);
        }

        PageResponse<OrderSummaryResponse> pageResponse = PageResponse.from(orders);
        return ResponseEntity.ok(
                ApiResponse.success("200", "Orders retrieved successfully", pageResponse)
        );
    }

    // ==================== STATUS TRANSITIONS ====================

    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<OrderResponse>> confirmOrder(@PathVariable UUID id) {
        log.info("POST /api/orders/{}/confirm", id);
        OrderResponse response = orderService.confirmOrder(id);
        return ResponseEntity.ok(
                ApiResponse.success("200", "Order confirmed successfully", response)
        );
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null
                ? body.getOrDefault("reason", "Customer requested cancellation")
                : "Customer requested cancellation";
        log.info("POST /api/orders/{}/cancel - Reason: {}", id, reason);
        OrderResponse response = orderService.cancelOrder(id, reason);
        return ResponseEntity.ok(
                ApiResponse.success("200", "Order cancelled successfully", response)
        );
    }

    @PostMapping("/{id}/processing")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> markAsProcessing(@PathVariable UUID id) {
        log.info("POST /api/orders/{}/processing", id);
        OrderResponse response = orderService.markAsProcessing(id);
        return ResponseEntity.ok(
                ApiResponse.success("200", "Order marked as processing", response)
        );
    }

    @PostMapping("/{id}/ship")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> markAsShipped(@PathVariable UUID id) {
        log.info("POST /api/orders/{}/ship", id);
        OrderResponse response = orderService.markAsShipped(id);
        return ResponseEntity.ok(
                ApiResponse.success("200", "Order shipped successfully", response)
        );
    }

    @PostMapping("/{id}/deliver")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> markAsDelivered(@PathVariable UUID id) {
        log.info("POST /api/orders/{}/deliver", id);
        OrderResponse response = orderService.markAsDelivered(id);
        return ResponseEntity.ok(
                ApiResponse.success("200", "Order delivered successfully", response)
        );
    }

    // ==================== ADMIN ====================

    @GetMapping("/admin/by-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<OrderSummaryResponse>>> getOrdersByStatus(
            @RequestParam OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("GET /api/orders/admin/by-status - Status: {}, Page: {}, Size: {}", status, page, size);
        Page<OrderSummaryResponse> orders = orderService.getOrdersByStatus(status, page, size);
        PageResponse<OrderSummaryResponse> pageResponse = PageResponse.from(orders);
        return ResponseEntity.ok(
                ApiResponse.success("200", "Orders retrieved successfully", pageResponse)
        );
    }
}
```

---

## Inventory-Service Changes Required ⭐

For the saga to work, **inventory-service must be updated** to handle the new `ORDER_CREATED` event and publish `STOCK_RESERVATION_FAILED`.

### 31. Update `inventory-service/dto/event/OrderEvent.java`

Add the `ORDER_CREATED` constant:

```java
// Add to existing constants:
public static final String ORDER_CREATED = "ORDER_CREATED";
```

### 32. Update `inventory-service/dto/event/InventoryEvent.java`

Add the `STOCK_RESERVATION_FAILED` constant and factory:

```java
// Add to existing constants:
public static final String STOCK_RESERVATION_FAILED = "STOCK_RESERVATION_FAILED";

// Add factory method:
public static InventoryEvent stockReservationFailed(String sku, UUID variantId,
                                                     UUID productId, int requestedQty,
                                                     int availableQty, String orderId) {
    return new InventoryEvent(
            UUID.randomUUID().toString(),
            STOCK_RESERVATION_FAILED,
            null,
            sku,
            variantId,
            productId,
            availableQty,
            availableQty,
            0,
            "Insufficient stock. Requested: " + requestedQty + ", Available: " + availableQty,
            orderId,
            Instant.now()
    );
}
```

### 33. Update `inventory-service/kafka/InventoryEventProducer.java`

Add the new publish method:

```java
/**
 * Publish a stock reservation failed event.
 * Called when ORDER_CREATED cannot be fulfilled due to insufficient stock.
 */
public void publishStockReservationFailed(String sku, UUID variantId,
                                           UUID productId, int requestedQty,
                                           int availableQty, String orderId) {
    InventoryEvent event = InventoryEvent.stockReservationFailed(
            sku, variantId, productId, requestedQty, availableQty, orderId
    );
    sendEvent(sku, event);
    log.warn("Published STOCK_RESERVATION_FAILED for order: {}, SKU: {}", orderId, sku);
}
```

### 34. Update `inventory-service/kafka/OrderEventListener.java`

Add the `ORDER_CREATED` handler:

```java
// Add to switch statement:
case OrderEvent.ORDER_CREATED -> handleOrderCreated(event);

// Add new method:
/**
 * Handle ORDER_CREATED — attempt to reserve stock for all items.
 * This is the saga participant's response to the saga trigger.
 *
 * On success: publishes STOCK_RESERVED for each item
 * On failure: publishes STOCK_RESERVATION_FAILED and rolls back any partial reservations
 */
private void handleOrderCreated(OrderEvent event) {
    log.info("Processing ORDER_CREATED for order: {} with {} items",
            event.orderId(), event.items().size());

    try {
        // Build bulk reservation request from event items
        List<BulkStockCheckRequest.StockCheckItem> items = event.items().stream()
                .map(item -> new BulkStockCheckRequest.StockCheckItem(item.sku(), item.quantity()))
                .toList();

        // This internally reserves stock and publishes STOCK_RESERVED events per item
        reservationService.reserveStock(event.orderId(), items);

        log.info("Successfully reserved stock for order: {}", event.orderId());
    } catch (Exception e) {
        log.error("Failed to reserve stock for order: {}. Reason: {}",
                event.orderId(), e.getMessage(), e);

        // Publish failure event — order-service will mark order as FAILED
        // Use first item's details for the event (or could publish per-item)
        if (!event.items().isEmpty()) {
            OrderEvent.OrderItem firstItem = event.items().get(0);
            eventProducer.publishStockReservationFailed(
                    firstItem.sku(),
                    firstItem.variantId(),
                    null, // productId not in OrderEvent.OrderItem
                    firstItem.quantity(),
                    0,
                    event.orderId()
            );
        }
    }
}
```

> **Note:** You'll also need to add the `InventoryEventProducer` field and `BulkStockCheckRequest` import to the `OrderEventListener` class.

---

## Infrastructure Changes

### Add to root `pom.xml`

```xml
<modules>
    <module>common</module>
    <module>auth-service</module>
    <module>api-gateway</module>
    <module>product-service</module>
    <module>inventory-service</module>
    <module>order-service</module>
</modules>
```

### Add to `init-db/01-init-databases.sql`

```sql
CREATE DATABASE IF NOT EXISTS order_db;
GRANT ALL PRIVILEGES ON order_db.* TO 'ecommerce'@'%';
```

### Add to `docker-compose.dev.yml`

```yaml
  order-service:
    build:
      context: .
      dockerfile: order-service/Dockerfile
    container_name: order-service-dev
    environment:
      SPRING_PROFILES_ACTIVE: dev
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/order_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh
      SPRING_DATASOURCE_USERNAME: ${MYSQL_USER}
      SPRING_DATASOURCE_PASSWORD: ${MYSQL_PASSWORD}
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
    ports:
      - "8083:8083"
    depends_on:
      mysql:
        condition: service_healthy
    networks:
      - ecommerce-dev-network
```

> **Note:** No `INVENTORY_SERVICE_URL` — Kafka-only communication.

---

## API Endpoints Summary

| Method | Endpoint | Description | Returns | Auth |
|--------|----------|-------------|---------|------|
| `POST` | `/api/orders` | Create order (saga trigger) | **202 Accepted** (PENDING) | User |
| `GET` | `/api/orders/{id}` | Get order by ID (**poll for status**) | 200 | User |
| `GET` | `/api/orders/number/{orderNumber}` | Get order by order number | 200 | User |
| `GET` | `/api/orders/my-orders` | Get current user's orders (paginated) | 200 | User |
| `POST` | `/api/orders/{id}/confirm` | Confirm order (requires STOCK_RESERVED) | 200 | User |
| `POST` | `/api/orders/{id}/cancel` | Cancel order (saga compensation) | 200 | User |
| `POST` | `/api/orders/{id}/processing` | Mark as processing | 200 | Admin |
| `POST` | `/api/orders/{id}/ship` | Mark as shipped | 200 | Admin |
| `POST` | `/api/orders/{id}/deliver` | Mark as delivered | 200 | Admin |
| `GET` | `/api/orders/admin/by-status` | Get orders by status | 200 | Admin |

---

## Testing Strategy

### Unit Tests

- **OrderServiceTest** — Test service with mocked repository and Kafka producer
  - `createOrder_savesAsPendingAndPublishesEvent` — Verify PENDING status and ORDER_CREATED published
  - `cancelOrder_publishesCompensation` — Verify ORDER_CANCELLED published for STOCK_RESERVED orders
  - `cancelOrder_noCancellationEventForPending` — PENDING cancellation does NOT publish event
  - `confirmOrder_requiresStockReserved` — Reject confirm if not STOCK_RESERVED
  - `confirmOrder_publishesOrderCompleted` — Verify ORDER_COMPLETED published

- **InventoryEventListenerTest** — Test saga response handling
  - `handleStockReserved_transitionsPendingToStockReserved`
  - `handleStockReserved_idempotentForAlreadyReserved`
  - `handleStockReservationFailed_transitionsPendingToFailed`
  - `handleStockReservationFailed_ignoresNonPendingOrders`

### Integration Tests

- **Full Saga Test** — With `@EmbeddedKafka`
  - Create order → verify ORDER_CREATED published → simulate STOCK_RESERVED → verify status change
  - Create order → simulate STOCK_RESERVATION_FAILED → verify FAILED status

---

## Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| **Eventual consistency** | Client polls GET /api/orders/{id}; consider WebSocket/SSE for real-time updates |
| **Lost Kafka messages** | Producer uses `acks=all`, idempotence enabled, retry 3 times |
| **Inventory-service down** | Order stays PENDING indefinitely; implement saga timeout (scheduled task to FAIL stale PENDING orders after N minutes) |
| **Duplicate events** | Idempotent status transitions (only PENDING→STOCK_RESERVED, ignore if already transitioned) |
| **Partial reservation failure** | Inventory-service rolls back partial reservations before publishing STOCK_RESERVATION_FAILED |
| **Race condition: cancel before reservation** | PENDING cancellation does NOT publish ORDER_CANCELLED (nothing to compensate); if STOCK_RESERVED arrives after cancel, order is already CANCELLED |
| **Multiple STOCK_RESERVED events per order** | First event transitions status; subsequent ones are idempotent no-ops |
| **Kafka message loss** | Producer uses `acks=all` and idempotence; retry 3 times |

---

## Future Enhancements

1. **Saga Timeout** — Scheduled task to mark PENDING orders as FAILED after 15 minutes if no inventory response
2. **WebSocket/SSE** — Real-time order status updates instead of polling
3. **Dead Letter Queue** — Handle permanently failed Kafka messages
4. **Payment Service** — Add payment-service as another saga participant
5. **Order Event Sourcing** — Store all state transitions as events for audit trail

---

## Success Criteria

- [ ] Order-service starts successfully and connects to MySQL and Kafka
- [ ] `POST /api/orders` returns 202 Accepted with PENDING status
- [ ] `ORDER_CREATED` event is published to `order-events` topic
- [ ] Inventory-service consumes `ORDER_CREATED` and reserves stock
- [ ] Inventory-service publishes `STOCK_RESERVED` to `inventory-events` topic
- [ ] Order-service consumes `STOCK_RESERVED` and updates order to `STOCK_RESERVED`
- [ ] Inventory-service publishes `STOCK_RESERVATION_FAILED` when stock is insufficient
- [ ] Order-service consumes `STOCK_RESERVATION_FAILED` and updates order to `FAILED`
- [ ] `confirmOrder` publishes `ORDER_COMPLETED` and transitions to `CONFIRMED`
- [ ] `cancelOrder` publishes `ORDER_CANCELLED` (compensation) for reserved orders
- [ ] Paginated order listing works with `PageResponse`
- [ ] All status transitions are validated (no invalid transitions)
- [ ] Status transitions are idempotent (duplicate events don't cause errors)
- [ ] `GlobalExceptionHandler` returns consistent `ApiResponse` error format
- [ ] Flyway migration creates tables correctly
- [ ] No REST dependency on inventory-service — fully event-driven

