package in.devtamakuwala.smarti18nauto.interceptor;

import in.devtamakuwala.smarti18nauto.annotation.AutoTranslate;
import in.devtamakuwala.smarti18nauto.config.SmartI18nProperties;
import in.devtamakuwala.smarti18nauto.engine.TranslationEngine;
import in.devtamakuwala.smarti18nauto.util.LanguageDetectionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.lang.reflect.Type;

/**
 * Request body advice that optionally translates incoming request bodies
 * to the application's base language.
 * <p>
 * This is disabled by default and can be enabled via
 * {@code smart.i18n.translate-request-body=true}.
 * When enabled, incoming request bodies on {@link AutoTranslate}-annotated
 * endpoints are translated from the detected language to the source locale.
 * </p>
 *
 * @author devtamakuwala
 * @since 0.0.1
 */
@RestControllerAdvice
public class TranslationRequestBodyAdvice extends RequestBodyAdviceAdapter {

    private static final Logger log = LoggerFactory.getLogger(TranslationRequestBodyAdvice.class);

    private final TranslationEngine translationEngine;
    private final LanguageDetectionUtil languageDetectionUtil;
    private final SmartI18nProperties properties;

    public TranslationRequestBodyAdvice(TranslationEngine translationEngine,
                                         LanguageDetectionUtil languageDetectionUtil,
                                         SmartI18nProperties properties) {
        this.translationEngine = translationEngine;
        this.languageDetectionUtil = languageDetectionUtil;
        this.properties = properties;
    }

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        if (!properties.isTranslateRequestBody()) {
            return false;
        }
        return methodParameter.hasMethodAnnotation(AutoTranslate.class)
                || methodParameter.getDeclaringClass().isAnnotationPresent(AutoTranslate.class);
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage,
                                 MethodParameter parameter, Type targetType,
                                 Class<? extends HttpMessageConverter<?>> converterType) {
        if (body == null) {
            return null;
        }

        try {
            String incomingLang = languageDetectionUtil.resolveTargetLanguage();
            String baseLang = properties.getSourceLocale();

            if (incomingLang.equalsIgnoreCase(baseLang)) {
                return body;
            }

            log.debug("Translating request body from {} to base language {}",
                    incomingLang, baseLang);

            // Translate FROM the incoming language TO the base language
            return translationEngine.translateObject(body, incomingLang, baseLang);
        } catch (Exception e) {
            log.error("Request body translation failed: {}", e.getMessage(), e);
            return body;
        }
    }
}

