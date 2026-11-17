package de.tuefteltyp.infinitechest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Server-thread-only directory, persistent per world and unique across dimensions.
 * Entries for unloaded chests stay reserved until their owner is actually removed.
 */
public final class ChestDirectory {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();
    private static Path database;
    private static boolean ready;
    private ChestDirectory() { }

    public record Entry(String name, String dimension, BlockPos pos, List<String> recipients) { }
    public enum RenameResult { SUCCESS, INVALID, TAKEN, UNAVAILABLE }

    private static String key(String name) { return NameRules.key(name); }
    private static String dimension(ServerLevel level) { return level.dimension().identifier().toString(); }
    private static boolean at(Entry entry, ServerLevel level, BlockPos pos) {
        return entry.dimension().equals(dimension(level)) && entry.pos().equals(pos);
    }

    public static void open(MinecraftServer server) {
        ENTRIES.clear();
        ready = false;
        database = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("endlesschest_names.json");
        if (!Files.exists(database)) { ready = true; return; }
        try (Reader reader = Files.newBufferedReader(database, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (JsonElement element : root.getAsJsonArray("chests")) {
                JsonObject item = element.getAsJsonObject();
                String name = NameRules.normalize(item.get("name").getAsString());
                String dim = Identifier.parse(item.get("dimension").getAsString()).toString();
                BlockPos pos = new BlockPos(item.get("x").getAsInt(), item.get("y").getAsInt(), item.get("z").getAsInt());
                if (name.isEmpty() || ENTRIES.containsKey(key(name))) throw new IOException("Duplicate or invalid chest name in directory");
                List<String> saved = new ArrayList<>();
                if (item.has("recipients") && item.get("recipients").isJsonArray()) {
                    for (JsonElement raw : item.getAsJsonArray("recipients")) {
                        if (raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isString()) {
                            String recipient = NameRules.normalize(raw.getAsString());
                            if (!recipient.isEmpty() && saved.stream().noneMatch(n -> key(n).equals(key(recipient))) && saved.size() < 100)
                                saved.add(recipient);
                        }
                    }
                }
                ENTRIES.put(key(name), new Entry(name, dim, pos, List.copyOf(saved)));
            }
            ready = true;
        } catch (Exception error) {
            ENTRIES.clear();
            InfiniteChestMod.LOGGER.error("Name database could not be loaded. Naming/transfer locked to protect existing names: " + database, error);
        }
    }

    public static void close() { ENTRIES.clear(); database = null; ready = false; }

    /** World-indexed chest locations. Entries can be stale: never trust them without rechecking the block. */
    public static List<BlockPos> locations(ServerLevel level) {
        if (!ready) return List.of();
        String dim = dimension(level);
        return ENTRIES.values().stream()
            .filter(entry -> entry.dimension().equals(dim))
            .map(Entry::pos).toList();
    }


    private static boolean persist(Map<String, Entry> entries) {
        if (!ready || database == null) return false;
        Path temp = database.resolveSibling(database.getFileName().toString() + ".tmp");
        try {
            JsonArray array = new JsonArray();
            for (Entry entry : entries.values()) {
                JsonObject element = new JsonObject();
                element.addProperty("name", entry.name());
                element.addProperty("dimension", entry.dimension());
                element.addProperty("x", entry.pos().getX());
                element.addProperty("y", entry.pos().getY());
                element.addProperty("z", entry.pos().getZ());
                JsonArray recipients = new JsonArray();
                for (String target : entry.recipients()) recipients.add(target);
                element.add("recipients", recipients);
                array.add(element);
            }
            JsonObject root = new JsonObject();
            root.add("chests", array);
            Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(temp, database, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, database, StandardCopyOption.REPLACE_EXISTING);
            }
            ENTRIES.clear();
            ENTRIES.putAll(entries);
            return true;
        } catch (IOException error) {
            InfiniteChestMod.LOGGER.error("Failed to persist Endless Chest names. No rename was applied.", error);
            try { Files.deleteIfExists(temp); } catch (IOException ignored) { }
            return false;
        }
    }

    /** Called on a genuine new block placement: always generate a fresh random identity. */
    public static String registerNew(ServerLevel level, InfiniteChestBlockEntity chest) {
        if (!ready) return "";
        String name;
        do { name = "Chest-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT); }
        while (ENTRIES.containsKey(key(name)));
        Map<String, Entry> replacement = new LinkedHashMap<>(ENTRIES);
        List<String> oldNames = replacement.values().stream().filter(e -> at(e, level, chest.getBlockPos()))
            .map(Entry::name).toList();
        replacement.entrySet().removeIf(entry -> at(entry.getValue(), level, chest.getBlockPos()));
        for (var other : new ArrayList<>(replacement.entrySet())) {
            Entry e = other.getValue();
            List<String> remaining = e.recipients().stream()
                .filter(n -> oldNames.stream().noneMatch(old -> key(old).equals(key(n)))).toList();
            replacement.put(other.getKey(), new Entry(e.name(), e.dimension(), e.pos(), remaining));
        }
        replacement.put(key(name), new Entry(name, dimension(level), chest.getBlockPos().immutable(), List.of()));
        if (!persist(replacement)) return "";
        chest.updateNetworkName(name);
        return name;
    }

    public static String ensure(ServerLevel level, InfiniteChestBlockEntity chest) {
        if (!ready) return "";
        BlockPos pos = chest.getBlockPos();
        String current = NameRules.normalize(chest.networkName());
        // Existing mapping at the location always wins; this preserves names across upgrades.
        for (Entry entry : ENTRIES.values()) {
            if (at(entry, level, pos)) {
                if (!entry.name().equals(chest.networkName())) chest.updateNetworkName(entry.name());
                return entry.name();
            }
        }
        if (current.isEmpty() || ENTRIES.containsKey(key(current))) {
            do { current = "Chest-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT); }
            while (ENTRIES.containsKey(key(current)));
        }
        Map<String, Entry> replacement = new LinkedHashMap<>(ENTRIES);
        replacement.put(key(current), new Entry(current, dimension(level), pos.immutable(), List.of()));
        if (!persist(replacement)) return "";
        chest.updateNetworkName(current);
        return current;
    }

    public static RenameResult rename(ServerLevel level, InfiniteChestBlockEntity chest, String requested) {
        if (!ready || ensure(level, chest).isEmpty()) return RenameResult.UNAVAILABLE;
        String name = NameRules.normalize(requested);
        if (name.isEmpty()) return RenameResult.INVALID;
        Entry taken = ENTRIES.get(key(name));
        if (taken != null && !at(taken, level, chest.getBlockPos())) return RenameResult.TAKEN;
        if (name.equals(chest.networkName())) return RenameResult.SUCCESS;
        Map<String, Entry> replacement = new LinkedHashMap<>(ENTRIES);
        Entry oldEntry = replacement.values().stream()
            .filter(entry -> at(entry, level, chest.getBlockPos())).findFirst().orElse(null);
        if (oldEntry == null) return RenameResult.UNAVAILABLE;
        replacement.remove(key(oldEntry.name()));
        // Update saved favorites even in chests whose chunks are currently unloaded.
        for (var other : new ArrayList<>(replacement.entrySet())) {
            Entry e = other.getValue();
            List<String> updated = e.recipients().stream()
                .map(n -> key(n).equals(key(oldEntry.name())) ? name : n).toList();
            replacement.put(other.getKey(), new Entry(e.name(), e.dimension(), e.pos(), updated));
        }
        replacement.put(key(name), new Entry(name, dimension(level), chest.getBlockPos().immutable(), oldEntry.recipients()));
        if (!persist(replacement)) return RenameResult.UNAVAILABLE;
        chest.updateNetworkName(name);
        return RenameResult.SUCCESS;
    }

    public static boolean release(ServerLevel level, BlockPos pos) {
        if (!ready) return false;
        Map<String, Entry> replacement = new LinkedHashMap<>(ENTRIES);
        boolean changed = replacement.entrySet().removeIf(entry -> at(entry.getValue(), level, pos));
        if (!changed) return true;
        // Do not retain a dead destination in any chest's favorite recipients.
        for (var other : new ArrayList<>(replacement.entrySet())) {
            Entry e = other.getValue();
            List<String> remaining = e.recipients().stream().filter(n -> replacement.containsKey(key(n))).toList();
            replacement.put(other.getKey(), new Entry(e.name(), e.dimension(), e.pos(), remaining));
        }
        return persist(replacement);
    }

    /** Saved recipients belong to each chest, not to the player or client UI. */
    public static List<String> recipients(ServerLevel level, BlockPos pos) {
        return ENTRIES.values().stream().filter(e -> at(e, level, pos))
            .findFirst().map(Entry::recipients).orElse(List.of());
    }

    public enum RecipientResult { ADDED, REMOVED, DUPLICATE, NOT_FOUND, INVALID, FULL, UNAVAILABLE }

    public static RecipientResult changeRecipient(ServerLevel level, BlockPos source, String rawName, boolean add) {
        if (!ready) return RecipientResult.UNAVAILABLE;
        String name = NameRules.normalize(rawName);
        if (name.isEmpty()) return RecipientResult.INVALID;
        Entry owner = ENTRIES.values().stream().filter(e -> at(e, level, source)).findFirst().orElse(null);
        if (owner == null) return RecipientResult.UNAVAILABLE;
        Entry destination = find(name);
        if (add && (destination == null || destination == owner)) return RecipientResult.NOT_FOUND;
        List<String> changed = new ArrayList<>(owner.recipients());
        boolean contains = changed.stream().anyMatch(n -> key(n).equals(key(name)));
        if (add) {
            if (contains) return RecipientResult.DUPLICATE;
            if (changed.size() >= 100) return RecipientResult.FULL;
            changed.add(destination.name());
        } else {
            if (!contains) return RecipientResult.NOT_FOUND;
            changed.removeIf(n -> key(n).equals(key(name)));
        }
        Map<String, Entry> replacement = new LinkedHashMap<>(ENTRIES);
        replacement.put(key(owner.name()), new Entry(owner.name(), owner.dimension(), owner.pos(), List.copyOf(changed)));
        return persist(replacement) ? (add ? RecipientResult.ADDED : RecipientResult.REMOVED) : RecipientResult.UNAVAILABLE;
    }

    public static List<String> namesExcept(ServerLevel level, BlockPos from) {
        if (!ready) return List.of();
        return ENTRIES.values().stream().filter(entry -> !at(entry, level, from))
            .map(Entry::name).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public static Entry find(String name) {
        return ready && name != null ? ENTRIES.get(key(name.strip())) : null;
    }

    public static ServerLevel resolve(MinecraftServer server, Entry entry) {
        if (entry == null) return null;
        Identifier id;
        try { id = Identifier.parse(entry.dimension()); }
        catch (RuntimeException badId) { return null; }
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, id);
        return server.getLevel(dimension);
    }
}
