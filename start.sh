#!/usr/bin/env bash
# Launch both processes in the single Cloud Run container:
#   - Java Spring Boot engine on the internal port (JAVA_PORT, default 8081), localhost only
#   - Python FastAPI front door on the public $PORT (default 8080)
# The Python app proxies delegated calls to the Java engine; we wait for the engine's health
# check before starting the front door so the first request never races a cold JVM.
set -euo pipefail

JAVA_PORT="${JAVA_PORT:-8081}"
APP_PORT="${PORT:-8080}"

echo "[start] launching Java engine on internal port ${JAVA_PORT}"
# Bind the JVM to loopback so it is never exposed outside the container.
java -jar /srv/terravow-engine.jar \
  --server.port="${JAVA_PORT}" \
  --server.address=127.0.0.1 &
JAVA_PID=$!

# If the JVM dies, take the container down so Cloud Run restarts it rather than serving a
# half-up service.
trap 'kill "${JAVA_PID}" 2>/dev/null || true' EXIT

echo "[start] waiting for Java engine health"
for i in $(seq 1 60); do
  if ! kill -0 "${JAVA_PID}" 2>/dev/null; then
    echo "[start] FATAL: Java engine exited during startup" >&2
    exit 1
  fi
  if curl -sf "http://127.0.0.1:${JAVA_PORT}/api/health" >/dev/null 2>&1; then
    echo "[start] Java engine healthy after ${i}s"
    break
  fi
  sleep 1
done

echo "[start] launching Python front door"
# exec so uvicorn receives Cloud Run signals as the foreground process.
exec uvicorn app.main:app --host 0.0.0.0 --port "${APP_PORT}"
