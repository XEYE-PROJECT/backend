package com.xeye.backend.element.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElementTest {

    @Test
    void newElementIsUntrained() {
        Element element = Element.create(1L, "hello", null, null);
        assertFalse(element.trained());
    }

    @Test
    void changingTextResetsTrainedAndReportsChange() {
        Element element = new Element(1L, 1L, "hello", null, null, null, true, null, null);
        boolean changed = element.changeText("world");
        assertTrue(changed);
        assertFalse(element.trained());
    }

    @Test
    void settingSameTextIsNoChange() {
        Element element = new Element(1L, 1L, "hello", null, null, null, true, null, null);
        assertFalse(element.changeText("hello"));
        assertTrue(element.trained());
    }

    @Test
    void changingParamsDoesNotAffectTrained() {
        Element element = new Element(1L, 1L, "hello", null, null, null, true, null, null);
        element.changeParams("{\"k\":1}");
        assertTrue(element.trained());
    }

    @Test
    void blankTextIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Element.create(1L, "  ", null, null));
    }
}
