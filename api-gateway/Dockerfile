FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

COPY . .

RUN mvn clean package -DskipTests -pl api-gateway -am

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=builder /app/api-gateway/target/*.jar app.jar
EXPOSE 7999
ENTRYPOINT ["java", "-jar", "app.jar"]
