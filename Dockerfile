# --- Build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies separately from source so `docker build` reuses them
# when only application code changes.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# --- Runtime stage ---
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/bpmn-backend-1.0.0-SNAPSHOT-all.jar app.jar

# Render injects PORT at runtime; JVM heap is capped to fit the free instance's 512MB RAM.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
