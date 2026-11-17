package com.example.lodestonewarps.manager;

import com.example.lodestonewarps.LodestoneWarps;
import com.example.lodestonewarps.config.ModConfig;
import com.example.lodestonewarps.data.PlayerData;
import com.example.lodestonewarps.data.WarpDataState;
import com.example.lodestonewarps.util.ReflectionUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class PlayerTimerManager {

    public static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        ModConfig config = LodestoneWarps.getConfig();
        WarpDataState dataState = WarpDataState.get(server);

        // Ноль в конфиге (человек ставит его, чтобы «выключить») раньше давал деление на ноль
        // и валил сервер на первом же тике: остаток от деления на 0 — это ArithmeticException.
        int cleanupTicks = config.cleanup_expired_player_data_minutes * 1200;
        if (cleanupTicks > 0 && server.getTickCount() % cleanupTicks == 0) {
            dataState.cleanupExpiredData(now);
        }

        if (config.weakness_reapply_ticks <= 0 || server.getTickCount() % config.weakness_reapply_ticks != 0) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String playerName = ReflectionUtils.getPlayerName(player);
            PlayerData pData = dataState.getPlayerData(playerName);
            net.minecraft.world.entity.LivingEntity pLiv = (net.minecraft.world.entity.LivingEntity) (Object) player;

            if (now < pData.getWeaknessUntil() && config.weakness_protected_from_milk) {
                long remainingMs = pData.getWeaknessUntil() - now;
                int remainingTicks = (int) (remainingMs / 50);
                int tier = pData.getPenaltyTier();

                MobEffectInstance currentEffect = ReflectionUtils.getMobEffect(pLiv, MobEffects.WEAKNESS);
                if (currentEffect == null || currentEffect.getDuration() < remainingTicks - 20) {
                    ReflectionUtils.addMobEffect(pLiv, new MobEffectInstance(MobEffects.WEAKNESS, remainingTicks, config.weaknessAmplifierFor(tier), false, false, true));
                }

                if (tier >= 1) {
                    MobEffectInstance slow = ReflectionUtils.getMobEffect(pLiv, MobEffects.SLOWNESS);
                    if (slow == null || slow.getDuration() < remainingTicks - 20) {
                        ReflectionUtils.addMobEffect(pLiv, new MobEffectInstance(MobEffects.SLOWNESS, remainingTicks, config.slownessAmplifierFor(tier), false, false, true));
                    }
                }
                if (tier >= 2) {
                    MobEffectInstance fatigue = ReflectionUtils.getMobEffect(pLiv, MobEffects.MINING_FATIGUE);
                    if (fatigue == null || fatigue.getDuration() < remainingTicks - 20) {
                        ReflectionUtils.addMobEffect(pLiv, new MobEffectInstance(MobEffects.MINING_FATIGUE, remainingTicks, config.miningFatigueAmplifierFor(tier), false, false, true));
                    }
                }
            }

            if (now < pData.getBlindnessUntil() && !pData.isBlindnessCancelledByMilk()) {
                long remainingMs = pData.getBlindnessUntil() - now;
                int remainingTicks = (int) (remainingMs / 50);

                if (!ReflectionUtils.hasMobEffect(pLiv, MobEffects.BLINDNESS) && remainingTicks > 20) {
                    ReflectionUtils.addMobEffect(pLiv, new MobEffectInstance(MobEffects.BLINDNESS, remainingTicks, 0, false, false, true));
                }
            }
        }
    }
}