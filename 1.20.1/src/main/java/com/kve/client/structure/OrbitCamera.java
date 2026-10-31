package com.kve.client.structure;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Spherical orbit camera for the structure editor's 3D viewport,
 * section "Camera (no external lib)". Minecraft already ships JOML
 * (net.minecraftforge / mojang mapped Matrix4f etc. are JOML types in
 * 1.20.1), so this needs no extra dependency, matching the "Do NOT
 * depend: jMonkey/Garrett, libGDX, ArcGIS, Three orbit-controls".
 * <p>
 * Convention: yaw=0, pitch=0 looks along +Z, matching the eye formula
 * given directly:
 * <pre>
 * eye = target + (dist*cos(pitch)*sin(yaw), dist*sin(pitch), dist*cos(pitch)*cos(yaw))
 * </pre>
 */
public final class OrbitCamera {

    private static final float PITCH_LIMIT = 1.4f;

    private final Vector3f target = new Vector3f();
    private float yaw = 0.7f;
    private float pitch = 0.5f;
    private float distance;

    private final float minDistance;
    private final float maxDistance;
    private float fovDegrees;

    public OrbitCamera(float startDistance, float minDistance, float maxDistance, float fovDegrees) {
        this.distance = clamp(startDistance, minDistance, maxDistance);
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
        this.fovDegrees = fovDegrees;
    }

    /** dx/dy in pixels of mouse drag delta; sensitivity applied by the caller-agnostic constant below. */
    public void orbit(double dx, double dy) {
        yaw -= (float) dx * 0.01f;
        pitch = clamp(pitch - (float) dy * 0.01f, -PITCH_LIMIT, PITCH_LIMIT);
    }

    public void zoom(double wheelDelta) {
        float factor = (float) Math.pow(0.9, wheelDelta);
        float newDistance = clamp(distance * factor, minDistance, maxDistance);
        float delta = distance - newDistance;
        if (delta != 0f) {
            target.fma(delta, forwardVector());
            float bound = maxDistance * 4f;
            target.x = clamp(target.x, -bound, bound);
            target.y = clamp(target.y, -bound, bound);
            target.z = clamp(target.z, -bound, bound);
        }
        distance = newDistance;
    }

    public void pan(double dx, double dy) {
        Vector3f right = rightVector();
        Vector3f up = upVector();
        float scale = distance * 0.0015f;
        target.fma((float) -dx * scale, right);
        target.fma((float) dy * scale, up);
        float bound = maxDistance * 4f;
        target.x = clamp(target.x, -bound, bound);
        target.y = clamp(target.y, -bound, bound);
        target.z = clamp(target.z, -bound, bound);
    }

    public void resetTarget() {
        target.set(0f, 0f, 0f);
    }

    public void rotate90Snap() {
        yaw += (float) (Math.PI / 2.0);
    }

    public Vector3f eyePosition() {
        float cosPitch = (float) Math.cos(pitch);
        float x = distance * cosPitch * (float) Math.sin(yaw);
        float y = distance * (float) Math.sin(pitch);
        float z = distance * cosPitch * (float) Math.cos(yaw);
        return new Vector3f(target.x + x, target.y + y, target.z + z);
    }

    public Vector3f forwardVector() {
        return target.sub(eyePosition(), new Vector3f()).normalize();
    }

    public Vector3f rightVector() {
        return forwardVector().cross(new Vector3f(0, 1, 0)).normalize();
    }

    public Vector3f upVector() {
        return rightVector().cross(forwardVector(), new Vector3f()).normalize();
    }

    public Matrix4f viewMatrix() {
        Vector3f eye = eyePosition();
        return new Matrix4f().lookAt(eye, target, new Vector3f(0, 1, 0));
    }

    public Matrix4f projectionMatrix(float aspect) {
        return new Matrix4f().perspective(
                (float) Math.toRadians(fovDegrees), aspect, 0.05f, distance + 200f);
    }

    public Vector3f target() { return target; }
    public void setTarget(Vector3f t) { target.set(t); }
    public float distance() { return distance; }
    public void setDistance(float d) { this.distance = clamp(d, minDistance, maxDistance); }
    public float fovDegrees() { return fovDegrees; }
    public void setFovDegrees(float fov) { this.fovDegrees = fov; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    public void setYawPitch(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = clamp(pitch, -PITCH_LIMIT, PITCH_LIMIT);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
