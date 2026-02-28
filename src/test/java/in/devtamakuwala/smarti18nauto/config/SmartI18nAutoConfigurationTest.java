package in.devtamakuwala.smarti18nauto.config;

import in.devtamakuwala.smarti18nauto.cache.TranslationCache;
import in.devtamakuwala.smarti18nauto.engine.TranslationEngine;
import in.devtamakuwala.smarti18nauto.filter.ContentFilter;
import in.devtamakuwala.smarti18nauto.traversal.ObjectTraverser;
import in.devtamakuwala.smarti18nauto.util.LanguageDetectionUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SmartI18nAutoConfiguration}.
 */
class SmartI18nAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SmartI18nAutoConfiguration.class));

    @Test
    @DisplayName("Should register core beans when enabled")
    void shouldRegisterCoreBeansWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "smart.i18n.enabled=true",
                        "smart.i18n.gemini.api-key=test-key"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(SmartI18nProperties.class);
                    assertThat(context).hasSingleBean(ContentFilter.class);
                    assertThat(context).hasSingleBean(ObjectTraverser.class);
                    assertThat(context).hasSingleBean(LanguageDetectionUtil.class);
                    assertThat(context).hasSingleBean(TranslationCache.class);
                    assertThat(context).hasSingleBean(TranslationEngine.class);
                });
    }

    @Test
    @DisplayName("Should not register beans when disabled")
    void shouldNotRegisterBeansWhenDisabled() {
        contextRunner
                .withPropertyValues("smart.i18n.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ContentFilter.class);
                    assertThat(context).doesNotHaveBean(TranslationEngine.class);
                });
    }

    @Test
    @DisplayName("Should register by default (matchIfMissing)")
    void shouldRegisterByDefault() {
        contextRunner
                .withPropertyValues("smart.i18n.gemini.api-key=test-key")
                .run(context -> {
                    assertThat(context).hasSingleBean(SmartI18nProperties.class);
                    assertThat(context).hasSingleBean(ContentFilter.class);
                });
    }

    @Test
    @DisplayName("Should configure properties correctly")
    void shouldConfigureProperties() {
        contextRunner
                .withPropertyValues(
                        "smart.i18n.source-locale=en",
                        "smart.i18n.default-target-locale=fr",
                        "smart.i18n.cache.ttl-minutes=120",
                        "smart.i18n.cache.max-size=5000",
                        "smart.i18n.batch.max-size=25",
                        "smart.i18n.filter.min-length=3",
                        "smart.i18n.gemini.api-key=test-key"
                )
                .run(context -> {
                    SmartI18nProperties props = context.getBean(SmartI18nProperties.class);
                    assertThat(props.getSourceLocale()).isEqualTo("en");
                    assertThat(props.getDefaultTargetLocale()).isEqualTo("fr");
                    assertThat(props.getCache().getTtlMinutes()).isEqualTo(120);
                    assertThat(props.getCache().getMaxSize()).isEqualTo(5000);
                    assertThat(props.getBatch().getMaxSize()).isEqualTo(25);
                    assertThat(props.getFilter().getMinLength()).isEqualTo(3);
                });
    }

    @Test
    @DisplayName("Should configure safeguard properties correctly")
    void shouldConfigureSafeguardProperties() {
        contextRunner
                .withPropertyValues(
                        "smart.i18n.gemini.api-key=test-key",
                        "smart.i18n.safeguard.max-strings-per-request=100",
                        "smart.i18n.safeguard.max-text-length=3000",
                        "smart.i18n.safeguard.max-traversal-depth=16",
                        "smart.i18n.safeguard.web-client-max-buffer-size-mb=4"
                )
                .run(context -> {
                    SmartI18nProperties props = context.getBean(SmartI18nProperties.class);
                    assertThat(props.getSafeguard().getMaxStringsPerRequest()).isEqualTo(100);
                    assertThat(props.getSafeguard().getMaxTextLength()).isEqualTo(3000);
                    assertThat(props.getSafeguard().getMaxTraversalDepth()).isEqualTo(16);
                    assertThat(props.getSafeguard().getWebClientMaxBufferSizeMb()).isEqualTo(4);
                });
    }

    @Test
    @DisplayName("API key should be masked in toString()")
    void apiKeyShouldBeMaskedInToString() {
        SmartI18nProperties.GoogleCloudConfig gcConfig = new SmartI18nProperties.GoogleCloudConfig();
        gcConfig.setApiKey("AIzaSyD-super-secret-key-1234");
        String toString = gcConfig.toString();
        assertThat(toString).doesNotContain("AIzaSyD-super-secret-key-1234");
        assertThat(toString).contains("****");

        SmartI18nProperties.GeminiConfig geminiConfig = new SmartI18nProperties.GeminiConfig();
        geminiConfig.setApiKey("my-secret-gemini-key");
        assertThat(geminiConfig.toString()).doesNotContain("my-secret-gemini-key");
        assertThat(geminiConfig.toString()).contains("****");

        SmartI18nProperties.OpenAiConfig openAiConfig = new SmartI18nProperties.OpenAiConfig();
        openAiConfig.setApiKey("sk-proj-very-secret");
        assertThat(openAiConfig.toString()).doesNotContain("sk-proj-very-secret");
        assertThat(openAiConfig.toString()).contains("****");
    }
}

