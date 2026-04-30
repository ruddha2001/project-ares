package codes.ani.ares.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Set;

/**
 * Validates that a string value begins with one of the supported Ares URI schemes.
 *
 * <p>Performs case-insensitive prefix matching against the following known schemes:</p>
 * <ul>
 *   <li>{@code github://}</li>
 *   <li>{@code https://github.com/}</li>
 *   <li>{@code mcp://}</li>
 *   <li>{@code notion://}</li>
 * </ul>
 *
 * <p>A {@code null} or blank value is always considered invalid.</p>
 */
public class SupportedAresUriValidator implements ConstraintValidator<SupportedAresUri, String> {

    private static final Set<String> SUPPORTED_SCHEMES = Set.of("github://", "https://github.com/", "mcp://", "notion://");

    /**
     * Validates that the provided string starts with a supported URI scheme.
     *
     * @param value   the URI string to validate
     * @param context the constraint validation context
     * @return {@code true} if the value matches a supported scheme; {@code false} otherwise
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return false;
        }

        return SUPPORTED_SCHEMES.stream()
                .anyMatch(value.toLowerCase()::startsWith);
    }
}