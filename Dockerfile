# Imagen de PRODUCCIÓN: fat jar sobre un JRE ligero, sin docker socket ni DevTools.
# (Para desarrollo con hot-reload sigue existiendo Dockerfile.dev + docker-compose.dev.yml.)

# ── Build: compila el jar dentro de la imagen ──
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
# Los tests corren en CI (job `test` del workflow) antes de construir la imagen.
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

# Perfil de producción fijado en la imagen: sin defaults de desarrollo, secretos obligatorios
# y ProductionConfigGuard al arrancar. El env del despliegue puede repetirlo, nunca cambiarlo a dev.
ENV SPRING_PROFILES_ACTIVE=prod
# Commit desplegado, para etiquetar los eventos de Sentry (lo pasa el workflow con --build-arg).
ARG GIT_SHA=unknown
ENV SENTRY_RELEASE=${GIT_SHA}

EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=5 \
    CMD curl -fsS http://localhost:8000/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
