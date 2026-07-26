# Imagen de PRODUCCIÓN: fat jar sobre un JRE ligero, sin docker socket ni DevTools.
# (Para desarrollo con hot-reload sigue existiendo Dockerfile.dev + docker-compose.dev.yml.)

# ── Build: compila el jar dentro de la imagen ──
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

# ── Runtime: JRE mínimo + curl para el healthcheck ──
FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 1001 spring
USER spring
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8000
ENTRYPOINT ["java", "-jar", "app.jar"]
