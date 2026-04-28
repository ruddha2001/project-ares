package codes.ani.ares.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Set;

public class SupportedAresUriValidator implements ConstraintValidator<SupportedAresUri, String> {

    private static final Set<String> SUPPORTED_SCHEMES = Set.of("github://", "https://github.com/", "mcp://", "notion://");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return false;
        }

        return SUPPORTED_SCHEMES.stream()
                .anyMatch(value.toLowerCase()::startsWith);
    }
}