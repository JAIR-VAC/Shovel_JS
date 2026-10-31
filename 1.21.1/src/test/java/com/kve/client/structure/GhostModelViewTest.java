package com.kve.client.structure;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * En NeoForge 1.21 la rotacion de camara no viene en el PoseStack del
 * evento. Sin componerla, el holograma quedaba pegado a la pantalla. El model-view
 * de los VBO tiene que girar con la camara.
 */
class GhostModelViewTest {

    @Test
    void anchorOffsetTurnsWithTheCamera() {
        Matrix4f anchor = new Matrix4f().translation(0, 0, -5);

        Vector3f ahead = new Matrix4f(GhostModelViewTestSupport.view(0)).mul(anchor).transformPosition(new Vector3f());
        Vector3f straight = StructureGhostPreviewRenderer.ghostModelView(GhostModelViewTestSupport.view(0), anchor).transformPosition(new Vector3f());
        assertEquals(ahead.z, straight.z, 1e-4);
        assertTrue(straight.z < 0, "delante de la camara");

        Vector3f turned = StructureGhostPreviewRenderer.ghostModelView(GhostModelViewTestSupport.view(180), anchor).transformPosition(new Vector3f());
        assertTrue(turned.z > 0, "al darse vuelta el holograma queda detras: " + turned);
        assertEquals(5f, turned.length(), 1e-4, "girar no cambia la distancia");
    }

    @Test
    void doesNotMutateTheEventMatrix() {
        Matrix4f view = GhostModelViewTestSupport.view(90);
        Matrix4f copy = new Matrix4f(view);
        StructureGhostPreviewRenderer.ghostModelView(view, new Matrix4f().translation(1, 2, 3));
        assertEquals(copy, view, "la matriz del evento la comparte todo el render: no se toca");
    }
}

final class GhostModelViewTestSupport {
    private GhostModelViewTestSupport() {}

    static Matrix4f view(float yawDegrees) {
        return new Matrix4f().rotationY((float) Math.toRadians(yawDegrees));
    }
}
