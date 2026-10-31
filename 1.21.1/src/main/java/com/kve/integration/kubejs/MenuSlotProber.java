package com.kve.integration.kubejs;

import com.kve.CraftEditorMod;
import com.kve.common.recipetype.RecipeTypeDescriptor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MenuSlotProber {

    private MenuSlotProber() {}

    /** One slot position harvested from a real {@link AbstractContainerMenu}. */
    public static final class ProbedSlot {
        /** This slot's index within {@code menu.slots} (stable identity for a re-probe, not meant to be shown to the player). */
        public final int menuSlotIndex;
        public final int x;
        public final int y;
        /** "input" or "output" - a best-effort GUESS, see {@link #classifyRole(Slot)}. Always to be confirmed by a human before it's trusted. */
        public final String probableRole;

        ProbedSlot(int menuSlotIndex, int x, int y, String probableRole) {
            this.menuSlotIndex = menuSlotIndex;
            this.x = x;
            this.y = y;
            this.probableRole = probableRole;
        }
    }

    /** Outcome of one {@link #probeMenuSlots(String)} call - either a list of harvested slots, or an honest reason nothing could be harvested. */
    public static final class ProbeResult {
        public final String menuTypeId;
        public final boolean success;
        /** Null when {@link #success} is true. Human-readable (Spanish, matching the rest of KVE's in-editor text), meant to be shown directly to the player - never a raw exception message. */
        public final String failureReason;
        public final List<ProbedSlot> slots;

        private ProbeResult(String menuTypeId, boolean success, String failureReason, List<ProbedSlot> slots) {
            this.menuTypeId = menuTypeId;
            this.success = success;
            this.failureReason = failureReason;
            this.slots = slots;
        }

        static ProbeResult failure(String menuTypeId, String reason) {
            return new ProbeResult(menuTypeId, false, reason, List.of());
        }

        static ProbeResult success(String menuTypeId, List<ProbedSlot> slots) {
            return new ProbeResult(menuTypeId, true, null, slots);
        }

        /**
         * Converts the harvested slots into {@link RecipeTypeDescriptor.SlotDef}
         * proposals - same shape a human would type by hand into a
         * {@code recipe_types} JSON, so a future "aceptar propuesta" UI
         * can hand these straight to the existing
         * slotLayout editing/rendering code with no extra translation step.
         * Still just a proposal: nothing here is validated against
         * {@link RecipeTypeDescriptor.SlotLayout#hasAtLeastOneOfEachRole}
         * or written anywhere.
         */
        public List<RecipeTypeDescriptor.SlotDef> toSlotDefProposal() {
            List<RecipeTypeDescriptor.SlotDef> out = new ArrayList<>();
            for (ProbedSlot s : slots) {
                RecipeTypeDescriptor.SlotDef def = new RecipeTypeDescriptor.SlotDef();
                def.id = "probed_" + s.menuSlotIndex;
                def.role = "output".equals(s.probableRole) ? "output" : "input";
                def.x = s.x;
                def.y = s.y;
                out.add(def);
            }
            return out;
        }
    }

    /**
     * Harvests slot geometry for {@code menuTypeId} (e.g. "mekanism:crushing_gui",
     * the {@code MenuType} id - NOT the recipe serializer id, those are
     * usually different registries/ids for the same addon). Always runs
     * entirely client-side and read-only; see the class javadoc for the
     * full list of things this deliberately never does.
     * <p>
     * Must be called with a local player already in a world (client-side
     * integrated or remote server, doesn't matter which - nothing is sent
     * over the network either way). Calling this with no local player
     * (e.g. from the main menu, or accidentally from a server-side script
     * context) fails gracefully with an explanatory reason instead of
     * throwing.
     */
    /** El jugador local, o null en un servidor dedicado (ahi la clase Minecraft no existe). */
    private static Player localPlayer() {
        var dist = net.neoforged.fml.loading.FMLEnvironment.dist;
        if (dist != null && !dist.isClient()) return null;
        return ClientOnly.player();
    }

    private static final class ClientOnly {
        static Player player() {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            return mc == null ? null : mc.player;
        }
    }

    public static ProbeResult probeMenuSlots(String menuTypeId) {
        Player player = localPlayer();
        if (player == null) {
            return ProbeResult.failure(menuTypeId, "no hay un jugador local activo (hace falta estar dentro de una partida)");
        }

        ResourceLocation rl = ResourceLocation.tryParse(menuTypeId);
        if (rl == null) {
            return ProbeResult.failure(menuTypeId, "id de menu invalido: '" + menuTypeId + "'");
        }

        MenuType<?> type = BuiltInRegistries.MENU.get(rl);
        if (type == null) {
            return ProbeResult.failure(menuTypeId, "ningun MenuType registrado con ese id (¿el mod esta instalado?)");
        }

        Inventory playerInv = player.getInventory();

        AbstractContainerMenu menu;
        try {
            // Unico punto de entrada publico de MenuType: para tipos sin
            // datos extra esto ya alcanza. Para tipos que si registran
            // datos extra via la fabrica de 3 argumentos de Forge
            // (IForgeMenuType), Forge los resuelve puertas adentro de este
            // mismo metodo - ver la limitacion documentada en la clase
            // sobre ese caso cuando nunca hubo un open-screen real de por
            // medio.
            menu = type.create(0, playerInv);
        } catch (Exception ex) {
            CraftEditorMod.LOGGER.debug("[Shovel JS] probeMenuSlots({}): no se pudo instanciar el menu: {}", menuTypeId, ex.toString());
            menu = null;
        }

        if (menu == null) {
            return ProbeResult.failure(menuTypeId,
                "no se pudo instanciar el menu sin datos reales de una mesa en el mundo (ver log de debug) - "
                    + "este tipo queda como candidato a slotLayout manual, igual que hoy");
        }

        List<ProbedSlot> out = new ArrayList<>();
        for (Slot slot : menu.slots) {
            out.add(new ProbedSlot(slot.index, slot.x, slot.y, classifyRole(slot)));
        }

        if (out.isEmpty()) {
            return ProbeResult.failure(menuTypeId, "el menu se instancio pero no expuso ningun slot");
        }

        return ProbeResult.success(menuTypeId, out);
    }

    private static String classifyRole(Slot slot) {
        for (Class<?> c = slot.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            if (c.getSimpleName().toLowerCase(Locale.ROOT).contains("result")) {
                return "output";
            }
        }
        try {
            if (!slot.mayPlace(new ItemStack(Items.STONE))) {
                return "output";
            }
        } catch (Exception ex) {
            CraftEditorMod.LOGGER.debug("[Shovel JS] probeMenuSlots: mayPlace() fallo clasificando slot #{}: {}", slot.index, ex.toString());
        }
        return "input";
    }
}
