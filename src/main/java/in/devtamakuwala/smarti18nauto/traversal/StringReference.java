package in.devtamakuwala.smarti18nauto.traversal;

import java.lang.reflect.Field;

/**
 * Represents a reference to a String value within an object graph.
 * <p>
 * Used by {@link ObjectTraverser} to collect all translatable strings
 * and then write back translated values efficiently after batch translation.
 * </p>
 *
 * @author devtamakuwala
 * @since 0.0.1
 */
public class StringReference {

    private final Object parent;
    private final Field field;
    private final String originalValue;
    private final int listIndex;
    private final Object mapKey;

    /**
     * Creates a reference to a String field in an object.
     *
     * @param parent        the parent object containing the field
     * @param field         the field holding the string
     * @param originalValue the original string value
     */
    public StringReference(Object parent, Field field, String originalValue) {
        this.parent = parent;
        this.field = field;
        this.originalValue = originalValue;
        this.listIndex = -1;
        this.mapKey = null;
    }

    /**
     * Creates a reference to a String at a specific index in a List.
     *
     * @param parent        the parent List
     * @param listIndex     the index within the list
     * @param originalValue the original string value
     */
    public StringReference(Object parent, int listIndex, String originalValue) {
        this.parent = parent;
        this.field = null;
        this.originalValue = originalValue;
        this.listIndex = listIndex;
        this.mapKey = null;
    }

    /**
     * Creates a reference to a String value in a Map.
     *
     * @param parent        the parent Map
     * @param mapKey        the map key
     * @param originalValue the original string value
     */
    public StringReference(Object parent, Object mapKey, String originalValue, boolean isMap) {
        this.parent = parent;
        this.field = null;
        this.originalValue = originalValue;
        this.listIndex = -1;
        this.mapKey = mapKey;
    }

    public Object getParent() {
        return parent;
    }

    public Field getField() {
        return field;
    }

    public String getOriginalValue() {
        return originalValue;
    }

    public int getListIndex() {
        return listIndex;
    }

    public Object getMapKey() {
        return mapKey;
    }

    /**
     * Writes the translated value back to the original location.
     *
     * @param translatedValue the translated string
     */
    @SuppressWarnings("unchecked")
    public void writeBack(String translatedValue) {
        try {
            if (field != null) {
                // Object field
                field.setAccessible(true);
                field.set(parent, translatedValue);
            } else if (listIndex >= 0) {
                // List element
                ((java.util.List<Object>) parent).set(listIndex, translatedValue);
            } else if (mapKey != null) {
                // Map value
                ((java.util.Map<Object, Object>) parent).put(mapKey, translatedValue);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to write back translated value", e);
        }
    }
}

