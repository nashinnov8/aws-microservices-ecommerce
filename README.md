# Ecommerce Microservices Platform

A cloud-native, event-driven ecommerce backend built with **Spring Boot 3.2**, **Apache Kafka**, and **MySQL**. All services communicate through a central **API Gateway** and use the **Saga pattern** (via Kafka) for distributed transactions.

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Microservices](#microservices)
  - [API Gateway](#api-gateway-port-7999)
  - [Auth Service](#auth-service-port-8080)
  - [Product Service](#product-service-port-8081)
  - [Inventory Service](#inventory-service-port-8082)
  - [Order Service](#order-service-port-8083)
  - [Common Module](#common-module)
- [Kafka Event Flow](#kafka-event-flow)
- [Tech Stack](#tech-stack)
- [Getting Started (Development)](#getting-started-development)
  - [Prerequisites](#prerequisites)
  - [1. Clone the Repository](#1-clone-the-repository)
  - [2. Configure Environment Variables](#2-configure-environment-variables)
  - [3. Start Infrastructure & Services](#3-start-infrastructure--services)
  - [4. Verify Services](#4-verify-services)
- [Importing Postman Collection](#importing-postman-collection)
- [API Endpoints Quick Reference](#api-endpoints-quick-reference)
- [Project Structure](#project-structure)

---

## Architecture Overview

```
                          ┌──────────────────┐
                          │   API Gateway     │
                          │   (Port 7999)     │
                          └────────┬─────────┘
                  ┌────────────────┼────────────────┐──────────────────┐
                  ▼                ▼                 ▼                  ▼
         ┌──────────────┐ ┌──────────────┐ ┌───────────────┐ ┌──────────────┐
         │ Auth Service  │ │Product Svc   │ │Inventory Svc  │ │ Order Svc    │
         │  (8080)       │ │  (8081)      │ │  (8082)       │ │  (8083)      │
         └──────┬───────┘ └──────┬───────┘ └──────┬────────┘ └──────┬───────┘
                │                │                 │                  │
                ▼                ▼                  ▼                  ▼
           ┌─────────┐     ┌─────────┐        ┌─────────┐       ┌─────────┐
           │ auth_db  │     │product_db│       │inventory_db│    │ order_db │
           └─────────┘     └─────────┘        └─────────┘       └─────────┘
                                │                 │                  │
                                └────────┬────────┘──────────────────┘
                                         ▼
                                   ┌───────────┐
                                   │   Kafka    │
                                   │ (Zookeeper)│
                                   └───────────┘
```

All external HTTP traffic goes through the **API Gateway** (port `7999`).  
Services communicate asynchronously via **Kafka topics** for order/inventory saga orchestration and product sync events.

---

## Microservices

### API Gateway (Port 7999)

The single entry point for all client requests. Built with **Spring Cloud Gateway**.

| Feature | Details |
|---|---|
| **Routing** | Routes requests to auth, product, inventory, and order services |
| **Authentication** | JWT validation via a custom `JwtAuthentication` gateway filter |
| **Rate Limiting** | Redis-backed request rate limiter (`RequestRateLimiter`) |
| **Circuit Breaker** | Resilience4j circuit breaker per service with fallback endpoints |
| **CORS** | Global CORS configuration for all origins and methods |
| **Logout** | Gateway-level token blacklisting using Redis |

**Route mapping:**

| External Path | Target Service | Auth Required |
|---|---|---|
| `/api/auth/login`, `/api/auth/register`, `/api/auth/forgot-password`, `/api/auth/verify-email`, `/api/auth/reset-password` | Auth Service | ❌ No |
| `/api/auth/**` (other) | Auth Service | ✅ Yes |
| `/api/auth/logout` | Gateway (local) | ✅ Yes |
| `/api/products/**`, `/api/categories/**`, `/api/brands/**` | Product Service | ✅ Yes |
| `/api/inventory/**` | Inventory Service | ✅ Yes |
| `/api/reservations/**` | Inventory Service | ✅ Yes |
| `/api/orders/**` | Order Service | ✅ Yes |

**Key files:**
- `JwtAuthenticationGatewayFilterFactory` – validates JWT, extracts user ID & role into headers (`X-User-Id`, `X-User-Role`)
- `TokenBlacklistService` – Redis-based token blacklist for logout
- Fallback controllers – return friendly error responses when services are down

---

### Auth Service (Port 8080)

Handles user identity: registration, login, password management, and JWT token lifecycle.

| Feature | Details |
|---|---|
| **Registration** | Email/password registration with password strength validation |
| **Login** | Returns JWT access token + refresh token |
| **Refresh Token Rotation** | Secure refresh token strategy |
| **Password Reset** | Forgot password & reset via email link |
| **JWT** | HMAC-SHA256 signed tokens with configurable expiration |

**Database:** `auth_db` (MySQL)  
**Entities:** `UserCredential`, `RefreshToken`  
**Roles:** `USER`, `ADMIN`

**Key endpoints:**

| Method | Path | Description |
|---|---|---|
| POST | `/api/auth/register` | Register new user |
| POST | `/api/auth/login` | Login, returns tokens |
| POST | `/api/auth/logout` | Logout (blacklist token) |
| POST | `/api/auth/forgot-password` | Send password reset email |
| POST | `/api/auth/reset-password` | Reset password with token |
| GET | `/api/auth/me` | Get current user info (protected) |
| PUT | `/api/auth/change-password` | Change password (protected) |

---

### Product Service (Port 8081)

Manages the product catalog including products, product variants, categories, and brands.

| Feature | Details |
|---|---|
| **Products** | CRUD operations with filtering and pagination |
| **Product Variants** | SKU-level management (size, color, price) |
| **Categories** | Hierarchical product categorization |
| **Brands** | Brand management |
| **Kafka Producer** | Publishes `ProductEvent` on product/variant create/update/delete |
| **Kafka Consumer** | Listens to `InventoryEvent` for stock-level sync |

**Database:** `product_db` (MySQL)  
**Kafka Topics:**
- **Produces:** `product-events` (PRODUCT_CREATED, PRODUCT_UPDATED, PRODUCT_DELETED, VARIANT_CREATED, VARIANT_UPDATED, VARIANT_DELETED)
- **Consumes:** `inventory-events` (STOCK_UPDATED, LOW_STOCK_ALERT)

---

### Inventory Service (Port 8082)

Manages stock levels, warehouses, stock reservations, and stock movements.

| Feature | Details |
|---|---|
| **Inventory** | Track stock per SKU with low-stock thresholds |
| **Warehouses** | Multi-warehouse support with capacity tracking |
| **Reservations** | Reserve stock for orders, bulk reserve/release |
| **Stock Movements** | Full audit trail of stock changes |
| **Kafka Producer** | Publishes `InventoryEvent` on stock changes |
| **Kafka Consumer** | Listens to `ProductEvent` (auto-create inventory) and `OrderEvent` (saga) |

**Database:** `inventory_db` (MySQL)  
**Kafka Topics:**
- **Produces:** `inventory-events` (STOCK_RESERVED, STOCK_RESERVATION_FAILED, STOCK_RELEASED, STOCK_UPDATED, LOW_STOCK_ALERT)
- **Consumes:** `product-events`, `order-events`

**Key endpoints:**

| Method | Path | Description |
|---|---|---|
| GET | `/api/inventory/{sku}` | Get stock info by SKU |
| POST | `/api/inventory` | Create inventory record |
| PUT | `/api/inventory/{id}/stock` | Update stock level |
| POST | `/api/inventory/bulk-check` | Bulk stock availability check |
| GET | `/api/inventory/low-stock` | Get low-stock items |
| POST | `/api/reservations` | Reserve stock |
| POST | `/api/reservations/bulk` | Bulk reserve stock |
| PUT | `/api/reservations/{id}` | Update reservation status |

---

### Order Service (Port 8083)

Manages orders and orchestrates the order lifecycle using the **Kafka Saga pattern**.

| Feature | Details |
|---|---|
| **Create Order** | Places an order and publishes `ORDER_CREATED` to Kafka |
| **Saga Orchestration** | Listens to `InventoryEvent` to advance/fail order state |
| **Order Lifecycle** | PENDING → STOCK_RESERVED → CONFIRMED → PROCESSING → SHIPPED → DELIVERED |
| **Cancel Order** | Publishes `ORDER_CANCELLED` to trigger stock release |
| **Kafka Producer** | Publishes `OrderEvent` |
| **Kafka Consumer** | Listens to `InventoryEvent` |

**Database:** `order_db` (MySQL)  
**Entities:** `Order`, `OrderItem`

**Order Status Flow:**
```
PENDING ──→ STOCK_RESERVED ──→ CONFIRMED ──→ PROCESSING ──→ SHIPPED ──→ DELIVERED
   │                                │
   └──→ FAILED                      └──→ CANCELLED
```

**Kafka Topics:**
- **Produces:** `order-events` (ORDER_CREATED, ORDER_CANCELLED, ORDER_COMPLETED)
- **Consumes:** `inventory-events` (STOCK_RESERVED, STOCK_RESERVATION_FAILED, STOCK_RELEASED)

**Key endpoints:**

| Method | Path | Description |
|---|---|---|
| POST | `/api/orders` | Create a new order |
| GET | `/api/orders/{id}` | Get order by ID |
| GET | `/api/orders` | List orders (with pagination) |
| GET | `/api/orders/my-orders` | List current user's orders |
| PUT | `/api/orders/{id}/cancel` | Cancel an order |
| PUT | `/api/orders/{id}/confirm` | Confirm an order |
| PUT | `/api/orders/{id}/status` | Update order status (admin) |

---

### Common Module

A shared library (`common-0.0.1-SNAPSHOT.jar`) used by all services for consistent API responses.

| Class | Description |
|---|---|
| `ApiResponse<T>` | Standard API response wrapper with `code`, `message`, `data`, `timestamp`, `traceId` |
| `PageResponse<T>` | Paginated response wrapper built from Spring's `Page<T>` |

---

## Kafka Event Flow

```
┌──────────────┐    product-events     ┌─────────────────┐   inventory-events   ┌──────────────┐
│Product Service│ ──────────────────▶  │Inventory Service│ ──────────────────▶  │ Order Service │
│              │                        │                 │                       │              │
│              │  ◀──────────────────  │                 │  ◀──────────────────  │              │
└──────────────┘    inventory-events    └─────────────────┘    order-events       └──────────────┘
```

**Saga: Create Order Flow**
1. **Order Service** creates order (status: `PENDING`) → publishes `ORDER_CREATED` to `order-events`
2. **Inventory Service** consumes `ORDER_CREATED` → reserves stock for each item
   - Success → publishes `STOCK_RESERVED` to `inventory-events`
   - Failure → publishes `STOCK_RESERVATION_FAILED` to `inventory-events`
3. **Order Service** consumes `inventory-events`:
   - `STOCK_RESERVED` → updates order status to `STOCK_RESERVED`
   - `STOCK_RESERVATION_FAILED` → updates order status to `FAILED`

**Saga: Cancel Order Flow**
1. **Order Service** cancels order → publishes `ORDER_CANCELLED` to `order-events`
2. **Inventory Service** consumes `ORDER_CANCELLED` → releases reserved stock → publishes `STOCK_RELEASED`

---

## Tech Stack

| Layer | Technology |
|---|---|
| **Language** | Java 21 |
| **Framework** | Spring Boot 3.2.8 |
| **API Gateway** | Spring Cloud Gateway (2023.0.1) |
| **Messaging** | Apache Kafka (Confluent 7.5.0) |
| **Database** | MySQL 8.0 |
| **Caching / Rate Limiting** | Redis 7 (Alpine) |
| **Circuit Breaker** | Resilience4j |
| **Build** | Maven (multi-module) |
| **Containerization** | Docker & Docker Compose |
| **Authentication** | JWT (HMAC-SHA256) |

---

## Getting Started (Development)

### Prerequisites

- **Java 21** (JDK)
- **Maven 3.8+**
- **Docker** and **Docker Compose** (v2+)
- **Git**

### 1. Clone the Repository

```bash
git clone https://github.com/<your-username>/aws-microservices-ecommerce.git
cd aws-microservices-ecommerce
```

### 2. Configure Environment Variables

Create a `.env` file in the project root directory:

```bash
cp .env.example .env    # or create manually
```

Fill in the following variables in `.env`:

```env
# ─── MySQL ───
MYSQL_ROOT_PASSWORD=rootpassword
MYSQL_DATABASE=auth_db
MYSQL_USER=auth
MYSQL_PASSWORD=auth

# ─── Auth Service Datasource ───
SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/auth_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh
SPRING_DATASOURCE_USERNAME=auth
SPRING_DATASOURCE_PASSWORD=auth

# ─── Product Service Datasource ───
PRODUCT_DATASOURCE_URL=jdbc:mysql://mysql:3306/product_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh
PRODUCT_DATASOURCE_USERNAME=auth
PRODUCT_DATASOURCE_PASSWORD=auth

# ─── Mail (for auth-service email features) ───
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-password

# ─── JWT ───
JWT_SECRET=Md2DcKtJIOAiWmkN3+VpZH6Eyu7fyZy03KB5hHyV5n4=
JWT_EXPIRATION=3600000
JWT_REFRESH_EXPIRATION=86400000
JWT_VERIFICATION_EXPIRATION=3600000
```

> **Note:** The `.env` file is git-ignored. Never commit secrets to the repository.

> **Tip:** For Gmail app passwords, go to [Google App Passwords](https://myaccount.google.com/apppasswords).

### 3. Start Infrastructure & Services

**Option A: Start everything with Docker Compose (recommended)**

```bash
docker compose -f docker-compose.dev.yml up --build -d
```

This starts:
- **MySQL** (port 3306) – with auto-init script creating `auth_db`, `product_db`, `inventory_db`, `order_db`
- **Redis** (port 6379) – for API Gateway rate limiting & token blacklisting
- **Zookeeper** (port 2181) – Kafka coordination
- **Kafka** (port 9092 internal / 29092 external) – event messaging
- **Auth Service** (port 8080)
- **Product Service** (port 8081)
- **Inventory Service** (port 8082)
- **Order Service** (port 8083)
- **API Gateway** (port 7999)

**Option B: Start only infrastructure (run services from IDE)**

```bash
docker compose -f docker-compose.dev.yml up mysql redis zookeeper kafka -d
```

Then run each service individually from your IDE with the `dev` Spring profile. Each service uses `localhost` defaults when running outside Docker:
- Kafka: `localhost:29092`
- MySQL: `localhost:3306`

### 4. Verify Services

Check that all containers are healthy:

```bash
docker compose -f docker-compose.dev.yml ps
```

Test the API Gateway health endpoint:

```bash
curl http://localhost:7999/actuator/health
```

Test individual services:

```bash
# Auth Service
curl http://localhost:8080/actuator/health

# Product Service
curl http://localhost:8081/actuator/health

# Inventory Service
curl http://localhost:8082/actuator/health

# Order Service
curl http://localhost:8083/actuator/health
```

### Stopping Services

```bash
docker compose -f docker-compose.dev.yml down
```

To also remove volumes (database data, Kafka data):

```bash
docker compose -f docker-compose.dev.yml down -v
```

---

## Importing Postman Collection

A ready-to-use Postman collection is included in the project for testing all APIs.

### Step-by-step:

1. **Open Postman** (desktop app or web version)

2. **Click "Import"** (top-left of Postman)

   ![Postman Import](https://learning.postman.com/docs/img/import-export-import-ui-v10-2.jpg)

3. **Drag and drop** (or browse to) the file:
   ```
   postman/Ecommerce_Microservices.postman_collection.json
   ```

4. **Click "Import"** to confirm

5. The collection **"Ecommerce Microservices"** will appear in your sidebar with folders for each service

### Setting up the environment:

After importing, create a Postman **Environment** with these variables:

| Variable | Value | Description |
|---|---|---|
| `base_url` | `http://localhost:7999` | API Gateway URL |
| `auth_token` | *(leave empty, auto-filled)* | JWT access token |

> **Tip:** After calling the `/api/auth/login` endpoint, copy the returned `accessToken` and set it as the `auth_token` variable. Or use Postman's "Tests" script to auto-set it:
> ```javascript
> // Add this to the "Tests" tab of the Login request:
> if (pm.response.code === 200) {
>     var json = pm.response.json();
>     pm.environment.set("auth_token", json.data.accessToken);
> }
> ```

### Usage flow:

1. **Register** → `POST /api/auth/register`
2. **Login** → `POST /api/auth/login` (save the token)
3. **Use token** → All other requests should include: `Authorization: Bearer {{auth_token}}`
4. **Create products** → `POST /api/products`
5. **Create inventory** → `POST /api/inventory`
6. **Place an order** → `POST /api/orders` (triggers Kafka saga)
7. **Check order status** → `GET /api/orders/{id}` (observe saga progression)

---

## API Endpoints Quick Reference

All endpoints are accessed via the API Gateway at `http://localhost:7999`.

### Auth (`/api/auth`)
| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/register` | ❌ | Register |
| POST | `/api/auth/login` | ❌ | Login |
| POST | `/api/auth/logout` | ✅ | Logout |
| POST | `/api/auth/forgot-password` | ❌ | Forgot password |
| POST | `/api/auth/reset-password` | ❌ | Reset password |
| GET | `/api/auth/me` | ✅ | Current user info |
| PUT | `/api/auth/change-password` | ✅ | Change password |

### Products (`/api/products`, `/api/categories`, `/api/brands`)
| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/products` | ✅ | List products |
| GET | `/api/products/{id}` | ✅ | Get product |
| POST | `/api/products` | ✅ | Create product |
| PUT | `/api/products/{id}` | ✅ | Update product |
| DELETE | `/api/products/{id}` | ✅ | Delete product |
| GET | `/api/categories` | ✅ | List categories |
| POST | `/api/categories` | ✅ | Create category |
| GET | `/api/brands` | ✅ | List brands |
| POST | `/api/brands` | ✅ | Create brand |

### Inventory (`/api/inventory`, `/api/reservations`)
| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/inventory/{sku}` | ✅ | Get stock by SKU |
| POST | `/api/inventory` | ✅ | Create inventory |
| PUT | `/api/inventory/{id}/stock` | ✅ | Update stock |
| POST | `/api/inventory/bulk-check` | ✅ | Bulk stock check |
| GET | `/api/inventory/low-stock` | ✅ | Low stock items |
| POST | `/api/reservations` | ✅ | Reserve stock |
| POST | `/api/reservations/bulk` | ✅ | Bulk reserve |
| PUT | `/api/reservations/{id}` | ✅ | Update reservation |

### Orders (`/api/orders`)
| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/orders` | ✅ | Create order (starts saga) |
| GET | `/api/orders/{id}` | ✅ | Get order |
| GET | `/api/orders` | ✅ | List orders |
| GET | `/api/orders/my-orders` | ✅ | My orders |
| PUT | `/api/orders/{id}/cancel` | ✅ | Cancel order |
| PUT | `/api/orders/{id}/confirm` | ✅ | Confirm order |
| PUT | `/api/orders/{id}/status` | ✅ | Update status (admin) |

---

## Project Structure

```
aws-microservices-ecommerce/
├── docker-compose.dev.yml          # Development Docker Compose (all services)
├── docker-compose.prod.yml         # Production Docker Compose
├── pom.xml                         # Parent Maven POM (multi-module)
├── .env                            # Environment variables (git-ignored)
├── init-db/
│   └── 01-init-databases.sql       # Auto-creates all databases on first MySQL start
├── postman/                        # Postman collection (git-ignored, local only)
├── planner/                        # Planning docs (git-ignored, local only)
├── reviews/                        # Review docs (git-ignored, local only)
├── common/                         # Shared library (ApiResponse, PageResponse)
│   ├── pom.xml
│   └── src/main/java/response/
├── api-gateway/                    # Spring Cloud Gateway
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/ecommerce/apigateway/
│       ├── config/                 # Redis, Rate limit configs
│       ├── controller/fallback/    # Circuit breaker fallbacks
│       ├── filter/                 # JWT authentication filter
│       ├── service/                # Token blacklist service
│       └── utils/                  # Fallback response utilities
├── auth-service/                   # User authentication & authorization
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/ecommerce/authservice/
│       ├── config/                 # Security, JWT, Mail configs
│       ├── controller/             # Auth REST controller
│       ├── domain/                 # Entities, enums, repositories
│       ├── dto/                    # Request/response DTOs
│       ├── exception/              # Global exception handler
│       ├── filter/                 # Authentication filter
│       ├── service/                # Auth, JWT, Email services
│       └── validation/             # Password validator
├── product-service/                # Product catalog management
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/ecommerce/productservice/
│       ├── controller/             # Product, Category, Brand, Variant controllers
│       ├── domain/                 # Entities, repositories
│       ├── dto/                    # DTOs + Kafka events
│       ├── exception/              # Global exception handler
│       ├── kafka/                  # ProductEventProducer, InventoryEventListener
│       └── service/                # Product, Category, Brand, Variant services
├── inventory-service/              # Stock & warehouse management
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/ecommerce/inventoryservice/
│       ├── config/                 # Security, Kafka, Scheduled tasks
│       ├── controller/             # Inventory, Reservation controllers
│       ├── domain/                 # Entities (Inventory, Warehouse, StockMovement, etc.)
│       ├── dto/                    # DTOs + Kafka events
│       ├── exception/              # Global exception handler
│       ├── kafka/                  # InventoryEventProducer, OrderEventListener, ProductEventListener
│       └── service/                # Inventory, Reservation services
└── order-service/                  # Order management with Kafka Saga
    ├── pom.xml
    ├── Dockerfile
    └── src/main/java/com/ecommerce/orderservice/
        ├── controller/             # Order REST controller
        ├── domain/                 # Entities (Order, OrderItem), enums, repositories
        ├── dto/                    # DTOs + Kafka events
        ├── exception/              # Global exception handler
        ├── kafka/                  # OrderEventProducer, InventoryEventListener
        └── service/                # Order service
```

---

## License

This project is for educational/portfolio purposes.
