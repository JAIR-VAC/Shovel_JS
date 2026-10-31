package com.kve.common.permission;

import com.google.gson.*;
import com.kve.CraftEditorMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class KvePermissionManager {

    private KvePermissionManager() {}

    private static final Path FILE = Paths.get("config", "shovel_js_permissions.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static int requireOpLevel = 2;
    private static KvePermissionRole opRole = KvePermissionRole.ADMIN;
    private static KvePermissionRole defaultRole = KvePermissionRole.NONE;
    private static final Map<String, KvePermissionRole> PLAYER_OVERRIDES = new HashMap<>();

    private static boolean loaded = false;

    public static synchronized void loadIfNeeded() {
        if (loaded) return;
        load();
    }

    public static synchronized void reload() {
        loaded = false;
        load();
    }

    private static void load() {
        loaded = true;
        PLAYER_OVERRIDES.clear();
        requireOpLevel = 2;
        opRole = KvePermissionRole.ADMIN;
        defaultRole = KvePermissionRole.NONE;

        if (!Files.exists(FILE)) {
            writeDefaults();
            return;
        }
        try {
            String json = Files.readString(FILE, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("requireOpLevel")) requireOpLevel = Math.max(0, root.get("requireOpLevel").getAsInt());
            if (root.has("opRole")) opRole = KvePermissionRole.parse(root.get("opRole").getAsString(), KvePermissionRole.ADMIN);
            if (root.has("defaultRole")) defaultRole = KvePermissionRole.parse(root.get("defaultRole").getAsString(), KvePermissionRole.NONE);
            if (root.has("players")) {
                JsonObject players = root.getAsJsonObject("players");
                for (Map.Entry<String, JsonElement> e : players.entrySet()) {
                    PLAYER_OVERRIDES.put(e.getKey().toLowerCase(Locale.ROOT),
                            KvePermissionRole.parse(e.getValue().getAsString(), defaultRole));
                }
            }
            CraftEditorMod.LOGGER.info("[Shovel JS] Permissions loaded: {} override(s), defaultRole={}, opRole={} at level {}",
                    PLAYER_OVERRIDES.size(), defaultRole, opRole, requireOpLevel);
        } catch (Exception e) {
            CraftEditorMod.LOGGER.error("[Shovel JS] Could not read shovel_js_permissions.json, falling back to defaults (defaultRole=NONE): {}", e.getMessage());
        }
    }

    private static void writeDefaults() {
        PLAYER_OVERRIDES.clear();
        save();
        CraftEditorMod.LOGGER.info("[Shovel JS] Wrote default shovel_js_permissions.json (ops get ADMIN, everyone else gets NONE).");
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("requireOpLevel", requireOpLevel);
            root.addProperty("opRole", opRole.name());
            root.addProperty("defaultRole", defaultRole.name());
            JsonObject players = new JsonObject();
            if (PLAYER_OVERRIDES.isEmpty()) {
                players.addProperty("_example_player_name", "EDIT");
            } else {
                for (Map.Entry<String, KvePermissionRole> e : PLAYER_OVERRIDES.entrySet())
                    players.addProperty(e.getKey(), e.getValue().name());
            }
            root.add("players", players);
            Files.writeString(FILE, GSON.toJson(root), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] Could not write shovel_js_permissions.json: {}", e.getMessage());
        }
    }

    /**
     * Sets (or replaces) a player's role override and persists it
     * immediately - the mutator behind the in-game permission admin
     * screen, so a server admin never has to hand-edit
     * kve_permissions.json or restart the server for a role change to
     * take effect. Keyed by lowercase player name, matching how {@link
     * #resolve} looks overrides up.
     */
    public static synchronized void setPlayerRole(String playerName, KvePermissionRole role) {
        if (playerName == null || playerName.isBlank() || role == null) return;
        loadIfNeeded();
        PLAYER_OVERRIDES.put(playerName.toLowerCase(Locale.ROOT), role);
        save();
    }

    /** Removes a player's override, reverting them to whatever requireOpLevel/opRole/defaultRole would otherwise resolve. */
    public static synchronized void clearPlayerRole(String playerName) {
        if (playerName == null || playerName.isBlank()) return;
        loadIfNeeded();
        if (PLAYER_OVERRIDES.remove(playerName.toLowerCase(Locale.ROOT)) != null) save();
    }

    /** Read-only snapshot of every current name/uuid -> role override, for the admin screen's listing. */
    public static synchronized Map<String, KvePermissionRole> overridesSnapshot() {
        loadIfNeeded();
        return new LinkedHashMap<>(PLAYER_OVERRIDES);
    }

    public static synchronized int getRequireOpLevel() { loadIfNeeded(); return requireOpLevel; }
    public static synchronized KvePermissionRole getOpRole() { loadIfNeeded(); return opRole; }
    public static synchronized KvePermissionRole getDefaultRole() { loadIfNeeded(); return defaultRole; }

    public static KvePermissionRole resolve(ServerPlayer player) {
        loadIfNeeded();
        MinecraftServer server = player.getServer();
        if (server != null && !server.isDedicatedServer()) {
            if (server.isSingleplayerOwner(player.getGameProfile())) return KvePermissionRole.ADMIN;
        }

        String name = player.getGameProfile().getName();
        String uuid = player.getGameProfile().getId() != null ? player.getGameProfile().getId().toString() : "";
        if (name != null && PLAYER_OVERRIDES.containsKey(name.toLowerCase(Locale.ROOT)))
            return PLAYER_OVERRIDES.get(name.toLowerCase(Locale.ROOT));
        if (!uuid.isEmpty() && PLAYER_OVERRIDES.containsKey(uuid.toLowerCase(Locale.ROOT)))
            return PLAYER_OVERRIDES.get(uuid.toLowerCase(Locale.ROOT));

        if (player.hasPermissions(requireOpLevel)) return opRole;
        return defaultRole;
    }
}
