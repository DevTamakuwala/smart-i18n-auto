package in.devtamakuwala.smarti18nauto.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field, method return value, or parameter to be excluded from translation.
 * <p>
 * Use this on DTO fields that should never be translated (e.g., identifiers,
 * enum values, technical codes).
 * </p>
 *
 * @author devtamakuwala
 * @since 0.0.1
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SkipTranslation {
}

