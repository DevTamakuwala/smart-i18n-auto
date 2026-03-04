package in.devtamakuwala.smarti18nauto.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.devtamakuwala.smarti18nauto.config.SmartI18nProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Translation provider using Google Cloud Translation API v2 (REST).
 * <p>
 * Supports native batch translation via the API's {@code q} parameter array.
 * </p>
 *
 * @author devtamakuwala
 * @since 0.0.1
 */
public class GoogleCloudTranslationProvider implements TranslationProvider {

    private static final Logger log = LoggerFactory.getLogger(GoogleCloudTranslationProvider.class);
    private static final String API_URL = "https://translation.googleapis.com/language/translate/v2";

    private final SmartI18nProperties properties;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    /**
     * Private clean ObjectMapper used exclusively for serializing API request bodies.
     */
    private static final ObjectMapper REQUEST_SERIALIZER = new ObjectMapper();

    public GoogleCloudTranslationProvider(SmartI18nProperties properties,
                                          WebClient.Builder webClientBuilder,
                                          ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webClient = webClientBuilder
                .baseUrl(API_URL)
                .build();
    }

    @Override
    public String getName() {
        return "google-cloud";
    }

    @Override
    public boolean isAvailable() {
        return properties.getGoogleCloud().getApiKey() != null
                && !properties.getGoogleCloud().getApiKey().isBlank();
    }

    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    public String translate(String text, String sourceLang, String targetLang) {
        List<String> result = translateBatch(List.of(text), sourceLang, targetLang);
        return result.isEmpty() ? text : result.getFirst();
    }

    @Override
    public List<String> translateBatch(List<String> texts, String sourceLang, String targetLang) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        try {
            int batchSize = properties.getBatch().getMaxSize();
            if (texts.size() <= batchSize) {
                return executeBatch(texts, sourceLang, targetLang);
            }

            // Chunk into smaller batches
            List<String> results = new ArrayList<>();
            for (int i = 0; i < texts.size(); i += batchSize) {
                List<String> chunk = texts.subList(i, Math.min(i + batchSize, texts.size()));
                results.addAll(executeBatch(chunk, sourceLang, targetLang));
            }
            return results;
        } catch (Exception e) {
            log.error("Google Cloud Translation batch failed: {}", e.getMessage(), e);
            return new ArrayList<>(texts); // fallback to original
        }
    }

    private List<String> executeBatch(List<String> texts, String sourceLang, String targetLang) {
        String apiKey = properties.getGoogleCloud().getApiKey();
        Duration timeout = Duration.ofMillis(properties.getProvider().getTimeoutMs());

        // Build JSON POST body
        Map<String, Object> requestBody = new java.util.LinkedHashMap<>();
        requestBody.put("q", texts);
        requestBody.put("source", sourceLang);
        requestBody.put("target", targetLang);
        requestBody.put("format", "text");

        // Serialize to JSON string ourselves to avoid WebClient using the consumer app's
        // ObjectMapper, which may serialize objects with unexpected extra fields
        String jsonBody;
        try {
            jsonBody = REQUEST_SERIALIZER.writeValueAsString(requestBody);
        } catch (Exception e) {
            log.error("Failed to serialize Google Cloud Translation request body", e);
            return new ArrayList<>(texts);
        }

        String responseBody = webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("key", apiKey)
                        .build())
                .header("Content-Type", "application/json")
                .bodyValue(jsonBody)
                .retrieve()
                .bodyToMono(String.class)
                .block(timeout);

        return parseTranslationResponse(responseBody, texts);
    }

    private List<String> parseTranslationResponse(String responseBody, List<String> originals) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode translations = root.path("data").path("translations");

            if (!translations.isArray()) {
                log.warn("Unexpected Google Cloud Translation response structure");
                return new ArrayList<>(originals);
            }

            List<String> result = new ArrayList<>();
            for (JsonNode node : translations) {
                result.add(node.path("translatedText").asText());
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to parse Google Cloud Translation response: {}", e.getMessage(), e);
            return new ArrayList<>(originals);
        }
    }
}
