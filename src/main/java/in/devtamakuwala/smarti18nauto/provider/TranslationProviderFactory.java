package in.devtamakuwala.smarti18nauto.provider;

import in.devtamakuwala.smarti18nauto.config.SmartI18nProperties;
import in.devtamakuwala.smarti18nauto.util.SmartI18nLogger;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Factory that manages translation providers and implements fallback chain logic.
 * <p>
 * Resolves the active provider based on:
 * <ol>
 *   <li>Explicit provider name from properties ({@code smart.i18n.provider.active})</li>
 *   <li>Auto-detection based on which providers have API keys configured</li>
 * </ol>
 * When hybrid/fallback mode is enabled, it tries providers in priority order
 * until one succeeds.
 *
 *
 * @author devtamakuwala
 * @since 0.0.1
 */
public class TranslationProviderFactory {

    private static final SmartI18nLogger log = SmartI18nLogger.getLogger(TranslationProviderFactory.class);

    private final List<TranslationProvider> providers;
    private final SmartI18nProperties properties;

    public TranslationProviderFactory(List<TranslationProvider> providers,
                                       SmartI18nProperties properties) {
        this.providers = providers.stream()
                .sorted(Comparator.comparingInt(TranslationProvider::getOrder))
                .toList();
        this.properties = properties;
    }

    /**
     * Returns the primary translation provider.
     * <p>
     * If an explicit provider is configured, returns that provider.
     * Otherwise, returns the first available provider by priority order.
     * </p>
     *
     * @return the primary translation provider
     * @throws IllegalStateException if no provider is available
     */
    public TranslationProvider getPrimaryProvider() {
        String active = properties.getProvider().getActive();

        if (active != null && !active.isBlank()) {
            return providers.stream()
                    .filter(p -> p.getName().equalsIgnoreCase(active))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Configured provider '" + active + "' not found. Available: " +
                                    providers.stream().map(TranslationProvider::getName).toList()));
        }

        return providers.stream()
                .filter(TranslationProvider::isAvailable)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No translation provider is available. " +
                                "Configure at least one API key in smart.i18n.* properties."));
    }

    /**
     * Translates text using the primary provider, falling back to other providers on failure
     * if fallback mode is enabled.
     *
     * @param text       the text to translate
     * @param sourceLang source language code
     * @param targetLang target language code
     * @return translated text
     */
    public String translateWithFallback(String text, String sourceLang, String targetLang) {
        return translateBatchWithFallback(List.of(text), sourceLang, targetLang).getFirst();
    }

    /**
     * Translates a batch of texts using the primary provider, falling back to other providers
     * on failure if fallback mode is enabled.
     *
     * @param texts      the texts to translate
     * @param sourceLang source language code
     * @param targetLang target language code
     * @return translated texts in the same order
     */
    public List<String> translateBatchWithFallback(List<String> texts, String sourceLang, String targetLang) {
        boolean fallbackEnabled = properties.getProvider().isFallbackEnabled();

        if (!fallbackEnabled) {
            TranslationProvider primary = getPrimaryProvider();
            log.debug("API_INVOKE provider={} mode=primary-only source={} target={} items={}",
                    primary.getName(), sourceLang, targetLang, texts.size());
            List<String> result = primary.translateBatch(texts, sourceLang, targetLang);
            log.debug("API_SUCCESS provider={} items={}", primary.getName(), result != null ? result.size() : 0);
            return result;
        }

        // Try each available provider in order
        List<TranslationProvider> available = providers.stream()
                .filter(TranslationProvider::isAvailable)
                .toList();

        // Put the explicitly configured one first if specified
        String active = properties.getProvider().getActive();
        List<TranslationProvider> ordered;
        if (active != null && !active.isBlank()) {
            Optional<TranslationProvider> primary = available.stream()
                    .filter(p -> p.getName().equalsIgnoreCase(active))
                    .findFirst();
            if (primary.isPresent()) {
                ordered = new java.util.ArrayList<>();
                ordered.add(primary.get());
                available.stream()
                        .filter(p -> !p.getName().equalsIgnoreCase(active))
                        .forEach(ordered::add);
            } else {
                ordered = available;
            }
        } else {
            ordered = available;
        }

        for (TranslationProvider provider : ordered) {
            try {
                log.debug("API_INVOKE provider={} mode=fallback source={} target={} items={}",
                        provider.getName(), sourceLang, targetLang, texts.size());
                List<String> result = provider.translateBatch(texts, sourceLang, targetLang);
                if (result != null && result.size() == texts.size()) {
                    log.debug("API_SUCCESS provider={} items={}", provider.getName(), result.size());
                    return result;
                }
            } catch (Exception e) {
                log.warn("Provider '{}' failed, trying next: {}", provider.getName(), e.getMessage());
            }
        }

        log.error("All translation providers failed. Returning original texts.");
        return new java.util.ArrayList<>(texts);
    }
}

