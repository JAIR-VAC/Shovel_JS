package com.kve.client.gui.pack;

import com.kve.client.gui.KveGuiStyle;
import com.kve.client.gui.KvePlateButton;

import com.kve.common.Lang;
import com.kve.client.permission.ClientPermissionAdminState;
import com.kve.common.network.KVENetwork;
import com.kve.common.network.PermissionAdminSyncPacket;
import com.kve.common.permission.KvePermissionRole;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

/**
 * ADMIN-only screen listing every currently-online player and their
 * resolved {@link KvePermissionRole}, with a button per row to cycle their
 * role and one to drop back to whatever the server's default/op-level
 * rules would otherwise give them. This is the in-game replacement for
 * hand-editing {@code config/kve_permissions.json} - the same gap the
 * addon-flags toggle closed for recipe types, closed here for permissions.
 *
 * Every actual change is re-validated server-side by
 * {@code PermissionSetRolePacket} - this screen only being reachable for
 * an ADMIN client is a convenience, not the security boundary.
 */
@OnlyIn(Dist.CLIENT)
public class PermissionAdminScreen extends Screen {

    private static final int GUI_W = 260;
    private static final int GUI_H = 200;
    private static final int ROW_H = 16;
    private static final int ROWS_VISIBLE = 8;

    private final Screen parent;
    private int scroll = 0;
    private long lastRequestAt = 0;

    public PermissionAdminScreen(Screen parent) {
        super(Lang.c("gui.kve.perm.admin.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        setFocused(null);
        requestSnapshotIfStale();

        int L = (width - GUI_W) / 2;
        int T = (height - GUI_H) / 2;

        PermissionAdminSyncPacket snap = ClientPermissionAdminState.lastSnapshot();
        if (snap != null) {
            scroll = Math.max(0, Math.min(scroll, Math.max(0, snap.entries.size() - ROWS_VISIBLE)));
            KveGuiStyle.Layout ly = new KveGuiStyle.Layout(L + 6, T + 30, ROW_H);
            int shown = 0;
            for (int i = scroll; i < snap.entries.size() && shown < ROWS_VISIBLE; i++, shown++) {
                var e = snap.entries.get(i);
                int ry = ly.row();
                addRenderableWidget(new KvePlateButton(L + 6, ry, 90, 12, Component.literal(KveGuiStyle.truncate(font, e.name, 90)),
                        b -> {}));
                addRenderableWidget(new KvePlateButton(L + 100, ry, 76, 12, Component.literal(roleLabel(e.role)), b -> cycleRole(e.name, e.role)));
                addRenderableWidget(new KvePlateButton(L + 180, ry, 44, 12, Component.literal("Reset"), b -> setRole(e.name, "CLEAR")));
            }
            if (snap.entries.size() > ROWS_VISIBLE) {
                if (scroll > 0)
                    addRenderableWidget(new KvePlateButton(L + GUI_W - 20, T + 28, 14, 12, Component.literal("^"), b -> { scroll--; init(); }));
                if (scroll + ROWS_VISIBLE < snap.entries.size())
                    addRenderableWidget(new KvePlateButton(L + GUI_W - 20, T + 30 + ROWS_VISIBLE * ROW_H - 10, 14, 12, Component.literal("v"), b -> { scroll++; init(); }));
            }
        }

        addRenderableWidget(new KvePlateButton(L + 6, T + GUI_H - 30, GUI_W - 12, 12, Lang.c("gui.kve.perm.admin.refresh"),
                b -> { ClientPermissionAdminState.clear(); requestSnapshotIfStale(); init(); }));
        addRenderableWidget(new KvePlateButton(L + 6, T + GUI_H - 16, GUI_W - 12, 12, Lang.c("gui.kve.btn.back"), b -> minecraft.setScreen(parent)));
    }

    private String roleLabel(KvePermissionRole role) {
        return role.name();
    }

    private void cycleRole(String playerName, KvePermissionRole current) {
        KvePermissionRole[] v = KvePermissionRole.values();
        KvePermissionRole next = v[(current.ordinal() + 1) % v.length];
        setRole(playerName, next.name());
    }

    private void setRole(String playerName, String roleOrClear) {
        KVENetwork.requestSetRole(playerName, roleOrClear);
    }

    private void requestSnapshotIfStale() {
        long now = System.currentTimeMillis();
        if (ClientPermissionAdminState.lastSnapshot() != null && now - lastRequestAt < 1000) return;
        lastRequestAt = now;
        KVENetwork.requestAdminSnapshot();
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        int L = (width - GUI_W) / 2;
        int T = (height - GUI_H) / 2;
        KveGuiStyle.drawWindow(g, L, T, GUI_W, GUI_H, Lang.get("gui.kve.perm.admin.title"));

        PermissionAdminSyncPacket snap = ClientPermissionAdminState.lastSnapshot();
        if (snap == null) {
            KveGuiStyle.drawTextSafe(g, Lang.get("gui.kve.perm.checking"), L + 6, T + 16, GUI_W - 12, 0xFFE0C060);
        } else if (snap.entries.isEmpty()) {
            KveGuiStyle.drawTextSafe(g, "(nadie mas conectado)", L + 6, T + 16, GUI_W - 12, KveGuiStyle.th(0xFF667788));
        } else {
            String summary = "op>=" + snap.requireOpLevel + " -> " + roleLabel(snap.opRole)
                    + " | default: " + roleLabel(snap.defaultRole);
            KveGuiStyle.drawTextSafe(g, summary, L + 6, T + 16, GUI_W - 12, KveGuiStyle.th(0xFF778899));
        }

        KveGuiStyle.renderWidgets(this, g, mx, my, pt);
    }
}
