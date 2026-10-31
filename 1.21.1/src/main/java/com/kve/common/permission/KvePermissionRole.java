package com.kve.common.permission;

/**
 * Access level a player has for the KVE editor on a given server.
 * Ordinal order matters: {@link #atLeast(KvePermissionRole)} relies on it,
 * so don't reorder these without checking every call site.
 */
public enum KvePermissionRole {
    NONE,
    VIEW,
    EDIT,
    ADMIN;

    public boolean atLeast(KvePermissionRole min) {
        return this.ordinal() >= min.ordinal();
    }

    public static KvePermissionRole parse(String s, KvePermissionRole def) {
        if (s == null) return def;
        try {
            return KvePermissionRole.valueOf(s.trim().toUpperCase());
        } catch (Exception e) {
            return def;
        }
    }
}
