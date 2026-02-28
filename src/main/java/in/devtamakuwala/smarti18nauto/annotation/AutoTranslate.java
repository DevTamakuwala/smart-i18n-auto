package in.devtamakuwala.smarti18nauto.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method, service method, or entire class for automatic translation.
 * <p>
 * When applied at the class level, all public methods will have their return values translated.
 * When applied at the method level, only that method's return value is translated.
 * </p>
 *
 * @author devtamakuwala
 * @since 0.0.1
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AutoTranslate {

    /**
     * Override the source language (BCP-47 code). Defaults to the configured base language.
     *
     * @return source language code
     */
    String sourceLocale() default "";

    /**
     * Override the target language (BCP-47 code). Defaults to Accept-Language header value.
     *
     * @return target language code
     */
    String targetLocale() default "";
}

