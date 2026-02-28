package in.devtamakuwala.smarti18nauto.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TranslationMarker}.
 * Updated for the refactored request-scope approach (no more invisible characters).
 */
class TranslationMarkerTest {

    @AfterEach
    void cleanup() {
        TranslationMarker.endScope();
    }

    @Test
    @DisplayName("Should track objects as translated within a scope")
    void shouldTrackObjectsInScope() {
        TranslationMarker.beginScope();

        Object obj = new Object();
        assertFalse(TranslationMarker.isAlreadyTranslated(obj));

        TranslationMarker.markTranslated(obj);
        assertTrue(TranslationMarker.isAlreadyTranslated(obj));
    }

    @Test
    @DisplayName("Should clear tracking when scope ends")
    void shouldClearOnScopeEnd() {
        TranslationMarker.beginScope();

        Object obj = new Object();
        TranslationMarker.markTranslated(obj);
        assertTrue(TranslationMarker.isAlreadyTranslated(obj));

        TranslationMarker.endScope();
        assertFalse(TranslationMarker.isAlreadyTranslated(obj));
    }

    @Test
    @DisplayName("Should not track null objects")
    void shouldNotTrackNull() {
        TranslationMarker.beginScope();
        TranslationMarker.markTranslated(null);
        assertFalse(TranslationMarker.isAlreadyTranslated(null));
    }

    @Test
    @DisplayName("Should use identity comparison not equals")
    void shouldUseIdentityComparison() {
        TranslationMarker.beginScope();

        String obj1 = new String("test");
        String obj2 = new String("test");

        TranslationMarker.markTranslated(obj1);
        assertTrue(TranslationMarker.isAlreadyTranslated(obj1));
        assertFalse(TranslationMarker.isAlreadyTranslated(obj2)); // different identity
    }

    @Test
    @DisplayName("Legacy mark/unmark should be no-op (backward compat)")
    void legacyMethodsShouldBeNoOp() {
        String text = "Hello World";
        assertEquals(text, TranslationMarker.mark(text));
        assertEquals(text, TranslationMarker.unmark(text));
        assertNull(TranslationMarker.mark(null));
        assertNull(TranslationMarker.unmark(null));
    }

    @Test
    @DisplayName("beginScope should clear stale state from previous scope")
    void beginScopeShouldClearStaleState() {
        TranslationMarker.beginScope();
        Object obj = new Object();
        TranslationMarker.markTranslated(obj);
        assertTrue(TranslationMarker.isAlreadyTranslated(obj));

        // Simulate thread reuse — new request on same thread
        TranslationMarker.beginScope();
        assertFalse(TranslationMarker.isAlreadyTranslated(obj));
    }
}
