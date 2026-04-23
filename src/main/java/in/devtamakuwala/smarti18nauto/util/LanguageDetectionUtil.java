package in.devtamakuwala.smarti18nauto.util;

import in.devtamakuwala.smarti18nauto.config.SmartI18nProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Locale;

/**
 * Utility for resolving the target translation locale from the current HTTP request.
 * <p>
 * Resolution order:
 * <ol>
 *   <li>Custom header (configurable via {@code smart.i18n.header-name})</li>
 *   <li>Query parameter (configurable via {@code smart.i18n.query-param})</li>
 *   <li>{@code Accept-Language} header</li>
 *   <li>Default target locale from properties</li>
 * </ol>
 *
 *
 * @author devtamakuwala
 * @since 0.0.1
 */
public class LanguageDetectionUtil {

    private static final SmartI18nLogger log = SmartI18nLogger.getLogger(LanguageDetectionUtil.class);

    private final SmartI18nProperties properties;

    public LanguageDetectionUtil(SmartI18nProperties properties) {
        this.properties = properties;
    }

    /**
     * Resolves the target language from the current request context.
     *
     * @return BCP-47 language code, never null
     */
    public String resolveTargetLanguage() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                return resolveFromRequest(attrs.getRequest());
            }
        } catch (Exception e) {
            log.debug("No request context available: {}", e.getMessage());
        }

        return properties.getDefaultTargetLocale();
    }

    /**
     * Resolves the target language from the given HTTP request.
     *
     * @param request the HTTP servlet request
     * @return BCP-47 language code, never null
     */
    public String resolveFromRequest(HttpServletRequest request) {
        // 1. Custom header
        String customHeader = properties.getHeaderName();
        if (customHeader != null && !customHeader.isBlank()) {
            String value = request.getHeader(customHeader);
            if (value != null && !value.isBlank()) {
                log.debug("Target language from custom header '{}': {}", customHeader, value);
                return normalizeLanguageCode(value);
            }
        }

        // 2. Query parameter
        String queryParam = properties.getQueryParam();
        if (queryParam != null && !queryParam.isBlank()) {
            String value = request.getParameter(queryParam);
            if (value != null && !value.isBlank()) {
                log.debug("Target language from query param '{}': {}", queryParam, value);
                return normalizeLanguageCode(value);
            }
        }

        // 3. Accept-Language header
        String acceptLanguage = request.getHeader("Accept-Language");
        if (acceptLanguage != null && !acceptLanguage.isBlank()) {
            String lang = parseAcceptLanguage(acceptLanguage);
            log.debug("Target language from Accept-Language: {}", lang);
            return lang;
        }

        // 4. Default
        log.debug("Using default target language: {}", properties.getDefaultTargetLocale());
        return properties.getDefaultTargetLocale();
    }

    /**
     * Parses the Accept-Language header and returns the primary language code.
     *
     * @param acceptLanguage the Accept-Language header value
     * @return normalized language code
     */
    private String parseAcceptLanguage(String acceptLanguage) {
        try {
            // Take the first (highest priority) language
            String primary = acceptLanguage.split(",")[0].split(";")[0].strip();
            return normalizeLanguageCode(primary);
        } catch (Exception e) {
            log.warn("Failed to parse Accept-Language '{}': {}", acceptLanguage, e.getMessage());
            return properties.getDefaultTargetLocale();
        }
    }

    /**
     * Normalizes a language code to a consistent format (lowercase, primary subtag only).
     *
     * @param code the language code
     * @return normalized code
     */
    private String normalizeLanguageCode(String code) {
        if (code == null || code.isBlank()) {
            return properties.getDefaultTargetLocale();
        }
        // Use Locale to normalize
        Locale locale = Locale.forLanguageTag(code.strip());
        String lang = locale.getLanguage();
        return lang.isBlank() ? code.strip().toLowerCase() : lang;
    }
}

