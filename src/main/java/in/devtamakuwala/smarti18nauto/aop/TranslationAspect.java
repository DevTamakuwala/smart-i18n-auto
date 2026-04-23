package in.devtamakuwala.smarti18nauto.aop;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.devtamakuwala.smarti18nauto.annotation.AutoTranslate;
import in.devtamakuwala.smarti18nauto.config.SmartI18nProperties;
import in.devtamakuwala.smarti18nauto.engine.TranslationEngine;
import in.devtamakuwala.smarti18nauto.util.LanguageDetectionUtil;
import in.devtamakuwala.smarti18nauto.util.SmartI18nLogger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

/**
 * AOP aspect for translating return values of methods annotated with {@link AutoTranslate}.
 * <p>
 * This aspect enables translation outside the web layer (e.g., service methods).
 * It intercepts method return values and translates them based on the current
 * request context or configured defaults.
 * </p>
 *
 * @author devtamakuwala
 * @since 0.0.1
 */
@Aspect
public class TranslationAspect {

    private static final SmartI18nLogger log = SmartI18nLogger.getLogger(TranslationAspect.class);

    private final TranslationEngine translationEngine;
    private final LanguageDetectionUtil languageDetectionUtil;
    private final SmartI18nProperties properties;
    private final ObjectMapper objectMapper;

    public TranslationAspect(TranslationEngine translationEngine,
                              LanguageDetectionUtil languageDetectionUtil,
                              SmartI18nProperties properties,
                              ObjectMapper objectMapper) {
        this.translationEngine = translationEngine;
        this.languageDetectionUtil = languageDetectionUtil;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Intercepts methods annotated with {@link AutoTranslate} and translates
     * their return values.
     *
     * @param joinPoint     the join point
     * @param autoTranslate the annotation instance
     * @return the translated return value
     * @throws Throwable if the underlying method throws
     */
    @Around("@annotation(autoTranslate)")
    public Object translateReturnValue(ProceedingJoinPoint joinPoint,
                                        AutoTranslate autoTranslate) throws Throwable {
        Object result = joinPoint.proceed();

        if (result == null) {
            return null;
        }

        try {
            String sourceLang = autoTranslate.sourceLocale().isBlank()
                    ? properties.getSourceLocale()
                    : autoTranslate.sourceLocale();

            String targetLang = autoTranslate.targetLocale().isBlank()
                    ? languageDetectionUtil.resolveTargetLanguage()
                    : autoTranslate.targetLocale();

            if (sourceLang.equalsIgnoreCase(targetLang)) {
                return result;
            }

            log.debug("AOP translating return value of {}.{} from {} to {}",
                    joinPoint.getTarget().getClass().getSimpleName(),
                    joinPoint.getSignature().getName(),
                    sourceLang, targetLang);

            // Deep-copy to avoid mutating the original object
            Object copy = deepCopy(result);
            return translationEngine.translateObject(copy, sourceLang, targetLang);
        } catch (Exception e) {
            log.error("AOP translation failed for {}.{}: {}",
                    joinPoint.getTarget().getClass().getSimpleName(),
                    joinPoint.getSignature().getName(),
                    e.getMessage(), e);
            return result; // return original on failure
        }
    }

    private Object deepCopy(Object body) {
        if (body instanceof String) {
            return body;
        }
        try {
            String json = objectMapper.writeValueAsString(body);
            return objectMapper.readValue(json, body.getClass());
        } catch (Exception e) {
            log.warn("Could not deep-copy body of type {}, translating in-place: {}",
                    body.getClass().getSimpleName(), e.getMessage());
            return body;
        }
    }
}

