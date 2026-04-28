package codes.ani.ares.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = SupportedAresUriValidator.class)
public @interface SupportedAresUri {
    String message() default "Unsupported URI scheme. Must start with github://, mcp://, or notion://";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
