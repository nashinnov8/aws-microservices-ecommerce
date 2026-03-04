FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

COPY . .

RUN mvn clean package -DskipTests -pl order-service -am

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=builder /app/order-service/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
