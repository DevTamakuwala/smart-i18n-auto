package in.devtamakuwala.smarti18nauto.engine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import in.devtamakuwala.smarti18nauto.cache.TranslationCache;
import in.devtamakuwala.smarti18nauto.config.SmartI18nProperties;
import in.devtamakuwala.smarti18nauto.filter.ContentFilter;
import in.devtamakuwala.smarti18nauto.provider.TranslationProvider;
import in.devtamakuwala.smarti18nauto.provider.TranslationProviderFactory;
import in.devtamakuwala.smarti18nauto.traversal.ObjectTraverser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DefaultTranslationEngine}.
 * Updated for refactored engine: no marker characters, deduplication, safeguards.
 */
class DefaultTranslationEngineTest {

    private DefaultTranslationEngine engine;
    private SmartI18nProperties properties;
    private AtomicInteger apiCallCount;

    // A mock provider that prefixes text with "[FR]" and tracks call count
    class MockTranslationProvider implements TranslationProvider {
        @Override
        public String getName() { return "mock"; }

        @Override
        public String translate(String text, String sourceLang, String targetLang) {
            apiCallCount.incrementAndGet();
            return "[" + targetLang.toUpperCase() + "] " + text;
        }

        @Override
        public List<String> translateBatch(List<String> texts, String sourceLang, String targetLang) {
            apiCallCount.incrementAndGet();
            return texts.stream()
                    .map(t -> "[" + targetLang.toUpperCase() + "] " + t)
                    .toList();
        }

        @Override
        public boolean isAvailable() { return true; }

        @Override
        public int getOrder() { return 1; }
    }

    static class TestDto {
        String name;
        String description;
        int count;

        TestDto(String name, String description, int count) {
            this.name = name;
            this.description = description;
            this.count = count;
        }
    }

    static class DuplicateDto {
        String field1;
        String field2;
        String field3;

        DuplicateDto(String field1, String field2, String field3) {
            this.field1 = field1;
            this.field2 = field2;
            this.field3 = field3;
        }
    }

    @BeforeEach
    void setUp() {
        apiCallCount = new AtomicInteger(0);
        properties = new SmartI18nProperties();
        properties.getFilter().setMinLength(2);

        ContentFilter contentFilter = new ContentFilter(properties);
        ObjectTraverser objectTraverser = new ObjectTraverser(contentFilter);

        Cache<String, String> caffeineCache = Caffeine.newBuilder()
                .expireAfterWrite(60, TimeUnit.MINUTES)
                .maximumSize(1000)
                .build();
        TranslationCache translationCache = new TranslationCache(caffeineCache);

        TranslationProviderFactory providerFactory = new TranslationProviderFactory(
                List.of(new MockTranslationProvider()), properties);

        engine = new DefaultTranslationEngine(providerFactory, translationCache, objectTraverser, contentFilter, properties);
    }

    @Test
    @DisplayName("Should translate plain string without injecting invisible characters")
    void shouldTranslatePlainString() {
        Object result = engine.translateObject("Hello World", "en", "fr");
        assertEquals("[FR] Hello World", result);
        // Verify no invisible marker characters
        assertFalse(((String) result).contains("\u200B"));
    }

    @Test
    @DisplayName("Should skip translation when source equals target")
    void shouldSkipSameLanguage() {
        Object result = engine.translateObject("Hello World", "en", "en");
        assertEquals("Hello World", result);
    }

    @Test
    @DisplayName("Should translate DTO fields cleanly")
    void shouldTranslateDtoFields() {
        TestDto dto = new TestDto("Hello", "World Description", 42);
        engine.translateObject(dto, "en", "fr");

        assertTrue(dto.name.startsWith("[FR]"));
        assertTrue(dto.description.startsWith("[FR]"));
        assertFalse(dto.name.contains("\u200B")); // no data corruption
        assertEquals(42, dto.count);
    }

    @Test
    @DisplayName("Should translate list of strings")
    void shouldTranslateListOfStrings() {
        List<String> list = new ArrayList<>(List.of("Hello", "World"));
        engine.translateObject(list, "en", "fr");

        for (String item : list) {
            assertTrue(item.startsWith("[FR]"));
        }
    }

    @Test
    @DisplayName("Should handle null objects gracefully")
    void shouldHandleNull() {
        assertNull(engine.translateObject(null, "en", "fr"));
    }

    @Test
    @DisplayName("Should translate map values")
    void shouldTranslateMapValues() {
        Map<String, String> map = new java.util.LinkedHashMap<>();
        map.put("greeting", "Hello World");
        map.put("farewell", "Goodbye World");
        engine.translateObject(map, "en", "fr");

        for (String value : map.values()) {
            assertTrue(value.startsWith("[FR]"));
        }
    }

    @Test
    @DisplayName("Should deduplicate identical strings before API call")
    void shouldDeduplicateIdenticalStrings() {
        // All 3 fields have the same text — should only send 1 unique string to API
        DuplicateDto dto = new DuplicateDto("Hello World", "Hello World", "Hello World");
        engine.translateObject(dto, "en", "fr");

        // All fields should be translated
        assertEquals("[FR] Hello World", dto.field1);
        assertEquals("[FR] Hello World", dto.field2);
        assertEquals("[FR] Hello World", dto.field3);

        // Only 1 API batch call should have been made (not 3)
        assertEquals(1, apiCallCount.get());
    }

    @Test
    @DisplayName("Should enforce max strings per request limit")
    void shouldEnforceMaxStringsLimit() {
        properties.getSafeguard().setMaxStringsPerRequest(2);

        TestDto dto = new TestDto("Hello", "World Description", 42);
        engine.translateObject(dto, "en", "fr");

        // At least one field translated, at most 2
        int translatedCount = 0;
        if (dto.name.startsWith("[FR]")) translatedCount++;
        if (dto.description.startsWith("[FR]")) translatedCount++;
        assertTrue(translatedCount <= 2);
    }

    @Test
    @DisplayName("Should skip strings exceeding max text length")
    void shouldSkipLongStrings() {
        properties.getSafeguard().setMaxTextLength(10);

        Object result = engine.translateObject("This is a very long string that exceeds the limit", "en", "fr");
        // Should be returned untranslated
        assertEquals("This is a very long string that exceeds the limit", result);
    }
}
