package com.kve.client.structure;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class KveMeshBuilder {

    private final ByteBufferBuilder bytes;
    public final BufferBuilder buffer;
    private boolean closed;

    public KveMeshBuilder(int capacity, VertexFormat.Mode mode, VertexFormat format) {
        this.bytes = new ByteBufferBuilder(capacity);
        this.buffer = new BufferBuilder(bytes, mode, format);
    }

    /**
     * Sube la geometria a {@code vbo} (que tiene que estar bound) y libera la
     * memoria. Devuelve false si no habia vertices (nada subido).
     */
    public boolean uploadTo(VertexBuffer vbo) {
        try {
            MeshData mesh = buffer.build();
            if (mesh == null) return false;
            vbo.upload(mesh);
            return true;
        } finally {
            discard();
        }
    }

    public void discard() {
        if (closed) return;
        closed = true;
        bytes.close();
    }
}
