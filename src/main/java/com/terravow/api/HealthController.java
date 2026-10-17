package com.terravow.api;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness/identity endpoint for the internal TerraVow Java engine.
 *
 * <p>In the single Cloud Run container the Python FastAPI app is the public front door on
 * {@code $PORT} and this Spring Boot app runs alongside it on an internal port
 * (default {@code 8081}). The Python startup waits on this endpoint before accepting traffic, and
 * the Python API surfaces it under {@code /api/java/health} so an operator can confirm the Java
 * engine is reachable from inside the one service.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    /**
     * Reports that the Java engine is up, naming the service so a proxied response is never mistaken
     * for the Python front door's own health.
     *
     * @return a small status document
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "service", "terravow-java-engine",
                "component", "decision-gate");
    }
}
