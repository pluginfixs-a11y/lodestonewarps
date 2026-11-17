// src/main/java/com/example/lodestonewarps/manager/WarmupManager.java
package com.example.lodestonewarps.manager;

import com.example.lodestonewarps.LodestoneWarps;
import com.example.lodestonewarps.config.ModConfig;
import com.example.lodestonewarps.data.PlayerData;
import com.example.lodestonewarps.data.Warp;
import com.example.lodestonewarps.data.WarpDataState;
import com.example.lodestonewarps.util.ReflectionUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;


import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WarmupManager {

    public static class WarmupSession {
        public final String playerName;
        public final Warp warp;
        public long startTime;
        public int stabilityTicks = 0;
        public boolean preStabilityPassed = false;
        public Vec3 initialPos;
        public long lastRetryBlockUntil = 0;

        public WarmupSession(String playerName, Warp warp, long startTime, Vec3 initialPos) {
            this.playerName = playerName;
            this.warp = warp;
            this.startTime = startTime;
            this.initialPos = initialPos;
        }
    }

    public static class AnimationSession {
        public final ServerLevel level;
        public final BlockPos entrancePos;
        public final long startTime;
        public final boolean shouldExplode;

        public AnimationSession(ServerLevel level, BlockPos entrancePos, long startTime, boolean shouldExplode) {
            this.level = level;
            this.entrancePos = entrancePos;
            this.startTime = startTime;
            this.shouldExplode = shouldExplode;
        }
    }

    private static final Map<String, WarmupSession> activeSessions = new ConcurrentHashMap<>();
    private static final Map<String, Long> cancelRetryMap = new ConcurrentHashMap<>();
    private static final List<AnimationSession> activeAnimations = Collections.synchronizedList(new ArrayList<>());
    private static final Map<String, ChunkTicketHolder> activeTickets = new ConcurrentHashMap<>();

    private static class ChunkTicketHolder {
        public final ServerLevel level;
        public final ChunkPos chunkPos;
        public final long expiresAt;

        public ChunkTicketHolder(ServerLevel level, ChunkPos chunkPos, long expiresAt) {
            this.level = level;
            this.chunkPos = chunkPos;
            this.expiresAt = expiresAt;
        }
    }

    public static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        ModConfig config = LodestoneWarps.getConfig();

        tickTickets(now);
        tickAnimations(server, now, config);
        tickAmbientAura(server);
        tickSessions(server, now, config);
    }

    private static void tickAmbientAura(MinecraftServer server) {
        if (server.getTickCount() % 15 != 0) return;
        WarpDataState dataState = WarpDataState.get(server);
        ParticleOptions enchantPart = ReflectionUtils.getParticleType("minecraft:enchant", ParticleTypes.PORTAL);
        ParticleOptions witchPart = ReflectionUtils.getParticleType("minecraft:witch", ParticleTypes.PORTAL);

        for (Warp warp : dataState.getAllWarps()) {
            try {
                ServerLevel entLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION, warp.getDimension()));
                if (entLevel != null && !entLevel.players().isEmpty()) {
                    BlockPos p = warp.getEntrancePos();
                    if (entLevel.getBlockState(p).is(Blocks.LODESTONE)) {
                        ReflectionUtils.sendParticles(entLevel, enchantPart, p.getX() + 0.5, p.getY() + 1.1, p.getZ() + 0.5, 3, 0.4, 0.4, 0.4, 0.1);
                    }
                }
            } catch (Throwable ignored) {}

            try {
                ServerLevel exLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION, warp.getExitDimension()));
                if (exLevel != null && !exLevel.players().isEmpty()) {
                    BlockPos p = warp.getExitPos();
                    if (exLevel.getBlockState(p).is(Blocks.LODESTONE)) {
                        ReflectionUtils.sendParticles(exLevel, witchPart, p.getX() + 0.5, p.getY() + 1.1, p.getZ() + 0.5, 2, 0.4, 0.4, 0.4, 0.05);
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    private static void tickTickets(long now) {
        activeTickets.entrySet().removeIf(entry -> {
            ChunkTicketHolder ticket = entry.getValue();
            if (now >= ticket.expiresAt) {
                ticket.level.setChunkForced(ticket.chunkPos.x(), ticket.chunkPos.z(), false);
                return true;
            }
            return false;
        });
    }

    private static void tickAnimations(MinecraftServer server, long now, ModConfig config) {
        synchronized (activeAnimations) {
            Iterator<AnimationSession> iterator = activeAnimations.iterator();
            while (iterator.hasNext()) {
                AnimationSession anim = iterator.next();
                double elapsed = (now - anim.startTime) / 1000.0;

                if (elapsed <= 0.5) {
                    ReflectionUtils.sendParticles(
                            anim.level,
                            ParticleTypes.SMOKE,
                            anim.entrancePos.getX() + 0.5,
                            anim.entrancePos.getY() + 1.2,
                            anim.entrancePos.getZ() + 0.5,
                            10, 0.2, 0.2, 0.2, 0.02
                    );
                } else if (elapsed <= 1.0) {
                    if (anim.shouldExplode) {
                        ReflectionUtils.sendParticles(
                                anim.level,
                                ParticleTypes.FLAME,
                                anim.entrancePos.getX() + 0.5,
                                anim.entrancePos.getY() + 1.2,
                                anim.entrancePos.getZ() + 0.5,
                                20, 0.4, 0.4, 0.4, 0.05
                        );
                    } else {
                        ReflectionUtils.sendParticles(
                                anim.level,
                                ParticleTypes.LARGE_SMOKE,
                                anim.entrancePos.getX() + 0.5,
                                anim.entrancePos.getY() + 1.2,
                                anim.entrancePos.getZ() + 0.5,
                                15, 0.3, 0.3, 0.3, 0.02
                        );
                    }
                } else {
                    iterator.remove();
                    if (anim.shouldExplode) {
                        WarpManager.executeExplosion(anim.level, anim.entrancePos);
                    }
                }
            }
        }
    }

    private static void tickSessions(MinecraftServer server, long now, ModConfig config) {
        WarpDataState dataState = WarpDataState.get(server);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String playerName = getPlayerName(player);

            if (isCreative(player) && !config.allow_creative) continue;
            if (isSpectator(player) && !config.allow_spectator) continue;

            ServerLevel level = getLevel(player);
            BlockPos feetPos = getBlockPos(player);
            BlockPos underFeet = feetPos.below();
            String dimLoc = ReflectionUtils.getDimensionLocation(level);

            String blockKey1 = dimLoc + ":" + underFeet.getX() + "," + underFeet.getY() + "," + underFeet.getZ();
            String blockKey2 = dimLoc + ":" + feetPos.getX() + "," + feetPos.getY() + "," + feetPos.getZ();

            Warp warp = dataState.getWarpByEntranceKey(blockKey1);
            if (warp == null) {
                warp = dataState.getWarpByEntranceKey(blockKey2);
            }

            if (warp != null && isPlayerInZone(player, warp.getEntrancePos(), config)) {
                Long retryTime = cancelRetryMap.get(playerName);
                if (retryTime != null && now < retryTime) {
                    continue;
                }

                PlayerData pData = dataState.getPlayerData(playerName);
                boolean bypassCooldown = (isCreative(player) && config.creative_bypass_cooldown) ||
                        (isSpectator(player) && config.spectator_bypass_cooldown);

                if (!bypassCooldown && now < pData.getCooldownUntil()) {
                    continue;
                }

                net.minecraft.world.entity.Entity pEnt = (net.minecraft.world.entity.Entity) player;
                net.minecraft.world.entity.LivingEntity pLiv = (net.minecraft.world.entity.LivingEntity) player;

                if (pEnt.getVehicle() != null || pLiv.isSleeping() || pLiv.isSwimming() || pLiv.isFallFlying()) {
                    continue;
                }

                // Require 100% full health to warp -> play dark smoke error animation if not full HP
                float maxHp = pLiv.getMaxHealth();
                float currentHp = pLiv.getHealth();
                if (currentHp < maxHp - 0.01f) {
                    cancelSession(player, null, true);
                    continue;
                }

                // Check if entrance lodestone was destroyed in the world (throttled to once per second / 20 ticks)
                if (server.getTickCount() % 20 == 0) {
                    boolean entranceValid = level.getBlockState(warp.getEntrancePos()).is(Blocks.LODESTONE);
                    if (!entranceValid) {
                        dataState.removeWarpByEntranceKey(warp.getEntranceKey());
                        System.out.println("[LodestoneWarps] Auto-removed broken entrance lodestone: " + warp.getEntranceKey());
                        continue;
                    }
                }

                final Warp targetWarp = warp;
                WarmupSession session = activeSessions.computeIfAbsent(playerName, k -> new WarmupSession(playerName, targetWarp, now, getPosition(player)));

                if (!session.preStabilityPassed) {
                    session.stabilityTicks++;
                    if (session.stabilityTicks >= 10) {
                        session.preStabilityPassed = true;
                        session.initialPos = getPosition(player);
                        session.startTime = now;
                        if (config.exit_chunk_preload && activeTickets.size() < config.max_active_chunk_tickets) {
                            addChunkTicket(server, warp, config, now);
                        }
                    }
                    continue;
                }

                if (getPosition(player).distanceToSqr(session.initialPos) > (config.warmup_position_tolerance * config.warmup_position_tolerance)) {
                    cancelSession(player, null, true);
                    continue;
                }

                long secondsElapsed = (now - session.startTime) / 1000;
                long totalWarmup = config.warmup_seconds;

                if (server.getTickCount() % config.warmup_particle_ticks == 0) {
                    ReflectionUtils.sendParticles(
                            level,
                            ParticleTypes.PORTAL,
                            warp.getEntrancePos().getX() + 0.5,
                            warp.getEntrancePos().getY() + 1.2,
                            warp.getEntrancePos().getZ() + 0.5,
                            8, 0.2, 0.3, 0.2, 0.1
                    );
                }

                if (secondsElapsed >= totalWarmup) {
                    completeTeleport(player, warp, session);
                    activeSessions.remove(playerName);
                }
            } else {
                WarmupSession session = activeSessions.remove(playerName);
                if (session != null && session.preStabilityPassed) {
                    cancelSession(player, null, true);
                }
            }
        }
    }

    private static boolean isPlayerInZone(ServerPlayer player, BlockPos entrancePos, ModConfig config) {
        net.minecraft.world.entity.Entity pEnt = (net.minecraft.world.entity.Entity) player;
        double centerX = entrancePos.getX() + 0.5;
        double centerZ = entrancePos.getZ() + 0.5;
        double feetY = pEnt.getY();
        double targetY = entrancePos.getY() + 1.0;

        boolean xzValid = Math.abs(pEnt.getX() - centerX) <= config.warmup_position_tolerance &&
                Math.abs(pEnt.getZ() - centerZ) <= config.warmup_position_tolerance;
        boolean yValid = Math.abs(feetY - targetY) <= config.warmup_y_tolerance;

        return xzValid && yValid;
    }

    private static void addChunkTicket(MinecraftServer server, Warp warp, ModConfig config, long now) {
        ServerLevel exitLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION, warp.getExitDimension()));
        if (exitLevel != null) {
            ChunkPos pos = ChunkPos.containing(warp.getExitPos());
            exitLevel.setChunkForced(pos.x(), pos.z(), true);
            activeTickets.put(warp.getExitKey(), new ChunkTicketHolder(exitLevel, pos, now + (config.exit_chunk_ticket_seconds * 1000L)));
        }
    }

    public static void cancelSession(ServerPlayer player, String reason, boolean playAnimation) {
        String playerName = getPlayerName(player);
        WarmupSession session = activeSessions.remove(playerName);
        ModConfig config = LodestoneWarps.getConfig();

        cancelRetryMap.put(playerName, System.currentTimeMillis() + (config.warmup_cancel_retry_seconds * 1000L));

        if (playAnimation && config.cancel_animation_on_warmup_interrupt) {
            ServerLevel serverLevel = getLevel(player);
            if (activeAnimations.size() < config.max_active_explosion_animations) {
                activeAnimations.add(new AnimationSession(serverLevel, getBlockPos(player).below(), System.currentTimeMillis(), false));
            }
            SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getOptional(Identifier.parse(config.cancel_animation_sound))
                    .orElse(SoundEvents.FIRE_EXTINGUISH);
            net.minecraft.world.entity.Entity pEnt = (net.minecraft.world.entity.Entity) player;
            ReflectionUtils.playSound(
                    serverLevel,
                    pEnt.getX(), pEnt.getY(), pEnt.getZ(),
                    sound,
                    SoundSource.BLOCKS, 1.0f, 1.0f
            );
        }
    }

    private static void completeTeleport(ServerPlayer player, Warp warp, WarmupSession session) {
        ServerLevel entranceLevel = getLevel(player);
        MinecraftServer server = entranceLevel.getServer();
        ModConfig config = LodestoneWarps.getConfig();

        ServerLevel exitLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION, warp.getExitDimension()));

        boolean exitValid = exitLevel != null && exitLevel.getBlockState(warp.getExitPos()).is(Blocks.LODESTONE);

        if (!exitValid) {
            WarpDataState.get(server).removeWarpByEntranceKey(warp.getEntranceKey());
            if (activeAnimations.size() < config.max_active_explosion_animations) {
                activeAnimations.add(new AnimationSession(entranceLevel, warp.getEntrancePos(), System.currentTimeMillis(), true));
            } else {
                WarpManager.executeExplosion(entranceLevel, warp.getEntrancePos());
            }
            return;
        }

        if (!WarpManager.clearExitSpace(exitLevel, warp.getExitPos(), player)) {
            cancelSession(player, null, true);
            return;
        }

        ParticleOptions portalPart = ReflectionUtils.getParticleType("minecraft:portal", ParticleTypes.PORTAL);
        ParticleOptions witchPart = ReflectionUtils.getParticleType("minecraft:witch", ParticleTypes.PORTAL);
        ParticleOptions breathPart = ReflectionUtils.getParticleType("minecraft:dragon_breath", ParticleTypes.PORTAL);

        // Spawn entrance burst of purple portal and witch particles
        ReflectionUtils.sendParticles(
                entranceLevel,
                portalPart,
                warp.getEntrancePos().getX() + 0.5,
                warp.getEntrancePos().getY() + 1.2,
                warp.getEntrancePos().getZ() + 0.5,
                40, 0.5, 0.8, 0.5, 0.2
        );
        ReflectionUtils.sendParticles(
                entranceLevel,
                witchPart,
                warp.getEntrancePos().getX() + 0.5,
                warp.getEntrancePos().getY() + 1.2,
                warp.getEntrancePos().getZ() + 0.5,
                25, 0.3, 0.5, 0.3, 0.1
        );

        Vec3 targetVec = new Vec3(warp.getExitPos().getX() + 0.5, warp.getExitPos().getY() + 1.0, warp.getExitPos().getZ() + 0.5);

        net.minecraft.world.entity.Entity pEnt = (net.minecraft.world.entity.Entity) player;
        net.minecraft.world.entity.LivingEntity pLiv = (net.minecraft.world.entity.LivingEntity) player;

        ReflectionUtils.teleportPlayer(
                player,
                exitLevel,
                targetVec.x(),
                targetVec.y(),
                targetVec.z(),
                pEnt.getYRot(),
                pEnt.getXRot()
        );
        ReflectionUtils.resetFallDistance(player);

        // Spawn rich purple magic particle burst under player at exit
        ReflectionUtils.sendParticles(
                exitLevel,
                portalPart,
                targetVec.x(),
                targetVec.y() + 0.2,
                targetVec.z(),
                60, 0.5, 1.0, 0.5, 0.25
        );
        ReflectionUtils.sendParticles(
                exitLevel,
                witchPart,
                targetVec.x(),
                targetVec.y() + 0.2,
                targetVec.z(),
                35, 0.4, 0.6, 0.4, 0.15
        );
        ReflectionUtils.sendParticles(
                exitLevel,
                breathPart,
                targetVec.x(),
                targetVec.y() + 0.1,
                targetVec.z(),
                25, 0.3, 0.3, 0.3, 0.05
        );

        // Play mystical exit teleport sound
        ReflectionUtils.playSound(
                exitLevel,
                targetVec.x(), targetVec.y(), targetVec.z(),
                SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS,
                1.0f, 1.0f
        );

        double distance = Math.sqrt(warp.getEntrancePos().distSqr(warp.getExitPos()));
        long now = System.currentTimeMillis();

        WarpDataState dataState = WarpDataState.get(server);
        PlayerData pData = dataState.getPlayerData(getPlayerName(player));

        boolean bypassPenalty = (isCreative(player) && config.creative_bypass_penalty) ||
                (isSpectator(player) && config.spectator_bypass_penalty);

        if (!bypassPenalty) {
            int cdSec = (int) Math.min(config.cooldown_max_seconds, Math.max(config.cooldown_min_seconds, Math.ceil(distance / 500.0 * config.cooldown_seconds_per_500)));
            pData.setCooldownUntil(now + (cdSec * 1000L));

            int weakSec = (int) Math.min(config.weakness_max_seconds, Math.max(config.weakness_min_seconds, Math.ceil(distance / 1000.0 * config.weakness_seconds_per_1000)));
            pData.setWeaknessUntil(now + (weakSec * 1000L));

            // Штраф растёт с дистанцией: близко — только слабость, дальше + замедление, ещё дальше + усталость
            int tier = 0;
            if (distance >= config.mining_fatigue_from_distance) {
                tier = 2;
            } else if (distance >= config.slowness_from_distance) {
                tier = 1;
            }
            pData.setPenaltyTier(tier);

            pData.setBlindnessUntil(now + (config.blindness_seconds * 1000L));
            pData.setBlindnessCancelledByMilk(false);

            dataState.setDirty();

            int penaltyTicks = weakSec * 20;
            ReflectionUtils.addMobEffect(pLiv, new MobEffectInstance(MobEffects.BLINDNESS, config.blindness_seconds * 20, 0, false, false, true));
            ReflectionUtils.addMobEffect(pLiv, new MobEffectInstance(MobEffects.WEAKNESS, penaltyTicks, config.weaknessAmplifierFor(tier), false, false, true));
            if (tier >= 1) {
                ReflectionUtils.addMobEffect(pLiv, new MobEffectInstance(MobEffects.SLOWNESS, penaltyTicks, config.slownessAmplifierFor(tier), false, false, true));
            }
            if (tier >= 2) {
                ReflectionUtils.addMobEffect(pLiv, new MobEffectInstance(MobEffects.MINING_FATIGUE, penaltyTicks, config.miningFatigueAmplifierFor(tier), false, false, true));
            }

            if (config.apply_hp_penalty_after_teleport) {
                applyHpPenalty(player, config);
            }
        }
    }

    private static void applyHpPenalty(ServerPlayer player, ModConfig config) {
        net.minecraft.world.entity.Entity pEnt = (net.minecraft.world.entity.Entity) player;
        net.minecraft.world.entity.LivingEntity pLiv = (net.minecraft.world.entity.LivingEntity) player;

        float maxHp = pLiv.getMaxHealth();
        float currentHp = pLiv.getHealth();

        float damageAmount;
        if (currentHp >= maxHp) {
            damageAmount = maxHp - 1.0f;
        } else {
            damageAmount = currentHp;
        }

        if (damageAmount > 0) {
            if (config.allow_totem_save) {
                ReflectionUtils.hurtEntity(pLiv, pEnt.damageSources().generic(), damageAmount);
            } else {
                ReflectionUtils.hurtEntity(pLiv, pEnt.damageSources().fellOutOfWorld(), damageAmount);
            }
        }
    }

    public static void onPlayerLogout(ServerPlayer player) {
        activeSessions.remove(getPlayerName(player));
    }

    private static java.lang.reflect.Field cachedLevelField = null;
    private static java.lang.reflect.Method cachedLevelMethod = null;

    public static ServerLevel getLevel(net.minecraft.world.entity.Entity entity) {
        if (entity == null) return null;
        try {
            if (cachedLevelField != null) {
                Object val = cachedLevelField.get(entity);
                if (val instanceof ServerLevel sl) return sl;
            }
        } catch (Throwable ignored) {}
        try {
            if (cachedLevelMethod != null) {
                Object val = cachedLevelMethod.invoke(entity);
                if (val instanceof ServerLevel sl) return sl;
            }
        } catch (Throwable ignored) {}

        for (java.lang.reflect.Field f : net.minecraft.world.entity.Entity.class.getDeclaredFields()) {
            if (net.minecraft.world.level.Level.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                try {
                    Object val = f.get(entity);
                    if (val instanceof ServerLevel sl) {
                        cachedLevelField = f;
                        return sl;
                    }
                } catch (Throwable ignored) {}
            }
        }

        for (java.lang.reflect.Method m : net.minecraft.world.entity.Entity.class.getDeclaredMethods()) {
            if (m.getParameterCount() == 0 && net.minecraft.world.level.Level.class.isAssignableFrom(m.getReturnType())) {
                m.setAccessible(true);
                try {
                    Object val = m.invoke(entity);
                    if (val instanceof ServerLevel sl) {
                        cachedLevelMethod = m;
                        return sl;
                    }
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    public static Vec3 getPosition(net.minecraft.world.entity.Entity entity) {
        if (entity == null) return new Vec3(0, 0, 0);
        net.minecraft.world.entity.Entity e = (net.minecraft.world.entity.Entity) entity;
        return new Vec3(e.getX(), e.getY(), e.getZ());
    }

    public static BlockPos getBlockPos(net.minecraft.world.entity.Entity entity) {
        if (entity == null) return BlockPos.ZERO;
        net.minecraft.world.entity.Entity e = (net.minecraft.world.entity.Entity) entity;
        return new BlockPos((int) Math.floor(e.getX()), (int) Math.floor(e.getY()), (int) Math.floor(e.getZ()));
    }

    public static String getPlayerName(ServerPlayer player) {
        if (player == null) return "";
        try {
            return player.getName().getString();
        } catch (Throwable ignored) {}
        try {
            Object profile = player.getGameProfile();
            if (profile != null) {
                for (java.lang.reflect.Method m : profile.getClass().getMethods()) {
                    if (m.getParameterCount() == 0 && m.getReturnType() == String.class && !m.getName().equals("toString")) {
                        String n = (String) m.invoke(profile);
                        if (n != null && !n.isEmpty()) return n;
                    }
                }
            }
        } catch (Throwable ignored) {}
        try {
            return player.getUUID().toString();
        } catch (Throwable ignored) {}
        return "Unknown";
    }

    public static boolean isCreative(ServerPlayer player) {
        if (player == null) return false;
        try {
            if (player.gameMode != null) {
                return player.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.CREATIVE;
            }
        } catch (Throwable ignored) {}
        try {
            return ((net.minecraft.world.entity.player.Player) player).isCreative();
        } catch (Throwable ignored) {}
        return false;
    }

    public static boolean isSpectator(ServerPlayer player) {
        if (player == null) return false;
        try {
            if (player.gameMode != null) {
                return player.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.SPECTATOR;
            }
        } catch (Throwable ignored) {}
        try {
            return ((net.minecraft.world.entity.player.Player) player).isSpectator();
        } catch (Throwable ignored) {}
        return false;
    }
}