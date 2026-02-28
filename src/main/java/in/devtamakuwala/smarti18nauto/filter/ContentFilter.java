package in.devtamakuwala.smarti18nauto.filter;

import in.devtamakuwala.smarti18nauto.config.SmartI18nProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Determines whether a given string should be translated.
 * <p>
 * Skips values that are:
 * <ul>
 *   <li>Null, blank, or too short</li>
 *   <li>Numeric values (integers, decimals)</li>
 *   <li>UUIDs</li>
 *   <li>URLs or email addresses</li>
 *   <li>Enum-like tokens (ALL_UPPER_CASE)</li>
 *   <li>Single-character or very short tokens</li>
 *   <li>Matching user-configured skip patterns</li>
 * </ul>
 * </p>
 *
 * @author devtamakuwala
 * @since 0.0.1
 */
public class ContentFilter {

    private static final Logger log = LoggerFactory.getLogger(ContentFilter.class);

    /** Matches integers, decimals, scientific notation */
    private static final Pattern NUMERIC_PATTERN =
            Pattern.compile("^-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?$");

    /** Matches UUID format */
    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    /** Matches URLs */
    private static final Pattern URL_PATTERN =
            Pattern.compile("^(https?|ftp)://[^\\s]+$", Pattern.CASE_INSENSITIVE);

    /** Matches email addresses */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[\\w.-]+$");

    /** Matches UPPER_SNAKE_CASE enum-like tokens */
    private static final Pattern ENUM_PATTERN =
            Pattern.compile("^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$");

    /** Matches ISO date/datetime patterns */
    private static final Pattern DATE_PATTERN =
            Pattern.compile("^\\d{4}-\\d{2}-\\d{2}(T\\d{2}:\\d{2}(:\\d{2})?)?.*$");

    private final SmartI18nProperties properties;
    private final List<Pattern> customSkipPatterns;

    public ContentFilter(SmartI18nProperties properties) {
        this.properties = properties;
        this.customSkipPatterns = properties.getFilter().getSkipPatterns().stream()
                .map(Pattern::compile)
                .toList();
    }

    /**
     * Determines whether the given text is translatable.
     *
     * @param text the text to evaluate
     * @return true if the text should be translated
     */
    public boolean isTranslatable(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        String trimmed = text.strip();

        // Too short
        int minLength = properties.getFilter().getMinLength();
        if (trimmed.length() < minLength) {
            log.trace("Skipping short text: '{}'", trimmed);
            return false;
        }

        // Numeric
        if (NUMERIC_PATTERN.matcher(trimmed).matches()) {
            log.trace("Skipping numeric text: '{}'", trimmed);
            return false;
        }

        // UUID
        if (UUID_PATTERN.matcher(trimmed).matches()) {
            log.trace("Skipping UUID: '{}'", trimmed);
            return false;
        }

        // URL
        if (URL_PATTERN.matcher(trimmed).matches()) {
            log.trace("Skipping URL: '{}'", trimmed);
            return false;
        }

        // Email
        if (EMAIL_PATTERN.matcher(trimmed).matches()) {
            log.trace("Skipping email: '{}'", trimmed);
            return false;
        }

        // Enum-like
        if (ENUM_PATTERN.matcher(trimmed).matches()) {
            log.trace("Skipping enum-like token: '{}'", trimmed);
            return false;
        }

        // Date/Time
        if (DATE_PATTERN.matcher(trimmed).matches()) {
            log.trace("Skipping date/time: '{}'", trimmed);
            return false;
        }

        // Custom skip patterns
        for (Pattern pattern : customSkipPatterns) {
            if (pattern.matcher(trimmed).matches()) {
                log.trace("Skipping text matching custom pattern '{}': '{}'", pattern.pattern(), trimmed);
                return false;
            }
        }

        // Must contain at least one letter to be translatable
        if (!trimmed.chars().anyMatch(Character::isLetter)) {
            log.trace("Skipping non-letter text: '{}'", trimmed);
            return false;
        }

        return true;
    }
}

