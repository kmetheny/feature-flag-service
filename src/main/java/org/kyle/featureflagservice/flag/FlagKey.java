package org.kyle.featureflagservice.flag;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@SuppressWarnings("unused")
@NotBlank
@Size(max = 255)
@Pattern(regexp = "^[a-z0-9][a-z0-9._-]*$",
        message = "must be lowercase letters, digits, '.', '_' or '-'")
@Constraint(validatedBy = {})
@Target({FIELD, PARAMETER, RECORD_COMPONENT, TYPE_USE})
@Retention(RUNTIME)
public @interface FlagKey {
    String message() default "must be a valid flag key";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}