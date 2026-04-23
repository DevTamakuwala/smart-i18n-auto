package in.devtamakuwala.smarti18nauto.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.devtamakuwala.smarti18nauto.config.SmartI18nProperties;
import in.devtamakuwala.smarti18nauto.util.SmartI18nLogger;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Translation provider using Google Gemini API.
 * <p>
 * Uses the Gemini generateContent endpoint with a structured prompt
 * to perform translations. Supports batch translation by including
 * multiple texts in a single prompt.
 * </p>
 *
 * @author devtamakuwala
 * @since 0.0.1
 */
public class GoogleGeminiTranslationProvider implements TranslationProvider {

    private static final SmartI18nLogger log = SmartI18nLogger.getLogger(GoogleGeminiTranslationProvider.class);
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta";

    private final SmartI18nProperties properties;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    /**
     * Private clean ObjectMapper used exclusively for serializing API request bodies.
     * The injected ObjectMapper from the consumer application may have custom modules,
     * mixins, or serialization features that cause Jackson tree nodes (ObjectNode, ArrayNode)
     * to be serialized with metadata getter properties (isArray, isBoolean, etc.),
     * resulting in 400 Bad Request from external APIs.
     */
    private static final ObjectMapper REQUEST_SERIALIZER = new ObjectMapper();

    public GoogleGeminiTranslationProvider(SmartI18nProperties properties,
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
        return "gemini";
    }

    @Override
    public boolean isAvailable() {
        return properties.getGemini().getApiKey() != null
                && !properties.getGemini().getApiKey().isBlank();
    }

    @Override
    public int getOrder() {
        return 20;
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

            List<String> results = new ArrayList<>();
            for (int i = 0; i < texts.size(); i += batchSize) {
                List<String> chunk = texts.subList(i, Math.min(i + batchSize, texts.size()));
                results.addAll(executeBatch(chunk, sourceLang, targetLang));
            }
            return results;
        } catch (Exception e) {
            log.error("Gemini translation batch failed: {}", e.getMessage(), e);
            return new ArrayList<>(texts);
        }
    }

    private List<String> executeBatch(List<String> texts, String sourceLang, String targetLang) {
        String apiKey = properties.getGemini().getApiKey();
        String model = properties.getGemini().getModel();
        Duration timeout = Duration.ofMillis(properties.getProvider().getTimeoutMs());

        log.debug("API_HTTP_CALL provider=gemini source={} target={} items={}",
                sourceLang, targetLang, texts.size());

        String prompt = buildPrompt(texts, sourceLang, targetLang);

        // Build request body using plain Maps/Lists to avoid Jackson ObjectNode metadata
        // serialization issues with the consumer's ObjectMapper
        Map<String, Object> partMap = Map.of("text", prompt);
        Map<String, Object> contentMap = Map.of("parts", List.of(partMap));
        Map<String, Object> requestBody = Map.of("contents", List.of(contentMap));

        // Use the clean private ObjectMapper for serialization to guarantee correct JSON
        String jsonBody;
        try {
            jsonBody = REQUEST_SERIALIZER.writeValueAsString(requestBody);
        } catch (Exception e) {
            log.error("Failed to serialize Gemini request body", e);
            return new ArrayList<>(texts);
        }

        log.debug("Gemini request: model={}, texts={}, prompt length={}", model, texts.size(), prompt.length());

        String responseBody = webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/models/" + model + ":generateContent")
                        .queryParam("key", apiKey)
                        .build())
                .header("Content-Type", "application/json")
                .bodyValue(jsonBody)
                .retrieve()
                .onStatus(status -> status.isError(), response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("[no body]")
                                .flatMap(errorBody -> {
                                    log.error("Gemini API error {}: {}", response.statusCode(), errorBody);
                                    return reactor.core.publisher.Mono.error(
                                            new RuntimeException("Gemini API " + response.statusCode() + ": " + errorBody));
                                })
                )
                .bodyToMono(String.class)
                .block(timeout);

        return parseResponse(responseBody, texts);
    }

    private String buildPrompt(List<String> texts, String sourceLang, String targetLang) {
        StringBuilder sb = new StringBuilder();
        sb.append("Translate the following texts from ").append(sourceLang)
                .append(" to ").append(targetLang).append(".\n");
        sb.append("Return ONLY a JSON array of translated strings in the same order.\n");
        sb.append("Do not add any explanation or formatting. Just the JSON array.\n\n");
        sb.append("Input texts:\n");

        for (int i = 0; i < texts.size(); i++) {
            sb.append(i + 1).append(". ").append(texts.get(i)).append("\n");
        }

        return sb.toString();
    }

    private List<String> parseResponse(String responseBody, List<String> originals) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode candidates = root.path("candidates");

            if (!candidates.isArray() || candidates.isEmpty()) {
                log.warn("Gemini response has no candidates");
                return new ArrayList<>(originals);
            }

            String text = candidates.get(0)
                    .path("content")
                    .path("parts")
                    .get(0)
                    .path("text")
                    .asText();

            // Clean up the response - remove markdown code blocks if present
            text = text.strip();
            if (text.startsWith("```json")) {
                text = text.substring(7);
            } else if (text.startsWith("```")) {
                text = text.substring(3);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
            text = text.strip();

            JsonNode array = objectMapper.readTree(text);
            if (!array.isArray() || array.size() != originals.size()) {
                log.warn("Gemini response array size mismatch. Expected: {}, Got: {}",
                        originals.size(), array.isArray() ? array.size() : -1);
                return new ArrayList<>(originals);
            }

            List<String> result = new ArrayList<>();
            for (JsonNode node : array) {
                result.add(node.asText());
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}", e.getMessage(), e);
            return new ArrayList<>(originals);
        }
    }
}
