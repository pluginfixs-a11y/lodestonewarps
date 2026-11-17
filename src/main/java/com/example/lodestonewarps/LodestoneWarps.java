// src/main/java/com/example/lodestonewarps/LodestoneWarps.java
package com.example.lodestonewarps;

import com.example.lodestonewarps.config.ModConfig;
import com.example.lodestonewarps.event.BlockBreakHandler;
import com.example.lodestonewarps.event.RightClickHandler;
import com.example.lodestonewarps.manager.PlayerTimerManager;
import com.example.lodestonewarps.manager.WarmupManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;

public class LodestoneWarps implements ModInitializer {
    public static final String MOD_ID = "lodestonewarps";
    private static ModConfig config;

    @Override
    public void onInitialize() {
        File configFile = new File(FabricLoader.getInstance().getConfigDir().toFile(), "lodestonewarps.json");
        config = ModConfig.load(configFile);

        UseBlockCallback.EVENT.register(new RightClickHandler());
        PlayerBlockBreakEvents.BEFORE.register(new BlockBreakHandler());

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> WarmupManager.onPlayerLogout(handler.player));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            WarmupManager.tick(server);
            PlayerTimerManager.tick(server);
        });

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            com.example.lodestonewarps.data.WarpDataState.resetCache();
            com.example.lodestonewarps.data.WarpDataState.get(server);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            com.example.lodestonewarps.data.WarpDataState.get(server).saveToFile();
            com.example.lodestonewarps.data.WarpDataState.resetCache();
            config.save(configFile);
        });
    }

    public static ModConfig getConfig() {
        return config;
    }
}