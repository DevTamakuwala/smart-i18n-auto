package in.devtamakuwala.smarti18nauto.filter;

import in.devtamakuwala.smarti18nauto.config.SmartI18nProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ContentFilter}.
 */
class ContentFilterTest {

    private ContentFilter filter;

    @BeforeEach
    void setUp() {
        SmartI18nProperties properties = new SmartI18nProperties();
        properties.getFilter().setMinLength(2);
        properties.getFilter().setSkipPatterns(List.of());
        filter = new ContentFilter(properties);
    }

    @Test
    @DisplayName("Should accept normal text for translation")
    void shouldAcceptNormalText() {
        assertTrue(filter.isTranslatable("Hello World"));
        assertTrue(filter.isTranslatable("Welcome to our application"));
    }

    @Test
    @DisplayName("Should reject null and blank strings")
    void shouldRejectNullAndBlank() {
        assertFalse(filter.isTranslatable(null));
        assertFalse(filter.isTranslatable(""));
        assertFalse(filter.isTranslatable("   "));
    }

    @Test
    @DisplayName("Should reject short strings below min length")
    void shouldRejectShortStrings() {
        assertFalse(filter.isTranslatable("A"));
    }

    @Test
    @DisplayName("Should reject numeric values")
    void shouldRejectNumericValues() {
        assertFalse(filter.isTranslatable("12345"));
        assertFalse(filter.isTranslatable("3.14159"));
        assertFalse(filter.isTranslatable("-42"));
        assertFalse(filter.isTranslatable("1.5e10"));
    }

    @Test
    @DisplayName("Should reject UUIDs")
    void shouldRejectUuids() {
        assertFalse(filter.isTranslatable("550e8400-e29b-41d4-a716-446655440000"));
    }

    @Test
    @DisplayName("Should reject URLs")
    void shouldRejectUrls() {
        assertFalse(filter.isTranslatable("https://example.com/path"));
        assertFalse(filter.isTranslatable("http://api.example.com"));
    }

    @Test
    @DisplayName("Should reject email addresses")
    void shouldRejectEmails() {
        assertFalse(filter.isTranslatable("user@example.com"));
    }

    @Test
    @DisplayName("Should reject UPPER_SNAKE_CASE enum-like tokens")
    void shouldRejectEnumTokens() {
        assertFalse(filter.isTranslatable("STATUS_ACTIVE"));
        assertFalse(filter.isTranslatable("ORDER_PLACED"));
        assertFalse(filter.isTranslatable("HTTP"));
    }

    @Test
    @DisplayName("Should reject date/time patterns")
    void shouldRejectDatePatterns() {
        assertFalse(filter.isTranslatable("2025-01-15"));
        assertFalse(filter.isTranslatable("2025-01-15T10:30:00"));
    }

    @Test
    @DisplayName("Should reject strings with no letters")
    void shouldRejectNoLetters() {
        assertFalse(filter.isTranslatable("---"));
        assertFalse(filter.isTranslatable("!!!"));
    }

    @Test
    @DisplayName("Should respect custom skip patterns")
    void shouldRespectCustomSkipPatterns() {
        SmartI18nProperties props = new SmartI18nProperties();
        props.getFilter().setSkipPatterns(List.of("^SKU-.*$"));
        ContentFilter customFilter = new ContentFilter(props);

        assertFalse(customFilter.isTranslatable("SKU-12345"));
        assertTrue(customFilter.isTranslatable("Product Name"));
    }
}

