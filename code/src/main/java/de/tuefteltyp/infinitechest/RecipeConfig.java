package de.tuefteltyp.infinitechest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Server-authoritative nine-slot crafting configuration.
 * Changes become active after the generated world data pack has been loaded/reloaded.
 * An empty slot is represented by an empty string. Item IDs and #tag IDs are supported.
 */
public final class RecipeConfig {
    private static final Logger LOG = LoggerFactory.getLogger("EndlessChest/Recipe");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern ID = Pattern.compile("(?:#)?[a-z0-9_.-]+:[a-z0-9_./-]+");
    public static final String[] DEFAULT = {
        "minecraft:diamond_block", "minecraft:diamond_block", "minecraft:diamond_block",
        "minecraft:diamond_block", "minecraft:chest",         "minecraft:diamond_block",
        "minecraft:diamond_block", "minecraft:diamond_block", "minecraft:diamond_block"
    };
    public String[] slots = DEFAULT.clone();
    public static RecipeConfig INSTANCE = new RecipeConfig();

    public static Path configFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("endlesschest-recipe.json");
    }

    public static void load() {
        Path path = configFile();
        try {
            if (Files.exists(path)) {
                RecipeConfig read = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), RecipeConfig.class);
                if (read != null && isValid(read.slots)) INSTANCE = read;
                else LOG.error("Invalid recipe config {}; continuing with default recipe", path);
            }
            if (!Files.exists(path)) save();
        } catch (Exception ex) {
            LOG.error("Unable to read recipe config {}; using defaults", path, ex);
            INSTANCE = new RecipeConfig();
        }
    }

    public static boolean isValid(String[] data) {
        if (data == null || data.length != 9) return false;
        int used = 0;
        for (String entry : data) {
            if (entry == null || entry.length() > 128) return false;
            if (entry.isBlank()) continue;
            if (!ID.matcher(entry).matches()) return false;
            used++;
        }
        return used > 0;
    }

    public static boolean updateSlot(int index, String id) {
        if (index < 0 || index >= 9 || id == null) return false;
        String[] candidate = INSTANCE.slots.clone();
        candidate[index] = id.strip();
        if (!isValid(candidate)) return false;
        INSTANCE.slots = candidate;
        return true;
    }

    public static void save() {
        if (!isValid(INSTANCE.slots)) {
            LOG.error("Recipe config has invalid fields; refusing to overwrite saved recipe");
            return;
        }
        try {
            Path path = configFile();
            Files.createDirectories(path.getParent());
            writeAtomic(path, GSON.toJson(INSTANCE));
        } catch (Exception e) {
            LOG.error("Could not save recipe config", e);
        }
    }

    private static void writeAtomic(Path path, String text) throws java.io.IOException {
        Path tmp = Files.createTempFile(path.getParent(), ".endlesschest-", ".tmp");
        try {
            Files.writeString(tmp, text, StandardCharsets.UTF_8);
            try { Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(tmp); }
    }

    /** Generate a server-world data pack overriding the bundled recipe.
     * This does not force world reload. Use /reload to activate edits.
     */
    public static void installPack(Path worldRoot) {
        if (!isValid(INSTANCE.slots)) {
            LOG.error("Invalid current recipe; not writing data pack");
            return;
        }
        try {
            Path pack = worldRoot.resolve("datapacks").resolve("endlesschest_recipe");
            Path recipe = pack.resolve("data").resolve("infinitechest").resolve("recipe").resolve("infinite_chest.json");
            Files.createDirectories(recipe.getParent());
            JsonObject header = new JsonObject();
            JsonObject section = new JsonObject();
            section.addProperty("description", "Endless Chest configurable recipe");
            section.addProperty("min_format", 121);
            section.addProperty("max_format", 121);
            header.add("pack", section);
            writeAtomic(pack.resolve("pack.mcmeta"), GSON.toJson(header));
            writeAtomic(recipe, GSON.toJson(buildRecipe(INSTANCE.slots)));
            LOG.info("Endless Chest recipe pack installed at {}. Run /datapack enable \"file/endlesschest_recipe\" (if needed), then /reload.", pack);
        } catch (Exception e) {
            LOG.error("Could not install recipe datapack", e);
        }
    }

    public static JsonObject buildRecipe(String[] slots) {
        if (!isValid(slots)) throw new IllegalArgumentException("Recipe requires 9 valid slots");
        JsonObject result = new JsonObject();
        result.addProperty("type", "minecraft:crafting_shaped");
        result.addProperty("category", "misc");
        // Build a distinct symbol for each unique ingredient, preserving empty slots.
        Map<String, Character> lookup = new LinkedHashMap<>();
        char next = 'A';
        JsonArray rows = new JsonArray();
        for (int row = 0; row < 3; row++) {
            StringBuilder line = new StringBuilder();
            for (int col = 0; col < 3; col++) {
                String id = slots[row * 3 + col];
                if (id.isBlank()) { line.append(' '); continue; }
                if (!lookup.containsKey(id)) lookup.put(id, next++);
                line.append(lookup.get(id));
            }
            rows.add(line.toString());
        }
        result.add("pattern", rows);
        JsonObject key = new JsonObject();
        for (Map.Entry<String, Character> e : lookup.entrySet()) key.addProperty(Character.toString(e.getValue()), e.getKey());
        result.add("key", key);
        JsonObject output = new JsonObject();
        output.addProperty("id", "infinitechest:infinite_chest");
        output.addProperty("count", 1);
        result.add("result", output);
        return result;
    }
}
