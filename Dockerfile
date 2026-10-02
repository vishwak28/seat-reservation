# ---------- Stage 1: building the jar ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Copy only pom.xml first so the dependency download is cached
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B package -DskipTests

# ---------- Stage 2: running the jar ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN useradd --system --no-create-home appuser
COPY --from=build /build/target/app.jar app.jar
USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]