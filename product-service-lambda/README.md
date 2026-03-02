# Product Service - AWS Lambda

## Overview
The Product Service is a serverless microservice built with Java and AWS Lambda, designed to handle all product-related operations in the e-commerce platform. This service uses DynamoDB as its database for high scalability and performance.

## Architecture

### Technology Stack
- **Compute**: AWS Lambda (Java 17)
- **Database**: Amazon DynamoDB
- **API Gateway**: AWS API Gateway (REST API)
- **Authentication**: JWT Token validation (integrated with Auth Service)
- **Message Queue**: Amazon SQS (for Inventory Service integration)
- **Build Tool**: Maven
- **Framework**: AWS Lambda Java SDK

### Service Architecture
```
                    ┌─────────────────┐
                    │   Auth Service  │
                    │   (JWT Tokens)  │
                    └────────┬────────┘
                             │ Validates
                             ▼
┌─────────────┐      ┌─────────────────┐
│  API Client │─────▶│  API Gateway    │
└─────────────┘      └────────┬────────┘
                              │
                              ▼
                     ┌─────────────────┐
                     │ Product Service │
                     │  (Lambda - Java)│
                     └────┬──────┬─────┘
                          │      │
                  ┌───────┘      └────────┐
                  ▼                       ▼
         ┌─────────────────┐     ┌─────────────────┐
         │   DynamoDB      │     │   Amazon SQS    │
         │ (Product Table) │     │ (Event Queue)   │
         └─────────────────┘     └────────┬────────┘
                                          │
                                          ▼
                                 ┌─────────────────┐
                                 │ Inventory Service│
                                 │  (Stock Mgmt)   │
                                 └─────────────────┘
```

### Microservices Integration
The Product Service is part of a larger microservices ecosystem:
- **Auth Service**: Provides JWT authentication for all API requests
- **Inventory Service**: Manages stock levels and warehouse operations
- **Order Service**: Handles order processing and requires product information
- **Product Service**: (This service) Manages product catalog and metadata

## DynamoDB Table Design

### Product Table
**Table Name**: `products`

**Primary Key**:
- Partition Key: `productId` (String) - UUID
- Sort Key: None

**Attributes**:
```json
{
  "productId": "string (UUID)",
  "name": "string",
  "description": "string",
  "price": "number",
  "category": "string",
  "images": ["string"],
  "createdAt": "string (ISO 8601)",
  "updatedAt": "string (ISO 8601)",
  "isActive": "boolean",
  "sku": "string",
  "weight": "number",
  "dimensions": {
    "length": "number",
    "width": "number",
    "height": "number"
  },
  "brand": "string",
  "tags": ["string"]
}
```

**Note**: Stock/inventory data is NOT stored in this table. Stock levels are managed by the **Inventory Service** separately for better separation of concerns and scalability.

**Global Secondary Indexes (GSI)**:
1. **CategoryIndex**: 
   - Partition Key: `category`
   - Sort Key: `createdAt`
   - Use Case: Query products by category

2. **SKUIndex**:
   - Partition Key: `sku`
   - Use Case: Search product by SKU

## Lambda Functions

### 1. CreateProduct
**Handler**: `com.ecommerce.product.handler.CreateProductHandler`
- **Method**: POST
- **Endpoint**: `/products`
- **Description**: Creates a new product
- **Request Body**:
```json
{
  "name": "Product Name",
  "description": "Product Description",
  "price": 99.99,
  "category": "Electronics",
  "sku": "PROD-001",
  "images": ["image1.jpg", "image2.jpg"],
  "weight": 1.5,
  "dimensions": {
    "length": 10,
    "width": 5,
    "height": 3
  },
  "brand": "BrandName",
  "tags": ["featured", "new-arrival"]
}
```
- **Response**:
```json
{
  "success": true,
  "message": "Product created successfully",
  "data": {
    "productId": "uuid",
    "name": "Product Name",
    ...
  }
}
```

**Note**: After creating a product, the system sends an event to SQS for the Inventory Service to initialize stock levels.

### 2. GetProduct
**Handler**: `com.ecommerce.product.handler.GetProductHandler`
- **Method**: GET
- **Endpoint**: `/products/{productId}`
- **Description**: Retrieves a product by ID
- **Response**:
```json
{
  "success": true,
  "data": {
    "productId": "uuid",
    "name": "Product Name",
    ...
  }
}
```

### 3. ListProducts
**Handler**: `com.ecommerce.product.handler.ListProductsHandler`
- **Method**: GET
- **Endpoint**: `/products`
- **Query Parameters**:
  - `category` (optional): Filter by category
  - `limit` (optional, default: 20): Number of items per page
  - `lastKey` (optional): Pagination token
- **Description**: Lists all products with pagination
- **Response**:
```json
{
  "success": true,
  "data": {
    "products": [...],
    "lastEvaluatedKey": "pagination-token",
    "count": 20
  }
}
```

### 4. UpdateProduct
**Handler**: `com.ecommerce.product.handler.UpdateProductHandler`
- **Method**: PUT
- **Endpoint**: `/products/{productId}`
- **Description**: Updates an existing product
- **Request Body**: Same as CreateProduct (partial updates supported)

### 5. DeleteProduct
**Handler**: `com.ecommerce.product.handler.DeleteProductHandler`
- **Method**: DELETE
- **Endpoint**: `/products/{productId}`
- **Description**: Soft deletes a product (sets isActive to false)
- **Response**:
```json
{
  "success": true,
  "message": "Product deleted successfully"
}
```

**Note**: When a product is deleted, an event is sent to SQS to notify the Inventory Service to archive the stock records.

### 6. GetProductStock (Inventory Integration)
**Handler**: `com.ecommerce.product.handler.GetProductStockHandler`
- **Method**: GET
- **Endpoint**: `/products/{productId}/stock`
- **Description**: Retrieves stock information from Inventory Service
- **Response**:
```json
{
  "success": true,
  "data": {
    "productId": "uuid",
    "sku": "PROD-001",
    "availableStock": 50,
    "reservedStock": 10,
    "totalStock": 60,
    "warehouseLocations": [
      {
        "warehouseId": "WH-001",
        "location": "New York",
        "quantity": 40
      },
      {
        "warehouseId": "WH-002",
        "location": "Los Angeles",
        "quantity": 20
      }
    ]
  }
}
```

**Implementation Note**: This endpoint makes a synchronous call to the Inventory Service API to fetch real-time stock data.

## Project Structure
```
product-service-lambda/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── ecommerce/
│   │   │           └── product/
│   │   │               ├── handler/
│   │   │               │   ├── CreateProductHandler.java
│   │   │               │   ├── GetProductHandler.java
│   │   │               │   ├── ListProductsHandler.java
│   │   │               │   ├── UpdateProductHandler.java
│   │   │               │   ├── DeleteProductHandler.java
│   │   │               │   └── GetProductStockHandler.java
│   │   │               ├── model/
│   │   │               │   ├── Product.java
│   │   │               │   ├── Dimensions.java
│   │   │               │   └── StockInfo.java
│   │   │               ├── service/
│   │   │               │   ├── ProductService.java
│   │   │               │   └── InventoryServiceClient.java
│   │   │               ├── repository/
│   │   │               │   └── ProductRepository.java
│   │   │               ├── dto/
│   │   │               │   ├── CreateProductRequest.java
│   │   │               │   ├── UpdateProductRequest.java
│   │   │               │   └── ProductCreatedEvent.java
│   │   │               ├── exception/
│   │   │               │   ├── ProductNotFoundException.java
│   │   │               │   └── InvalidRequestException.java
│   │   │               └── util/
│   │   │                   ├── ResponseBuilder.java
│   │   │                   ├── ValidationUtil.java
│   │   │                   └── SQSPublisher.java
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       └── java/
├── pom.xml
├── template.yaml (SAM template)
└── README.md
```

## Setup and Deployment

### Prerequisites
- AWS Account
- AWS CLI configured
- AWS SAM CLI installed
- Java 17
- Maven 3.8+

### Environment Variables
Set the following environment variables in Lambda:

```bash
# DynamoDB
DYNAMODB_TABLE_NAME=products
DYNAMODB_REGION=us-east-1

# JWT Configuration (for authentication)
JWT_SECRET=your-jwt-secret-key
JWT_ISSUER=auth-service

# SQS Configuration (for Inventory Service events)
SQS_QUEUE_URL=https://sqs.us-east-1.amazonaws.com/your-account-id/product-events-queue
SQS_REGION=us-east-1

# Inventory Service Integration
INVENTORY_SERVICE_URL=https://your-inventory-service-api-gateway-url
INVENTORY_SERVICE_API_KEY=your-inventory-service-api-key

# CORS
ALLOWED_ORIGINS=http://localhost:3000,https://yourdomain.com
```

### Local Development

#### 1. Install Dependencies
```bash
mvn clean install
```

#### 2. Run DynamoDB Local
```bash
docker run -p 8000:8000 amazon/dynamodb-local
```

#### 3. Create Local Table
```bash
aws dynamodb create-table \
  --table-name products \
  --attribute-definitions \
    AttributeName=productId,AttributeType=S \
    AttributeName=category,AttributeType=S \
    AttributeName=createdAt,AttributeType=S \
    AttributeName=sku,AttributeType=S \
  --key-schema \
    AttributeName=productId,KeyType=HASH \
  --global-secondary-indexes \
    "[{\"IndexName\":\"CategoryIndex\",\"KeySchema\":[{\"AttributeName\":\"category\",\"KeyType\":\"HASH\"},{\"AttributeName\":\"createdAt\",\"KeyType\":\"RANGE\"}],\"Projection\":{\"ProjectionType\":\"ALL\"},\"ProvisionedThroughput\":{\"ReadCapacityUnits\":5,\"WriteCapacityUnits\":5}}]" \
  --provisioned-throughput \
    ReadCapacityUnits=5,WriteCapacityUnits=5 \
  --endpoint-url http://localhost:8000
```

#### 4. Test Locally with SAM
```bash
sam local start-api
```

### Deployment

#### Option 1: Using AWS SAM

**Build:**
```bash
sam build
```

**Deploy to Development:**
```bash
sam deploy \
  --template-file template.yaml \
  --stack-name product-service-dev \
  --capabilities CAPABILITY_IAM \
  --parameter-overrides Environment=dev
```

**Deploy to Production:**
```bash
sam deploy \
  --template-file template.yaml \
  --stack-name product-service-prod \
  --capabilities CAPABILITY_IAM \
  --parameter-overrides Environment=prod
```

#### Option 2: Using AWS CLI

**Package:**
```bash
mvn clean package
```

**Upload to S3:**
```bash
aws s3 cp target/product-service.jar s3://your-lambda-bucket/
```

**Update Lambda:**
```bash
aws lambda update-function-code \
  --function-name CreateProductFunction \
  --s3-bucket your-lambda-bucket \
  --s3-key product-service.jar
```

## SAM Template Example

```yaml
AWSTemplateFormatVersion: '2010-09-09'
Transform: AWS::Serverless-2016-10-31
Description: Product Service - Serverless E-commerce

Parameters:
  Environment:
    Type: String
    Default: dev
    AllowedValues:
      - dev
      - staging
      - prod

Globals:
  Function:
    Runtime: java17
    Timeout: 30
    MemorySize: 512
    Environment:
      Variables:
        DYNAMODB_TABLE_NAME: !Ref ProductTable
        SQS_QUEUE_URL: !Ref ProductEventsQueue
        ENVIRONMENT: !Ref Environment

Resources:
  # SQS Queue for Inventory Service Events
  ProductEventsQueue:
    Type: AWS::SQS::Queue
    Properties:
      QueueName: !Sub product-events-${Environment}
      VisibilityTimeout: 300
      MessageRetentionPeriod: 1209600  # 14 days
      RedrivePolicy:
        deadLetterTargetArn: !GetAtt ProductEventsDLQ.Arn
        maxReceiveCount: 3

  ProductEventsDLQ:
    Type: AWS::SQS::Queue
    Properties:
      QueueName: !Sub product-events-dlq-${Environment}
      MessageRetentionPeriod: 1209600  # 14 days

  # DynamoDB Table
  ProductTable:
    Type: AWS::DynamoDB::Table
    Properties:
      TableName: !Sub products-${Environment}
      BillingMode: PAY_PER_REQUEST
      AttributeDefinitions:
        - AttributeName: productId
          AttributeType: S
        - AttributeName: category
          AttributeType: S
        - AttributeName: createdAt
          AttributeType: S
        - AttributeName: sku
          AttributeType: S
      KeySchema:
        - AttributeName: productId
          KeyType: HASH
      GlobalSecondaryIndexes:
        - IndexName: CategoryIndex
          KeySchema:
            - AttributeName: category
              KeyType: HASH
            - AttributeName: createdAt
              KeyType: RANGE
          Projection:
            ProjectionType: ALL
        - IndexName: SKUIndex
          KeySchema:
            - AttributeName: sku
              KeyType: HASH
          Projection:
            ProjectionType: ALL

  # API Gateway
  ProductApi:
    Type: AWS::Serverless::Api
    Properties:
      StageName: !Ref Environment
      Cors:
        AllowMethods: "'GET,POST,PUT,DELETE,PATCH,OPTIONS'"
        AllowHeaders: "'Content-Type,Authorization'"
        AllowOrigin: "'*'"

  # Lambda Functions
  CreateProductFunction:
    Type: AWS::Serverless::Function
    Properties:
      CodeUri: target/product-service.jar
      Handler: com.ecommerce.product.handler.CreateProductHandler::handleRequest
      Events:
        CreateProduct:
          Type: Api
          Properties:
            RestApiId: !Ref ProductApi
            Path: /products
            Method: POST
      Policies:
        - DynamoDBCrudPolicy:
            TableName: !Ref ProductTable
        - SQSSendMessagePolicy:
            QueueName: !GetAtt ProductEventsQueue.QueueName

  GetProductFunction:
    Type: AWS::Serverless::Function
    Properties:
      CodeUri: target/product-service.jar
      Handler: com.ecommerce.product.handler.GetProductHandler::handleRequest
      Events:
        GetProduct:
          Type: Api
          Properties:
            RestApiId: !Ref ProductApi
            Path: /products/{productId}
            Method: GET
      Policies:
        - DynamoDBReadPolicy:
            TableName: !Ref ProductTable

  ListProductsFunction:
    Type: AWS::Serverless::Function
    Properties:
      CodeUri: target/product-service.jar
      Handler: com.ecommerce.product.handler.ListProductsHandler::handleRequest
      Events:
        ListProducts:
          Type: Api
          Properties:
            RestApiId: !Ref ProductApi
            Path: /products
            Method: GET
      Policies:
        - DynamoDBReadPolicy:
            TableName: !Ref ProductTable

  UpdateProductFunction:
    Type: AWS::Serverless::Function
    Properties:
      CodeUri: target/product-service.jar
      Handler: com.ecommerce.product.handler.UpdateProductHandler::handleRequest
      Events:
        UpdateProduct:
          Type: Api
          Properties:
            RestApiId: !Ref ProductApi
            Path: /products/{productId}
            Method: PUT
      Policies:
        - DynamoDBCrudPolicy:
            TableName: !Ref ProductTable
        - SQSSendMessagePolicy:
            QueueName: !GetAtt ProductEventsQueue.QueueName

  DeleteProductFunction:
    Type: AWS::Serverless::Function
    Properties:
      CodeUri: target/product-service.jar
      Handler: com.ecommerce.product.handler.DeleteProductHandler::handleRequest
      Events:
        DeleteProduct:
          Type: Api
          Properties:
            RestApiId: !Ref ProductApi
            Path: /products/{productId}
            Method: DELETE
      Policies:
        - DynamoDBCrudPolicy:
            TableName: !Ref ProductTable
        - SQSSendMessagePolicy:
            QueueName: !GetAtt ProductEventsQueue.QueueName

  GetProductStockFunction:
    Type: AWS::Serverless::Function
    Properties:
      CodeUri: target/product-service.jar
      Handler: com.ecommerce.product.handler.GetProductStockHandler::handleRequest
      Environment:
        Variables:
          INVENTORY_SERVICE_URL: !Sub '{{resolve:ssm:/ecommerce/${Environment}/inventory-service-url}}'
      Events:
        GetStock:
          Type: Api
          Properties:
            RestApiId: !Ref ProductApi
            Path: /products/{productId}/stock
            Method: GET
      Policies:
        - DynamoDBReadPolicy:
            TableName: !Ref ProductTable

Outputs:
  ProductApiUrl:
    Description: "API Gateway endpoint URL"
    Value: !Sub "https://${ProductApi}.execute-api.${AWS::Region}.amazonaws.com/${Environment}"
  
  ProductTableName:
    Description: "DynamoDB table name"
    Value: !Ref ProductTable
```

## Maven Dependencies (pom.xml)

```xml
<dependencies>
    <!-- AWS Lambda Core -->
    <dependency>
        <groupId>com.amazonaws</groupId>
        <artifactId>aws-lambda-java-core</artifactId>
        <version>1.2.3</version>
    </dependency>
    
    <!-- AWS Lambda Events -->
    <dependency>
        <groupId>com.amazonaws</groupId>
        <artifactId>aws-lambda-java-events</artifactId>
        <version>3.11.3</version>
    </dependency>

    <!-- AWS SDK DynamoDB -->
    <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>dynamodb</artifactId>
        <version>2.21.0</version>
    </dependency>
    
    <!-- AWS SDK DynamoDB Enhanced -->
    <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>dynamodb-enhanced</artifactId>
        <version>2.21.0</version>
    </dependency>

    <!-- AWS SDK SQS -->
    <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>sqs</artifactId>
        <version>2.21.0</version>
    </dependency>

    <!-- AWS SDK URL Connection Client -->
    <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>url-connection-client</artifactId>
        <version>2.21.0</version>
    </dependency>

    <!-- Gson for JSON processing -->
    <dependency>
        <groupId>com.google.code.gson</groupId>
        <artifactId>gson</artifactId>
        <version>2.10.1</version>
    </dependency>

    <!-- JWT -->
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>0.12.3</version>
    </dependency>

    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <version>1.18.30</version>
        <scope>provided</scope>
    </dependency>

    <!-- Testing -->
    <dependency>
        <groupId>junit</groupId>
        <artifactId>junit</artifactId>
        <version>4.13.2</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## API Examples

### Create Product
```bash
curl -X POST https://your-api-gateway-url/dev/products \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "name": "iPhone 15 Pro",
    "description": "Latest iPhone with A17 Pro chip",
    "price": 999.99,
    "category": "Electronics",
    "sku": "IPHONE-15-PRO-256GB",
    "images": ["iphone15pro-1.jpg", "iphone15pro-2.jpg"],
    "weight": 0.187,
    "dimensions": {
      "length": 14.67,
      "width": 7.08,
      "height": 0.83
    },
    "brand": "Apple",
    "tags": ["smartphone", "5g", "flagship"]
  }'
```

### Get Product
```bash
curl -X GET https://your-api-gateway-url/dev/products/{productId} \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### List Products by Category
```bash
curl -X GET "https://your-api-gateway-url/dev/products?category=Electronics&limit=20" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### Get Product Stock (from Inventory Service)
```bash
curl -X GET https://your-api-gateway-url/dev/products/{productId}/stock \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

## Cost Optimization

### Lambda
- Use appropriate memory allocation (512MB is usually sufficient)
- Set proper timeout (30 seconds recommended)
- Use provisioned concurrency only for critical endpoints
- Enable Lambda function logs retention (7-14 days)

### DynamoDB
- Use **PAY_PER_REQUEST** billing mode for unpredictable traffic
- Use **PROVISIONED** billing mode with auto-scaling for predictable traffic
- Enable Point-in-Time Recovery (PITR) for production
- Use DynamoDB Streams for event-driven architectures

### API Gateway
- Enable caching for GET requests
- Use API Gateway throttling to prevent abuse
- Implement request/response compression

## Monitoring and Logging

### CloudWatch Metrics
- Lambda invocations
- Lambda duration
- Lambda errors
- DynamoDB read/write capacity
- API Gateway 4XX/5XX errors

### CloudWatch Logs
All Lambda functions automatically log to CloudWatch Logs:
```
/aws/lambda/CreateProductFunction
/aws/lambda/GetProductFunction
/aws/lambda/ListProductsFunction
...
```

### X-Ray Tracing
Enable X-Ray for distributed tracing:
```yaml
Tracing: Active
```

### Alarms
Set up CloudWatch Alarms for:
- Lambda error rate > 5%
- Lambda duration > 10 seconds
- DynamoDB throttled requests
- API Gateway 5XX errors

## Security Best Practices

1. **IAM Roles**: Use least privilege principle for Lambda execution roles
2. **API Gateway**: Implement API key and usage plans
3. **JWT Validation**: Validate JWT tokens in Lambda authorizer
4. **Encryption**: Enable encryption at rest for DynamoDB
5. **VPC**: Consider placing Lambda in VPC for enhanced security
6. **Secrets**: Use AWS Secrets Manager for sensitive data
7. **CORS**: Configure proper CORS policies
8. **Input Validation**: Validate all inputs in Lambda handlers

## Integration with Other Services

### Auth Service Integration
The Product Service validates JWT tokens from the Auth Service to authenticate API requests.

```java
// Validate JWT token from Auth Service
public boolean validateToken(String token) {
    try {
        Jws<Claims> claims = Jwts.parserBuilder()
            .setSigningKey(jwtSecret)
            .build()
            .parseClaimsJws(token);
        return true;
    } catch (JwtException e) {
        return false;
    }
}
```

### Inventory Service Integration

The Product Service communicates with the Inventory Service in two ways:

#### 1. Asynchronous Communication (via SQS)
When product lifecycle events occur, messages are sent to SQS for the Inventory Service to process:

**Event Types:**
- `PRODUCT_CREATED`: Sent when a new product is created
- `PRODUCT_UPDATED`: Sent when product details are updated
- `PRODUCT_DELETED`: Sent when a product is soft-deleted

**Example Event Message:**
```json
{
  "eventType": "PRODUCT_CREATED",
  "productId": "uuid",
  "sku": "PROD-001",
  "name": "Product Name",
  "timestamp": "2026-01-21T10:00:00Z",
  "metadata": {
    "initiatedBy": "user-id",
    "source": "product-service"
  }
}
```

**Java Implementation:**
```java
// SQSPublisher.java
public void publishProductEvent(String eventType, Product product) {
    SqsClient sqsClient = SqsClient.builder()
        .region(Region.of(sqsRegion))
        .build();
    
    String messageBody = new Gson().toJson(Map.of(
        "eventType", eventType,
        "productId", product.getProductId(),
        "sku", product.getSku(),
        "name", product.getName(),
        "timestamp", Instant.now().toString()
    ));
    
    SendMessageRequest request = SendMessageRequest.builder()
        .queueUrl(sqsQueueUrl)
        .messageBody(messageBody)
        .build();
    
    sqsClient.sendMessage(request);
}
```

#### 2. Synchronous Communication (REST API)
To retrieve real-time stock information, the Product Service makes HTTP calls to the Inventory Service API:

**Endpoint:** `GET /inventory/products/{productId}/stock`

**Java Implementation:**
```java
// InventoryServiceClient.java
public StockInfo getProductStock(String productId) {
    HttpClient client = HttpClient.newHttpClient();
    
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(inventoryServiceUrl + "/products/" + productId + "/stock"))
        .header("Authorization", "Bearer " + inventoryApiKey)
        .GET()
        .build();
    
    HttpResponse<String> response = client.send(request, 
        HttpResponse.BodyHandlers.ofString());
    
    return new Gson().fromJson(response.body(), StockInfo.class);
}
```

### Event Flow Diagram
```
Product Service                    SQS Queue              Inventory Service
     │                                │                          │
     │  1. Create Product             │                          │
     │─────────────────────────────>  │                          │
     │                                │                          │
     │  2. Send PRODUCT_CREATED       │                          │
     │     event to SQS               │                          │
     │─────────────────────────────>  │                          │
     │                                │  3. Poll SQS             │
     │                                │ <────────────────────────│
     │                                │                          │
     │                                │  4. Process event &      │
     │                                │     initialize stock     │
     │                                │ ─────────────────────────>
     │                                │                          │
     │  5. Request stock info         │                          │
     │────────────────────────────────────────────────────────>  │
     │                                │                          │
     │  6. Return stock data          │                          │
     │ <──────────────────────────────────────────────────────── │
```

### Benefits of This Architecture
1. **Separation of Concerns**: Product catalog and inventory management are decoupled
2. **Scalability**: Each service can scale independently
3. **Resilience**: If Inventory Service is down, products can still be managed
4. **Event-Driven**: Asynchronous processing ensures high availability
5. **Real-time Data**: Synchronous calls provide up-to-date stock information

## Troubleshooting

### Common Issues

1. **Cold Start Latency**
   - Solution: Use provisioned concurrency or optimize package size

2. **DynamoDB Throttling**
   - Solution: Increase capacity or use exponential backoff

3. **Lambda Timeout**
   - Solution: Optimize code or increase timeout limit

4. **Memory Issues**
   - Solution: Increase Lambda memory allocation

## CI/CD Pipeline

```yaml
# .github/workflows/deploy.yml
name: Deploy Product Service

on:
  push:
    branches: [main, develop]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      
      - name: Set up Java
        uses: actions/setup-java@v2
        with:
          java-version: '17'
      
      - name: Build with Maven
        run: mvn clean package
      
      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v1
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: us-east-1
      
      - name: Deploy to AWS
        run: sam deploy --no-confirm-changeset
```

## References

- [AWS Lambda Documentation](https://docs.aws.amazon.com/lambda/)
- [DynamoDB Documentation](https://docs.aws.amazon.com/dynamodb/)
- [AWS SAM Documentation](https://docs.aws.amazon.com/serverless-application-model/)
- [API Gateway Documentation](https://docs.aws.amazon.com/apigateway/)

## License
MIT

## Support
For issues and questions, please contact: nashnguyen1002@gmail.com

