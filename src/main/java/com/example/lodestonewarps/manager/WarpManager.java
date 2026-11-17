package com.example.lodestonewarps.manager;

import com.example.lodestonewarps.LodestoneWarps;
import com.example.lodestonewarps.config.ModConfig;
import com.example.lodestonewarps.data.PendingCreation;
import com.example.lodestonewarps.data.Warp;
import com.example.lodestonewarps.data.WarpDataState;
import com.example.lodestonewarps.util.ReflectionUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.Set;

public class WarpManager {

    public static void handleLodestoneInteract(ServerPlayer player, ServerLevel world, BlockPos pos) {
        ModConfig config = LodestoneWarps.getConfig();
        String playerName = ReflectionUtils.getPlayerName(player);
        long now = System.currentTimeMillis();

        net.minecraft.world.entity.Entity pEnt = (net.minecraft.world.entity.Entity) player;
        net.minecraft.world.entity.LivingEntity pLiv = (net.minecraft.world.entity.LivingEntity) player;

        ItemStack mainHand = pLiv.getMainHandItem();
        if (!mainHand.is(Items.RECOVERY_COMPASS)) {
            return;
        }

        WarpDataState dataState = WarpDataState.get(ReflectionUtils.getLevel(player).getServer());
        String currentDimStr = ReflectionUtils.getDimensionLocation(world);
        Identifier currentDim = Identifier.parse(currentDimStr);
        String blockKey = currentDimStr + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();

        PendingCreation pending = dataState.getPendingCreation(playerName);

        if (pending == null || pending.isExpired(now)) {
            if (config.creation_requires_sneak && !pEnt.isShiftKeyDown()) {
                return;
            }

            if (pending != null && pending.isExpired(now)) {
                dataState.removePendingCreation(playerName);
            }

            if (dataState.isEntranceOrExit(blockKey)) {
                return;
            }

            if (!world.getWorldBorder().isWithinBounds(pos)) {
                return;
            }

            System.out.println("[LodestoneWarps] " + playerName + " set warp ENTRANCE at " + blockKey);
            long timeoutMs = config.creation_timeout_seconds * 1000L;
            PendingCreation newPending = new PendingCreation(currentDim, pos, now, now + timeoutMs);
            dataState.setPendingCreation(playerName, newPending);

            ParticleOptions enchantPart = ReflectionUtils.getParticleType("minecraft:enchant", ParticleTypes.PORTAL);
            ParticleOptions endRodPart = ReflectionUtils.getParticleType("minecraft:end_rod", ParticleTypes.PORTAL);
            ReflectionUtils.sendParticles(world, enchantPart, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 30, 0.4, 0.5, 0.4, 0.2);
            ReflectionUtils.sendParticles(world, endRodPart, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 15, 0.3, 0.4, 0.3, 0.05);
            SoundEvent chimeSound = ReflectionUtils.getSound("minecraft:block.amethyst_block.chime", SoundEvents.EXPERIENCE_ORB_PICKUP);
            ReflectionUtils.playSound(world, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, chimeSound, SoundSource.BLOCKS, 1.0f, 1.2f);

            ReflectionUtils.setEnchantmentGlint(mainHand, true);
        } else {
            if (!pending.getDimension().equals(currentDim)) {
                if (config.same_dimension_only) {
                    System.out.println("[LodestoneWarps] Rejected exit creation: dimension mismatch.");
                    return;
                }
            }

            BlockPos entrancePos = pending.getEntrancePos();
            String entranceKey = pending.getDimension().toString() + ":" + entrancePos.getX() + "," + entrancePos.getY() + "," + entrancePos.getZ();

            ServerLevel entranceLevel = ReflectionUtils.getLevel(player).getServer().getLevel(ResourceKey.create(Registries.DIMENSION, pending.getDimension()));

            if (entranceLevel == null || !entranceLevel.getBlockState(entrancePos).is(Blocks.LODESTONE)) {
                System.out.println("[LodestoneWarps] Rejected exit creation: entrance lodestone no longer exists.");
                dataState.removePendingCreation(playerName);
                ReflectionUtils.setEnchantmentGlint(mainHand, false);
                return;
            }

            if (entrancePos.equals(pos) && pending.getDimension().equals(currentDim)) {
                System.out.println("[LodestoneWarps] Rejected exit creation: entrance and exit are same block.");
                return;
            }

            if (dataState.isEntranceOrExit(blockKey) || dataState.isEntranceOrExit(entranceKey)) {
                System.out.println("[LodestoneWarps] Rejected exit creation: lodestone already registered.");
                return;
            }

            if (!world.getWorldBorder().isWithinBounds(pos)) {
                System.out.println("[LodestoneWarps] Rejected exit creation: outside world border.");
                return;
            }

            double distance = Math.sqrt(entrancePos.distSqr(pos));
            if (distance < config.min_distance) {
                System.out.println("[LodestoneWarps] Rejected exit creation: distance " + distance + " < min_distance " + config.min_distance);
                return;
            }
            if (distance > config.max_distance) {
                System.out.println("[LodestoneWarps] Rejected exit creation: distance " + distance + " > max_distance " + config.max_distance);
                return;
            }

            boolean bypassCost = (WarmupManager.isCreative(player) || WarmupManager.isSpectator(player)) && config.creative_bypass_cost;
            if (config.consume_recovery_compass && !bypassCost) {
                if (mainHand.getCount() > 0) {
                    mainHand.shrink(1);
                } else {
                    System.out.println("[LodestoneWarps] Rejected exit creation: no recovery compass.");
                    return;
                }
            }

            Warp warp = new Warp(pending.getDimension(), entrancePos, currentDim, pos, playerName, now);
            dataState.addWarp(warp);
            dataState.removePendingCreation(playerName);

            ParticleOptions witchPart = ReflectionUtils.getParticleType("minecraft:witch", ParticleTypes.PORTAL);
            ParticleOptions reversePart = ReflectionUtils.getParticleType("minecraft:reverse_portal", ParticleTypes.PORTAL);
            ReflectionUtils.sendParticles(world, witchPart, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 40, 0.4, 0.5, 0.4, 0.15);
            ReflectionUtils.sendParticles(world, reversePart, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 20, 0.3, 0.4, 0.3, 0.05);

            SoundEvent frameSound = ReflectionUtils.getSound("minecraft:block.end_portal_frame.fill", SoundEvents.ENDERMAN_TELEPORT);
            ReflectionUtils.playSound(world, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, frameSound, SoundSource.BLOCKS, 1.0f, 1.0f);

            System.out.println("[LodestoneWarps] " + playerName + " CREATED WARP: " + entranceKey + " -> " + blockKey + " (Distance: " + (int) distance + ")");
            ReflectionUtils.setEnchantmentGlint(mainHand, false);
        }
    }

    public static boolean clearExitSpace(ServerLevel level, BlockPos exitPos, ServerPlayer player) {
        ModConfig config = LodestoneWarps.getConfig();
        int width = 1;
        int height = config.exit_clear_base_height;

        if (config.exit_clear_for_player_size && player != null) {
            AABB bbox = ((net.minecraft.world.entity.Entity) player).getBoundingBox();
            width = Math.min(config.exit_clear_max_width, (int) Math.ceil(bbox.getXsize()));
            height = Math.min(config.exit_clear_max_height, (int) Math.ceil(bbox.getYsize()));
        }

        Set<BlockPos> targetPositions = new HashSet<>();
        int radius = width / 2;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = 1; dy <= height; dy++) {
                    targetPositions.add(exitPos.offset(dx, dy, dz));
                }
            }
        }

        for (BlockPos p : targetPositions) {
            BlockState state = level.getBlockState(p);
            if (state.isAir()) continue;

            Identifier blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (config.exit_clear_blacklist.contains(blockId.toString())) {
                return false;
            }

            if (config.exit_clear_blacklist_unbreakable && state.getDestroySpeed(level, p) < 0) {
                return false;
            }
        }

        for (BlockPos p : targetPositions) {
            BlockState state = level.getBlockState(p);
            if (state.isAir()) continue;

            if (config.exit_clear_drop_blocks) {
                level.destroyBlock(p, true);
            } else {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }

        return true;
    }

    public static void executeExplosion(ServerLevel level, BlockPos entrancePos) {
        ModConfig config = LodestoneWarps.getConfig();
        String entranceKey = ReflectionUtils.getDimensionLocation(level) + ":" + entrancePos.getX() + "," + entrancePos.getY() + "," + entrancePos.getZ();

        WarpDataState dataState = WarpDataState.get(level.getServer());
        dataState.removeWarpByEntranceKey(entranceKey);

        if (config.explosion_removes_entrance) {
            level.setBlock(entrancePos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }

        if (config.missing_exit_explosion) {
            ReflectionUtils.executeExplosion(
                    level,
                    null,
                    entrancePos.getX() + 0.5,
                    entrancePos.getY() + 0.5,
                    entrancePos.getZ() + 0.5,
                    config.explosion_power,
                    config.explosion_fire,
                    config.explosion_block_damage ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE
            );
        }
    }
}