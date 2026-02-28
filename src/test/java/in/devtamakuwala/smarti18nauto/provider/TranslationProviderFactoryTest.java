package in.devtamakuwala.smarti18nauto.provider;

import in.devtamakuwala.smarti18nauto.config.SmartI18nProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TranslationProviderFactory}.
 */
class TranslationProviderFactoryTest {

    static class TestProvider implements TranslationProvider {
        private final String name;
        private final boolean available;
        private final int order;

        TestProvider(String name, boolean available, int order) {
            this.name = name;
            this.available = available;
            this.order = order;
        }

        @Override
        public String getName() { return name; }

        @Override
        public String translate(String text, String sourceLang, String targetLang) {
            return "[" + name + "] " + text;
        }

        @Override
        public boolean isAvailable() { return available; }

        @Override
        public int getOrder() { return order; }
    }

    @Test
    @DisplayName("Should auto-detect first available provider by priority")
    void shouldAutoDetectProvider() {
        SmartI18nProperties props = new SmartI18nProperties();
        TranslationProviderFactory factory = new TranslationProviderFactory(List.of(
                new TestProvider("low-priority", true, 30),
                new TestProvider("high-priority", true, 10),
                new TestProvider("mid-priority", true, 20)
        ), props);

        TranslationProvider provider = factory.getPrimaryProvider();
        assertEquals("high-priority", provider.getName());
    }

    @Test
    @DisplayName("Should select explicitly configured provider")
    void shouldSelectExplicitProvider() {
        SmartI18nProperties props = new SmartI18nProperties();
        props.getProvider().setActive("mid-priority");

        TranslationProviderFactory factory = new TranslationProviderFactory(List.of(
                new TestProvider("high-priority", true, 10),
                new TestProvider("mid-priority", true, 20),
                new TestProvider("low-priority", true, 30)
        ), props);

        TranslationProvider provider = factory.getPrimaryProvider();
        assertEquals("mid-priority", provider.getName());
    }

    @Test
    @DisplayName("Should throw if no provider is available")
    void shouldThrowIfNoProviderAvailable() {
        SmartI18nProperties props = new SmartI18nProperties();
        TranslationProviderFactory factory = new TranslationProviderFactory(List.of(
                new TestProvider("unavailable", false, 10)
        ), props);

        assertThrows(IllegalStateException.class, factory::getPrimaryProvider);
    }

    @Test
    @DisplayName("Should throw if explicitly configured provider not found")
    void shouldThrowIfExplicitProviderNotFound() {
        SmartI18nProperties props = new SmartI18nProperties();
        props.getProvider().setActive("nonexistent");

        TranslationProviderFactory factory = new TranslationProviderFactory(List.of(
                new TestProvider("existing", true, 10)
        ), props);

        assertThrows(IllegalStateException.class, factory::getPrimaryProvider);
    }

    @Test
    @DisplayName("Should fallback to next provider on failure")
    void shouldFallbackOnFailure() {
        SmartI18nProperties props = new SmartI18nProperties();
        props.getProvider().setFallbackEnabled(true);

        TranslationProvider failingProvider = new TranslationProvider() {
            @Override
            public String getName() { return "failing"; }

            @Override
            public String translate(String text, String sourceLang, String targetLang) {
                throw new RuntimeException("API failure");
            }

            @Override
            public List<String> translateBatch(List<String> texts, String sourceLang, String targetLang) {
                throw new RuntimeException("API failure");
            }

            @Override
            public boolean isAvailable() { return true; }

            @Override
            public int getOrder() { return 10; }
        };

        TranslationProviderFactory factory = new TranslationProviderFactory(List.of(
                failingProvider,
                new TestProvider("backup", true, 20)
        ), props);

        List<String> result = factory.translateBatchWithFallback(
                List.of("Hello"), "en", "fr");

        assertEquals(1, result.size());
        assertEquals("[backup] Hello", result.getFirst());
    }
}

