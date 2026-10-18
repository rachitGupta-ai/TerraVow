# syntax=docker/dockerfile:1
# TerraVow single Cloud Run container: Python FastAPI front door (public, $PORT) + Java
# Spring Boot engine (internal, 8081). One service, one bill. The Python app proxies the
# calls it delegates to the Java engine over 127.0.0.1:8081.

# ---- Stage 1: build the Java engine jar with Maven ----
FROM maven:3.9-eclipse-temurin-17 AS java-build
WORKDIR /build
# Cache dependencies first for faster rebuilds.
COPY pom.xml ./
COPY .mvn ./.mvn
COPY mvnw ./
RUN mvn -q -B -DskipTests dependency:go-offline || true
COPY src ./src
RUN mvn -q -B -DskipTests package \
 && cp target/terravow-*.jar /build/terravow-engine.jar

# ---- Stage 2: runtime with Python 3.12 + a JRE, running both processes ----
FROM python:3.12-slim-bookworm AS runtime
ENV PYTHONUNBUFFERED=1 PIP_NO_CACHE_DIR=1 \
    JAVA_PORT=8081 \
    JAVA_ENGINE_URL=http://127.0.0.1:8081
WORKDIR /srv

# A headless JRE so the Java engine can run alongside Python in one container.
RUN apt-get update \
 && apt-get install -y --no-install-recommends openjdk-17-jre-headless curl \
 && rm -rf /var/lib/apt/lists/*

# Python front door (the prototype/ app) + its deps.
COPY prototype/requirements.txt ./requirements.txt
RUN pip install -r requirements.txt
COPY prototype/app ./app
COPY prototype/static ./static
COPY prototype/data ./data

# The built Java engine jar and the process launcher.
COPY --from=java-build /build/terravow-engine.jar ./terravow-engine.jar
COPY start.sh ./start.sh
RUN chmod +x ./start.sh

# Cloud Run injects $PORT; the Python front door binds it, Java stays internal on 8081.
CMD ["./start.sh"]
