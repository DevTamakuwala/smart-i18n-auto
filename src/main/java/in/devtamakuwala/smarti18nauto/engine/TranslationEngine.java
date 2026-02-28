package in.devtamakuwala.smarti18nauto.engine;

/**
 * Core interface for the translation engine.
 * <p>
 * Orchestrates object traversal, content filtering, caching,
 * and provider invocation to translate entire object graphs.
 * </p>
 *
 * @author devtamakuwala
 * @since 0.0.1
 */
public interface TranslationEngine {

    /**
     * Translates all translatable string values within the given object.
     * <p>
     * The object is traversed recursively. String fields that pass content filtering
     * are collected, batch-translated via the configured provider, and written back
     * to their original locations.
     * </p>
     *
     * @param body       the object to translate (can be a String, DTO, List, Map, etc.)
     * @param sourceLang BCP-47 source language code
     * @param targetLang BCP-47 target language code
     * @return the translated object (same instance, mutated in place for DTOs; new String for plain strings)
     */
    Object translateObject(Object body, String sourceLang, String targetLang);
}

