# Imagen de PRODUCCIÓN: fat jar sobre un JRE ligero, sin docker socket ni DevTools.
# (Para desarrollo con hot-reload sigue existiendo Dockerfile.dev + docker-compose.dev.yml.)

# Imágenes base fijadas por digest (Dependabot abre PRs cuando cambian): un `docker build` de
# hoy y de dentro de un año parten exactamente del mismo sistema base.
# ── Build: compila el jar dentro de la imagen ──
FROM maven:3-eclipse-temurin-26@sha256:b2c1ad85954592f9928e84327c65201f308ad9b5d8ed7d5b823717c97bf23fbb AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
# checkstyle.xml: el plugin corre en la fase validate también dentro de `package`.
COPY checkstyle.xml .
COPY src ./src
# Los tests corren en CI (job `test` del workflow) antes de construir la imagen.
RUN mvn -B -DskipTests package

# ── Runtime: JRE mínimo + curl para el healthcheck ──
FROM eclipse-temurin:21-jre@sha256:d7051a45dd955e4d5d1db4d3f4269fe13d1c6dff8cc6b7ef89fc8577b96c1982
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

# Flags JVM por defecto (JAVA_OPTS los sustituye entero; JAVA_TOOL_OPTIONS del entorno se
# suma): heap relativo al límite de memoria del contenedor, salir (y que docker reinicie) ante
# un OutOfMemoryError en vez de quedarse zombi, GC serie (un solo servicio pequeño, poca RAM).
ENV JAVA_OPTS="-XX:MaxRAMPercentage=65.0 -XX:+ExitOnOutOfMemoryError -XX:+UseSerialGC -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8000
# Readiness: incluye la BD (sin ella el servicio no puede atender nada). Liveness (solo el
# proceso) queda para /actuator/health/liveness si algún orquestador la necesita.
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=5 \
    CMD curl -fsS http://localhost:8000/actuator/health/readiness || exit 1
# `exec` para que java sea PID 1 y reciba el SIGTERM de docker stop: apagado ordenado de Spring
# (server.shutdown=graceful) dentro del stop_grace_period del compose.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
