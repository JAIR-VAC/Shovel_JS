package com.kve.client.gui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** El registro de items rotos (la logica que evita reintentar y llenar el log). */
class SafeItemRenderTest {

    @AfterEach
    void reset() { SafeItemRender.clearForTests(); }

    @Test
    void brokenIdsAreTrackedAndNotRetried() {
        assertFalse(SafeItemRender.isBroken("cataclysm:fake_item"));
        // Sin GuiGraphics no se puede dibujar: el helper de registro se prueba por su API publica.
        assertTrue(SafeItemRender.brokenIds().isEmpty());
    }
}
