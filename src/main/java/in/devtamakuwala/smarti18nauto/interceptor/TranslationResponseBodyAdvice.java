package in.devtamakuwala.smarti18nauto.interceptor;

import in.devtamakuwala.smarti18nauto.annotation.AutoTranslate;
import in.devtamakuwala.smarti18nauto.config.SmartI18nProperties;
import in.devtamakuwala.smarti18nauto.engine.TranslationEngine;
import in.devtamakuwala.smarti18nauto.util.LanguageDetectionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Response body advice that automatically translates outgoing response bodies.
 * <p>
 * Translation is triggered when:
 * <ul>
 *   <li>The controller method or class is annotated with {@link AutoTranslate}</li>
 *   <li>The {@code Accept-Language} header indicates a language different from the base language</li>
 * </ul>
 * </p>
 *
 * @author devtamakuwala
 * @since 0.0.1
 */
@RestControllerAdvice
public class TranslationResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    private static final Logger log = LoggerFactory.getLogger(TranslationResponseBodyAdvice.class);

    private final TranslationEngine translationEngine;
    private final LanguageDetectionUtil languageDetectionUtil;
    private final SmartI18nProperties properties;

    public TranslationResponseBodyAdvice(TranslationEngine translationEngine,
                                          LanguageDetectionUtil languageDetectionUtil,
                                          SmartI18nProperties properties) {
        this.translationEngine = translationEngine;
        this.languageDetectionUtil = languageDetectionUtil;
        this.properties = properties;
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

            return translationEngine.translateObject(body, sourceLang, targetLang);
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
}

