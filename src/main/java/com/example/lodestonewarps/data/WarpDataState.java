package com.example.lodestonewarps.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WarpDataState {
    private static final String FILE_NAME = "lodestonewarps_data.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, Warp> warpsByEntrance = new HashMap<>();
    private final Map<String, Warp> warpsByExit = new HashMap<>();
    private final Map<String, PendingCreation> pendingCreations = new HashMap<>();
    private final Map<String, PlayerData> playerDataMap = new HashMap<>();

    private static WarpDataState cachedInstance = null;
    private static File activeDataFile = null;
    private static MinecraftServer currentServer = null;

    public WarpDataState() {}

    public static class WarpDto {
        public String dimension;
        public int entranceX, entranceY, entranceZ;
        public String exitDimension;
        public int exitX, exitY, exitZ;
        public String creatorName;
        public long createdAt;

        public WarpDto() {}

        public WarpDto(Warp warp) {
            this.dimension = warp.getDimension().toString();
            this.entranceX = warp.getEntrancePos().getX();
            this.entranceY = warp.getEntrancePos().getY();
            this.entranceZ = warp.getEntrancePos().getZ();
            this.exitDimension = warp.getExitDimension().toString();
            this.exitX = warp.getExitPos().getX();
            this.exitY = warp.getExitPos().getY();
            this.exitZ = warp.getExitPos().getZ();
            this.creatorName = warp.getCreatorName();
            this.createdAt = warp.getCreatedAt();
        }

        public Warp toWarp() {
            Identifier dim = Identifier.parse(dimension);
            BlockPos ent = new BlockPos(entranceX, entranceY, entranceZ);
            Identifier exDim = Identifier.parse(exitDimension);
            BlockPos ex = new BlockPos(exitX, exitY, exitZ);
            return new Warp(dim, ent, exDim, ex, creatorName, createdAt);
        }
    }

    public static synchronized WarpDataState get(MinecraftServer server) {
        if (server != null && (currentServer != server || activeDataFile == null)) {
            currentServer = server;
            try {
                Path worldDir = server.getWorldPath(LevelResource.ROOT);
                File dataDir = worldDir.resolve("data").toFile();
                if (!dataDir.exists()) {
                    dataDir.mkdirs();
                }
                activeDataFile = new File(dataDir, FILE_NAME);
                System.out.println("[LodestoneWarps] Bound JSON data file to: " + activeDataFile.getAbsolutePath());

                if (activeDataFile.exists() && activeDataFile.length() > 0) {
                    WarpDataState loaded = loadFromFile(activeDataFile);
                    if (loaded != null) {
                        cachedInstance = loaded;
                        System.out.println("[LodestoneWarps] Successfully loaded " + cachedInstance.warpsByEntrance.size() + " warps from JSON on server bind.");
                        return cachedInstance;
                    }
                }
            } catch (Throwable t) {
                System.err.println("[LodestoneWarps] Failed to resolve data file path:");
                t.printStackTrace();
            }
        }

        if (cachedInstance != null) {
            return cachedInstance;
        }

        if (activeDataFile != null && activeDataFile.exists() && activeDataFile.length() > 0) {
            WarpDataState loaded = loadFromFile(activeDataFile);
            if (loaded != null) {
                cachedInstance = loaded;
                System.out.println("[LodestoneWarps] Successfully restored cached state with " + cachedInstance.warpsByEntrance.size() + " warps.");
                return cachedInstance;
            }
        }

        if (cachedInstance == null) {
            cachedInstance = new WarpDataState();
            System.out.println("[LodestoneWarps] Created new empty WarpDataState instance.");
        }
        return cachedInstance;
    }

    public static synchronized void resetCache() {
        cachedInstance = null;
        activeDataFile = null;
        currentServer = null;
        System.out.println("[LodestoneWarps] Reset cache references.");
    }

    private static WarpDataState loadFromFile(File file) {
        if (!file.exists() || file.length() == 0) return null;
        try (FileReader reader = new FileReader(file)) {
            Type listType = new TypeToken<List<WarpDto>>() {}.getType();
            List<WarpDto> dtoList = GSON.fromJson(reader, listType);
            if (dtoList != null) {
                WarpDataState state = new WarpDataState();
                for (WarpDto dto : dtoList) {
                    try {
                        Warp warp = dto.toWarp();
                        if (warp != null && warp.getEntranceKey() != null) {
                            state.warpsByEntrance.put(warp.getEntranceKey(), warp);
                            state.warpsByExit.put(warp.getExitKey(), warp);
                        }
                    } catch (Throwable t) {
                        System.err.println("[LodestoneWarps] Error parsing WarpDto: " + t.getMessage());
                    }
                }
                System.out.println("[LodestoneWarps] Successfully loaded " + state.warpsByEntrance.size() + " warps from JSON file.");
                return state;
            }
        } catch (Throwable t) {
            System.err.println("[LodestoneWarps] Error reading JSON data file " + file.getAbsolutePath() + ":");
            t.printStackTrace();
        }
        return null;
    }

    public synchronized void saveToFile() {
        if (activeDataFile == null && currentServer != null) {
            try {
                Path worldDir = currentServer.getWorldPath(LevelResource.ROOT);
                File dataDir = worldDir.resolve("data").toFile();
                if (!dataDir.exists()) dataDir.mkdirs();
                activeDataFile = new File(dataDir, FILE_NAME);
            } catch (Throwable ignored) {}
        }
        if (activeDataFile == null) {
            System.err.println("[LodestoneWarps] Cannot save JSON: activeDataFile is null.");
            return;
        }

        try {
            List<WarpDto> dtoList = new ArrayList<>();
            for (Warp warp : warpsByEntrance.values()) {
                dtoList.add(new WarpDto(warp));
            }

            // Пишем во временный файл и переставляем его на место одним move: если сервер упадёт
            // посреди записи, старый файл с варпами останется целым, а не превратится в обрезанный JSON.
            java.nio.file.Path target = activeDataFile.toPath();
            java.nio.file.Path tmp = target.resolveSibling(activeDataFile.getName() + ".tmp");

            try (FileWriter writer = new FileWriter(tmp.toFile())) {
                GSON.toJson(dtoList, writer);
            }
            try {
                java.nio.file.Files.move(tmp, target,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                java.nio.file.Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            System.out.println("[LodestoneWarps] Successfully saved " + dtoList.size() + " warps to JSON file: " + activeDataFile.getAbsolutePath());
        } catch (Throwable t) {
            System.err.println("[LodestoneWarps] Error saving JSON data file:");
            t.printStackTrace();
        }
    }

    public void setDirty() {
        saveToFile();
    }

    public void addWarp(Warp warp) {
        warpsByEntrance.put(warp.getEntranceKey(), warp);
        warpsByExit.put(warp.getExitKey(), warp);
        System.out.println("[LodestoneWarps] Added warp: " + warp.getEntranceKey() + " -> " + warp.getExitKey() + ". Total warps: " + warpsByEntrance.size());
        setDirty();
    }

    public void removeWarpByEntranceKey(String entranceKey) {
        Warp warp = warpsByEntrance.remove(entranceKey);
        if (warp != null) {
            warpsByExit.remove(warp.getExitKey());
            System.out.println("[LodestoneWarps] Removed warp: " + entranceKey + ". Remaining warps: " + warpsByEntrance.size());
            setDirty();
        }
    }

    public Warp getWarpByEntranceKey(String entranceKey) {
        return warpsByEntrance.get(entranceKey);
    }

    public Warp getWarpByExitKey(String exitKey) {
        return warpsByExit.get(exitKey);
    }

    public java.util.Collection<Warp> getAllWarps() {
        return warpsByEntrance.values();
    }

    public boolean isEntranceOrExit(String key) {
        return warpsByEntrance.containsKey(key) || warpsByExit.containsKey(key);
    }

    public PendingCreation getPendingCreation(String playerName) {
        return pendingCreations.get(playerName);
    }

    public void setPendingCreation(String playerName, PendingCreation pending) {
        pendingCreations.put(playerName, pending);
        setDirty();
    }

    public void removePendingCreation(String playerName) {
        if (pendingCreations.remove(playerName) != null) {
            setDirty();
        }
    }

    public PlayerData getPlayerData(String playerName) {
        return playerDataMap.computeIfAbsent(playerName, k -> new PlayerData());
    }

    public Map<String, PlayerData> getAllPlayerData() {
        return playerDataMap;
    }

    public void cleanupExpiredData(long currentTime) {
        boolean changed = pendingCreations.entrySet().removeIf(e -> e.getValue().isExpired(currentTime));
        changed |= playerDataMap.entrySet().removeIf(e -> e.getValue().isExpired(currentTime));
        if (changed) {
            setDirty();
        }
    }
}