package codes.ani.ares.service;

import java.util.Map;
import java.util.regex.Pattern;

public class SanitizationService {

    private static final Map<String, Pattern> PATTERNS = Map.of(
            "EMAIL", Pattern.compile("[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+"),
            "CREDIT_CARD", Pattern.compile("\\b(?:\\d[ -]*?){13,16}\\b"),
            "API_KEY", Pattern.compile("(?i)(api[_-]?key|secret|password|auth|token)[\"']?\\s*[:=]\\s*[\"']?([a-zA-Z0-9_\\-]{16,})[\"']?"),
            "PHONE", Pattern.compile("\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b")
    );

    public String sanitize(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }

        String sanitized = input;

        for (Map.Entry<String, Pattern> entry : PATTERNS.entrySet()) {
            sanitized = entry.getValue().matcher(sanitized).replaceAll("[MASKED_"+entry.getKey()+"]");
        }

        return sanitized;
    }
}
