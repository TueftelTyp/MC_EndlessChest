package de.tuefteltyp.infinitechest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

public final class StorageConfig {
    public static StorageConfig INSTANCE = new StorageConfig();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public int itemsPerPage = 8;
    public int maxTransferDistance = 64;
    public boolean allowTransferToVanilla = true;
    public boolean allowRemoteTransfers = true;
    public boolean notifyOnDeposit = true;
    public boolean sortByQuantity = false;
    /** Authoritative server option for crafting from nearby Endless Chests. */
    public boolean enableNearbyCrafting = true;
    /** Euclidean distance in blocks between the player and chest. */
    public int nearbyCraftingRadius = 8;

    public static void load() {
        try {
            Path path = FabricLoader.getInstance().getConfigDir().resolve("mc-infinite-storage.json");
            if (Files.exists(path)) {
                StorageConfig loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), StorageConfig.class);
                if (loaded != null) INSTANCE = loaded;
            }
            INSTANCE.itemsPerPage = Math.max(1, Math.min(20, INSTANCE.itemsPerPage));
            INSTANCE.maxTransferDistance = Math.max(1, Math.min(100000, INSTANCE.maxTransferDistance));
            INSTANCE.nearbyCraftingRadius = Math.max(1, Math.min(32, INSTANCE.nearbyCraftingRadius));
            save();
        } catch (Exception e) { InfiniteChestMod.LOGGER.error("Could not load storage config", e); }
    }
    public static void save() {
        try {
            Path path = FabricLoader.getInstance().getConfigDir().resolve("mc-infinite-storage.json");
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(INSTANCE), StandardCharsets.UTF_8);
        } catch (Exception e) { InfiniteChestMod.LOGGER.error("Could not save storage config", e); }
    }
}
