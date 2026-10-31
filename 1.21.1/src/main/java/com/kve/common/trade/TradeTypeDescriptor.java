package com.kve.common.trade;

public final class TradeTypeDescriptor {

    public String entityTypeId = "";
    public String displayName = "";
    /** blank = no extra mod required beyond whatever {@code entityTypeId}'s namespace implies. */
    public String requiresMod = "";
    public boolean abstractVillagerCompatible = false;
    /**
     * (comerciantes modded): cadena de getters publicos sin
     * argumentos, separados por '.', que devuelve el "oficio" propio del mod
     * en una instancia viva - p.ej. Ribbits: {@code "getRibbitData.getProfession"}
     * (el resultado se convierte a id via getId()/ResourceLocation, ver
     * {@link MerchantTypeDetector#professionOf}). Opcional: sin esto se
     * intenta una busqueda generica por reflection.
     */
    public String professionGetter = "";
    /** Ids de oficios propios del mod para ofrecer en el picker (ademas de los observados en el mundo). Opcional. */
    public java.util.List<String> professions = new java.util.ArrayList<>();

    public TradeTypeDescriptor() {}

    public String effectiveDisplayName() {
        return (displayName == null || displayName.isBlank()) ? entityTypeId : displayName;
    }
}
