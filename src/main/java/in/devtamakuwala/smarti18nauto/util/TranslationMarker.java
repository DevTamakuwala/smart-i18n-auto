package in.devtamakuwala.smarti18nauto.util;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Request-scoped tracker that prevents double translation within the same
 * request lifecycle.
 * <p>
 * Previous approach appended an invisible Unicode zero-width space ({@code U+200B})
 * to every translated string — this corrupted data for downstream consumers
 * (string comparisons, length checks, database storage, JSON serialization).
 * </p>
 * <p>
 * This version uses a {@link ThreadLocal} holding an identity-based {@link Set}
 * of already-translated object references. The set is automatically cleaned up
 * when the translation scope ends via {@link #beginScope()} / {@link #endScope()}.
 * </p>
 *
 * @author devtamakuwala
 * @since 0.0.2
 */
public final class TranslationMarker {

    /**
     * Thread-local set of object identity hashes that have been translated
     * in the current request scope.
     */
    private static final ThreadLocal<Set<Object>> TRANSLATED_OBJECTS =
            ThreadLocal.withInitial(() -> Collections.newSetFromMap(new IdentityHashMap<>()));

    private TranslationMarker() {
        // utility class
    }

    /**
     * Begins a translation scope. Clears any stale state from the previous request
     * on this thread (important for thread-pool reuse).
     */
    public static void beginScope() {
        TRANSLATED_OBJECTS.get().clear();
    }

    /**
     * Ends the translation scope and releases the thread-local state
     * to prevent memory leaks in thread pools.
     */
    public static void endScope() {
        TRANSLATED_OBJECTS.remove();
    }

    /**
     * Marks the given object as already translated in the current scope.
     *
     * @param obj the object that was translated (the root body, not individual strings)
     */
    public static void markTranslated(Object obj) {
        if (obj != null) {
            TRANSLATED_OBJECTS.get().add(obj);
        }
    }

    /**
     * Checks whether the given object has already been translated in the current scope.
     *
     * @param obj the object to check
     * @return true if already translated
     */
    public static boolean isAlreadyTranslated(Object obj) {
        return obj != null && TRANSLATED_OBJECTS.get().contains(obj);
    }

    // ========================================================================
    // Legacy compatibility — retained for tests but no longer inject markers
    // ========================================================================

    /**
     * @deprecated No longer injects invisible characters. Returns text unchanged.
     */
    @Deprecated(since = "0.0.2")
    public static String mark(String text) {
        return text;
    }

    /**
     * @deprecated No longer needed. Returns text unchanged.
     */
    @Deprecated(since = "0.0.2")
    public static String unmark(String text) {
        return text;
    }
}

