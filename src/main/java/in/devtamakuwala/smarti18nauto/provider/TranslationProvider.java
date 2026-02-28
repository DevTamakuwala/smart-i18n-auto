package in.devtamakuwala.smarti18nauto.provider;

import java.util.List;

/**
 * Strategy interface for translation providers.
 * <p>
 * Each implementation encapsulates a specific translation API
 * (Google Cloud Translation, Google Gemini, OpenAI, etc.).
 * </p>
 *
 * @author devtamakuwala
 * @since 0.0.1
 */
public interface TranslationProvider {

    /**
     * Returns the unique name of this provider (e.g., "google-cloud", "gemini", "openai").
     *
     * @return provider name
     */
    String getName();

    /**
     * Translates a single text from source language to target language.
     *
     * @param text       the text to translate
     * @param sourceLang BCP-47 source language code (e.g., "en")
     * @param targetLang BCP-47 target language code (e.g., "fr")
     * @return translated text
     */
    String translate(String text, String sourceLang, String targetLang);

    /**
     * Translates a batch of texts from source language to target language.
     * <p>
     * Default implementation falls back to sequential single translations.
     * Providers that support batch APIs should override this for efficiency.
     * </p>
     *
     * @param texts      list of texts to translate
     * @param sourceLang BCP-47 source language code
     * @param targetLang BCP-47 target language code
     * @return list of translated texts in the same order
     */
    default List<String> translateBatch(List<String> texts, String sourceLang, String targetLang) {
        return texts.stream()
                .map(text -> translate(text, sourceLang, targetLang))
                .toList();
    }

    /**
     * Checks whether this provider is available (e.g., API key is configured).
     *
     * @return true if the provider can be used
     */
    boolean isAvailable();

    /**
     * Returns the priority order for auto-detection. Lower values = higher priority.
     *
     * @return priority order
     */
    default int getOrder() {
        return 100;
    }
}

