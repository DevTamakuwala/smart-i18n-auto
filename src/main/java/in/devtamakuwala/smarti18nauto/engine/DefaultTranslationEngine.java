package in.devtamakuwala.smarti18nauto.engine;

import in.devtamakuwala.smarti18nauto.cache.TranslationCache;
import in.devtamakuwala.smarti18nauto.config.SmartI18nProperties;
import in.devtamakuwala.smarti18nauto.filter.ContentFilter;
import in.devtamakuwala.smarti18nauto.provider.TranslationProviderFactory;
import in.devtamakuwala.smarti18nauto.traversal.ObjectTraverser;
import in.devtamakuwala.smarti18nauto.traversal.StringReference;
import in.devtamakuwala.smarti18nauto.util.SmartI18nLogger;
import in.devtamakuwala.smarti18nauto.util.TranslationMarker;

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
 *
 * <p><strong>Thread Safety</strong></p>
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

    private static final SmartI18nLogger log = SmartI18nLogger.getLogger(DefaultTranslationEngine.class);

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

        // Handle plain String directly — return new translated string
        if (body instanceof String str) {
            return translateString(str, sourceLang, targetLang);
        }

        // Skip primitive wrappers, numbers, booleans — nothing to translate
        if (body instanceof Number || body instanceof Boolean || body instanceof Character) {
            return body;
        }

        // Handle List at the top level (including List<String>, List<DTO>, etc.)
        if (body instanceof List<?> list) {
            return translateList(list, sourceLang, targetLang);
        }

        // Handle Map at the top level (including Map<String,String>, Map<String,Object>, etc.)
        if (body instanceof Map<?, ?> map) {
            return translateMap(map, sourceLang, targetLang);
        }

        try {
            TranslationMarker.beginScope();

            // Prevent double translation within the same request scope
            // (checked AFTER beginScope so stale state from previous requests is cleared)
            if (TranslationMarker.isAlreadyTranslated(body)) {
                log.debug("Object already translated in this scope, skipping");
                return body;
            }

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
                int cacheHits = uniqueTextToIndices.size() - uncachedTexts.size();
                log.debug("CACHE_SUMMARY source={} target={} unique={} hits={} misses={} -> calling provider API",
                        sourceLang, targetLang, uniqueTextToIndices.size(), cacheHits, uncachedTexts.size());
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
                log.debug("CACHE_SUMMARY source={} target={} unique={} hits={} misses=0 -> no API call",
                        sourceLang, targetLang, uniqueTextToIndices.size(), uniqueTextToIndices.size());
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

    /**
     * Translates a top-level List body. Handles lists of Strings directly
     * via batch translation, and recursively translates non-String elements.
     *
     * @param list       the list to translate
     * @param sourceLang source language
     * @param targetLang target language
     * @return the translated list (same instance, mutated in-place)
     */
    @SuppressWarnings("unchecked")
    private Object translateList(List<?> list, String sourceLang, String targetLang) {
        if (list.isEmpty()) {
            return list;
        }

        List<Object> mutableList = (List<Object>) list;

        // Check if it's a simple List<String> — translate efficiently via batch
        boolean allStrings = list.stream().allMatch(e -> e == null || e instanceof String);
        if (allStrings) {
            // Collect translatable string indices and texts
            List<Integer> translatableIndices = new ArrayList<>();
            List<String> translatableTexts = new ArrayList<>();
            for (int i = 0; i < mutableList.size(); i++) {
                Object elem = mutableList.get(i);
                if (elem instanceof String str && contentFilter.isTranslatable(str)
                        && str.length() <= properties.getSafeguard().getMaxTextLength()) {
                    translatableIndices.add(i);
                    translatableTexts.add(str);
                }
            }

            if (translatableTexts.isEmpty()) {
                return list;
            }

            // Batch translate with caching
            translateAndWriteBackStrings(translatableTexts, translatableIndices,
                    mutableList, sourceLang, targetLang);
            return list;
        }

        // Mixed list — recursively translate each element
        for (int i = 0; i < mutableList.size(); i++) {
            Object elem = mutableList.get(i);
            if (elem == null) continue;

            if (elem instanceof String str) {
                String translated = (String) translateObject(str, sourceLang, targetLang);
                mutableList.set(i, translated);
            } else {
                // Recursively translate nested objects (DTO, Map, List, etc.)
                translateObject(elem, sourceLang, targetLang);
            }
        }
        return list;
    }

    /**
     * Translates a top-level Map body. Translates String values in the map
     * and recursively translates non-String values.
     *
     * @param map        the map to translate
     * @param sourceLang source language
     * @param targetLang target language
     * @return the translated map (same instance, mutated in-place)
     */
    @SuppressWarnings("unchecked")
    private Object translateMap(Map<?, ?> map, String sourceLang, String targetLang) {
        if (map.isEmpty()) {
            return map;
        }

        Map<Object, Object> mutableMap = (Map<Object, Object>) map;

        // Collect translatable string entries
        List<Object> stringKeys = new ArrayList<>();
        List<String> stringValues = new ArrayList<>();
        List<Map.Entry<Object, Object>> nonStringEntries = new ArrayList<>();

        for (Map.Entry<Object, Object> entry : mutableMap.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String str) {
                if (contentFilter.isTranslatable(str)
                        && str.length() <= properties.getSafeguard().getMaxTextLength()) {
                    stringKeys.add(entry.getKey());
                    stringValues.add(str);
                }
            } else if (value != null) {
                nonStringEntries.add(entry);
            }
        }

        // Batch translate string values
        if (!stringValues.isEmpty()) {
            translateAndWriteBackMapStrings(stringValues, stringKeys, mutableMap, sourceLang, targetLang);
        }

        // Recursively translate non-string values
        for (Map.Entry<Object, Object> entry : nonStringEntries) {
            Object value = entry.getValue();
             if (value instanceof String) {
                // Already handled above
                continue;
            }
            Object translated = translateObject(value, sourceLang, targetLang);
            if (translated != value) {
                mutableMap.put(entry.getKey(), translated);
            }
        }

        return map;
    }

    /**
     * Batch translates a list of strings, uses cache, and writes results back to a List.
     */
    private void translateAndWriteBackStrings(List<String> texts, List<Integer> indices,
                                               List<Object> targetList,
                                               String sourceLang, String targetLang) {
        // Deduplicate
        LinkedHashMap<String, List<Integer>> uniqueToPositions = new LinkedHashMap<>();
        for (int i = 0; i < texts.size(); i++) {
            uniqueToPositions.computeIfAbsent(texts.get(i), k -> new ArrayList<>()).add(i);
        }

        Map<String, String> resolved = new HashMap<>();
        List<String> uncached = new ArrayList<>();

        for (String text : uniqueToPositions.keySet()) {
            String cached = translationCache.get(sourceLang, targetLang, text);
            if (cached != null) {
                resolved.put(text, cached);
            } else {
                uncached.add(text);
            }
        }

        if (!uncached.isEmpty()) {
            int cacheHits = uniqueToPositions.size() - uncached.size();
            log.debug("CACHE_SUMMARY scope=list source={} target={} unique={} hits={} misses={} -> calling provider API",
                    sourceLang, targetLang, uniqueToPositions.size(), cacheHits, uncached.size());
            List<String> translated = providerFactory.translateBatchWithFallback(uncached, sourceLang, targetLang);
            for (int i = 0; i < uncached.size(); i++) {
                translationCache.put(sourceLang, targetLang, uncached.get(i), translated.get(i));
                resolved.put(uncached.get(i), translated.get(i));
            }
        } else {
            log.debug("CACHE_SUMMARY scope=list source={} target={} unique={} hits={} misses=0 -> no API call",
                    sourceLang, targetLang, uniqueToPositions.size(), uniqueToPositions.size());
        }

        // Write back
        for (Map.Entry<String, List<Integer>> entry : uniqueToPositions.entrySet()) {
            String translatedText = resolved.get(entry.getKey());
            if (translatedText != null) {
                for (int pos : entry.getValue()) {
                    targetList.set(indices.get(pos), translatedText);
                }
            }
        }
    }

    /**
     * Batch translates a list of strings, uses cache, and writes results back to a Map.
     */
    private void translateAndWriteBackMapStrings(List<String> texts, List<Object> keys,
                                                  Map<Object, Object> targetMap,
                                                  String sourceLang, String targetLang) {
        // Deduplicate
        LinkedHashMap<String, List<Integer>> uniqueToPositions = new LinkedHashMap<>();
        for (int i = 0; i < texts.size(); i++) {
            uniqueToPositions.computeIfAbsent(texts.get(i), k -> new ArrayList<>()).add(i);
        }

        Map<String, String> resolved = new HashMap<>();
        List<String> uncached = new ArrayList<>();

        for (String text : uniqueToPositions.keySet()) {
            String cached = translationCache.get(sourceLang, targetLang, text);
            if (cached != null) {
                resolved.put(text, cached);
            } else {
                uncached.add(text);
            }
        }

        if (!uncached.isEmpty()) {
            int cacheHits = uniqueToPositions.size() - uncached.size();
            log.debug("CACHE_SUMMARY scope=map source={} target={} unique={} hits={} misses={} -> calling provider API",
                    sourceLang, targetLang, uniqueToPositions.size(), cacheHits, uncached.size());
            List<String> translated = providerFactory.translateBatchWithFallback(uncached, sourceLang, targetLang);
            for (int i = 0; i < uncached.size(); i++) {
                translationCache.put(sourceLang, targetLang, uncached.get(i), translated.get(i));
                resolved.put(uncached.get(i), translated.get(i));
            }
        } else {
            log.debug("CACHE_SUMMARY scope=map source={} target={} unique={} hits={} misses=0 -> no API call",
                    sourceLang, targetLang, uniqueToPositions.size(), uniqueToPositions.size());
        }

        // Write back
        for (Map.Entry<String, List<Integer>> entry : uniqueToPositions.entrySet()) {
            String translatedText = resolved.get(entry.getKey());
            if (translatedText != null) {
                for (int pos : entry.getValue()) {
                    targetMap.put(keys.get(pos), translatedText);
                }
            }
        }
    }
}

