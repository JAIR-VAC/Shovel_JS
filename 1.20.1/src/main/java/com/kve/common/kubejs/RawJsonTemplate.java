package com.kve.common.kubejs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.kve.CraftEditorMod;
import com.kve.common.recipe.RecipeData;
import com.kve.common.recipetype.ChemicalRegistryHelper;
import com.kve.common.recipetype.RecipeTypeDescriptor;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class RawJsonTemplate {

    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private RawJsonTemplate() {}

    /** Outcome of a render: the JSON when every required piece resolved, otherwise what's missing (for a clear SKIPPED comment instead of a recipe the mod's codec would reject). */
    public static final class Result {
        public final JsonObject json;
        public final List<String> missing;
        Result(JsonObject json, List<String> missing) { this.json = json; this.missing = missing; }
        public boolean ok() { return json != null && missing.isEmpty(); }
    }

    static String exportJs(RecipeData r, RecipeTypeDescriptor d, String sanitizedId) {
        Result res = render(r, d);
        String mod = d.effectiveModId();
        if (!res.ok()) {
            StringBuilder sb = new StringBuilder();
            sb.append("  // ==== NO EXPORTADO: ").append(r.recipeId).append(" [").append(r.customTypeId).append("] ====\n");
            sb.append("  // Faltan datos que el formato nativo de ").append(mod).append(" exige: ")
              .append(String.join(", ", res.missing)).append("\n");
            sb.append("  // Completalos en el editor de maquina y volve a guardar.\n");
            return sb.toString();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("  // ").append(r.recipeId).append(" [").append(r.customTypeId)
          .append("] (JSON nativo de ").append(mod).append(", verificado contra el jar real - no necesita addon de KubeJS)\n");
        String body = PRETTY.toJson(res.json).replace("\n", "\n  ");
        String outputId = null;
        if (d.idFromOutput) {
            JsonElement o = res.json.get("output");
            outputId = o != null && o.isJsonPrimitive() ? o.getAsString() : outputItemId(r);
        }
        if (outputId != null) {
            sb.append("  // id = el del item de salida: ").append(mod)
              .append(" busca esta receta por id, asi reemplaza la original\n");
            sb.append("  event.custom(").append(body).append(").id('").append(outputId).append("');\n");
        } else {
            sb.append("  event.custom(").append(body).append(").id('shovel_js:").append(sanitizedId).append("');\n");
        }
        return sb.toString();
    }

    static String outputItemId(RecipeData r) {
        ItemStack out = ItemStack.EMPTY;
        if (r.machineOutputs != null) {
            for (RecipeData.MachineSlot s : r.machineOutputs) {
                if (s != null && s.item != null && !s.item.isEmpty()) { out = s.item; break; }
            }
        }
        if (out.isEmpty() && r.output != null) out = r.output;
        if (out.isEmpty()) return null;
        net.minecraft.resources.ResourceLocation key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(out.getItem());
        return key == null ? null : key.toString();
    }

    public static Result render(RecipeData r, RecipeTypeDescriptor d) {
        Ctx ctx = new Ctx(r, d);
        JsonElement out = resolve(d.rawJson, ctx, true);
        JsonObject resolved = out != null && out.isJsonObject() ? out.getAsJsonObject() : new JsonObject();
        JsonObject json = new JsonObject();
        json.addProperty("type", d.recipeSerializer);
        for (Map.Entry<String, JsonElement> e : resolved.entrySet()) {
            if (!e.getKey().equals("type")) json.add(e.getKey(), e.getValue());
        }

        if (d.slotLayout != null) {
            for (RecipeTypeDescriptor.SlotDef def : d.slotLayout.slots) {
                if (def.required && ctx.slotFor(def) == null) {
                    ctx.missing.add(def.label != null && !def.label.isBlank() ? def.label : def.id);
                }
            }
        }

        Set<String> declared = new LinkedHashSet<>();
        for (RecipeTypeDescriptor.ExtraField f : d.extraFields) declared.add(f.key);
        for (Map.Entry<String, JsonElement> e : ctx.extra.entrySet()) {
            if (declared.contains(e.getKey()) || json.has(e.getKey()) || e.getKey().equals("type")) continue;
            json.add(e.getKey(), e.getValue());
        }
        return new Result(json, new ArrayList<>(new LinkedHashSet<>(ctx.missing)));
    }

    private static JsonElement resolve(JsonElement t, Ctx ctx, boolean root) {
        if (t == null || t.isJsonNull()) return t;
        if (t.isJsonObject()) {
            JsonObject in = t.getAsJsonObject();
            JsonObject o = new JsonObject();
            int placeholders = 0, resolved = 0;
            for (Map.Entry<String, JsonElement> e : in.entrySet()) {
                boolean ph = isPlaceholder(e.getValue());
                if (ph) placeholders++;
                JsonElement v = resolve(e.getValue(), ctx, false);
                if (v == null) continue;
                if (ph) resolved++;
                o.add(e.getKey(), v);
            }
            if (!root && placeholders > 0 && resolved == 0) return null;
            return o;
        }
        if (t.isJsonArray()) {
            JsonArray a = new JsonArray();
            for (JsonElement el : t.getAsJsonArray()) {
                JsonElement v = resolve(el, ctx, false);
                if (v == null) continue;
                if (isPlaceholder(el) && v.isJsonArray()) a.addAll(v.getAsJsonArray());
                else a.add(v);
            }
            return a;
        }
        if (isPlaceholder(t)) return resolvePlaceholder(t.getAsString(), ctx);
        return t.deepCopy();
    }

    private static boolean isPlaceholder(JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() && e.getAsString().startsWith("$");
    }

    private static JsonElement resolvePlaceholder(String ph, Ctx ctx) {
        int alt = ph.indexOf("||");
        if (alt >= 0) {
            JsonElement v = resolvePlaceholder(ph.substring(0, alt), ctx);
            if (v != null) return v;
            String rest = ph.substring(alt + 2);
            if (rest.startsWith("$")) return resolvePlaceholder(rest, ctx);
            try {
                return JsonParser.parseString(ph.substring(alt + 2));
            } catch (Exception e) {
                CraftEditorMod.LOGGER.warn("[Shovel JS] rawJson for {} has an unparsable '||' fallback in '{}'.", ctx.d.recipeSerializer, ph);
                return null;
            }
        }
        String[] p = ph.substring(1).split(":");
        String kind = p[0];
        try {
            switch (kind) {
                case "input":
                case "output": {
                    List<RecipeData.MachineSlot> list = kind.equals("output") ? ctx.outputs : ctx.inputs;
                    for (int i = 0; i < list.size(); i++) {
                        if (!KubeJSExporter.isValidMachineSlot(list.get(i))) continue;
                        return formatSlot(list.get(i), p.length > 1 ? p[1] : "ingredient", p.length > 2 ? p[2] : "",
                                ctx.defForIndex(kind.equals("output"), i), ctx);
                    }
                    return null;
                }
                case "slot": {
                    RecipeTypeDescriptor.SlotDef def = ctx.def(p[1]);
                    if (def == null) return null;
                    RecipeData.MachineSlot s = ctx.slotFor(def);
                    if (s == null) return null;
                    JsonElement v = formatSlot(s, p.length > 2 ? p[2] : "ingredient", p.length > 3 ? p[3] : "", def, ctx);
                    if (v == null && def.required) {
                        ctx.missing.add((def.label != null && !def.label.isBlank() ? def.label : def.id) + " (valor no valido)");
                    }
                    return v;
                }
                case "slotKind": {
                    RecipeTypeDescriptor.SlotDef def = ctx.def(p[1]);
                    RecipeData.MachineSlot s = def == null ? null : ctx.slotFor(def);
                    return s == null ? null : new JsonPrimitive(chemicalKind(s, def, ""));
                }
                case "inputs":
                case "outputs": {
                    boolean out = kind.equals("outputs");
                    String fmt = p.length > 1 ? p[1] : "ingredient";
                    String exclude = p.length > 2 ? p[2] : "";
                    JsonArray a = new JsonArray();
                    boolean expand = !out && canExpand(ctx, fmt);
                    List<RecipeData.MachineSlot> list = out ? ctx.outputs : ctx.inputs;
                    for (int i = 0; i < list.size(); i++) {
                        RecipeData.MachineSlot s = list.get(i);
                        if (!KubeJSExporter.isValidMachineSlot(s)) continue;
                        RecipeTypeDescriptor.SlotDef def = ctx.defForIndex(out, i);
                        if (!exclude.isEmpty() && def != null && exclude.equals(def.id)) continue;
                        JsonElement v = formatSlot(s, fmt, "", def, ctx);
                        if (v == null) continue;
                        int copies = expand ? Math.max(1, stackSize(clampToMax(s, def))) : 1;
                        for (int c = 0; c < copies; c++) a.add(v.deepCopy());
                    }
                    return a.size() == 0 ? null : a;
                }
                case "inputAt":
                case "outputAt": {
                    boolean out = kind.equals("outputAt");
                    int want = Integer.parseInt(p[1]);
                    List<RecipeData.MachineSlot> list = out ? ctx.outputs : ctx.inputs;
                    int seen = 0;
                    for (int i = 0; i < list.size(); i++) {
                        if (!KubeJSExporter.isValidMachineSlot(list.get(i))) continue;
                        if (seen++ == want) {
                            return formatSlot(list.get(i), p.length > 2 ? p[2] : "ingredient", p.length > 3 ? p[3] : "",
                                    ctx.defForIndex(out, i), ctx);
                        }
                    }
                    return null;
                }
                case "field":
                    return resolveField(p, ctx);
                case "recipeId": {
                    if (ctx.d.idFromOutput) {
                        String out = outputItemId(ctx.r);
                        if (out != null) return new JsonPrimitive(out);
                    }
                    return new JsonPrimitive("shovel_js:" + KubeJSExporter.sanitizeId(ctx.r.recipeId == null ? "" : ctx.r.recipeId));
                }
                default:
                    CraftEditorMod.LOGGER.warn("[Shovel JS] rawJson for {} uses unknown placeholder '{}' - ignored.", ctx.d.recipeSerializer, ph);
                    return null;
            }
        } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
            CraftEditorMod.LOGGER.warn("[Shovel JS] rawJson for {} has a malformed placeholder '{}' - ignored.", ctx.d.recipeSerializer, ph);
            return null;
        }
    }

    static RecipeData.MachineSlot clampToMax(RecipeData.MachineSlot s, RecipeTypeDescriptor.SlotDef def) {
        if (def == null || def.maxCount <= 0 || s.type == RecipeData.MachineSlotType.FLUID) return s;
        if (stackSize(s) <= def.maxCount) return s;
        RecipeData.MachineSlot c = new RecipeData.MachineSlot();
        c.type = s.type;
        c.item = s.item.copy();
        c.fluidId = s.fluidId;
        c.fluidAmount = s.fluidAmount;
        c.tagId = s.tagId;
        c.tagCount = s.tagCount;
        c.chance = s.chance;
        if (c.type == RecipeData.MachineSlotType.ITEM) c.item.setCount(def.maxCount);
        else c.tagCount = def.maxCount;
        return c;
    }

    private static boolean canExpand(Ctx ctx, String fmt) {
        if (!expands(fmt)) return false;
        if (ctx.d.slotLayout == null) return true;
        int fixed = 0;
        for (RecipeTypeDescriptor.SlotDef def : ctx.d.slotLayout.slots) {
            if (def.isOutput()) continue;
            if (def.repeatable) return true;
            if (!"fluid".equalsIgnoreCase(def.type)) fixed++;
        }
        int total = 0;
        for (int i = 0; i < ctx.inputs.size(); i++) {
            RecipeData.MachineSlot s = ctx.inputs.get(i);
            if (!KubeJSExporter.isValidMachineSlot(s) || s.type == RecipeData.MachineSlotType.FLUID) continue;
            total += Math.max(1, stackSize(clampToMax(s, ctx.defForIndex(false, i))));
        }
        return total <= fixed;
    }

    private static boolean expands(String fmt) {
        return fmt.equals("ingredient") || fmt.equals("create_ingredient") || fmt.equals("wrapped_ingredient");
    }

    private static int stackSize(RecipeData.MachineSlot s) {
        return switch (s.type) {
            case ITEM -> s.item.getCount();
            case TAG -> s.tagCount;
            case FLUID -> 1;
        };
    }

    static JsonElement formatSlot(RecipeData.MachineSlot s, String fmt, String arg, RecipeTypeDescriptor.SlotDef def, Ctx ctx) {
        if (!KubeJSExporter.isValidMachineSlot(s)) return null;
        s = clampToMax(s, def);
        String fluid = s.type == RecipeData.MachineSlotType.FLUID ? KubeJSExporter.sourceFluidId(s.fluidId) : "";
        boolean fluidTag = fluid.startsWith("#");
        String fluidId = fluidTag ? fluid.substring(1) : fluid;
        int amount = Math.max(1, s.fluidAmount);
        switch (fmt) {
            case "ingredient":
                return ingredient(s);
            case "item_stack":
                return itemStack(s, false);
            case "create_result": {
                if (s.type == RecipeData.MachineSlotType.FLUID && !fluidTag) {
                    JsonObject o = new JsonObject();
                    o.addProperty("fluid", fluidId);
                    o.addProperty("amount", amount);
                    return o;
                }
                return itemStack(s, true);
            }
            case "item_id": {
                if (s.type != RecipeData.MachineSlotType.ITEM) return null;
                return new JsonPrimitive(Objects.requireNonNull(KubeJSExporter.itemId(s.item)));
            }
            case "mek_item": {
                JsonElement ing = ingredient(s);
                if (ing == null) return null;
                JsonObject o = new JsonObject();
                o.add("ingredient", ing);
                int n = stackSize(s);
                if (n > 1) o.addProperty("amount", n);
                return o;
            }
            case "fluid_ingredient": {
                if (s.type != RecipeData.MachineSlotType.FLUID) return null;
                JsonObject o = new JsonObject();
                o.addProperty("amount", amount);
                o.addProperty(fluidTag ? "tag" : "fluid", fluidId);
                return o;
            }
            case "fluid_stack": {
                if (s.type != RecipeData.MachineSlotType.FLUID || fluidTag) return null;
                JsonObject o = new JsonObject();
                o.addProperty("amount", amount);
                o.addProperty("fluid", fluidId);
                return o;
            }
            case "chemical":
            case "boxed_chemical": {
                if (s.type != RecipeData.MachineSlotType.FLUID) return null;
                JsonObject o = new JsonObject();
                o.addProperty("amount", amount);
                String k = chemicalKind(s, def, arg);
                if (fmt.equals("boxed_chemical")) o.addProperty("chemicalType", k);
                o.addProperty(fluidTag ? "tag" : k, fluidId);
                return o;
            }
            case "create_ingredient": {
                if (s.type == RecipeData.MachineSlotType.FLUID) {
                    JsonObject o = new JsonObject();
                    o.addProperty(fluidTag ? "fluidTag" : "fluid", fluidId);
                    o.addProperty("amount", amount);
                    return o;
                }
                return ingredient(s);
            }
            case "if_fluid": {
                if (s.type != RecipeData.MachineSlotType.FLUID || fluidTag) return null;
                return new JsonPrimitive(ifFluid(fluidId, amount));
            }
            case "block": {
                JsonObject o = new JsonObject();
                if (s.type == RecipeData.MachineSlotType.ITEM) o.addProperty("block", Objects.requireNonNull(KubeJSExporter.itemId(s.item)));
                else if (s.type == RecipeData.MachineSlotType.TAG) o.addProperty("tag", s.tagId.startsWith("#") ? s.tagId.substring(1) : s.tagId);
                else return null;
                return o;
            }
            case "id_stack": {
                if (s.type != RecipeData.MachineSlotType.ITEM) return null;
                JsonObject o = new JsonObject();
                o.addProperty("id", Objects.requireNonNull(KubeJSExporter.itemId(s.item)));
                o.addProperty("count", s.item.getCount());
                return o;
            }
            case "forge_fluid": {
                if (s.type != RecipeData.MachineSlotType.FLUID || fluidTag) return null;
                JsonObject o = new JsonObject();
                o.addProperty("Amount", amount);
                o.addProperty("FluidName", fluidId);
                return o;
            }
            case "irons_fluid": {
                if (s.type != RecipeData.MachineSlotType.FLUID || fluidTag) return null;
                JsonObject o = new JsonObject();
                o.addProperty("Amount", amount);
                o.addProperty("FluidName", fluidId);
                if ("irons_spellbooks:potion".equals(fluidId)) {
                    JsonElement pv = arg == null || arg.isBlank() ? null : resolveField(new String[]{"field", arg}, ctx);
                    String potion = pv != null && pv.isJsonPrimitive() ? pv.getAsString().trim() : "";
                    if (potion.isEmpty()) {
                        ctx.missing.add("Pocion del fluido irons_spellbooks:potion");
                        return null;
                    }
                    JsonObject tag = new JsonObject();
                    tag.addProperty("irons_spellbooks:bottle_type", "REGULAR");
                    tag.addProperty("Potion", potion);
                    o.add("Tag", tag);
                }
                return o;
            }
            case "ie_ingredient": {
                return ieIngredient(s);
            }
            case "ie_output": {
                if (s.type == RecipeData.MachineSlotType.ITEM) return itemStack(s, false);
                return ieIngredient(s);
            }
            case "bp_drop": {
                JsonElement st = itemStack(s, false);
                if (st == null) return null;
                JsonObject o = new JsonObject();
                o.addProperty("chance", s.chance < 1.0 ? Math.round(Math.max(0.0, s.chance) * 100.0) / 100.0 : 1.0);
                o.add("output", st);
                return o;
            }
            case "thermal_ingredient": {
                if (s.type == RecipeData.MachineSlotType.FLUID) {
                    JsonObject o = new JsonObject();
                    o.addProperty(fluidTag ? "fluid_tag" : "fluid", fluidId);
                    o.addProperty("amount", amount);
                    return o;
                }
                JsonElement ing = ingredient(s);
                if (ing == null) return null;
                int n = stackSize(s);
                if (n > 1) ing.getAsJsonObject().addProperty("count", n);
                return ing;
            }
            case "thermal_output": {
                if (s.type == RecipeData.MachineSlotType.FLUID) {
                    if (fluidTag) return null;
                    JsonObject o = new JsonObject();
                    o.addProperty("fluid", fluidId);
                    o.addProperty("amount", amount);
                    return o;
                }
                return itemStack(s, true);
            }
            case "astra_fluid": {
                if (s.type != RecipeData.MachineSlotType.FLUID || fluidTag) return null;
                JsonObject o = new JsonObject();
                o.addProperty("fluid", fluidId);
                o.addProperty("millibuckets", amount);
                return o;
            }
            case "counted_ingredient": {
                JsonElement ing = ingredient(s);
                if (ing == null) return null;
                JsonObject o = ing.getAsJsonObject();
                o.addProperty("count", stackSize(s));
                return o;
            }
            case "entity_id": {
                if (s.type != RecipeData.MachineSlotType.ITEM) return null;
                if (!(s.item.getItem() instanceof net.minecraft.world.item.SpawnEggItem egg)) return null;
                net.minecraft.resources.ResourceLocation key =
                        net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(egg.getType(null));
                return key == null ? null : new JsonPrimitive(key.toString());
            }
            case "tc_entity": {
                if (s.type == RecipeData.MachineSlotType.TAG) {
                    JsonObject o = new JsonObject();
                    o.addProperty("tag", s.tagId.startsWith("#") ? s.tagId.substring(1) : s.tagId);
                    return o;
                }
                JsonElement ent = formatSlot(s, "entity_id", "", def, ctx);
                if (ent == null) return null;
                JsonObject o = new JsonObject();
                o.add("type", ent);
                return o;
            }
            case "weighted_entity": {
                JsonElement ent = formatSlot(s, "entity_id", "", def, ctx);
                if (ent == null) return null;
                JsonObject o = new JsonObject();
                o.add("mob", ent);
                o.addProperty("weight", Math.max(1, stackSize(s)));
                return o;
            }
            case "astra_fluid_ingredient": {
                if (s.type != RecipeData.MachineSlotType.FLUID) return null;
                JsonObject ing = new JsonObject();
                ing.addProperty(fluidTag ? "tag" : "fluid", fluidId);
                JsonObject o = new JsonObject();
                o.add("ingredient", ing);
                o.addProperty("millibuckets", amount);
                return o;
            }
            case "apoth_salvage": {
                if (s.type != RecipeData.MachineSlotType.ITEM) return null;
                int max = Math.max(1, s.item.getCount());
                int min = Math.max(0, Math.min(max, (int) Math.round(max * Math.max(0.0, Math.min(1.0, s.chance)))));
                JsonObject stack = new JsonObject();
                stack.addProperty("item", Objects.requireNonNull(KubeJSExporter.itemId(s.item)));
                JsonObject o = new JsonObject();
                o.addProperty("min_count", min);
                o.addProperty("max_count", max);
                o.add("stack", stack);
                return o;
            }
            case "de_catalyst": {
                JsonElement ing = ingredient(s);
                if (ing == null) return null;
                int n = stackSize(s);
                if (n <= 1) return ing;
                JsonObject o = new JsonObject();
                o.addProperty("type", "draconicevolution:ingredient_stack");
                o.addProperty("count", n);
                JsonArray items = new JsonArray();
                items.add(ing);
                o.add("items", items);
                return o;
            }
            case "wrapped_ingredient": {
                JsonElement ing = ingredient(s);
                if (ing == null) return null;
                JsonObject o = new JsonObject();
                o.add("item", ing);
                return o;
            }
            case "ars_crush_result": {
                if (s.type != RecipeData.MachineSlotType.ITEM) return null;
                JsonObject o = new JsonObject();
                o.addProperty("item", Objects.requireNonNull(KubeJSExporter.itemId(s.item)));
                o.addProperty("chance", Math.round(Math.max(0.0, Math.min(1.0, s.chance)) * 100.0) / 100.0);
                if (s.item.getCount() > 1) o.addProperty("count", s.item.getCount());
                return o;
            }
            case "bm_added_output": {
                JsonElement st = itemStack(s, false);
                if (st == null) return null;
                JsonObject o = new JsonObject();
                o.add("type", st);
                o.addProperty("chance", Math.round(Math.max(0.0, Math.min(1.0, s.chance)) * 100.0) / 100.0);
                o.addProperty("mainchance", 0.0);
                return o;
            }
            case "amount":
                return new JsonPrimitive(s.type == RecipeData.MachineSlotType.FLUID ? amount : stackSize(s));
            case "chance": {
                // Mekanism sawmill "secondaryChance": the slot's own chance
                // (Ctrl+rueda sobre el slot de salida), must stay in (0, 1].
                double c = Math.round(Math.max(0.01, Math.min(1.0, s.chance)) * 100.0) / 100.0;
                return new JsonPrimitive(c);
            }
            default:
                CraftEditorMod.LOGGER.warn("[Shovel JS] rawJson for {} uses unknown slot format '{}' - ignored.", ctx.d.recipeSerializer, fmt);
                return null;
        }
    }

    private static JsonElement ieIngredient(RecipeData.MachineSlot s) {
        JsonElement ing = ingredient(s);
        if (ing == null) return null;
        int n = stackSize(s);
        if (n <= 1) return ing;
        JsonObject o = new JsonObject();
        o.add("base_ingredient", ing);
        o.addProperty("count", n);
        return o;
    }

    private static JsonElement ingredient(RecipeData.MachineSlot s) {
        JsonObject o = new JsonObject();
        if (s.type == RecipeData.MachineSlotType.ITEM) {
            o.addProperty("item", Objects.requireNonNull(KubeJSExporter.itemId(s.item)));
            return o;
        }
        if (s.type == RecipeData.MachineSlotType.TAG) {
            o.addProperty("tag", s.tagId.startsWith("#") ? s.tagId.substring(1) : s.tagId);
            return o;
        }
        return null;
    }

    private static JsonElement itemStack(RecipeData.MachineSlot s, boolean withChance) {
        if (s.type != RecipeData.MachineSlotType.ITEM) return null;
        ItemStack st = s.item;
        JsonObject o = new JsonObject();
        o.addProperty("item", Objects.requireNonNull(KubeJSExporter.itemId(st)));
        if (st.getCount() > 1) o.addProperty("count", st.getCount());
        if (KubeJSExporter.hasMeaningfulTag(st)) o.addProperty("nbt", Objects.requireNonNull(st.getTag()).toString());
        if (withChance && s.chance < 1.0) o.addProperty("chance", Math.round(Math.max(0.0, s.chance) * 100.0) / 100.0);
        return o;
    }

    static String ifFluid(String fluidId, long amount) {
        return "{Amount:" + amount + ",FluidName:\"" + fluidId + "\"}";
    }

    private static String chemicalKind(RecipeData.MachineSlot s, RecipeTypeDescriptor.SlotDef def, String arg) {
        if (arg != null && !arg.isBlank()) return arg;
        List<String> kinds = ChemicalRegistryHelper.parseKinds(def == null ? "" : def.chemical);
        if (kinds.size() == 1) return kinds.get(0);
        String id = s.fluidId == null ? "" : s.fluidId.trim();
        String found = id.startsWith("#") ? null : ChemicalRegistryHelper.kindOf(id, kinds);
        if (found != null) return found;
        return kinds.isEmpty() ? "gas" : kinds.get(0);
    }

    private static JsonElement resolveField(String[] p, Ctx ctx) {
        String key = p[1];
        String fmt = p.length > 2 ? p[2] : "";
        RecipeTypeDescriptor.ExtraField f = ctx.field(key);
        JsonElement v = ctx.extra.has(key) ? ctx.extra.get(key) : null;
        boolean blank = v == null || v.isJsonNull() || (v.isJsonPrimitive() && v.getAsJsonPrimitive().isString() && v.getAsString().isBlank());
        if (blank) {
            String def = f == null ? "" : f.defaultValue;
            if (def == null || def.isBlank()) {
                if (f != null && f.required) ctx.missing.add(f.effectiveLabel());
                return null;
            }
            v = new JsonPrimitive(def);
        }
        if (!fmt.isEmpty() && !"list".equals(fmt) && v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()
                && (f == null || f.type == null || !"snbt".equalsIgnoreCase(f.type))) {
            String raw = v.getAsString().trim();
            if (raw.startsWith("[") || raw.startsWith("{")) {
                try { return JsonParser.parseString(raw); } catch (Exception ignored) {}
            }
        }
        switch (fmt) {
            case "": return typed(v, f);
            case "ingredient": {
                if (!v.isJsonPrimitive()) return v;
                String s = v.getAsString().trim();
                JsonObject o = new JsonObject();
                if (s.startsWith("#")) o.addProperty("tag", s.substring(1));
                else o.addProperty("item", s);
                return o;
            }
            case "item_stack": {
                if (!v.isJsonPrimitive()) return v;
                JsonObject o = new JsonObject();
                o.addProperty("item", v.getAsString().trim());
                return o;
            }
            case "if_fluid": {
                if (!v.isJsonPrimitive()) return v;
                long amount = fieldAmount(ctx, p.length > 3 ? p[3] : null, 1000);
                return new JsonPrimitive(ifFluid(v.getAsString().trim(), Math.max(1, amount)));
            }
            case "ie_output": {
                if (!v.isJsonPrimitive()) return v;
                String id = v.getAsString().trim();
                if (id.isEmpty() || id.equals("minecraft:air")) return null;
                JsonObject o = new JsonObject();
                if (id.startsWith("#")) o.addProperty("tag", id.substring(1));
                else o.addProperty("item", id);
                return o;
            }
            case "state_ingredient": {
                if (!v.isJsonPrimitive()) return v;
                String id = v.getAsString().trim();
                if (id.isEmpty()) return null;
                JsonObject o = new JsonObject();
                if (id.startsWith("#")) {
                    o.addProperty("type", "tag");
                    o.addProperty("tag", id.substring(1));
                } else {
                    o.addProperty("type", "block");
                    o.addProperty("block", id);
                }
                return o;
            }
            case "fluid_in": {
                if (!v.isJsonPrimitive()) return v;
                String id = v.getAsString().trim();
                if (id.isEmpty()) return null;
                JsonObject o = new JsonObject();
                o.addProperty("amount", Math.max(1, fieldAmount(ctx, p.length > 3 ? p[3] : null, 1000)));
                if (id.startsWith("#")) o.addProperty("tag", id.substring(1));
                else o.addProperty("fluid", KubeJSExporter.sourceFluidId(id));
                return o;
            }
            case "thermal_fluid": {
                if (!v.isJsonPrimitive()) return v;
                String id = v.getAsString().trim();
                if (id.isEmpty()) return null;
                JsonObject o = new JsonObject();
                if (id.startsWith("#")) o.addProperty("fluid_tag", id.substring(1));
                else o.addProperty("fluid", KubeJSExporter.sourceFluidId(id));
                o.addProperty("amount", Math.max(1, fieldAmount(ctx, p.length > 3 ? p[3] : null, 1000)));
                return o;
            }
            case "create_fluid": {
                if (!v.isJsonPrimitive()) return v;
                String id = v.getAsString().trim();
                JsonObject o = new JsonObject();
                if (id.startsWith("#")) o.addProperty("fluidTag", id.substring(1));
                else o.addProperty("fluid", id);
                o.addProperty("amount", Math.max(1, fieldAmount(ctx, p.length > 3 ? p[3] : null, 1000)));
                return o;
            }
            case "list": {
                if (!v.isJsonPrimitive()) return v;
                JsonArray a = new JsonArray();
                for (String part : v.getAsString().split("[,\\n]")) {
                    if (!part.isBlank()) a.add(part.trim());
                }
                return a.size() == 0 ? null : a;
            }
            case "block_ingredient": {
                if (!v.isJsonPrimitive()) return v;
                String id = v.getAsString().trim();
                JsonObject o = new JsonObject();
                if (id.startsWith("#")) o.addProperty("tag", id.substring(1));
                else o.addProperty("block", id);
                return o;
            }
            case "chemical_ingredient": {
                if (!v.isJsonPrimitive()) return v;
                String id = v.getAsString().trim();
                String chemKind = p.length > 3 && !p[3].isBlank() ? p[3] : "gas";
                long amount = fieldAmount(ctx, p.length > 4 ? p[4] : null, 1);
                JsonObject o = new JsonObject();
                o.addProperty("amount", Math.max(1, amount));
                if (id.startsWith("#")) o.addProperty("tag", id.substring(1));
                else o.addProperty(chemKind, id);
                return o;
            }
            default:
                CraftEditorMod.LOGGER.warn("[Shovel JS] rawJson for {} uses unknown field format '{}' - ignored.", ctx.d.recipeSerializer, fmt);
                return typed(v, f);
        }
    }

    private static long fieldAmount(Ctx ctx, String amountKey, long fallback) {
        if (amountKey == null || amountKey.isBlank()) return fallback;
        JsonElement a = resolveField(new String[]{"field", amountKey}, ctx);
        if (a != null && a.isJsonPrimitive()) {
            try { return a.getAsLong(); } catch (Exception ignored) {}
        }
        return fallback;
    }

    private static JsonElement typed(JsonElement v, RecipeTypeDescriptor.ExtraField f) {
        if (!v.isJsonPrimitive() || f == null || f.type == null) return v;
        String s = v.getAsString().trim();
        try {
            switch (f.type.toLowerCase(Locale.ROOT)) {
                case "int":
                case "long":
                    return new JsonPrimitive(Long.parseLong(s));
                case "float":
                case "double":
                    double dv = Double.parseDouble(s);
                    return dv == Math.rint(dv) && !s.contains(".") ? new JsonPrimitive((long) dv) : new JsonPrimitive(dv);
                case "boolean":
                    return new JsonPrimitive(Boolean.parseBoolean(s));
                case "auto":
                    if (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("false")) return new JsonPrimitive(Boolean.parseBoolean(s));
                    try { return new JsonPrimitive(Long.parseLong(s)); } catch (NumberFormatException ignored) {}
                    try { return new JsonPrimitive(Double.parseDouble(s)); } catch (NumberFormatException ignored) {}
                    return new JsonPrimitive(s);
                case "snbt":
                    return new JsonPrimitive(s);
                default:
                    if (s.startsWith("[") || s.startsWith("{")) {
                        try { return JsonParser.parseString(s); } catch (Exception ignored) {}
                    }
                    return new JsonPrimitive(s);
            }
        } catch (NumberFormatException e) {
            return v;
        }
    }

    static final class Ctx {
        final RecipeData r;
        final RecipeTypeDescriptor d;
        final List<RecipeData.MachineSlot> inputs;
        final List<RecipeData.MachineSlot> outputs;
        final JsonObject extra;
        final List<String> missing = new ArrayList<>();

        Ctx(RecipeData r, RecipeTypeDescriptor d) {
            this.r = r;
            this.d = d;
            List<RecipeData.MachineSlot> in = r.machineInputs == null ? new ArrayList<>() : r.machineInputs;
            List<RecipeData.MachineSlot> out = r.machineOutputs == null ? new ArrayList<>() : r.machineOutputs;
            if (in.isEmpty() && out.isEmpty()) {
                in = new ArrayList<>();
                out = new ArrayList<>();
                if (r.shapelessIngredients != null) for (ItemStack s : r.shapelessIngredients) addItem(in, s);
                if (r.ingredients != null) for (ItemStack s : r.ingredients) addItem(in, s);
                addItem(out, r.output);
                addItem(out, r.secondaryOutput);
            }
            this.inputs = in;
            this.outputs = out;
            JsonObject ex = KubeJSExporter.parseExtraJsonSafe(r.customExtraJson);
            this.extra = ex == null ? new JsonObject() : ex;
        }

        private static void addItem(List<RecipeData.MachineSlot> list, ItemStack s) {
            if (s == null || s.isEmpty()) return;
            RecipeData.MachineSlot slot = new RecipeData.MachineSlot();
            slot.item = s.copy();
            list.add(slot);
        }

        RecipeTypeDescriptor.ExtraField field(String key) {
            for (RecipeTypeDescriptor.ExtraField f : d.extraFields) if (f.key.equals(key)) return f;
            return null;
        }

        RecipeTypeDescriptor.SlotDef def(String id) {
            if (d.slotLayout != null) {
                for (RecipeTypeDescriptor.SlotDef s : d.slotLayout.slots) if (s.id.equals(id)) return s;
            }
            CraftEditorMod.LOGGER.warn("[Shovel JS] rawJson for {} references unknown slot id '{}'.", d.recipeSerializer, id);
            return null;
        }

        private List<RecipeTypeDescriptor.SlotDef> roleDefs(boolean output) {
            List<RecipeTypeDescriptor.SlotDef> out = new ArrayList<>();
            if (d.slotLayout != null) {
                for (RecipeTypeDescriptor.SlotDef s : d.slotLayout.slots) if (s.isOutput() == output) out.add(s);
            }
            return out;
        }

        RecipeData.MachineSlot slotFor(RecipeTypeDescriptor.SlotDef def) {
            List<RecipeTypeDescriptor.SlotDef> defs = roleDefs(def.isOutput());
            List<RecipeData.MachineSlot> list = def.isOutput() ? outputs : inputs;
            RecipeTypeDescriptor.SlotDef rep = null;
            for (RecipeTypeDescriptor.SlotDef s : defs) if (s.repeatable) { rep = s; break; }
            int fixedIdx = 0;
            for (RecipeTypeDescriptor.SlotDef s : defs) {
                if (s == rep) continue;
                if (s == def) break;
                fixedIdx++;
            }
            int idx = def == rep ? defs.size() - 1 : fixedIdx;
            if (idx < 0 || idx >= list.size()) return null;
            RecipeData.MachineSlot s = list.get(idx);
            return KubeJSExporter.isValidMachineSlot(s) ? s : null;
        }

        RecipeTypeDescriptor.SlotDef defForIndex(boolean output, int i) {
            List<RecipeTypeDescriptor.SlotDef> defs = roleDefs(output);
            if (defs.isEmpty()) return null;
            RecipeTypeDescriptor.SlotDef rep = null;
            for (RecipeTypeDescriptor.SlotDef s : defs) if (s.repeatable) { rep = s; break; }
            int k = 0;
            for (RecipeTypeDescriptor.SlotDef s : defs) {
                if (s == rep) continue;
                if (k == i) return s;
                k++;
            }
            return rep != null ? rep : defs.get(defs.size() - 1);
        }
    }
}
