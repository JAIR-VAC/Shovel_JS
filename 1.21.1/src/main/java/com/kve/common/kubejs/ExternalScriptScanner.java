package com.kve.common.kubejs;

import com.kve.CraftEditorMod;
import com.kve.common.config.KVEConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class ExternalScriptScanner {

    public static class ExternalItemStats {
        public String sourceFile = "";
        public int    maxDamage      = -1;
        public int    maxStackSize   = -1;
        public int    burnTime       = -1;
        public double attackDamage   = Double.NaN;
        public double attackSpeed    = Double.NaN;
        public boolean fireResistant    = false;
        public boolean fireResistantSet = false;
        public String  rarity           = "";
        public final Map<String, KubeJSItemAttrExporter.AttrMod> attributes = new LinkedHashMap<>();
    }

    public static class ExternalMobStats {
        public String sourceFile = "";
        public double health          = Double.NaN;
        public double armor           = Double.NaN;
        public double armorToughness  = Double.NaN;
        public double attackDamage    = Double.NaN;
        public double attackSpeed     = Double.NaN;
        public double movementSpeed   = Double.NaN;
        public double knockbackResist = Double.NaN;
        public double followRange     = Double.NaN;
        public final List<KubeJSMobExporter.AttrMod> attributeMods = new ArrayList<>();
        public String  customName        = "";
        public boolean customNameSet     = false;
        public boolean customNameVisible = false;
    }

    public static class ExternalTagStats {
        public String sourceFile = "";
        public TagExporter.RegistryType regType = TagExporter.RegistryType.ITEM;
        public String tagId = "";
        public final List<String> added   = new ArrayList<>();
        public final List<String> removed = new ArrayList<>();

        public String key() { return regType.name() + "|" + tagId; }
    }

    private static final Map<String, ExternalItemStats> ITEM_RESULTS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String, ExternalMobStats>  MOB_RESULTS  = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String, ExternalTagStats>  TAG_RESULTS  = new java.util.concurrent.ConcurrentHashMap<>();
    private static boolean scanned = false;

    public record RootStatus(Path path, boolean existed, int fileCount) {}

    private static volatile List<RootStatus> lastRootStatuses = List.of();
    private static volatile long lastScanEpochMillis = -1L;
    private static final AtomicBoolean SCANNING = new AtomicBoolean(false);

    private static final long MAX_FILE_SIZE = 2_000_000L;

    private static final Map<String, FileCacheEntry> FILE_CACHE = new HashMap<>();

    private static final class FileCacheEntry {
        final long size;
        final long lastModifiedMillis;
        final List<Map.Entry<String, ExternalItemStats>> items;
        final List<Map.Entry<String, ExternalMobStats>>  mobs;
        final List<Map.Entry<String, ExternalTagStats>>  tags;

        FileCacheEntry(long size, long lastModifiedMillis,
                       List<Map.Entry<String, ExternalItemStats>> items,
                       List<Map.Entry<String, ExternalMobStats>> mobs,
                       List<Map.Entry<String, ExternalTagStats>> tags) {
            this.size = size;
            this.lastModifiedMillis = lastModifiedMillis;
            this.items = items;
            this.mobs = mobs;
            this.tags = tags;
        }

        boolean matches(long otherSize, long otherModifiedMillis) {
            return size == otherSize && lastModifiedMillis == otherModifiedMillis;
        }
    }

    private static final Pattern MODIFY_BLOCK = Pattern.compile(
            "event\\.modify\\(\\s*['\"]([^'\"]+)['\"]\\s*,\\s*\\w+\\s*=>\\s*\\{");

    private static final Pattern MOB_TYPE_BLOCK = Pattern.compile(
            "(?:mob\\.type|event\\.entity\\.type)\\s*==\\s*['\"]([^'\"]+)['\"]\\s*\\)\\s*\\{");

    private static final Pattern TAGS_BLOCK = Pattern.compile(
            "ServerEvents\\.tags\\(\\s*['\"]([^'\"]+)['\"]\\s*,\\s*\\w+\\s*=>\\s*\\{");

    private static final Pattern TAG_ADD_REMOVE = Pattern.compile(
            "event\\.(add|remove)\\(\\s*['\"]([^'\"]+)['\"]\\s*(.*?)\\)",
            Pattern.DOTALL);

    private static final Pattern QUOTED_STRING = Pattern.compile("['\"]([^'\"]+)['\"]");

    private static final Pattern ATTACK_DAMAGE   = Pattern.compile("item\\.attackDamage\\s*=\\s*([\\d.]+)");
    private static final Pattern ATTACK_SPEED    = Pattern.compile("item\\.attackSpeed\\s*=\\s*([\\d.]+)");
    private static final Pattern MAX_DAMAGE      = Pattern.compile("item\\.maxDamage\\s*=\\s*(\\d+)");
    private static final Pattern MAX_STACK       = Pattern.compile("item\\.maxStackSize\\s*=\\s*(\\d+)");
    private static final Pattern BURN_TIME       = Pattern.compile("item\\.burnTime\\s*=\\s*(\\d+)");
    private static final Pattern FIRE_RESISTANT  = Pattern.compile("item\\.fireResistant\\s*=\\s*(true|false)");
    private static final Pattern RARITY          = Pattern.compile("item\\.rarity\\s*=\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern ADD_ATTRIBUTE   = Pattern.compile(
            "\\.addAttribute\\(\\s*['\"]([^'\"]+)['\"]\\s*,[^,]+,[^,]+,\\s*([\\d.\\-]+)\\s*,\\s*['\"]([^'\"]+)['\"]\\s*\\)",
            Pattern.DOTALL);

    private static final Pattern SET_ATTR_BASE = Pattern.compile(
            "setAttributeBaseValue\\(\\s*['\"]([^'\"]+)['\"]\\s*,\\s*([\\d.\\-]+)\\s*\\)");
    private static final Pattern SET_CUSTOM_NAME = Pattern.compile(
            "setCustomName\\(\\s*Text\\.of\\(\\s*['\"]([^'\"]*)['\"]");
    private static final Pattern SET_CUSTOM_NAME_VISIBLE = Pattern.compile(
            "setCustomNameVisible\\(\\s*(true|false)\\s*\\)");

    private static List<Path> scanRoots() {
        LinkedHashSet<Path> roots = new LinkedHashSet<>();
        roots.add(Paths.get("kubejs", "server_scripts"));
        roots.add(Paths.get("kubejs", "startup_scripts"));
        roots.add(Paths.get("kubejs", "client_scripts"));
        for (String extra : KVEConfig.externalScanExtraDirs) {
            if (extra == null || extra.isBlank()) continue;
            try {
                roots.add(Paths.get(extra.trim()));
            } catch (Exception ex) {
                CraftEditorMod.LOGGER.warn("[Shovel JS] Ignoring invalid external_scan_extra_dirs entry '{}': {}", extra, ex.getMessage());
            }
        }
        return new ArrayList<>(roots);
    }

    public static void ensureRootsExist() {
        for (Path root : scanRoots()) {
            try {
                Files.createDirectories(root);
            } catch (IOException ex) {
                CraftEditorMod.LOGGER.warn("[Shovel JS] Could not create scan folder {}: {}", root, ex.getMessage());
            }
        }
    }

    /** Runs {@link #scan()} on a background thread and hands the result back on the client thread via {@code onDone} - regexing every external script in a large modpack's kubejs folder is real I/O + CPU work that has no business blocking the render thread (see the Scan tab, the only caller of this today). Ignored (no-op) if a scan is already running. */
    private static volatile java.util.function.Supplier<java.util.concurrent.Executor> MAIN_THREAD = () -> Runnable::run;

    public static void setMainThreadExecutor(java.util.function.Supplier<java.util.concurrent.Executor> executor) {
        MAIN_THREAD = executor == null ? () -> Runnable::run : executor;
    }

    public static void scanAsync(Runnable onDone) {
        if (!SCANNING.compareAndSet(false, true)) return;
        Thread t = new Thread(() -> {
            try {
                scan();
            } finally {
                SCANNING.set(false);
                if (onDone != null) {
                    MAIN_THREAD.get().execute(onDone);
                }
            }
        }, "shovel_js-external-script-scan");
        t.setDaemon(true);
        t.start();
    }

    public static boolean isScanning() { return SCANNING.get(); }
    public static long getLastScanEpochMillis() { return lastScanEpochMillis; }
    public static List<RootStatus> getLastRootStatuses() { return lastRootStatuses; }

    public static void scan() {
        ITEM_RESULTS.clear();
        MOB_RESULTS.clear();
        TAG_RESULTS.clear();
        scanned = true;

        List<Path> roots = scanRoots();
        List<RootStatus> statuses = new ArrayList<>();

        Set<String> seenPaths = new HashSet<>();
        int filesScanned = 0;
        int filesReparsed = 0;
        for (Path root : roots) {
            boolean exists = Files.isDirectory(root);
            int rootFileCount = 0;
            if (!exists) { statuses.add(new RootStatus(root, false, 0)); continue; }
            try (Stream<Path> walk = Files.walk(root)) {
                List<Path> jsFiles = walk
                        .filter(p -> p.toString().endsWith(".js"))
                        .filter(p -> !isOurOwnScript(p))
                        .toList();
                for (Path p : jsFiles) {
                    try {
                        BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
                        if (attrs.size() > MAX_FILE_SIZE) continue;

                        String cacheKey = p.toAbsolutePath().toString();
                        seenPaths.add(cacheKey);
                        long modifiedMillis = attrs.lastModifiedTime().toMillis();

                        FileCacheEntry cached = FILE_CACHE.get(cacheKey);
                        FileCacheEntry entry;
                        if (cached != null && cached.matches(attrs.size(), modifiedMillis)) {
                            entry = cached;
                        } else {
                            String content = Files.readString(p, StandardCharsets.UTF_8);
                            String fileName = root.relativize(p).toString().replace('\\', '/');
                            entry = new FileCacheEntry(
                                    attrs.size(), modifiedMillis,
                                    scanItemBlocks(fileName, content),
                                    scanMobBlocks(fileName, content),
                                    scanTagBlocks(fileName, content));
                            FILE_CACHE.put(cacheKey, entry);
                            filesReparsed++;
                        }

                        for (var e : entry.items) {
                            if (hasAnyStat(e.getValue())) {
                                ITEM_RESULTS.merge(e.getKey(), e.getValue(), ExternalScriptScanner::mergeItem);
                            }
                        }
                        for (var e : entry.mobs) {
                            if (hasAnyStat(e.getValue())) {
                                MOB_RESULTS.merge(e.getKey(), e.getValue(), ExternalScriptScanner::mergeMob);
                            }
                        }
                        for (var e : entry.tags) {
                            TAG_RESULTS.merge(e.getKey(), e.getValue(), ExternalScriptScanner::mergeTag);
                        }
                        filesScanned++;
                        rootFileCount++;
                    } catch (IOException ex) {
                        CraftEditorMod.LOGGER.debug("[Shovel JS] fallo escaneando script '{}', se omite: {}", p, ex.toString());
                    }
                }
            } catch (IOException ex) {
                CraftEditorMod.LOGGER.debug("[Shovel JS] fallo escaneando la carpeta '{}', se omite: {}", root, ex.toString());
            }
            statuses.add(new RootStatus(root, true, rootFileCount));
        }

        FILE_CACHE.keySet().retainAll(seenPaths);

        lastRootStatuses = List.copyOf(statuses);
        lastScanEpochMillis = System.currentTimeMillis();

        CraftEditorMod.LOGGER.info(
                "[Shovel JS] External script scan complete: {} files ({} re-parsed, {} from cache), {} items, {} mobs, {} tags detected.",
                filesScanned, filesReparsed, filesScanned - filesReparsed,
                ITEM_RESULTS.size(), MOB_RESULTS.size(), TAG_RESULTS.size());
    }

    private static double parseDoubleOrDefault(String raw, double fallback) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static int parseIntOrDefault(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static boolean isOurOwnScript(Path p) {
        String s = p.toString().replace('\\', '/');
        return s.contains("/shovel_js/") || s.contains("\\shovel_js\\");
    }

    private static List<Map.Entry<String, ExternalItemStats>> scanItemBlocks(String fileName, String content) {
        List<Map.Entry<String, ExternalItemStats>> results = new ArrayList<>();
        Matcher m = MODIFY_BLOCK.matcher(content);
        while (m.find()) {
            String itemId = m.group(1);
            int bodyStart = m.end();
            int bodyEnd = findMatchingBrace(content, bodyStart - 1);
            if (bodyEnd < 0) continue;
            String body = content.substring(bodyStart, bodyEnd);

            ExternalItemStats stats = new ExternalItemStats();
            stats.sourceFile = fileName;

            Matcher mm;
            mm = ATTACK_DAMAGE.matcher(body);
            if (mm.find()) stats.attackDamage = parseDoubleOrDefault(mm.group(1), stats.attackDamage);
            mm = ATTACK_SPEED.matcher(body);
            if (mm.find()) stats.attackSpeed = parseDoubleOrDefault(mm.group(1), stats.attackSpeed);
            mm = MAX_DAMAGE.matcher(body);
            if (mm.find()) stats.maxDamage = parseIntOrDefault(mm.group(1), stats.maxDamage);
            mm = MAX_STACK.matcher(body);
            if (mm.find()) stats.maxStackSize = parseIntOrDefault(mm.group(1), stats.maxStackSize);
            mm = BURN_TIME.matcher(body);
            if (mm.find()) stats.burnTime = parseIntOrDefault(mm.group(1), stats.burnTime);
            mm = FIRE_RESISTANT.matcher(body);
            if (mm.find()) { stats.fireResistantSet = true; stats.fireResistant = Boolean.parseBoolean(mm.group(1)); }
            mm = RARITY.matcher(body); if (mm.find()) stats.rarity = mm.group(1);

            mm = ADD_ATTRIBUTE.matcher(body);
            while (mm.find()) {
                String attrId = mm.group(1);
                double value;
                try { value = Double.parseDouble(mm.group(2)); } catch (NumberFormatException ex) { continue; }
                KubeJSItemAttrExporter.Op op = KubeJSItemAttrExporter.Op.fromJs(mm.group(3));
                stats.attributes.put(attrId, new KubeJSItemAttrExporter.AttrMod(attrId, value, op));
            }

            if (hasAnyStat(stats)) {
                results.add(Map.entry(itemId, stats));
            }
        }
        return results;
    }

    private static List<Map.Entry<String, ExternalMobStats>> scanMobBlocks(String fileName, String content) {
        List<Map.Entry<String, ExternalMobStats>> results = new ArrayList<>();
        Matcher m = MOB_TYPE_BLOCK.matcher(content);
        while (m.find()) {
            String mobId = m.group(1);
            int bodyStart = m.end();
            int bodyEnd = findMatchingBrace(content, bodyStart - 1);
            if (bodyEnd < 0) continue;
            String body = content.substring(bodyStart, bodyEnd);

            ExternalMobStats stats = new ExternalMobStats();
            stats.sourceFile = fileName;

            Matcher mm = SET_ATTR_BASE.matcher(body);
            while (mm.find()) {
                String attrId = mm.group(1);
                double value;
                try { value = Double.parseDouble(mm.group(2)); } catch (NumberFormatException ex) { continue; }
                switch (attrId) {
                    case "minecraft:generic.max_health"           -> stats.health = value;
                    case "minecraft:generic.armor"                -> stats.armor = value;
                    case "minecraft:generic.armor_toughness"      -> stats.armorToughness = value;
                    case "minecraft:generic.attack_damage"        -> stats.attackDamage = value;
                    case "minecraft:generic.attack_speed"         -> stats.attackSpeed = value;
                    case "minecraft:generic.movement_speed"       -> stats.movementSpeed = value;
                    case "minecraft:generic.knockback_resistance" -> stats.knockbackResist = value;
                    case "minecraft:generic.follow_range"         -> stats.followRange = value;
                    default -> stats.attributeMods.add(
                            new KubeJSMobExporter.AttrMod(attrId, value, "", 1.0));
                }
            }

            Matcher nameMatch = SET_CUSTOM_NAME.matcher(body);
            if (nameMatch.find()) {
                stats.customNameSet = true;
                stats.customName = nameMatch.group(1).replace("\u00A7", "&");
            }
            Matcher visMatch = SET_CUSTOM_NAME_VISIBLE.matcher(body);
            if (visMatch.find()) stats.customNameVisible = Boolean.parseBoolean(visMatch.group(1));

            if (hasAnyStat(stats)) {
                results.add(Map.entry(mobId, stats));
            }
        }
        return results;
    }

    /**
     * Detects ServerEvents.tags(type, event => { event.add(...) / event.remove(...) })
     * blocks in external scripts - the same shape TagExporter.writeScript()
     * itself generates - so KVE's tag editor can show a conflict warning
     * when someone hand-edits a tag that's also managed from the GUI.
     */
    private static List<Map.Entry<String, ExternalTagStats>> scanTagBlocks(String fileName, String content) {
        Map<String, ExternalTagStats> byKey = new LinkedHashMap<>();
        Matcher m = TAGS_BLOCK.matcher(content);
        while (m.find()) {
            TagExporter.RegistryType regType = TagExporter.RegistryType.fromKubejsId(m.group(1));
            if (regType == null) continue;
            int bodyStart = m.end();
            int bodyEnd = findMatchingBrace(content, bodyStart - 1);
            if (bodyEnd < 0) continue;
            String body = content.substring(bodyStart, bodyEnd);

            Matcher am = TAG_ADD_REMOVE.matcher(body);
            while (am.find()) {
                boolean isAdd = am.group(1).equals("add");
                String tagId = am.group(2);
                String argsRest = am.group(3);

                String key = regType.name() + "|" + tagId;
                ExternalTagStats stats = byKey.computeIfAbsent(key, k -> {
                    ExternalTagStats s = new ExternalTagStats();
                    s.sourceFile = fileName;
                    s.regType = regType;
                    s.tagId = tagId;
                    return s;
                });

                List<String> target = isAdd ? stats.added : stats.removed;
                Matcher vm = QUOTED_STRING.matcher(argsRest);
                while (vm.find()) {
                    String v = vm.group(1);
                    if (!target.contains(v)) target.add(v);
                }
            }
        }

        List<Map.Entry<String, ExternalTagStats>> results = new ArrayList<>();
        for (var e : byKey.entrySet()) {
            ExternalTagStats stats = e.getValue();
            if (!stats.added.isEmpty() || !stats.removed.isEmpty()) {
                results.add(Map.entry(e.getKey(), stats));
            }
        }
        return results;
    }

    private static boolean hasAnyStat(ExternalItemStats s) {
        return s.maxDamage >= 0 || s.maxStackSize >= 0 || s.burnTime >= 0
                || !Double.isNaN(s.attackDamage) || !Double.isNaN(s.attackSpeed)
                || s.fireResistantSet || !s.rarity.isEmpty() || !s.attributes.isEmpty();
    }

    private static boolean hasAnyStat(ExternalMobStats s) {
        return !Double.isNaN(s.health) || !Double.isNaN(s.armor) || !Double.isNaN(s.armorToughness)
                || !Double.isNaN(s.attackDamage) || !Double.isNaN(s.attackSpeed) || !Double.isNaN(s.movementSpeed)
                || !Double.isNaN(s.knockbackResist) || !Double.isNaN(s.followRange)
                || !s.attributeMods.isEmpty() || s.customNameSet;
    }

    private static ExternalItemStats mergeItem(ExternalItemStats a, ExternalItemStats b) {
        b.sourceFile = a.sourceFile + ", " + b.sourceFile;
        if (a.maxDamage    >= 0 && b.maxDamage    < 0) b.maxDamage    = a.maxDamage;
        if (a.maxStackSize >= 0 && b.maxStackSize < 0) b.maxStackSize = a.maxStackSize;
        if (a.burnTime     >= 0 && b.burnTime     < 0) b.burnTime     = a.burnTime;
        if (!Double.isNaN(a.attackDamage) && Double.isNaN(b.attackDamage)) b.attackDamage = a.attackDamage;
        if (!Double.isNaN(a.attackSpeed)  && Double.isNaN(b.attackSpeed))  b.attackSpeed  = a.attackSpeed;
        if (a.fireResistantSet && !b.fireResistantSet) { b.fireResistantSet = true; b.fireResistant = a.fireResistant; }
        if (!a.rarity.isEmpty() && b.rarity.isEmpty()) b.rarity = a.rarity;
        for (var entry : a.attributes.entrySet()) b.attributes.putIfAbsent(entry.getKey(), entry.getValue());
        return b;
    }

    private static ExternalMobStats mergeMob(ExternalMobStats a, ExternalMobStats b) {
        b.sourceFile = a.sourceFile + ", " + b.sourceFile;
        if (!Double.isNaN(a.health)          && Double.isNaN(b.health))          b.health          = a.health;
        if (!Double.isNaN(a.armor)           && Double.isNaN(b.armor))           b.armor           = a.armor;
        if (!Double.isNaN(a.armorToughness)  && Double.isNaN(b.armorToughness))  b.armorToughness  = a.armorToughness;
        if (!Double.isNaN(a.attackDamage)    && Double.isNaN(b.attackDamage))    b.attackDamage    = a.attackDamage;
        if (!Double.isNaN(a.attackSpeed)     && Double.isNaN(b.attackSpeed))     b.attackSpeed     = a.attackSpeed;
        if (!Double.isNaN(a.movementSpeed)   && Double.isNaN(b.movementSpeed))   b.movementSpeed   = a.movementSpeed;
        if (!Double.isNaN(a.knockbackResist) && Double.isNaN(b.knockbackResist)) b.knockbackResist = a.knockbackResist;
        if (!Double.isNaN(a.followRange)     && Double.isNaN(b.followRange))     b.followRange     = a.followRange;
        if (a.customNameSet && !b.customNameSet) {
            b.customNameSet = true; b.customName = a.customName; b.customNameVisible = a.customNameVisible;
        }
        b.attributeMods.addAll(0, a.attributeMods);
        return b;
    }

    private static ExternalTagStats mergeTag(ExternalTagStats a, ExternalTagStats b) {
        b.sourceFile = a.sourceFile + ", " + b.sourceFile;
        for (String v : a.added)   if (!b.added.contains(v))   b.added.add(v);
        for (String v : a.removed) if (!b.removed.contains(v)) b.removed.add(v);
        return b;
    }

    private static int findMatchingBrace(String content, int openBraceIdx) {
        int depth = 0;
        boolean inString = false;
        char stringChar = 0;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        for (int i = openBraceIdx; i < content.length(); i++) {
            char c = content.charAt(i);

            if (inLineComment) {
                if (c == '\n') inLineComment = false;
                continue;
            }
            if (inBlockComment) {
                if (c == '/' && i > 0 && content.charAt(i - 1) == '*') inBlockComment = false;
                continue;
            }
            if (inString) {
                if (c == stringChar && !isEscaped(content, i)) inString = false;
                continue;
            }
            if (c == '/' && i + 1 < content.length() && content.charAt(i + 1) == '/') { inLineComment = true; continue; }
            if (c == '/' && i + 1 < content.length() && content.charAt(i + 1) == '*') { inBlockComment = true; continue; }
            if (c == '\'' || c == '"' || c == '`') { inString = true; stringChar = c; continue; }

            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    /**
     * True if the character at {@code idx} is preceded by an odd number of
     * consecutive backslashes (i.e. it is escaped). A naive "is the previous
     * char a backslash" check misidentifies strings like "a\\" (a literal
     * backslash followed by the real closing quote) as still being open,
     * which then swallows the rest of the file looking for a closing quote
     * that was already there - corrupting the brace count that follows.
     */
    private static boolean isEscaped(String content, int idx) {
        int backslashes = 0;
        int i = idx - 1;
        while (i >= 0 && content.charAt(i) == '\\') { backslashes++; i--; }
        return (backslashes % 2) == 1;
    }

    public static boolean isScanned() { return scanned; }

    public static ExternalItemStats getItemStats(String itemId) { return ITEM_RESULTS.get(itemId); }
    public static ExternalMobStats  getMobStats(String mobId)   { return MOB_RESULTS.get(mobId); }
    public static ExternalTagStats  getTagStats(String tagKey)  { return TAG_RESULTS.get(tagKey); }

    public static int getItemHitCount() { return ITEM_RESULTS.size(); }
    public static int getMobHitCount()  { return MOB_RESULTS.size(); }
    public static int getTagHitCount()  { return TAG_RESULTS.size(); }

    /** Every hit id/source-file pair found by the last {@link #scan()}, sorted, for a results list UI (the Scan tab) - one entry per item/mob/tag key, not the raw stat values (those are per-field lookups via {@link #getItemStats()}/{@link #getMobStats()}/{@link #getTagStats()}). */
    public static List<Map.Entry<String, String>> getItemHits() { return hitList(ITEM_RESULTS, s -> s.sourceFile); }
    public static List<Map.Entry<String, String>> getMobHits()  { return hitList(MOB_RESULTS, s -> s.sourceFile); }
    public static List<Map.Entry<String, String>> getTagHits()  { return hitList(TAG_RESULTS, s -> s.sourceFile); }

    private static <T> List<Map.Entry<String, String>> hitList(Map<String, T> results, java.util.function.Function<T, String> sourceFile) {
        List<Map.Entry<String, String>> out = new ArrayList<>();
        for (var e : results.entrySet()) out.add(Map.entry(e.getKey(), sourceFile.apply(e.getValue())));
        out.sort(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    public static com.google.gson.JsonObject exportHitsAsJson() {
        com.google.gson.JsonObject root = new com.google.gson.JsonObject();
        root.addProperty("scannedAtEpochMillis", lastScanEpochMillis);

        com.google.gson.JsonArray itemsArr = new com.google.gson.JsonArray();
        for (var e : ITEM_RESULTS.entrySet()) {
            ExternalItemStats s = e.getValue();
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            o.addProperty("id", e.getKey());
            o.addProperty("sourceFile", s.sourceFile);
            if (s.maxDamage >= 0) o.addProperty("maxDamage", s.maxDamage);
            if (s.maxStackSize >= 0) o.addProperty("maxStackSize", s.maxStackSize);
            if (s.burnTime >= 0) o.addProperty("burnTime", s.burnTime);
            if (!Double.isNaN(s.attackDamage)) o.addProperty("attackDamage", s.attackDamage);
            if (!Double.isNaN(s.attackSpeed)) o.addProperty("attackSpeed", s.attackSpeed);
            if (s.fireResistantSet) o.addProperty("fireResistant", s.fireResistant);
            if (!s.rarity.isEmpty()) o.addProperty("rarity", s.rarity);
            itemsArr.add(o);
        }
        root.add("items", itemsArr);

        com.google.gson.JsonArray mobsArr = new com.google.gson.JsonArray();
        for (var e : MOB_RESULTS.entrySet()) {
            ExternalMobStats s = e.getValue();
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            o.addProperty("id", e.getKey());
            o.addProperty("sourceFile", s.sourceFile);
            if (!Double.isNaN(s.health)) o.addProperty("health", s.health);
            if (!Double.isNaN(s.armor)) o.addProperty("armor", s.armor);
            if (!Double.isNaN(s.armorToughness)) o.addProperty("armorToughness", s.armorToughness);
            if (!Double.isNaN(s.attackDamage)) o.addProperty("attackDamage", s.attackDamage);
            if (!Double.isNaN(s.attackSpeed)) o.addProperty("attackSpeed", s.attackSpeed);
            if (!Double.isNaN(s.movementSpeed)) o.addProperty("movementSpeed", s.movementSpeed);
            if (!Double.isNaN(s.knockbackResist)) o.addProperty("knockbackResist", s.knockbackResist);
            if (!Double.isNaN(s.followRange)) o.addProperty("followRange", s.followRange);
            if (s.customNameSet) o.addProperty("customName", s.customName);
            mobsArr.add(o);
        }
        root.add("mobs", mobsArr);

        com.google.gson.JsonArray tagsArr = new com.google.gson.JsonArray();
        for (var e : TAG_RESULTS.entrySet()) {
            ExternalTagStats s = e.getValue();
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            o.addProperty("registryType", s.regType.name());
            o.addProperty("tagId", s.tagId);
            o.addProperty("sourceFile", s.sourceFile);
            com.google.gson.JsonArray added = new com.google.gson.JsonArray();
            s.added.forEach(added::add);
            o.add("added", added);
            com.google.gson.JsonArray removed = new com.google.gson.JsonArray();
            s.removed.forEach(removed::add);
            o.add("removed", removed);
            tagsArr.add(o);
        }
        root.add("tags", tagsArr);

        return root;
    }

    public static void reset() {
        ITEM_RESULTS.clear();
        MOB_RESULTS.clear();
        TAG_RESULTS.clear();
        FILE_CACHE.clear();
        scanned = false;
        lastRootStatuses = List.of();
        lastScanEpochMillis = -1L;
    }
}
