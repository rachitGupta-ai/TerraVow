package com.terravow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * TerraVow application entry point.
 *
 * <p>TerraVow is a provenance-first environmental determination platform that runs as a set of
 * Cloud Run services, reasoning over Vertex AI Agent Engine. This bootstrap class starts the
 * Spring context that hosts the capability services, the Decision_Gate, and the Evidence/Audit
 * spine.
 *
 * <p>Req 12.1: services are hosted on Cloud Run with Vertex AI reasoning delivered through Agent
 * Engine.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class TerraVowApplication {

    public static void main(String[] args) {
        SpringApplication.run(TerraVowApplication.class, args);
    }
}
