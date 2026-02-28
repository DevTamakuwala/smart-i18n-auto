package in.devtamakuwala.smarti18nauto.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TranslationCache}.
 */
class TranslationCacheTest {

    private TranslationCache translationCache;

    @BeforeEach
    void setUp() {
        Cache<String, String> caffeineCache = Caffeine.newBuilder()
                .expireAfterWrite(60, TimeUnit.MINUTES)
                .maximumSize(1000)
                .build();
        translationCache = new TranslationCache(caffeineCache);
    }

    @Test
    @DisplayName("Should cache and retrieve translations")
    void shouldCacheAndRetrieveTranslations() {
        translationCache.put("en", "fr", "Hello", "Bonjour");

        String cached = translationCache.get("en", "fr", "Hello");
        assertEquals("Bonjour", cached);
    }

    @Test
    @DisplayName("Should return null for uncached translations")
    void shouldReturnNullForUncached() {
        assertNull(translationCache.get("en", "fr", "Unknown"));
    }

    @Test
    @DisplayName("Should use getOrTranslate with supplier for cache miss")
    void shouldUseSupplierForCacheMiss() {
        AtomicInteger callCount = new AtomicInteger(0);

        String result1 = translationCache.getOrTranslate("en", "fr", "Hello", () -> {
            callCount.incrementAndGet();
            return "Bonjour";
        });

        String result2 = translationCache.getOrTranslate("en", "fr", "Hello", () -> {
            callCount.incrementAndGet();
            return "Bonjour";
        });

        assertEquals("Bonjour", result1);
        assertEquals("Bonjour", result2);
        assertEquals(1, callCount.get()); // supplier called only once
    }

    @Test
    @DisplayName("Should keep different language pairs separate")
    void shouldKeepLanguagePairsSeparate() {
        translationCache.put("en", "fr", "Hello", "Bonjour");
        translationCache.put("en", "de", "Hello", "Hallo");

        assertEquals("Bonjour", translationCache.get("en", "fr", "Hello"));
        assertEquals("Hallo", translationCache.get("en", "de", "Hello"));
    }

    @Test
    @DisplayName("Should invalidate all entries")
    void shouldInvalidateAll() {
        translationCache.put("en", "fr", "Hello", "Bonjour");
        translationCache.put("en", "de", "Hello", "Hallo");

        translationCache.invalidateAll();

        assertNull(translationCache.get("en", "fr", "Hello"));
        assertNull(translationCache.get("en", "de", "Hello"));
    }

    @Test
    @DisplayName("Should report approximate size")
    void shouldReportSize() {
        assertEquals(0, translationCache.size());
        translationCache.put("en", "fr", "Hello", "Bonjour");
        assertTrue(translationCache.size() >= 1);
    }
}

