package com.kve.common.permission;

import java.util.function.BooleanSupplier;

/**
 * (separacion runtime/editor): el codigo del runtime que guarda o
 * exporta estado pregunta aca si el jugador puede editar/aplicar. El editor
 * (kve_editor) conecta estos ganchos a su ClientPermissionState al arrancar;
 * sin editor nadie edita, asi que el default no importa.
 */
public final class EditPermission {
    private EditPermission() {}

    public static volatile BooleanSupplier canEdit = () -> true;
    public static volatile BooleanSupplier canApply = () -> true;

    public static boolean canEdit() { return canEdit.getAsBoolean(); }
    public static boolean canApply() { return canApply.getAsBoolean(); }
}
