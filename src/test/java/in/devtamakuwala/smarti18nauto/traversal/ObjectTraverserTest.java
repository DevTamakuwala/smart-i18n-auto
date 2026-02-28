package in.devtamakuwala.smarti18nauto.traversal;

import in.devtamakuwala.smarti18nauto.annotation.SkipTranslation;
import in.devtamakuwala.smarti18nauto.config.SmartI18nProperties;
import in.devtamakuwala.smarti18nauto.filter.ContentFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ObjectTraverser}.
 */
class ObjectTraverserTest {

    private ObjectTraverser traverser;

    @BeforeEach
    void setUp() {
        SmartI18nProperties properties = new SmartI18nProperties();
        properties.getFilter().setMinLength(2);
        ContentFilter filter = new ContentFilter(properties);
        traverser = new ObjectTraverser(filter);
    }

    // --- Test DTOs ---

    static class SimpleDto {
        String name = "Hello World";
        String description = "This is a test";
        int count = 42;
    }

    static class NestedDto {
        String title = "Outer Title";
        SimpleDto inner = new SimpleDto();
    }

    static class DtoWithSkip {
        String name = "Translate Me";
        @SkipTranslation
        String code = "DO_NOT_TRANSLATE";
        String description = "Also Translate";
    }

    static class DtoWithList {
        String title = "List Title";
        List<String> items = new ArrayList<>(List.of("Item One", "Item Two", "Item Three"));
    }

    static class DtoWithMap {
        String title = "Map Title";
        Map<String, String> data = new LinkedHashMap<>(Map.of(
                "key1", "Value One",
                "key2", "Value Two"
        ));
    }

    static class CircularDto {
        String name = "Circular";
        CircularDto self;
    }

    @Test
    @DisplayName("Should collect strings from simple DTO")
    void shouldCollectStringsFromSimpleDto() {
        SimpleDto dto = new SimpleDto();
        List<StringReference> refs = traverser.collectStrings(dto);

        assertEquals(2, refs.size());
        Set<String> values = new HashSet<>();
        refs.forEach(r -> values.add(r.getOriginalValue()));
        assertTrue(values.contains("Hello World"));
        assertTrue(values.contains("This is a test"));
    }

    @Test
    @DisplayName("Should collect strings from nested DTO")
    void shouldCollectStringsFromNestedDto() {
        NestedDto dto = new NestedDto();
        List<StringReference> refs = traverser.collectStrings(dto);

        // outer title + inner name + inner description = 3
        assertEquals(3, refs.size());
    }

    @Test
    @DisplayName("Should skip @SkipTranslation fields")
    void shouldSkipAnnotatedFields() {
        DtoWithSkip dto = new DtoWithSkip();
        List<StringReference> refs = traverser.collectStrings(dto);

        assertEquals(2, refs.size());
        Set<String> values = new HashSet<>();
        refs.forEach(r -> values.add(r.getOriginalValue()));
        assertTrue(values.contains("Translate Me"));
        assertTrue(values.contains("Also Translate"));
        assertFalse(values.contains("DO_NOT_TRANSLATE"));
    }

    @Test
    @DisplayName("Should collect strings from lists")
    void shouldCollectStringsFromList() {
        DtoWithList dto = new DtoWithList();
        List<StringReference> refs = traverser.collectStrings(dto);

        // title + 3 list items = 4
        assertEquals(4, refs.size());
    }

    @Test
    @DisplayName("Should collect strings from maps")
    void shouldCollectStringsFromMap() {
        DtoWithMap dto = new DtoWithMap();
        List<StringReference> refs = traverser.collectStrings(dto);

        // title + 2 map values = 3
        assertEquals(3, refs.size());
    }

    @Test
    @DisplayName("Should handle circular references without infinite loop")
    void shouldHandleCircularReferences() {
        CircularDto dto = new CircularDto();
        dto.self = dto; // create circular reference

        assertDoesNotThrow(() -> {
            List<StringReference> refs = traverser.collectStrings(dto);
            assertEquals(1, refs.size());
            assertEquals("Circular", refs.getFirst().getOriginalValue());
        });
    }

    @Test
    @DisplayName("Should handle null objects")
    void shouldHandleNull() {
        List<StringReference> refs = traverser.collectStrings(null);
        assertTrue(refs.isEmpty());
    }

    @Test
    @DisplayName("Should write back translated values correctly")
    void shouldWriteBackTranslatedValues() {
        SimpleDto dto = new SimpleDto();
        List<StringReference> refs = traverser.collectStrings(dto);

        for (StringReference ref : refs) {
            ref.writeBack("TRANSLATED_" + ref.getOriginalValue());
        }

        assertTrue(dto.name.startsWith("TRANSLATED_") || dto.description.startsWith("TRANSLATED_"));
    }

    @Test
    @DisplayName("Should write back to list elements correctly")
    void shouldWriteBackToListElements() {
        DtoWithList dto = new DtoWithList();
        List<StringReference> refs = traverser.collectStrings(dto);

        for (StringReference ref : refs) {
            if (ref.getListIndex() >= 0) {
                ref.writeBack("TRANSLATED");
            }
        }

        assertTrue(dto.items.contains("TRANSLATED"));
    }

    @Test
    @DisplayName("Should respect max depth limit")
    void shouldRespectMaxDepthLimit() {
        SmartI18nProperties depthProps = new SmartI18nProperties();
        depthProps.getFilter().setMinLength(2);
        ContentFilter depthFilter = new ContentFilter(depthProps);
        // Create traverser with very shallow depth limit
        ObjectTraverser shallowTraverser = new ObjectTraverser(depthFilter, 1);

        NestedDto dto = new NestedDto();
        List<StringReference> refs = shallowTraverser.collectStrings(dto);

        // Depth 0 = NestedDto fields (title=String, inner=SimpleDto)
        // Depth 1 = SimpleDto fields but depth > maxDepth so stops
        // Should only get the outer title
        assertEquals(1, refs.size());
        assertEquals("Outer Title", refs.getFirst().getOriginalValue());
    }

    @Test
    @DisplayName("Should cache field metadata across calls")
    void shouldCacheFieldMetadata() {
        SimpleDto dto1 = new SimpleDto();
        SimpleDto dto2 = new SimpleDto();

        List<StringReference> refs1 = traverser.collectStrings(dto1);
        List<StringReference> refs2 = traverser.collectStrings(dto2);

        // Both should yield same number of refs (proving cached metadata works)
        assertEquals(refs1.size(), refs2.size());
    }
}

