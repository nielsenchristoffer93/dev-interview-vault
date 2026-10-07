# Multi-stage Dockerfile för att bygga och köra minnessimuleringen
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache tar
WORKDIR /app
COPY --from=builder /app/target/classes /app/classes

# Skapa katalog för heap dumps
RUN mkdir -p /dumps

# Standardstart (kan overrideas i k8s-manifest)
ENTRYPOINT ["java", "-cp", "/app/classes", "com.vault.memory.MemoryLeakSimulation"]
