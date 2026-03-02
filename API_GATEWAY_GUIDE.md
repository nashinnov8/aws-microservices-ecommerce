# API Gateway Implementation Guide

## Overview
This guide covers two approaches for implementing an API Gateway in your e-commerce microservices platform:
1. **AWS API Gateway** (Managed Service)
2. **Self-Managed API Gateway** (Spring Cloud Gateway, Kong, Nginx)

Both approaches will route requests to your services: Auth Service, Product Service (Lambda), and Inventory Service.

---

## Table of Contents
- [Architecture Comparison](#architecture-comparison)
- [Option 1: AWS API Gateway](#option-1-aws-api-gateway)
- [Option 2: Self-Managed with Spring Cloud Gateway](#option-2-self-managed-with-spring-cloud-gateway)
- [Option 3: Self-Managed with Kong](#option-3-self-managed-with-kong)
- [Option 4: Self-Managed with Nginx](#option-4-self-managed-with-nginx)
- [Comparison Table](#comparison-table)
- [Recommendation](#recommendation)

---

## Architecture Comparison

### Current Architecture with AWS API Gateway
```
┌──────────────┐
│   Clients    │
│ (Web/Mobile) │
└──────┬───────┘
       │
       ▼
┌─────────────────────────────────────┐
│      AWS API Gateway (Managed)      │
│  - Authentication (Lambda Authorizer)│
│  - Rate Limiting                    │
│  - Request/Response Transformation  │
│  - Caching                          │
└──────┬──────────────┬───────────────┘
       │              │
       ▼              ▼
┌──────────────┐  ┌──────────────┐
│ Auth Service │  │Product Service│
│  (ECS/EC2)   │  │  (Lambda)    │
└──────────────┘  └──────────────┘
       │
       ▼
┌──────────────┐
│  Inventory   │
│   Service    │
└──────────────┘
```

### Architecture with Self-Managed Gateway
```
┌──────────────┐
│   Clients    │
│ (Web/Mobile) │
└──────┬───────┘
       │
       ▼
┌─────────────────────────────────────┐
│   API Gateway Service (Custom)      │
│   - Spring Cloud Gateway/Kong/Nginx │
│   - Running on ECS/EC2/EKS          │
│   - JWT Validation                  │
│   - Rate Limiting                   │
│   - Load Balancing                  │
└──────┬──────────────┬───────────────┘
       │              │
       ▼              ▼
┌──────────────┐  ┌──────────────┐
│ Auth Service │  │Product Service│
│  (ECS/EC2)   │  │  (Lambda)    │
└──────────────┘  └──────────────┘
       │
       ▼
┌──────────────┐
│  Inventory   │
│   Service    │
└──────────────┘
```

---

## Option 1: AWS API Gateway

### Advantages
✅ Fully managed by AWS (no infrastructure to maintain)
✅ Automatic scaling
✅ Built-in authentication (Lambda Authorizers)
✅ Native integration with Lambda
✅ Built-in caching, throttling, and monitoring
✅ Pay-per-request pricing (no idle costs)

### Disadvantages
❌ Can be expensive at high scale
❌ Limited customization options
❌ Vendor lock-in to AWS
❌ Cold start issues with Lambda Authorizers

### Implementation

#### 1. Architecture Setup

```yaml
# template.yaml (AWS SAM)
AWSTemplateFormatVersion: '2010-09-09'
Transform: AWS::Serverless-2016-10-31
Description: E-commerce API Gateway

Parameters:
  Environment:
    Type: String
    Default: dev
    AllowedValues: [dev, staging, prod]

Resources:
  # API Gateway
  EcommerceApiGateway:
    Type: AWS::Serverless::Api
    Properties:
      Name: !Sub ecommerce-api-${Environment}
      StageName: !Ref Environment
      Cors:
        AllowMethods: "'GET,POST,PUT,DELETE,PATCH,OPTIONS'"
        AllowHeaders: "'Content-Type,Authorization,X-Api-Key'"
        AllowOrigin: "'*'"
      Auth:
        DefaultAuthorizer: JWTAuthorizer
        Authorizers:
          JWTAuthorizer:
            FunctionArn: !GetAtt AuthorizerFunction.Arn
            Identity:
              Header: Authorization
              ReauthorizeEvery: 300  # Cache for 5 minutes
      GatewayResponses:
        UNAUTHORIZED:
          StatusCode: 401
          ResponseParameters:
            gatewayresponse.header.Access-Control-Allow-Origin: "'*'"
        ACCESS_DENIED:
          StatusCode: 403
          ResponseParameters:
            gatewayresponse.header.Access-Control-Allow-Origin: "'*'"
      MethodSettings:
        - ResourcePath: '/*'
          HttpMethod: '*'
          ThrottlingBurstLimit: 5000
          ThrottlingRateLimit: 1000
          LoggingLevel: INFO
          DataTraceEnabled: true
          MetricsEnabled: true

  # Lambda Authorizer for JWT Validation
  AuthorizerFunction:
    Type: AWS::Serverless::Function
    Properties:
      FunctionName: !Sub jwt-authorizer-${Environment}
      Runtime: java17
      Handler: com.ecommerce.gateway.JwtAuthorizerHandler::handleRequest
      CodeUri: gateway-authorizer/
      MemorySize: 512
      Timeout: 10
      Environment:
        Variables:
          JWT_SECRET: !Sub '{{resolve:ssm:/ecommerce/${Environment}/jwt-secret}}'
          AUTH_SERVICE_URL: !Sub '{{resolve:ssm:/ecommerce/${Environment}/auth-service-url}}'

  # Auth Service Integration
  AuthServiceHttpApi:
    Type: AWS::Serverless::HttpApi
    Properties:
      StageName: !Ref Environment
      DefinitionBody:
        openapi: '3.0.1'
        info:
          title: !Sub auth-service-proxy-${Environment}
        paths:
          /auth/{proxy+}:
            x-amazon-apigateway-any-method:
              x-amazon-apigateway-integration:
                type: http_proxy
                httpMethod: ANY
                uri: !Sub 
                  - 'http://${LoadBalancerDNS}/{proxy}'
                  - LoadBalancerDNS: !Sub '{{resolve:ssm:/ecommerce/${Environment}/auth-service-lb}}'
                connectionType: VPC_LINK
                connectionId: !Ref VpcLink

  # VPC Link for Auth Service (ECS)
  VpcLink:
    Type: AWS::ApiGatewayV2::VpcLink
    Properties:
      Name: !Sub ecommerce-vpc-link-${Environment}
      SubnetIds:
        - !Sub '{{resolve:ssm:/ecommerce/${Environment}/subnet-1}}'
        - !Sub '{{resolve:ssm:/ecommerce/${Environment}/subnet-2}}'
      SecurityGroupIds:
        - !Sub '{{resolve:ssm:/ecommerce/${Environment}/security-group}}'

  # Product Service Routes (Lambda)
  CreateProductFunction:
    Type: AWS::Serverless::Function
    Properties:
      CodeUri: product-service/
      Handler: com.ecommerce.product.handler.CreateProductHandler::handleRequest
      Runtime: java17
      Events:
        CreateProduct:
          Type: Api
          Properties:
            RestApiId: !Ref EcommerceApiGateway
            Path: /api/v1/products
            Method: POST

  GetProductFunction:
    Type: AWS::Serverless::Function
    Properties:
      CodeUri: product-service/
      Handler: com.ecommerce.product.handler.GetProductHandler::handleRequest
      Runtime: java17
      Events:
        GetProduct:
          Type: Api
          Properties:
            RestApiId: !Ref EcommerceApiGateway
            Path: /api/v1/products/{productId}
            Method: GET

  ListProductsFunction:
    Type: AWS::Serverless::Function
    Properties:
      CodeUri: product-service/
      Handler: com.ecommerce.product.handler.ListProductsHandler::handleRequest
      Runtime: java17
      Events:
        ListProducts:
          Type: Api
          Properties:
            RestApiId: !Ref EcommerceApiGateway
            Path: /api/v1/products
            Method: GET

  # Inventory Service Integration (HTTP Proxy)
  InventoryServiceProxy:
    Type: AWS::Serverless::Function
    Properties:
      FunctionName: !Sub inventory-proxy-${Environment}
      Runtime: java17
      Handler: com.ecommerce.gateway.InventoryProxyHandler::handleRequest
      CodeUri: gateway-proxy/
      Environment:
        Variables:
          INVENTORY_SERVICE_URL: !Sub '{{resolve:ssm:/ecommerce/${Environment}/inventory-service-url}}'
      Events:
        ProxyRequest:
          Type: Api
          Properties:
            RestApiId: !Ref EcommerceApiGateway
            Path: /api/v1/inventory/{proxy+}
            Method: ANY

Outputs:
  ApiGatewayUrl:
    Description: "API Gateway endpoint URL"
    Value: !Sub "https://${EcommerceApiGateway}.execute-api.${AWS::Region}.amazonaws.com/${Environment}"
    Export:
      Name: !Sub ecommerce-api-url-${Environment}

  ApiGatewayId:
    Description: "API Gateway ID"
    Value: !Ref EcommerceApiGateway
```

#### 2. JWT Authorizer Implementation

```java
// JwtAuthorizerHandler.java
package com.ecommerce.gateway;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayCustomAuthorizerEvent;
import com.amazonaws.services.lambda.runtime.events.IamPolicyResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.util.Collections;

public class JwtAuthorizerHandler implements 
    RequestHandler<APIGatewayCustomAuthorizerEvent, IamPolicyResponse> {
    
    private final String jwtSecret = System.getenv("JWT_SECRET");
    
    @Override
    public IamPolicyResponse handleRequest(APIGatewayCustomAuthorizerEvent event, Context context) {
        String token = event.getAuthorizationToken();
        
        // Remove "Bearer " prefix
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        
        try {
            // Validate JWT token
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                .build()
                .parseClaimsJws(token)
                .getBody();
            
            String userId = claims.getSubject();
            String role = claims.get("role", String.class);
            
            // Generate IAM Policy
            return IamPolicyResponse.builder()
                .withPrincipalId(userId)
                .withPolicyDocument(generatePolicy("Allow", event.getMethodArn()))
                .withContext(Collections.singletonMap("role", role))
                .build();
                
        } catch (Exception e) {
            context.getLogger().log("JWT validation failed: " + e.getMessage());
            throw new RuntimeException("Unauthorized");
        }
    }
    
    private IamPolicyResponse.PolicyDocument generatePolicy(String effect, String resource) {
        return IamPolicyResponse.PolicyDocument.builder()
            .withVersion("2012-10-17")
            .withStatement(Collections.singletonList(
                IamPolicyResponse.Statement.builder()
                    .withEffect(effect)
                    .withAction("execute-api:Invoke")
                    .withResource(resource)
                    .build()
            ))
            .build();
    }
}
```

#### 3. Deployment Commands

```bash
# Build the project
mvn clean package

# Deploy using SAM
sam build
sam deploy \
  --template-file template.yaml \
  --stack-name ecommerce-api-gateway \
  --capabilities CAPABILITY_IAM \
  --parameter-overrides Environment=dev

# Get API Gateway URL
aws cloudformation describe-stacks \
  --stack-name ecommerce-api-gateway \
  --query 'Stacks[0].Outputs[?OutputKey==`ApiGatewayUrl`].OutputValue' \
  --output text
```

#### 4. API Gateway Usage Plans & API Keys

```yaml
# Add to template.yaml
UsagePlan:
  Type: AWS::ApiGateway::UsagePlan
  Properties:
    UsagePlanName: !Sub ecommerce-usage-plan-${Environment}
    Description: Usage plan for e-commerce API
    ApiStages:
      - ApiId: !Ref EcommerceApiGateway
        Stage: !Ref Environment
    Throttle:
      BurstLimit: 5000
      RateLimit: 1000
    Quota:
      Limit: 1000000
      Period: MONTH

ApiKey:
  Type: AWS::ApiGateway::ApiKey
  Properties:
    Name: !Sub ecommerce-api-key-${Environment}
    Description: API Key for external clients
    Enabled: true

UsagePlanKey:
  Type: AWS::ApiGateway::UsagePlanKey
  Properties:
    KeyId: !Ref ApiKey
    KeyType: API_KEY
    UsagePlanId: !Ref UsagePlan
```

---

## Option 2: Self-Managed with Spring Cloud Gateway

### Advantages
✅ Full control over routing logic
✅ Java-based (matches your existing stack)
✅ Excellent Spring ecosystem integration
✅ Custom filters and predicates
✅ No AWS vendor lock-in
✅ Lower cost at high scale

### Disadvantages
❌ You manage infrastructure (ECS, scaling, monitoring)
❌ Need to implement caching, rate limiting
❌ Requires more development effort

### Implementation

#### 1. Project Structure

```
api-gateway/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── ecommerce/
│   │   │           └── gateway/
│   │   │               ├── ApiGatewayApplication.java
│   │   │               ├── config/
│   │   │               │   ├── GatewayConfig.java
│   │   │               │   ├── SecurityConfig.java
│   │   │               │   └── CorsConfig.java
│   │   │               ├── filter/
│   │   │               │   ├── JwtAuthenticationFilter.java
│   │   │               │   ├── LoggingFilter.java
│   │   │               │   └── RateLimitFilter.java
│   │   │               └── exception/
│   │   │                   └── GlobalExceptionHandler.java
│   │   └── resources/
│   │       ├── application.yml
│   │       └── application-dev.yml
│   └── test/
├── Dockerfile
├── pom.xml
└── README.md
```

#### 2. Maven Dependencies (pom.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
    </parent>

    <groupId>com.ecommerce</groupId>
    <artifactId>api-gateway</artifactId>
    <version>1.0.0</version>
    <name>API Gateway</name>

    <properties>
        <java.version>17</java.version>
        <spring-cloud.version>2023.0.0</spring-cloud.version>
    </properties>

    <dependencies>
        <!-- Spring Cloud Gateway -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway</artifactId>
        </dependency>

        <!-- Spring Boot Actuator -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- Redis for Rate Limiting -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
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

        <!-- Resilience4j for Circuit Breaker -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-circuitbreaker-reactor-resilience4j</artifactId>
        </dependency>

        <!-- Micrometer for Metrics -->
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

#### 3. Application Configuration (application.yml)

```yaml
server:
  port: 8080

spring:
  application:
    name: api-gateway
  
  cloud:
    gateway:
      # Global CORS Configuration
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins: "*"
            allowedMethods:
              - GET
              - POST
              - PUT
              - DELETE
              - PATCH
              - OPTIONS
            allowedHeaders: "*"
            exposedHeaders:
              - Authorization
            maxAge: 3600

      # Route Definitions
      routes:
        # Auth Service Routes
        - id: auth-service
          uri: ${AUTH_SERVICE_URL:http://localhost:8080}
          predicates:
            - Path=/api/v1/auth/**
          filters:
            - RewritePath=/api/v1/auth/(?<segment>.*), /auth/$\{segment}
            - name: CircuitBreaker
              args:
                name: authServiceCircuitBreaker
                fallbackUri: forward:/fallback/auth
            - name: Retry
              args:
                retries: 3
                statuses: BAD_GATEWAY,GATEWAY_TIMEOUT
                methods: GET,POST
                backoff:
                  firstBackoff: 50ms
                  maxBackoff: 500ms
                  factor: 2

        # Product Service Routes (Lambda via API Gateway)
        - id: product-service
          uri: ${PRODUCT_SERVICE_URL:https://your-lambda-api-gateway.amazonaws.com}
          predicates:
            - Path=/api/v1/products/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 100
                redis-rate-limiter.burstCapacity: 200
                redis-rate-limiter.requestedTokens: 1
            - name: CircuitBreaker
              args:
                name: productServiceCircuitBreaker
                fallbackUri: forward:/fallback/products

        # Inventory Service Routes
        - id: inventory-service
          uri: ${INVENTORY_SERVICE_URL:http://localhost:8081}
          predicates:
            - Path=/api/v1/inventory/**
          filters:
            - RewritePath=/api/v1/inventory/(?<segment>.*), /api/v1/inventory/$\{segment}
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 100
                redis-rate-limiter.burstCapacity: 200
            - name: CircuitBreaker
              args:
                name: inventoryServiceCircuitBreaker
                fallbackUri: forward:/fallback/inventory

      # Default Filters (applied to all routes)
      default-filters:
        - name: Retry
          args:
            retries: 3
            statuses: BAD_GATEWAY
        - AddResponseHeader=X-Response-Time, ${responseTime}

  # Redis Configuration (for Rate Limiting)
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD:}
    timeout: 2000ms

# JWT Configuration
jwt:
  secret: ${JWT_SECRET:your-jwt-secret-key}
  expiration: ${JWT_EXPIRATION:3600000}

# Management Endpoints
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,gateway
  endpoint:
    health:
      show-details: always
    gateway:
      enabled: true
  metrics:
    export:
      prometheus:
        enabled: true

# Logging
logging:
  level:
    org.springframework.cloud.gateway: DEBUG
    reactor.netty.http.client: DEBUG
```

#### 4. Main Application Class

```java
package com.ecommerce.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
            // Health check route (public)
            .route("health-check", r -> r
                .path("/health")
                .uri("http://localhost:8080"))
            .build();
    }
}
```

#### 5. JWT Authentication Filter

```java
package com.ecommerce.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.util.List;

@Slf4j
@Component
public class JwtAuthenticationFilter extends 
    AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    @Value("${jwt.secret}")
    private String jwtSecret;

    public JwtAuthenticationFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            // Skip authentication for public endpoints
            if (isPublicEndpoint(exchange.getRequest().getPath().value())) {
                return chain.filter(exchange);
            }

            String authHeader = exchange.getRequest()
                .getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return onError(exchange, "Missing or invalid Authorization header", 
                    HttpStatus.UNAUTHORIZED);
            }

            String token = authHeader.substring(7);

            try {
                SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
                Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

                // Add user info to request headers
                exchange.getRequest()
                    .mutate()
                    .header("X-User-Id", claims.getSubject())
                    .header("X-User-Role", claims.get("role", String.class))
                    .build();

                return chain.filter(exchange);

            } catch (Exception e) {
                log.error("JWT validation failed: {}", e.getMessage());
                return onError(exchange, "Invalid JWT token", HttpStatus.UNAUTHORIZED);
            }
        };
    }

    private boolean isPublicEndpoint(String path) {
        List<String> publicEndpoints = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/forgot-password",
            "/health",
            "/actuator/health"
        );
        return publicEndpoints.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> onError(ServerWebExchange exchange, String message, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }

    public static class Config {
        // Configuration properties if needed
    }
}
```

#### 6. Rate Limiting Configuration

```java
package com.ecommerce.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            // Rate limit by user ID
            String userId = exchange.getRequest()
                .getHeaders()
                .getFirst("X-User-Id");
            
            if (userId != null) {
                return Mono.just(userId);
            }
            
            // Fallback to IP address
            String ip = exchange.getRequest()
                .getRemoteAddress()
                .getAddress()
                .getHostAddress();
            
            return Mono.just(ip);
        };
    }
}
```

#### 7. Fallback Controller

```java
package com.ecommerce.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/auth")
    public ResponseEntity<?> authServiceFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of(
                "success", false,
                "message", "Auth service is temporarily unavailable. Please try again later."
            ));
    }

    @GetMapping("/products")
    public ResponseEntity<?> productServiceFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of(
                "success", false,
                "message", "Product service is temporarily unavailable. Please try again later."
            ));
    }

    @GetMapping("/inventory")
    public ResponseEntity<?> inventoryServiceFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of(
                "success", false,
                "message", "Inventory service is temporarily unavailable. Please try again later."
            ));
    }
}
```

#### 8. Dockerfile

```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

#### 9. docker-compose.yml

```yaml
version: '3.8'

services:
  redis:
    image: redis:7-alpine
    container_name: gateway-redis
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    networks:
      - gateway-network

  api-gateway:
    build: .
    container_name: api-gateway
    environment:
      SPRING_PROFILES_ACTIVE: dev
      AUTH_SERVICE_URL: http://auth-service:8080
      INVENTORY_SERVICE_URL: http://inventory-service:8081
      PRODUCT_SERVICE_URL: ${PRODUCT_SERVICE_URL}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      JWT_SECRET: ${JWT_SECRET}
    ports:
      - "8080:8080"
    depends_on:
      - redis
    networks:
      - gateway-network

networks:
  gateway-network:
    driver: bridge

volumes:
  redis_data:
```

#### 10. Deploy to AWS ECS

```bash
# Build Docker image
docker build -t api-gateway:latest .

# Tag for ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin <account-id>.dkr.ecr.us-east-1.amazonaws.com
docker tag api-gateway:latest <account-id>.dkr.ecr.us-east-1.amazonaws.com/api-gateway:latest

# Push to ECR
docker push <account-id>.dkr.ecr.us-east-1.amazonaws.com/api-gateway:latest

# Deploy to ECS (using task definition)
aws ecs update-service \
  --cluster ecommerce-cluster \
  --service api-gateway-service \
  --force-new-deployment
```

---

## Option 3: Self-Managed with Kong

### Quick Setup

```yaml
# docker-compose-kong.yml
version: '3.8'

services:
  kong-database:
    image: postgres:15
    environment:
      POSTGRES_USER: kong
      POSTGRES_DB: kong
      POSTGRES_PASSWORD: kong
    volumes:
      - kong_data:/var/lib/postgresql/data

  kong-migration:
    image: kong:3.4
    command: kong migrations bootstrap
    environment:
      KONG_DATABASE: postgres
      KONG_PG_HOST: kong-database
      KONG_PG_USER: kong
      KONG_PG_PASSWORD: kong
    depends_on:
      - kong-database

  kong:
    image: kong:3.4
    environment:
      KONG_DATABASE: postgres
      KONG_PG_HOST: kong-database
      KONG_PG_USER: kong
      KONG_PG_PASSWORD: kong
      KONG_PROXY_ACCESS_LOG: /dev/stdout
      KONG_ADMIN_ACCESS_LOG: /dev/stdout
      KONG_PROXY_ERROR_LOG: /dev/stderr
      KONG_ADMIN_ERROR_LOG: /dev/stderr
      KONG_ADMIN_LISTEN: 0.0.0.0:8001
    ports:
      - "8000:8000"  # Proxy
      - "8443:8443"  # Proxy SSL
      - "8001:8001"  # Admin API
    depends_on:
      - kong-database
      - kong-migration

  konga:
    image: pantsel/konga
    environment:
      NODE_ENV: production
    ports:
      - "1337:1337"
    depends_on:
      - kong

volumes:
  kong_data:
```

### Configure Routes

```bash
# Add Auth Service
curl -i -X POST http://localhost:8001/services \
  --data name=auth-service \
  --data url=http://auth-service:8080

curl -i -X POST http://localhost:8001/services/auth-service/routes \
  --data "paths[]=/api/v1/auth" \
  --data "strip_path=false"

# Add JWT Plugin
curl -X POST http://localhost:8001/services/auth-service/plugins \
  --data "name=jwt"

# Add Rate Limiting
curl -X POST http://localhost:8001/services/auth-service/plugins \
  --data "name=rate-limiting" \
  --data "config.minute=100"
```

---

## Option 4: Self-Managed with Nginx

### nginx.conf

```nginx
events {
    worker_connections 1024;
}

http {
    # Upstream services
    upstream auth_service {
        server auth-service:8080;
    }

    upstream product_service {
        server your-lambda-api-gateway.amazonaws.com:443;
    }

    upstream inventory_service {
        server inventory-service:8081;
    }

    # Rate limiting
    limit_req_zone $binary_remote_addr zone=api_limit:10m rate=100r/m;

    server {
        listen 80;
        server_name api.yourdomain.com;

        # CORS headers
        add_header Access-Control-Allow-Origin *;
        add_header Access-Control-Allow-Methods "GET, POST, PUT, DELETE, OPTIONS";
        add_header Access-Control-Allow-Headers "Authorization, Content-Type";

        # Auth Service
        location /api/v1/auth {
            limit_req zone=api_limit burst=20;
            proxy_pass http://auth_service;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
        }

        # Product Service
        location /api/v1/products {
            limit_req zone=api_limit burst=50;
            proxy_pass https://product_service;
            proxy_ssl_server_name on;
            proxy_set_header Authorization $http_authorization;
        }

        # Inventory Service
        location /api/v1/inventory {
            limit_req zone=api_limit burst=30;
            proxy_pass http://inventory_service;
            proxy_set_header Host $host;
        }

        # Health check
        location /health {
            return 200 "OK";
        }
    }
}
```

---

## Comparison Table

| Feature | AWS API Gateway | Spring Cloud Gateway | Kong | Nginx |
|---------|----------------|---------------------|------|-------|
| **Cost** | Pay-per-request | Infrastructure only | Infrastructure only | Infrastructure only |
| **Scalability** | Auto-scaling | Manual/Auto (ECS) | Manual/Auto | Manual/Auto |
| **Complexity** | Low | Medium | Medium | Low |
| **Customization** | Limited | High | Medium | Medium |
| **JWT Validation** | Lambda Authorizer | Built-in Filter | Plugin | Lua script |
| **Rate Limiting** | Built-in | Redis-based | Built-in | Built-in |
| **Monitoring** | CloudWatch | Custom (Prometheus) | Built-in | Custom |
| **Vendor Lock-in** | AWS only | None | None | None |
| **Cold Start** | Yes (Lambda) | No | No | No |
| **Learning Curve** | Low | Medium | Medium | Low |
| **Best For** | AWS-native, serverless | Java ecosystem | API management focus | Simple routing |

---

## Recommendation

### For Your E-commerce Project:

**Recommended: AWS API Gateway (Option 1)** ✅

**Reasons:**
1. Your Product Service is already on AWS Lambda
2. Native integration with AWS services (SQS, DynamoDB)
3. Auto-scaling without managing infrastructure
4. Built-in monitoring with CloudWatch
5. Lower operational overhead
6. Pay-per-request model (cost-effective for startups)

**When to Switch to Spring Cloud Gateway:**
- When you reach 10M+ requests/month (cost becomes significant)
- When you need complex routing logic
- When you want to avoid AWS vendor lock-in
- When you need more customization

### Implementation Plan

**Phase 1: AWS API Gateway (Current)**
- Quick setup with SAM template
- Lambda Authorizer for JWT
- VPC Link for Auth/Inventory services
- Direct Lambda integration for Product Service

**Phase 2: Hybrid Approach (Future)**
- Keep AWS API Gateway for Product Service (Lambda)
- Add Spring Cloud Gateway for Auth/Inventory services
- Use API Gateway as entry point, route internally

**Phase 3: Full Migration (Scale)**
- Migrate to Spring Cloud Gateway when needed
- Deploy on EKS for better orchestration
- Implement service mesh (Istio) if needed

---

## Testing API Gateway

### Test Commands

```bash
# Register user
curl -X POST https://your-api-gateway-url/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "test@example.com",
    "password": "password123"
  }'

# Login
curl -X POST https://your-api-gateway-url/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "password123"
  }'

# Get products (with JWT)
curl -X GET https://your-api-gateway-url/api/v1/products \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"

# Get product stock
curl -X GET https://your-api-gateway-url/api/v1/products/{productId}/stock \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"

# Get inventory
curl -X GET https://your-api-gateway-url/api/v1/inventory/products/{productId}/stock \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

---

## Monitoring and Observability

### CloudWatch Dashboards (AWS API Gateway)

```json
{
  "widgets": [
    {
      "type": "metric",
      "properties": {
        "metrics": [
          ["AWS/ApiGateway", "Count", {"stat": "Sum"}],
          [".", "4XXError"],
          [".", "5XXError"],
          [".", "Latency", {"stat": "Average"}]
        ],
        "period": 300,
        "stat": "Average",
        "region": "us-east-1",
        "title": "API Gateway Metrics"
      }
    }
  ]
}
```

### Prometheus + Grafana (Spring Cloud Gateway)

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'api-gateway'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['api-gateway:8080']
```

---

## Security Best Practices

1. **Always use HTTPS** (TLS 1.2+)
2. **Implement rate limiting** per user/IP
3. **Validate JWT tokens** on every request
4. **Use API keys** for external clients
5. **Enable CORS** with specific origins
6. **Log all requests** for audit trails
7. **Implement circuit breakers** for resilience
8. **Use VPC Links** for internal services
9. **Rotate secrets** regularly (JWT secrets, API keys)
10. **Set up WAF** (Web Application Firewall) for DDoS protection

---

## Conclusion

Start with **AWS API Gateway** for simplicity and AWS integration. As your platform grows, consider migrating to **Spring Cloud Gateway** for more control and lower costs at scale.

Both options are production-ready and will serve your e-commerce platform well!

## Support
For issues and questions: nashnguyen1002@gmail.com

