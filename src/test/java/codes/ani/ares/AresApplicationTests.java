package codes.ani.ares;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies that the Spring Boot application context loads successfully with the
 * {@code test} profile.
 *
 * <p>Fails early if any bean definition, configuration property binding, or
 * autoconfiguration causes a context startup failure.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class AresApplicationTests {

    /**
     * Context loading smoke test.
     */
    @Test
    void contextLoads() {
    }

}
