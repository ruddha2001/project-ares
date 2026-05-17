package codes.ani.ares;

import codes.ani.ares.repository.AresBlockRepository;
import codes.ani.ares.repository.AresJobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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

    @MockitoBean
    private AresBlockRepository aresBlockRepository;

    @MockitoBean
    private AresJobRepository aresJobRepository;

    /**
     * Context loading smoke test.
     */
    @Test
    void contextLoads() {
    }

}
