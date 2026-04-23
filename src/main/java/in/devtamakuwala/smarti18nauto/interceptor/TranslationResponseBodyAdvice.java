package in.devtamakuwala.smarti18nauto.interceptor;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.devtamakuwala.smarti18nauto.annotation.AutoTranslate;
import in.devtamakuwala.smarti18nauto.config.SmartI18nProperties;
import in.devtamakuwala.smarti18nauto.engine.TranslationEngine;
import in.devtamakuwala.smarti18nauto.util.LanguageDetectionUtil;
import in.devtamakuwala.smarti18nauto.util.SmartI18nLogger;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.lang.reflect.Type;

/**
 * Response body advice that automatically translates outgoing response bodies.
 * <p>
 * Translation is triggered when:
 * <ul>
 *   <li>The controller method or class is annotated with {@link AutoTranslate}</li>
 *   <li>The {@code Accept-Language} header indicates a language different from the base language</li>
 * </ul>
 *
 *
 * @author devtamakuwala
 * @since 0.0.1
 */
@RestControllerAdvice
public class TranslationResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    private static final SmartI18nLogger log = SmartI18nLogger.getLogger(TranslationResponseBodyAdvice.class);

    private final TranslationEngine translationEngine;
    private final LanguageDetectionUtil languageDetectionUtil;
    private final SmartI18nProperties properties;
    private final ObjectMapper objectMapper;

    public TranslationResponseBodyAdvice(TranslationEngine translationEngine,
                                          LanguageDetectionUtil languageDetectionUtil,
                                          SmartI18nProperties properties,
                                          ObjectMapper objectMapper) {
        this.translationEngine = translationEngine;
        this.languageDetectionUtil = languageDetectionUtil;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        // Support if method or class has @AutoTranslate
        return returnType.hasMethodAnnotation(AutoTranslate.class)
                || returnType.getDeclaringClass().isAnnotationPresent(AutoTranslate.class);
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                   MethodParameter returnType,
                                   MediaType selectedContentType,
                                   Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                   ServerHttpRequest request,
                                   ServerHttpResponse response) {
        if (body == null) {
            return null;
        }

        try {
            // Resolve languages
            String sourceLang = resolveSourceLang(returnType);
            String targetLang = resolveTargetLang(returnType, request);

            if (sourceLang.equalsIgnoreCase(targetLang)) {
                log.debug("Source and target language are the same ({}), skipping translation", sourceLang);
                return body;
            }

            log.debug("Translating response body from {} to {} for {}.{}",
                    sourceLang, targetLang,
                    returnType.getDeclaringClass().getSimpleName(),
                    returnType.getMethod() != null ? returnType.getMethod().getName() : "unknown");

            // Deep-copy the body before translating to avoid mutating the original DTO.
            // Without this, if the controller returns the same object instance across requests,
            // the first translation overwrites the original English values and subsequent
            // requests for different languages see already-translated text as the source.
            Object copy = deepCopy(body, returnType);
            return translationEngine.translateObject(copy, sourceLang, targetLang);
        } catch (Exception e) {
            log.error("Response body translation failed: {}", e.getMessage(), e);
            return body; // return original on failure
        }
    }

    private String resolveSourceLang(MethodParameter returnType) {
        AutoTranslate annotation = returnType.getMethodAnnotation(AutoTranslate.class);
        if (annotation == null) {
            annotation = returnType.getDeclaringClass().getAnnotation(AutoTranslate.class);
        }

        if (annotation != null && !annotation.sourceLocale().isBlank()) {
            return annotation.sourceLocale();
        }

        return properties.getSourceLocale();
    }

    private String resolveTargetLang(MethodParameter returnType, ServerHttpRequest request) {
        AutoTranslate annotation = returnType.getMethodAnnotation(AutoTranslate.class);
        if (annotation == null) {
            annotation = returnType.getDeclaringClass().getAnnotation(AutoTranslate.class);
        }

        if (annotation != null && !annotation.targetLocale().isBlank()) {
            return annotation.targetLocale();
        }

        // Try to resolve from the request
        if (request instanceof ServletServerHttpRequest servletRequest) {
            return languageDetectionUtil.resolveFromRequest(servletRequest.getServletRequest());
        }

        return languageDetectionUtil.resolveTargetLanguage();
    }

    /**
     * Deep-copies the body object via Jackson serialization/deserialization.
     * This ensures the original DTO is never mutated by in-place translation.
     * <p>
     * Uses the method's generic return type to preserve generic type information
     * (e.g., {@code List<MyDTO>}, {@code Map<String, Object>}) so Jackson can
     * deserialize elements as their correct types instead of raw {@code LinkedHashMap}.
     * Falls back to the original object if copying fails (e.g., non-serializable types).
     *
     * @param body       the response body to copy
     * @param returnType the controller method parameter (provides generic return type)
     * @return a deep copy of the body, or the original if copying fails
     */
    private Object deepCopy(Object body, MethodParameter returnType) {
        if (body instanceof String) {
            return body; // Strings are immutable, no need to copy
        }
        // Skip primitive wrappers, numbers, booleans — immutable, no translation needed
        if (body instanceof Number || body instanceof Boolean || body instanceof Character) {
            return body;
        }
        try {
            String json = objectMapper.writeValueAsString(body);

            // Use the generic return type from the controller method to preserve
            // parameterized types like List<MyDTO> or Map<String, List<String>>
            Type genericType = (returnType.getMethod() != null)
                    ? returnType.getMethod().getGenericReturnType()
                    : null;

            if (genericType != null) {
                JavaType javaType = objectMapper.getTypeFactory().constructType(genericType);
                return objectMapper.readValue(json, javaType);
            }

            // Fallback: use the runtime class (loses generics but still works for simple types)
            return objectMapper.readValue(json, body.getClass());
        } catch (Exception e) {
            log.warn("Could not deep-copy body of type {}, translating in-place: {}",
                    body.getClass().getSimpleName(), e.getMessage());
            return body;
        }
    }
}

