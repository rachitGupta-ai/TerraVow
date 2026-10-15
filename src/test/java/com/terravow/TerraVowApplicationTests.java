package com.terravow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: the Spring application context loads. Confirms the Maven module, Spring Boot
 * wiring, and package layout are coherent (Req 12.1).
 */
@SpringBootTest
class TerraVowApplicationTests {

    @Test
    void contextLoads() {
        // Intentionally empty: a failure to load the context fails this test.
    }
}
