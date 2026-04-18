package codes.ani.ares.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SanitizationServiceTest {
    private final SanitizationService service = new SanitizationService();

    @Test
    void shouldMaskSensitiveData() {
        String raw = "My email is test@ani.codes and my key is api_key: 'abc123secret456789'. Call me at 123-456-7890.";
        String sanitized = service.sanitize(raw);

        assertTrue(sanitized.contains("[MASKED_EMAIL]"));
        assertTrue(sanitized.contains("[MASKED_API_KEY]"));
        assertTrue(sanitized.contains("[MASKED_PHONE]"));
        assertFalse(sanitized.contains("ani@codes.ani"));
    }
}
