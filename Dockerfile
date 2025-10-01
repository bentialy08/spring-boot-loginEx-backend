# Build stage
FROM maven:3.9.5-eclipse-temurin-21-alpine AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

# Run stage
FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app

COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar spring-login.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "spring-login.jar"]