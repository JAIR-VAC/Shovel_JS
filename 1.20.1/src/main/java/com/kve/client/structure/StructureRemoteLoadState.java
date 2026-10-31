package com.kve.client.structure;

import com.kve.CraftEditorMod;
import com.kve.common.network.KVENetwork;
import com.kve.common.structure.StructureNbtIO;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public final class StructureRemoteLoadState {

    private StructureRemoteLoadState() {}

    private record PendingRequest(String id,
                                   Consumer<StructureNbtIO.LoadResult> onSuccess,
                                   Consumer<String> onFailure) {}

    private static int nextRequestId = 1;
    private static final Map<Integer, PendingRequest> pending = new HashMap<>();
    private static final Map<String, Integer> latestRequestForId = new HashMap<>();

    public static synchronized void request(ResourceLocation id,
                                             Consumer<StructureNbtIO.LoadResult> onSuccess,
                                             Consumer<String> onFailure) {
        int requestId = nextRequestId++;
        String idStr = id.toString();
        Integer previousForSameId = latestRequestForId.put(idStr, requestId);
        if (previousForSameId != null) pending.remove(previousForSameId);
        pending.put(requestId, new PendingRequest(idStr, onSuccess, onFailure));
        KVENetwork.requestStructureRemote(requestId, idStr);
    }

    /** Called by {@code StructureRemoteResponsePacket#handle} on the client thread once a reply arrives. */
    public static synchronized void onResponse(int requestId, String id, byte[] data) {
        PendingRequest req = pending.remove(requestId);
        if (req == null) return;
        latestRequestForId.remove(req.id(), requestId);

        if (data == null) {
            if (req.onFailure() != null) req.onFailure().accept(id);
            return;
        }
        try {
            StructureNbtIO.LoadResult result = StructureNbtIO.fromRemoteBytes(data, new ResourceLocation(id));
            if (req.onSuccess() != null) req.onSuccess().accept(result);
        } catch (IOException | RuntimeException ex) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] No se pudo interpretar la estructura recibida del servidor {}: {}", id, ex.getMessage());
            if (req.onFailure() != null) req.onFailure().accept(id);
        }
    }
}
