package in.devtamakuwala.smarti18nauto.engine;

import in.devtamakuwala.smarti18nauto.cache.TranslationCache;
import in.devtamakuwala.smarti18nauto.config.SmartI18nProperties;
import in.devtamakuwala.smarti18nauto.filter.ContentFilter;
import in.devtamakuwala.smarti18nauto.provider.TranslationProviderFactory;
import in.devtamakuwala.smarti18nauto.traversal.ObjectTraverser;
import in.devtamakuwala.smarti18nauto.traversal.StringReference;
import in.devtamakuwala.smarti18nauto.util.TranslationMarker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Default implementation of {@link TranslationEngine}.
 * <p>
 * Pipeline:
 * <ol>
 *   <li>Check if already translated in this request scope (prevents double translation)</li>
 *   <li>Traverse the object graph to collect all translatable string references</li>
 *   <li>Enforce per-request safety limits (max strings, max text length)</li>
 *   <li>Deduplicate identical strings before sending to provider</li>
 *   <li>Check the cache for each unique string</li>
 *   <li>Batch-translate uncached strings via the provider</li>
 *   <li>Store results in cache</li>
 *   <li>Fan results back to all references (including duplicates)</li>
 *   <li>Mark the root object as translated in the current scope</li>
 * </ol>
 * </p>
 *
 * <h3>Thread Safety</h3>
 * <p>
 * This engine mutates the body object in-place via reflection. Callers (interceptors,
 * AOP aspects) must ensure they do not pass shared/cached DTO instances. The
 * {@code ResponseBodyAdvice} and AOP aspect in this library operate on per-request
 * return values, which are safe.
 * </p>
 *
 * @author devtamakuwala
 * @since 0.0.2
 */
public class DefaultTranslationEngine implements TranslationEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultTranslationEngine.class);

    private final TranslationProviderFactory providerFactory;
    private final TranslationCache translationCache;
    private final ObjectTraverser objectTraverser;
    private final ContentFilter contentFilter;
    private final SmartI18nProperties properties;

    public DefaultTranslationEngine(TranslationProviderFactory providerFactory,
                                     TranslationCache translationCache,
                                     ObjectTraverser objectTraverser,
                                     ContentFilter contentFilter,
                                     SmartI18nProperties properties) {
        this.providerFactory = providerFactory;
        this.translationCache = translationCache;
        this.objectTraverser = objectTraverser;
        this.contentFilter = contentFilter;
        this.properties = properties;
    }

    /**
     * Backward-compatible constructor for existing tests (no properties = default safeguards).
     */
    public DefaultTranslationEngine(TranslationProviderFactory providerFactory,
                                     TranslationCache translationCache,
                                     ObjectTraverser objectTraverser,
                                     ContentFilter contentFilter) {
        this(providerFactory, translationCache, objectTraverser, contentFilter, new SmartI18nProperties());
    }

    @Override
    public Object translateObject(Object body, String sourceLang, String targetLang) {
        if (body == null) {
            return null;
        }

        // Skip if source and target are the same
        if (sourceLang.equalsIgnoreCase(targetLang)) {
            log.debug("Source and target languages are the same ({}), skipping translation", sourceLang);
            return body;
        }

        // Prevent double translation within the same request scope
        if (TranslationMarker.isAlreadyTranslated(body)) {
            log.debug("Object already translated in this scope, skipping");
            return body;
        }

        // Handle plain String
        if (body instanceof String str) {
            return translateString(str, sourceLang, targetLang);
        }

        try {
            TranslationMarker.beginScope();

            // Collect all translatable string references
            List<StringReference> references = objectTraverser.collectStrings(body);

            if (references.isEmpty()) {
                log.debug("No translatable strings found in object of type: {}", body.getClass().getSimpleName());
                return body;
            }

            // --- Cost protection: enforce per-request string limit ---
            int maxStrings = properties.getSafeguard().getMaxStringsPerRequest();
            if (references.size() > maxStrings) {
                log.warn("Translation string count ({}) exceeds max per request ({}). Truncating.",
                        references.size(), maxStrings);
                references = references.subList(0, maxStrings);
            }

            int maxTextLen = properties.getSafeguard().getMaxTextLength();

            log.debug("Found {} translatable strings in {}", references.size(), body.getClass().getSimpleName());

            // --- Deduplicate identical strings ---
            // Map: original text -> list of StringReference indices pointing to it
            LinkedHashMap<String, List<Integer>> uniqueTextToIndices = new LinkedHashMap<>();
            for (int i = 0; i < references.size(); i++) {
                String text = references.get(i).getOriginalValue();
                // Skip texts exceeding max length
                if (text.length() > maxTextLen) {
                    log.debug("Skipping text exceeding max length ({} > {})", text.length(), maxTextLen);
                    continue;
                }
                uniqueTextToIndices.computeIfAbsent(text, k -> new ArrayList<>()).add(i);
            }

            // Check cache for each unique text; collect uncached
            Map<String, String> resolvedTranslations = new HashMap<>();
            List<String> uncachedTexts = new ArrayList<>();

            for (String text : uniqueTextToIndices.keySet()) {
                String cached = translationCache.get(sourceLang, targetLang, text);
                if (cached != null) {
                    resolvedTranslations.put(text, cached);
                } else {
                    uncachedTexts.add(text);
                }
            }

            if (!uncachedTexts.isEmpty()) {
                log.debug("Translating {} unique uncached strings (out of {} unique, {} total refs)",
                        uncachedTexts.size(), uniqueTextToIndices.size(), references.size());

                // Batch translate uncached strings
                List<String> translated = providerFactory.translateBatchWithFallback(
                        uncachedTexts, sourceLang, targetLang);

                // Store in cache and resolved map
                for (int i = 0; i < uncachedTexts.size(); i++) {
                    String original = uncachedTexts.get(i);
                    String translatedText = translated.get(i);
                    translationCache.put(sourceLang, targetLang, original, translatedText);
                    resolvedTranslations.put(original, translatedText);
                }
            } else {
                log.debug("All {} unique strings were cache hits", uniqueTextToIndices.size());
            }

            // Write back all translations (fanning out deduplicated results)
            for (Map.Entry<String, List<Integer>> entry : uniqueTextToIndices.entrySet()) {
                String translatedText = resolvedTranslations.get(entry.getKey());
                if (translatedText != null) {
                    for (int refIndex : entry.getValue()) {
                        references.get(refIndex).writeBack(translatedText);
                    }
                }
            }

            // Mark root object as translated in this scope
            TranslationMarker.markTranslated(body);

            return body;
        } catch (Exception e) {
            log.error("Translation failed for object of type {}: {}",
                    body.getClass().getSimpleName(), e.getMessage(), e);
            return body; // return original on failure
        } finally {
            TranslationMarker.endScope();
        }
    }

    /**
     * Translates a plain string value.
     */
    private String translateString(String text, String sourceLang, String targetLang) {
        if (!contentFilter.isTranslatable(text)) {
            return text;
        }

        int maxTextLen = properties.getSafeguard().getMaxTextLength();
        if (text.length() > maxTextLen) {
            log.debug("Plain string exceeds max length ({} > {}), skipping", text.length(), maxTextLen);
            return text;
        }

        return translationCache.getOrTranslate(
                sourceLang, targetLang, text,
                () -> providerFactory.translateWithFallback(text, sourceLang, targetLang)
        );
    }
}

