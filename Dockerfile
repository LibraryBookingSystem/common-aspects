# syntax=docker/dockerfile:1.4
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom.xml first for better layer caching
COPY pom.xml .

# Download dependencies (cached if pom.xml doesn't change)
RUN --mount=type=cache,target=/root/.m2,id=maven-cache,sharing=shared \
    mvn dependency:go-offline -B || mvn dependency:resolve -B || true

# Copy source code
COPY src ./src

# Build the library (cached if source doesn't change)
RUN --mount=type=cache,target=/root/.m2,id=maven-cache,sharing=shared \
    mvn clean package -DskipTests -B



