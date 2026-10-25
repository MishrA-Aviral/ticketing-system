# Stage 1: Build the application using Java 21 and Maven
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependency downloads by copying pom.xml first
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN mvn dependency:go-offline -B --no-transfer-progress || true

# Copy source code and build the executable JAR without running tests
COPY src ./src
RUN mvn clean package -DskipTests --no-transfer-progress

# Stage 2: Minimal runtime environment with Java 21 JRE
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Run as a dedicated non-root user
RUN addgroup --system spring && adduser --system --ingroup spring spring
USER spring:spring

# Copy the packaged Spring Boot executable JAR from build stage
COPY --from=build --chown=spring:spring /app/target/ticketing-system-0.0.1-SNAPSHOT.jar app.jar

# Render assigns a dynamic port via the PORT environment variable; default to 8080
ENV PORT=8080
EXPOSE 8080

# Launch application respecting the PORT environment variable
ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT} -jar app.jar"]
