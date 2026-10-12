# Single-service production image for Render (and local Docker).
# Builds the React SPA, packages it into the Spring Boot JAR, and runs one process on $PORT.
#
#   docker build -t docket .
#   docker run --rm -p 8080:8080 -e PORT=8080 -e JWT_SECRET=... -e DB_HOST=... docket

# --- Frontend (Vite) ---
FROM node:22-alpine AS frontend
WORKDIR /fe
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
ENV VITE_API_BASE_URL=/api
RUN npm run build

# --- Backend (Maven) ---
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build
COPY backend/pom.xml .
RUN mvn -B -q dependency:go-offline
COPY backend/src ./src
# Embed the SPA so Spring serves it from classpath:/static/
COPY --from=frontend /fe/dist/ ./src/main/resources/static/
RUN mvn -B -q package -DskipTests

# --- Runtime ---
FROM eclipse-temurin:21-jre
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 --create-home docket \
 && mkdir -p /data/uploads && chown -R docket:docket /data
USER docket
WORKDIR /app
COPY --from=backend /build/target/docket-backend-*.jar /app/app.jar
ENV SPRING_PROFILES_ACTIVE=prod \
    STORAGE_DIR=/data/uploads \
    PORT=8080 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Dfile.encoding=UTF-8 -Duser.timezone=UTC"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
  CMD curl -fsS "http://127.0.0.1:${PORT:-8080}/api/health" || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
