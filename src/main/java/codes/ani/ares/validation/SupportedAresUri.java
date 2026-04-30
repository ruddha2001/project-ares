package codes.ani.ares.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Jakarta Bean Validation constraint that validates whether a string
 * begins with a supported Ares URI scheme.
 *
 * <p>Supported schemes are {@code github://}, {@code https://github.com/},
 * {@code mcp://}, and {@code notion://}. The validation is case-insensitive.</p>
 *
 * <p>The actual validation logic is delegated to {@link SupportedAresUriValidator}.</p>
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = SupportedAresUriValidator.class)
public @interface SupportedAresUri {
    /**
     * @return the error message returned when validation fails
     */
    String message() default "Unsupported URI scheme. Must start with github://, mcp://, or notion://";

    /**
     * @return validation group predicates
     */
    Class<?>[] groups() default {};

    /**
     * @return payload type used to associate metadata with the constraint declaration
     */
    Class<? extends Payload>[] payload() default {};
}
