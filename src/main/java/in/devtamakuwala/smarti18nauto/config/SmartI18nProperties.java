package in.devtamakuwala.smarti18nauto.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for Smart I18N Auto.
 * <p>
 * All properties are prefixed with {@code smart.i18n}.
 * </p>
 *
 * <p><strong>Example configuration:</strong></p>
 * <pre>
 * smart.i18n.enabled=true
 * smart.i18n.source-locale=en
 * smart.i18n.default-target-locale=en
 * smart.i18n.provider.active=google-cloud
 * smart.i18n.google-cloud.api-key=YOUR_KEY
 * smart.i18n.cache.ttl-minutes=60
 * smart.i18n.cache.max-size=10000
 * </pre>
 *
 * @author devtamakuwala
 * @since 0.0.1
 */
@ConfigurationProperties(prefix = "smart.i18n")
public class SmartI18nProperties {

    /**
     * Enable or disable the translation middleware entirely.
     */
    private boolean enabled = true;

    /**
     * The base/source language of the application content (BCP-47 code).
     */
    private String sourceLocale = "en";

    /**
     * Default target language when none is detected from the request.
     */
    private String defaultTargetLocale = "en";

    /**
     * Custom HTTP header name to read target language from.
     */
    private String headerName = "";

    /**
     * Query parameter name to read target language from.
     */
    private String queryParam = "";

    /**
     * Whether to translate incoming request bodies (disabled by default).
     */
    private boolean translateRequestBody = false;

    /**
     * Provider configuration.
     */
    private ProviderConfig provider = new ProviderConfig();

    /**
     * Google Cloud Translation API configuration.
     */
    private GoogleCloudConfig googleCloud = new GoogleCloudConfig();

    /**
     * Google Gemini API configuration.
     */
    private GeminiConfig gemini = new GeminiConfig();

    /**
     * OpenAI API configuration.
     */
    private OpenAiConfig openai = new OpenAiConfig();

    /**
     * Cache configuration.
     */
    private CacheConfig cache = new CacheConfig();

    /**
     * Batch translation configuration.
     */
    private BatchConfig batch = new BatchConfig();

    /**
     * Content filter configuration.
     */
    private FilterConfig filter = new FilterConfig();

    /**
     * Safety guardrails to prevent cost explosion and resource exhaustion.
     */
    private SafeguardConfig safeguard = new SafeguardConfig();

    // --- Getters and Setters ---

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSourceLocale() {
        return sourceLocale;
    }

    public void setSourceLocale(String sourceLocale) {
        this.sourceLocale = sourceLocale;
    }

    public String getDefaultTargetLocale() {
        return defaultTargetLocale;
    }

    public void setDefaultTargetLocale(String defaultTargetLocale) {
        this.defaultTargetLocale = defaultTargetLocale;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public String getQueryParam() {
        return queryParam;
    }

    public void setQueryParam(String queryParam) {
        this.queryParam = queryParam;
    }

    public boolean isTranslateRequestBody() {
        return translateRequestBody;
    }

    public void setTranslateRequestBody(boolean translateRequestBody) {
        this.translateRequestBody = translateRequestBody;
    }

    public ProviderConfig getProvider() {
        return provider;
    }

    public void setProvider(ProviderConfig provider) {
        this.provider = provider;
    }

    public GoogleCloudConfig getGoogleCloud() {
        return googleCloud;
    }

    public void setGoogleCloud(GoogleCloudConfig googleCloud) {
        this.googleCloud = googleCloud;
    }

    public GeminiConfig getGemini() {
        return gemini;
    }

    public void setGemini(GeminiConfig gemini) {
        this.gemini = gemini;
    }

    public OpenAiConfig getOpenai() {
        return openai;
    }

    public void setOpenai(OpenAiConfig openai) {
        this.openai = openai;
    }

    public CacheConfig getCache() {
        return cache;
    }

    public void setCache(CacheConfig cache) {
        this.cache = cache;
    }

    public BatchConfig getBatch() {
        return batch;
    }

    public void setBatch(BatchConfig batch) {
        this.batch = batch;
    }

    public FilterConfig getFilter() {
        return filter;
    }

    public void setFilter(FilterConfig filter) {
        this.filter = filter;
    }

    public SafeguardConfig getSafeguard() {
        return safeguard;
    }

    public void setSafeguard(SafeguardConfig safeguard) {
        this.safeguard = safeguard;
    }

    // ============================================================
    // Nested configuration classes
    // ============================================================

    /**
     * Provider selection and fallback configuration.
     */
    public static class ProviderConfig {

        /**
         * Explicitly select a provider by name: "google-cloud", "gemini", or "openai".
         * If blank, auto-detection is used based on available API keys.
         */
        private String active = "";

        /**
         * Enable fallback chain: if the primary provider fails, try the next available one.
         */
        private boolean fallbackEnabled = false;

        /**
         * Timeout in milliseconds for provider API calls.
         */
        private long timeoutMs = 10000;

        public String getActive() {
            return active;
        }

        public void setActive(String active) {
            this.active = active;
        }

        public boolean isFallbackEnabled() {
            return fallbackEnabled;
        }

        public void setFallbackEnabled(boolean fallbackEnabled) {
            this.fallbackEnabled = fallbackEnabled;
        }

        public long getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }

    /**
     * Google Cloud Translation API v2 configuration.
     */
    public static class GoogleCloudConfig {

        /**
         * Google Cloud Translation API key.
         */
        private String apiKey;

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        @Override
        public String toString() {
            return "GoogleCloudConfig{apiKey=" + maskSecret(apiKey) + "}";
        }
    }

    /**
     * Google Gemini API configuration.
     */
    public static class GeminiConfig {

        /**
         * Gemini API key.
         */
        private String apiKey;

        /**
         * Gemini model to use.
         */
        private String model = "gemini-2.0-flash";

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        @Override
        public String toString() {
            return "GeminiConfig{apiKey=" + maskSecret(apiKey) + ", model='" + model + "'}";
        }
    }

    /**
     * OpenAI API configuration.
     */
    public static class OpenAiConfig {

        /**
         * OpenAI API key.
         */
        private String apiKey;

        /**
         * OpenAI model to use.
         */
        private String model = "gpt-4o-mini";

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        @Override
        public String toString() {
            return "OpenAiConfig{apiKey=" + maskSecret(apiKey) + ", model='" + model + "'}";
        }
    }

    /**
     * Caffeine cache configuration.
     */
    public static class CacheConfig {
        // ...existing code...
        private long ttlMinutes = 60;
        private long maxSize = 10000;
        public long getTtlMinutes() { return ttlMinutes; }
        public void setTtlMinutes(long ttlMinutes) { this.ttlMinutes = ttlMinutes; }
        public long getMaxSize() { return maxSize; }
        public void setMaxSize(long maxSize) { this.maxSize = maxSize; }
    }

    /**
     * Batch translation configuration.
     */
    public static class BatchConfig {
        private int maxSize = 50;
        public int getMaxSize() { return maxSize; }
        public void setMaxSize(int maxSize) { this.maxSize = maxSize; }
    }

    /**
     * Content filtering configuration.
     */
    public static class FilterConfig {
        private int minLength = 2;
        private List<String> skipPatterns = new ArrayList<>();
        public int getMinLength() { return minLength; }
        public void setMinLength(int minLength) { this.minLength = minLength; }
        public List<String> getSkipPatterns() { return skipPatterns; }
        public void setSkipPatterns(List<String> skipPatterns) { this.skipPatterns = skipPatterns; }
    }

    /**
     * Safety guardrails to prevent cost explosion and resource exhaustion.
     */
    public static class SafeguardConfig {

        /**
         * Maximum number of translatable strings per single request.
         * Prevents cost explosion on large DTOs.
         */
        private int maxStringsPerRequest = 200;

        /**
         * Maximum character length of a single text eligible for translation.
         * Longer strings are silently skipped.
         */
        private int maxTextLength = 5000;

        /**
         * Maximum depth for recursive DTO traversal. Prevents StackOverflowError.
         */
        private int maxTraversalDepth = 32;

        /**
         * Maximum in-memory buffer size in MB for WebClient responses.
         * Prevents OOM from oversized API responses.
         */
        private int webClientMaxBufferSizeMb = 2;

        public int getMaxStringsPerRequest() {
            return maxStringsPerRequest;
        }

        public void setMaxStringsPerRequest(int maxStringsPerRequest) {
            this.maxStringsPerRequest = maxStringsPerRequest;
        }

        public int getMaxTextLength() {
            return maxTextLength;
        }

        public void setMaxTextLength(int maxTextLength) {
            this.maxTextLength = maxTextLength;
        }

        public int getMaxTraversalDepth() {
            return maxTraversalDepth;
        }

        public void setMaxTraversalDepth(int maxTraversalDepth) {
            this.maxTraversalDepth = maxTraversalDepth;
        }

        public int getWebClientMaxBufferSizeMb() {
            return webClientMaxBufferSizeMb;
        }

        public void setWebClientMaxBufferSizeMb(int webClientMaxBufferSizeMb) {
            this.webClientMaxBufferSizeMb = webClientMaxBufferSizeMb;
        }
    }

    /**
     * Masks a secret value for safe logging. Shows only last 4 characters.
     *
     * @param secret the secret to mask
     * @return masked string
     */
    static String maskSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return "[not set]";
        }
        if (secret.length() <= 4) {
            return "****";
        }
        return "****" + secret.substring(secret.length() - 4);
    }
}

