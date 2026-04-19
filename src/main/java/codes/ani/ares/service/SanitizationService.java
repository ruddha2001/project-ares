package codes.ani.ares.service;

import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Sanitizes input text by masking common sensitive data patterns.
 */
@Service
public class SanitizationService {

    /**
     * Compiled regex patterns keyed by sensitive data type label used in mask placeholders.
     */
    private static final Map<String, Pattern> PATTERNS = Map.of(
            "EMAIL", Pattern.compile("[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+"),
            "CREDIT_CARD", Pattern.compile("\\b(?:\\d[ -]*?){13,16}\\b"),
            "API_KEY", Pattern.compile("(?i)(api[_-]?key|secret|password|auth|token)[\"']?\\s*[:=]\\s*[\"']?([a-zA-Z0-9_\\-]{16,})[\"']?"),
            "PHONE", Pattern.compile("\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b")
    );

    /**
     * Replaces detected sensitive values with typed mask tokens such as {@code [MASKED_EMAIL]}.
     *
     * @param input raw text to sanitize
     * @return sanitized text, or the original value when input is {@code null} or blank
     */
    public String sanitize(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }

        String sanitized = input;

        for (Map.Entry<String, Pattern> entry : PATTERNS.entrySet()) {
            sanitized = entry.getValue().matcher(sanitized).replaceAll("[MASKED_" + entry.getKey() + "]");
        }

        return sanitized;
    }
}
