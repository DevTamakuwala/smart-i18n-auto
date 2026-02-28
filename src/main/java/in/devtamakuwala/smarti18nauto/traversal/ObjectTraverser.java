package in.devtamakuwala.smarti18nauto.traversal;

import in.devtamakuwala.smarti18nauto.annotation.SkipTranslation;
import in.devtamakuwala.smarti18nauto.filter.ContentFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Recursively traverses an object graph to collect all translatable string references.
 * <p>
 * Handles:
 * <ul>
 *   <li>Plain String values</li>
 *   <li>{@link List} and array elements</li>
 *   <li>{@link Map} values (String values only)</li>
 *   <li>Nested DTOs (POJOs) via reflection</li>
 * </ul>
 * Uses an {@link IdentityHashMap} to detect circular references and prevent infinite loops.
 * Respects {@link SkipTranslation} annotations on fields.
 * Enforces a configurable maximum traversal depth to prevent {@link StackOverflowError}.
 * Caches reflected field metadata per class to avoid repeated reflection overhead.
 *
 *
 * @author devtamakuwala
 * @since 0.0.2
 */
public class ObjectTraverser {

    private static final Logger log = LoggerFactory.getLogger(ObjectTraverser.class);

    /**
     * Packages that should never be traversed into (JDK and framework internals).
     */
    private static final Set<String> SKIP_PACKAGES = Set.of(
            "java.", "javax.", "jakarta.", "sun.", "com.sun.",
            "org.springframework.", "com.fasterxml.", "jdk."
    );

    /**
     * Thread-safe cache of reflected field metadata per class.
     * Avoids calling getDeclaredFields() + setAccessible() on every request.
     */
    private static final ConcurrentHashMap<Class<?>, List<Field>> FIELD_CACHE = new ConcurrentHashMap<>();

    private final ContentFilter contentFilter;
    private final int maxDepth;

    /**
     * Creates an ObjectTraverser with default max depth (32).
     */
    public ObjectTraverser(ContentFilter contentFilter) {
        this(contentFilter, 32);
    }

    /**
     * Creates an ObjectTraverser with the specified max traversal depth.
     */
    public ObjectTraverser(ContentFilter contentFilter, int maxDepth) {
        this.contentFilter = contentFilter;
        this.maxDepth = maxDepth;
    }

    /**
     * Collects all translatable {@link StringReference}s from the given object.
     *
     * @param obj the root object to traverse
     * @return list of string references that should be translated
     */
    public List<StringReference> collectStrings(Object obj) {
        List<StringReference> references = new ArrayList<>();
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        traverse(obj, null, null, -1, null, references, visited, 0);
        return references;
    }

    private void traverse(Object obj, Object parent, Field parentField,
                           int listIndex, Object mapKey,
                           List<StringReference> references, Set<Object> visited,
                           int depth) {
        if (obj == null) {
            return;
        }

        // Depth limit to prevent StackOverflowError
        if (depth > maxDepth) {
            log.debug("Max traversal depth ({}) exceeded, stopping", maxDepth);
            return;
        }

        Class<?> clazz = obj.getClass();

        // Handle String
        if (obj instanceof String str) {
            if (!contentFilter.isTranslatable(str)) {
                return;
            }

            if (parentField != null && parent != null) {
                references.add(new StringReference(parent, parentField, str));
            } else if (listIndex >= 0 && parent != null) {
                references.add(new StringReference(parent, listIndex, str));
            } else if (mapKey != null && parent != null) {
                references.add(new StringReference(parent, mapKey, str, true));
            }
            return;
        }

        // Skip primitives, wrappers, enums
        if (clazz.isPrimitive() || clazz.isEnum() || isWrapperType(clazz)) {
            return;
        }

        // Circular reference protection
        if (!visited.add(obj)) {
            log.trace("Circular reference detected, skipping: {}", clazz.getName());
            return;
        }

        // Handle List
        if (obj instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                Object element = list.get(i);
                traverse(element, obj, null, i, null, references, visited, depth + 1);
            }
            return;
        }

        // Handle Map
        if (obj instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object value = entry.getValue();
                Object key = entry.getKey();
                traverse(value, obj, null, -1, key, references, visited, depth + 1);
            }
            return;
        }

        // Handle arrays
        if (clazz.isArray()) {
            int length = Array.getLength(obj);
            for (int i = 0; i < length; i++) {
                Object element = Array.get(obj, i);
                traverse(element, null, null, -1, null, references, visited, depth + 1);
            }
            return;
        }

        // Skip JDK/framework internal classes
        String className = clazz.getName();
        for (String skipPkg : SKIP_PACKAGES) {
            if (className.startsWith(skipPkg)) {
                return;
            }
        }

        // Handle POJO - traverse fields via reflection (with caching)
        traverseFields(obj, clazz, references, visited, depth);
    }

    private void traverseFields(Object obj, Class<?> clazz,
                                 List<StringReference> references, Set<Object> visited,
                                 int depth) {
        List<Field> fields = getTranslatableFields(clazz);

        for (Field field : fields) {
            try {
                Object value = field.get(obj);
                if (value != null) {
                    traverse(value, obj, field, -1, null, references, visited, depth + 1);
                }
            } catch (Exception e) {
                log.debug("Cannot access field {}.{}: {}",
                        clazz.getSimpleName(), field.getName(), e.getMessage());
            }
        }
    }

    /**
     * Returns the list of translatable fields for the given class, using a cache
     * to avoid repeated reflection. Fields are filtered once: static, transient,
     * synthetic, and @SkipTranslation fields are excluded.
     */
    private List<Field> getTranslatableFields(Class<?> clazz) {
        return FIELD_CACHE.computeIfAbsent(clazz, this::computeTranslatableFields);
    }

    private List<Field> computeTranslatableFields(Class<?> clazz) {
        List<Field> result = new ArrayList<>();
        Class<?> current = clazz;

        while (current != null && current != Object.class) {
            String currentName = current.getName();
            boolean skipClass = false;
            for (String skipPkg : SKIP_PACKAGES) {
                if (currentName.startsWith(skipPkg)) {
                    skipClass = true;
                    break;
                }
            }
            if (skipClass) {
                break;
            }

            for (Field field : current.getDeclaredFields()) {
                int mod = field.getModifiers();
                if (Modifier.isStatic(mod) || Modifier.isTransient(mod) || field.isSynthetic()) {
                    continue;
                }
                if (field.isAnnotationPresent(SkipTranslation.class)) {
                    continue;
                }

                field.setAccessible(true);
                result.add(field);
            }

            current = current.getSuperclass();
        }

        return Collections.unmodifiableList(result);
    }

    private boolean isWrapperType(Class<?> clazz) {
        return clazz == Boolean.class || clazz == Byte.class || clazz == Character.class
                || clazz == Short.class || clazz == Integer.class || clazz == Long.class
                || clazz == Float.class || clazz == Double.class || clazz == Void.class
                || Number.class.isAssignableFrom(clazz);
    }
}

