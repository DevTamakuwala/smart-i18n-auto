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
 * Translation provider using OpenAI Chat Completion API.
 * <p>
 * Sends translation requests as structured chat messages. Supports
 * batch translation by including multiple texts in a single prompt.
 * </p>
 *
 * @author devtamakuwala
 * @since 0.0.1
 */
public class OpenAiTranslationProvider implements TranslationProvider {

    private static final SmartI18nLogger log = SmartI18nLogger.getLogger(OpenAiTranslationProvider.class);
    private static final String API_URL = "https://api.openai.com/v1";

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

    public OpenAiTranslationProvider(SmartI18nProperties properties,
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
        return "openai";
    }

    @Override
    public boolean isAvailable() {
        return properties.getOpenai().getApiKey() != null
                && !properties.getOpenai().getApiKey().isBlank();
    }

    @Override
    public int getOrder() {
        return 30;
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
            log.error("OpenAI translation batch failed: {}", e.getMessage(), e);
            return new ArrayList<>(texts);
        }
    }

    private List<String> executeBatch(List<String> texts, String sourceLang, String targetLang) {
        String apiKey = properties.getOpenai().getApiKey();
        String model = properties.getOpenai().getModel();
        Duration timeout = Duration.ofMillis(properties.getProvider().getTimeoutMs());

        log.debug("API_HTTP_CALL provider=openai source={} target={} items={}",
                sourceLang, targetLang, texts.size());

        String systemPrompt = String.format(
                "You are a professional translator. Translate texts from %s to %s. " +
                "Return ONLY a JSON array of translated strings in the same order. " +
                "Do not add any explanation, formatting, or code blocks. Just the raw JSON array.",
                sourceLang, targetLang
        );

        StringBuilder userPrompt = new StringBuilder("Translate these texts:\n");
        for (int i = 0; i < texts.size(); i++) {
            userPrompt.append(i + 1).append(". ").append(texts.get(i)).append("\n");
        }

        // Build request body using plain Maps/Lists to avoid Jackson ObjectNode metadata
        // serialization issues with the consumer's ObjectMapper
        Map<String, Object> systemMsg = new java.util.LinkedHashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt);

        Map<String, Object> userMsg = new java.util.LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt.toString());

        Map<String, Object> requestBody = new java.util.LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", List.of(systemMsg, userMsg));
        requestBody.put("temperature", 0.1);

        // Use the clean private ObjectMapper for serialization to guarantee correct JSON
        String jsonBody;
        try {
            jsonBody = REQUEST_SERIALIZER.writeValueAsString(requestBody);
        } catch (Exception e) {
            log.error("Failed to serialize OpenAI request body", e);
            return new ArrayList<>(texts);
        }

        String responseBody = webClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(jsonBody)
                .retrieve()
                .bodyToMono(String.class)
                .block(timeout);

        return parseResponse(responseBody, texts);
    }

    private List<String> parseResponse(String responseBody, List<String> originals) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");

            if (!choices.isArray() || choices.isEmpty()) {
                log.warn("OpenAI response has no choices");
                return new ArrayList<>(originals);
            }

            String content = choices.get(0)
                    .path("message")
                    .path("content")
                    .asText();

            // Clean up the response
            content = content.strip();
            if (content.startsWith("```json")) {
                content = content.substring(7);
            } else if (content.startsWith("```")) {
                content = content.substring(3);
            }
            if (content.endsWith("```")) {
                content = content.substring(0, content.length() - 3);
            }
            content = content.strip();

            JsonNode array = objectMapper.readTree(content);
            if (!array.isArray() || array.size() != originals.size()) {
                log.warn("OpenAI response array size mismatch. Expected: {}, Got: {}",
                        originals.size(), array.isArray() ? array.size() : -1);
                return new ArrayList<>(originals);
            }

            List<String> result = new ArrayList<>();
            for (JsonNode node : array) {
                result.add(node.asText());
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to parse OpenAI response: {}", e.getMessage(), e);
            return new ArrayList<>(originals);
        }
    }
}
