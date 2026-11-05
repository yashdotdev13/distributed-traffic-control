# ---------- Build stage ----------
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

# Copy Maven wrapper and build configuration first for better layer caching
COPY mvnw pom.xml ./
COPY .mvn .mvn

RUN chmod +x mvnw

# Download dependencies before copying application source
RUN ./mvnw -B dependency:go-offline

COPY src ./src

RUN ./mvnw -B -DskipTests package


# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre AS runtime

WORKDIR /app

# curl is required by the existing Docker Compose healthcheck
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 appuser \
    && useradd --system --uid 10001 --gid appuser \
        --no-create-home appuser

# Copy only the packaged application
COPY --from=build --chown=10001:10001 /app/target/*.jar /app/app.jar

USER 10001:10001

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
