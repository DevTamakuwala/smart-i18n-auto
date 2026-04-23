package in.devtamakuwala.smarti18nauto.cache;

import com.github.benmanes.caffeine.cache.Cache;
import in.devtamakuwala.smarti18nauto.util.SmartI18nLogger;

import java.util.function.Supplier;

/**
 * Translation cache backed by Caffeine.
 * <p>
 * Uses a composite key of {@code text_sourceLang_targetLang} (e.g., {@code hii_en_fr})
 * to avoid collisions between different language pairs.
 * </p>
 *
 * @author devtamakuwala
 * @since 0.0.1
 */
public class TranslationCache {

    private static final SmartI18nLogger log = SmartI18nLogger.getLogger(TranslationCache.class);

    private final Cache<String, String> cache;

    public TranslationCache(Cache<String, String> cache) {
        this.cache = cache;
    }

    /**
     * Returns the cached translation or computes it using the supplier.
     *
     * @param sourceLang source language
     * @param targetLang target language
     * @param text       original text
     * @param translator supplier that performs the actual translation
     * @return translated text (from cache or freshly translated)
     */
    public String getOrTranslate(String sourceLang, String targetLang, String text,
                                  Supplier<String> translator) {
        String key = buildKey(sourceLang, targetLang, text);
        String cached = cache.getIfPresent(key);
        if (cached != null) {
            log.debug("CACHE_HIT source={} target={} textLength={}",
                    sourceLang, targetLang, text != null ? text.length() : 0);
            return cached;
        }

        log.debug("CACHE_MISS source={} target={} textLength={} - invoking translation provider",
                sourceLang, targetLang, text != null ? text.length() : 0);

        String translated = translator.get();
        if (translated != null) {
            cache.put(key, translated);
            log.debug("CACHE_STORE source={} target={} textLength={}",
                    sourceLang, targetLang, text != null ? text.length() : 0);
        }
        return translated;
    }

    /**
     * Retrieves a cached translation, or null if not cached.
     *
     * @param sourceLang source language
     * @param targetLang target language
     * @param text       original text
     * @return cached translation, or null
     */
    public String get(String sourceLang, String targetLang, String text) {
        String cached = cache.getIfPresent(buildKey(sourceLang, targetLang, text));
        if (cached != null) {
            log.debug("CACHE_HIT source={} target={} textLength={}",
                    sourceLang, targetLang, text != null ? text.length() : 0);
        }
        return cached;
    }

    /**
     * Stores a translation in the cache.
     *
     * @param sourceLang source language
     * @param targetLang target language
     * @param text       original text
     * @param translated translated text
     */
    public void put(String sourceLang, String targetLang, String text, String translated) {
        cache.put(buildKey(sourceLang, targetLang, text), translated);
    }

    /**
     * Invalidates all cached translations.
     */
    public void invalidateAll() {
        cache.invalidateAll();
        log.info("Translation cache invalidated");
    }

    /**
     * Returns the approximate number of entries in the cache.
     *
     * @return cache size
     */
    public long size() {
        return cache.estimatedSize();
    }

    private String buildKey(String sourceLang, String targetLang, String text) {
        // Key format: text_sourceLang_targetLang (e.g., "hii_en_fr")
        // Uses full text — Caffeine handles internal hashing. Using hashCode() here
        // would cause silent data corruption on hash collisions.
        return text + "_" + sourceLang + "_" + targetLang;
    }
}

