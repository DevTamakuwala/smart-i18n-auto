package in.devtamakuwala.smarti18nauto.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import in.devtamakuwala.smarti18nauto.aop.TranslationAspect;
import in.devtamakuwala.smarti18nauto.cache.TranslationCache;
import in.devtamakuwala.smarti18nauto.engine.DefaultTranslationEngine;
import in.devtamakuwala.smarti18nauto.engine.TranslationEngine;
import in.devtamakuwala.smarti18nauto.filter.ContentFilter;
import in.devtamakuwala.smarti18nauto.interceptor.TranslationRequestBodyAdvice;
import in.devtamakuwala.smarti18nauto.interceptor.TranslationResponseBodyAdvice;
import in.devtamakuwala.smarti18nauto.provider.*;
import in.devtamakuwala.smarti18nauto.traversal.ObjectTraverser;
import in.devtamakuwala.smarti18nauto.util.LanguageDetectionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Main auto-configuration for Smart I18N Auto.
 * <p>
 * Automatically configures all translation infrastructure beans when
 * {@code smart.i18n.enabled=true} (default) and required classes are on the classpath.
 * </p>
 *
 * @author devtamakuwala
 * @since 0.0.1
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "smart.i18n", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SmartI18nProperties.class)
public class SmartI18nAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SmartI18nAutoConfiguration.class);

    // ============================================================
    // Core beans
    // ============================================================

    @Bean
    @ConditionalOnMissingBean
    public ContentFilter smartI18nContentFilter(SmartI18nProperties properties) {
        log.info("Smart I18N Auto: Registering ContentFilter");
        return new ContentFilter(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectTraverser smartI18nObjectTraverser(ContentFilter contentFilter, SmartI18nProperties properties) {
        return new ObjectTraverser(contentFilter, properties.getSafeguard().getMaxTraversalDepth());
    }

    @Bean
    @ConditionalOnMissingBean
    public LanguageDetectionUtil smartI18nLanguageDetectionUtil(SmartI18nProperties properties) {
        return new LanguageDetectionUtil(properties);
    }

    // ============================================================
    // Cache
    // ============================================================

    @Bean
    @ConditionalOnMissingBean(name = "smartI18nCaffeineCache")
    public Cache<String, String> smartI18nCaffeineCache(SmartI18nProperties properties) {
        log.info("Smart I18N Auto: Configuring Caffeine cache (TTL={}min, maxSize={})",
                properties.getCache().getTtlMinutes(), properties.getCache().getMaxSize());
        return Caffeine.newBuilder()
                .expireAfterWrite(properties.getCache().getTtlMinutes(), TimeUnit.MINUTES)
                .maximumSize(properties.getCache().getMaxSize())
                .recordStats()
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public TranslationCache smartI18nTranslationCache(Cache<String, String> smartI18nCaffeineCache) {
        return new TranslationCache(smartI18nCaffeineCache);
    }

    // ============================================================
    // Translation Providers
    // ============================================================

    /**
     * Provides a default ObjectMapper if none is already configured.
     */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper smartI18nObjectMapper() {
        return new ObjectMapper();
    }

    /**
     * Provides a default WebClient.Builder if none is already configured.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(WebClient.class)
    public WebClient.Builder smartI18nWebClientBuilder() {
        return WebClient.builder();
    }

    /**
     * Provider configurations - each conditionally registered based on API key availability.
     * Each provider gets a cloned WebClient.Builder to prevent base URL cross-contamination.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(WebClient.class)
    static class ProviderConfiguration {

        /**
         * Clones the builder and applies response buffer size limits.
         */
        private static WebClient.Builder safeBuilder(WebClient.Builder source, SmartI18nProperties properties) {
            int maxBufferMb = properties.getSafeguard().getWebClientMaxBufferSizeMb();
            return source.clone()
                    .codecs(configurer -> configurer.defaultCodecs()
                            .maxInMemorySize(maxBufferMb * 1024 * 1024));
        }

        @Bean
        @ConditionalOnMissingBean(name = "googleCloudTranslationProvider")
        @ConditionalOnProperty(prefix = "smart.i18n.google-cloud", name = "api-key")
        public GoogleCloudTranslationProvider googleCloudTranslationProvider(
                SmartI18nProperties properties,
                WebClient.Builder webClientBuilder,
                ObjectMapper objectMapper) {
            log.info("Smart I18N Auto: Registering Google Cloud Translation provider");
            return new GoogleCloudTranslationProvider(properties, safeBuilder(webClientBuilder, properties), objectMapper);
        }

        @Bean
        @ConditionalOnMissingBean(name = "googleGeminiTranslationProvider")
        @ConditionalOnProperty(prefix = "smart.i18n.gemini", name = "api-key")
        public GoogleGeminiTranslationProvider googleGeminiTranslationProvider(
                SmartI18nProperties properties,
                WebClient.Builder webClientBuilder,
                ObjectMapper objectMapper) {
            log.info("Smart I18N Auto: Registering Google Gemini translation provider");
            return new GoogleGeminiTranslationProvider(properties, safeBuilder(webClientBuilder, properties), objectMapper);
        }

        @Bean
        @ConditionalOnMissingBean(name = "openAiTranslationProvider")
        @ConditionalOnProperty(prefix = "smart.i18n.openai", name = "api-key")
        public OpenAiTranslationProvider openAiTranslationProvider(
                SmartI18nProperties properties,
                WebClient.Builder webClientBuilder,
                ObjectMapper objectMapper) {
            log.info("Smart I18N Auto: Registering OpenAI translation provider");
            return new OpenAiTranslationProvider(properties, safeBuilder(webClientBuilder, properties), objectMapper);
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public TranslationProviderFactory smartI18nProviderFactory(
            List<TranslationProvider> providers,
            SmartI18nProperties properties) {
        log.info("Smart I18N Auto: Initializing provider factory with {} provider(s): {}",
                providers.size(),
                providers.stream().map(TranslationProvider::getName).toList());
        return new TranslationProviderFactory(providers, properties);
    }

    // ============================================================
    // Translation Engine
    // ============================================================

    @Bean
    @ConditionalOnMissingBean
    public TranslationEngine smartI18nTranslationEngine(
            TranslationProviderFactory providerFactory,
            TranslationCache translationCache,
            ObjectTraverser objectTraverser,
            ContentFilter contentFilter,
            SmartI18nProperties properties) {
        log.info("Smart I18N Auto: Registering DefaultTranslationEngine");
        return new DefaultTranslationEngine(providerFactory, translationCache, objectTraverser, contentFilter, properties);
    }

    // ============================================================
    // Web interceptors (only in servlet web applications)
    // ============================================================

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "jakarta.servlet.http.HttpServletRequest")
    static class WebInterceptorConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public TranslationResponseBodyAdvice smartI18nResponseBodyAdvice(
                TranslationEngine translationEngine,
                LanguageDetectionUtil languageDetectionUtil,
                SmartI18nProperties properties,
                ObjectMapper objectMapper) {
            log.info("Smart I18N Auto: Registering response body translation advice");
            return new TranslationResponseBodyAdvice(translationEngine, languageDetectionUtil, properties, objectMapper);
        }

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "smart.i18n", name = "translate-request-body", havingValue = "true")
        public TranslationRequestBodyAdvice smartI18nRequestBodyAdvice(
                TranslationEngine translationEngine,
                LanguageDetectionUtil languageDetectionUtil,
                SmartI18nProperties properties) {
            log.info("Smart I18N Auto: Registering request body translation advice");
            return new TranslationRequestBodyAdvice(translationEngine, languageDetectionUtil, properties);
        }
    }

    // ============================================================
    // AOP (available in both web and non-web contexts)
    // ============================================================

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
    static class AopConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public TranslationAspect smartI18nTranslationAspect(
                TranslationEngine translationEngine,
                LanguageDetectionUtil languageDetectionUtil,
                SmartI18nProperties properties,
                ObjectMapper objectMapper) {
            log.info("Smart I18N Auto: Registering translation AOP aspect");
            return new TranslationAspect(translationEngine, languageDetectionUtil, properties, objectMapper);
        }
    }
}

