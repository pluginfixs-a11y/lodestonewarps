package com.example.lodestonewarps.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ReflectionUtils {

    private static Field cachedLevelField = null;
    private static Method cachedLevelMethod = null;

    public static ServerLevel getLevel(Entity entity) {
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

        for (Field f : Entity.class.getDeclaredFields()) {
            if (Level.class.isAssignableFrom(f.getType())) {
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

        for (Method m : Entity.class.getDeclaredMethods()) {
            if (m.getParameterCount() == 0 && Level.class.isAssignableFrom(m.getReturnType())) {
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

    public static String getPlayerName(ServerPlayer player) {
        if (player == null) return "";
        try {
            return player.getName().getString();
        } catch (Throwable ignored) {}
        try {
            Object profile = player.getGameProfile();
            if (profile != null) {
                for (Method m : profile.getClass().getMethods()) {
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

    public static Vec3 getPosition(Entity entity) {
        if (entity == null) return new Vec3(0, 0, 0);
        Entity e = (Entity) entity;
        return new Vec3(e.getX(), e.getY(), e.getZ());
    }

    public static BlockPos getBlockPos(Entity entity) {
        if (entity == null) return BlockPos.ZERO;
        Entity e = (Entity) entity;
        return new BlockPos((int) Math.floor(e.getX()), (int) Math.floor(e.getY()), (int) Math.floor(e.getZ()));
    }

    public static String getDimensionLocation(Level level) {
        if (level == null) return "minecraft:overworld";
        try {
            return level.dimension().identifier().toString();
        } catch (Throwable ignored) {}
        try {
            Object dimKey = level.dimension();
            for (Method m : dimKey.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType() != void.class) {
                    Object res = m.invoke(dimKey);
                    if (res != null && res.toString().contains(":")) {
                        return res.toString();
                    }
                }
            }
        } catch (Throwable ignored) {}
        return "minecraft:overworld";
    }

    public static void displayClientMessage(ServerPlayer player, Component component, boolean overlay) {
        if (player == null || component == null) return;
        try {
            Player p = (Player) (Object) player;
            if (overlay) {
                p.sendOverlayMessage(component);
            } else {
                p.sendSystemMessage(component);
            }
            return;
        } catch (Throwable ignored) {}
        for (Method m : player.getClass().getMethods()) {
            if (m.getName().contains("displayClientMessage") || m.getName().contains("sendMessage") || m.getName().startsWith("method_")) {
                if (m.getParameterCount() == 2 && m.getParameterTypes()[1] == boolean.class) {
                    try {
                        m.setAccessible(true);
                        m.invoke(player, component, overlay);
                        return;
                    } catch (Throwable ignored) {}
                }
            }
        }
    }

    public static void sendParticles(ServerLevel level, ParticleOptions particle, double x, double y, double z, int count, double deltaX, double deltaY, double deltaZ, double speed) {
        if (level == null || particle == null) return;
        try {
            level.sendParticles(particle, x, y, z, count, deltaX, deltaY, deltaZ, speed);
            return;
        } catch (Throwable ignored) {}
        for (Method m : level.getClass().getMethods()) {
            if (m.getName().contains("sendParticles") || m.getName().startsWith("method_") || m.getName().equals("a")) {
                if (m.getParameterCount() == 9) {
                    try {
                        m.setAccessible(true);
                        m.invoke(level, particle, x, y, z, count, deltaX, deltaY, deltaZ, speed);
                        return;
                    } catch (Throwable ignored) {}
                }
            }
        }
    }

    public static void playSound(ServerLevel level, double x, double y, double z, SoundEvent sound, SoundSource source, float volume, float pitch) {
        if (level == null || sound == null) return;
        for (Method m : level.getClass().getMethods()) {
            if (m.getName().contains("playSound") || m.getName().startsWith("method_") || m.getName().equals("a")) {
                Class<?>[] params = m.getParameterTypes();
                try {
                    if (params.length == 8 && double.class.isAssignableFrom(params[1])) {
                        m.invoke(level, null, x, y, z, sound, source, volume, pitch);
                        return;
                    } else if (params.length == 8 && Holder.class.isAssignableFrom(params[4])) {
                        m.invoke(level, null, x, y, z, Holder.direct(sound), source, volume, pitch);
                        return;
                    } else if (params.length == 5 && BlockPos.class.isAssignableFrom(params[0])) {
                        m.invoke(level, new BlockPos((int) x, (int) y, (int) z), sound, source, volume, pitch);
                        return;
                    }
                } catch (Throwable ignored) {}
            }
        }
        try {
            level.playSound(null, new BlockPos((int) x, (int) y, (int) z), sound, source, volume, pitch);
        } catch (Throwable ignored) {}
    }

    public static void teleportPlayer(ServerPlayer player, ServerLevel targetLevel, double x, double y, double z, float yaw, float pitch) {
        if (player == null || targetLevel == null) return;
        try {
            player.teleportTo(targetLevel, x, y, z, Collections.emptySet(), yaw, pitch, false);
            return;
        } catch (Throwable ignored) {}

        for (Method m : player.getClass().getMethods()) {
            if (m.getName().contains("teleport") || m.getName().startsWith("method_")) {
                Class<?>[] params = m.getParameterTypes();
                try {
                    if (params.length >= 7 && ServerLevel.class.isAssignableFrom(params[0])) {
                        m.setAccessible(true);
                        if (params.length == 8) {
                            m.invoke(player, targetLevel, x, y, z, Collections.emptySet(), yaw, pitch, false);
                        } else if (params.length == 7) {
                            m.invoke(player, targetLevel, x, y, z, yaw, pitch, false);
                        }
                        return;
                    }
                } catch (Throwable ignored) {}
            }
        }
    }

    public static void executeExplosion(ServerLevel level, Entity exploder, double x, double y, double z, float power, boolean createFire, Level.ExplosionInteraction mode) {
        if (level == null) return;
        try {
            level.explode(exploder, x, y, z, power, createFire, mode);
            return;
        } catch (Throwable ignored) {}
        for (Method m : level.getClass().getMethods()) {
            if (m.getName().contains("explode") || m.getName().startsWith("method_")) {
                try {
                    m.setAccessible(true);
                    if (m.getParameterCount() == 7) {
                        m.invoke(level, exploder, x, y, z, power, createFire, mode);
                        return;
                    } else if (m.getParameterCount() == 5) {
                        m.invoke(level, exploder, x, y, z, power, mode);
                        return;
                    }
                } catch (Throwable ignored) {}
            }
        }
    }

    public static void addMobEffect(LivingEntity entity, MobEffectInstance effect) {
        if (entity == null || effect == null) return;
        try {
            entity.addEffect(effect);
            return;
        } catch (Throwable ignored) {}
        for (Method m : entity.getClass().getMethods()) {
            if (m.getName().contains("addEffect") || m.getName().startsWith("method_")) {
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == MobEffectInstance.class) {
                    try {
                        m.setAccessible(true);
                        m.invoke(entity, effect);
                        return;
                    } catch (Throwable ignored) {}
                }
            }
        }
    }

    public static boolean hasMobEffect(LivingEntity entity, Holder<MobEffect> effect) {
        if (entity == null || effect == null) return false;
        try {
            return entity.hasEffect(effect);
        } catch (Throwable ignored) {}
        for (Method m : entity.getClass().getMethods()) {
            if (m.getName().contains("hasEffect") || m.getName().startsWith("method_")) {
                if (m.getParameterCount() == 1) {
                    try {
                        m.setAccessible(true);
                        Object res = m.invoke(entity, effect);
                        if (res instanceof Boolean b) return b;
                    } catch (Throwable ignored) {}
                }
            }
        }
        return false;
    }

    public static MobEffectInstance getMobEffect(LivingEntity entity, Holder<MobEffect> effect) {
        if (entity == null || effect == null) return null;
        try {
            return entity.getEffect(effect);
        } catch (Throwable ignored) {}
        for (Method m : entity.getClass().getMethods()) {
            if (m.getName().contains("getEffect") || m.getName().startsWith("method_")) {
                if (m.getParameterCount() == 1) {
                    try {
                        m.setAccessible(true);
                        Object res = m.invoke(entity, effect);
                        if (res instanceof MobEffectInstance mei) return mei;
                    } catch (Throwable ignored) {}
                }
            }
        }
        return null;
    }

    public static void hurtEntity(LivingEntity target, DamageSource source, float amount) {
        if (target == null || source == null) return;
        try {
            target.hurt(source, amount);
            return;
        } catch (Throwable ignored) {}
        for (Method m : target.getClass().getMethods()) {
            if (m.getName().contains("hurt") || m.getName().startsWith("method_")) {
                if (m.getParameterCount() == 2 && float.class.isAssignableFrom(m.getParameterTypes()[1])) {
                    try {
                        m.setAccessible(true);
                        m.invoke(target, source, amount);
                        return;
                    } catch (Throwable ignored) {}
                }
            }
        }
    }

    public static void resetFallDistance(Entity entity) {
        if (entity == null) return;
        try {
            ((Entity) entity).fallDistance = 0.0f;
            return;
        } catch (Throwable ignored) {}
        try {
            for (Field f : Entity.class.getDeclaredFields()) {
                if (f.getType() == float.class) {
                    f.setAccessible(true);
                    f.setFloat(entity, 0.0f);
                }
            }
        } catch (Throwable ignored) {}
    }

    public static net.minecraft.nbt.ListTag getList(net.minecraft.nbt.CompoundTag tag, String key, int type) {
        if (tag == null || key == null) return new net.minecraft.nbt.ListTag();
        try {
            return tag.getListOrEmpty(key);
        } catch (Throwable ignored) {}
        try {
            for (Method m : tag.getClass().getMethods()) {
                if (m.getName().equals("getList") || m.getName().startsWith("method_") || m.getName().equals("c")) {
                    if (m.getParameterCount() == 2 && m.getParameterTypes()[0] == String.class) {
                        try {
                            m.setAccessible(true);
                            Object res = m.invoke(tag, key, type);
                            if (res instanceof net.minecraft.nbt.ListTag lt) return lt;
                        } catch (Throwable ignored) {}
                    }
                }
            }
        } catch (Throwable ignored) {}
        try {
            Object raw = tag.get(key);
            if (raw instanceof net.minecraft.nbt.ListTag lt) return lt;
        } catch (Throwable ignored) {}
        return new net.minecraft.nbt.ListTag();
    }

    public static void setEnchantmentGlint(net.minecraft.world.item.ItemStack stack, boolean glint) {
        if (stack == null || stack.isEmpty()) return;
        try {
            if (glint) {
                stack.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            } else {
                stack.remove(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
            }
            return;
        } catch (Throwable ignored) {}
        try {
            for (Method m : stack.getClass().getMethods()) {
                if (m.getName().equals("set") || m.getName().startsWith("method_")) {
                    if (m.getParameterCount() == 2) {
                        try {
                            m.setAccessible(true);
                            if (glint) {
                                m.invoke(stack, net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                            } else {
                                m.invoke(stack, net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, null);
                            }
                            return;
                        } catch (Throwable ignored) {}
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    public static Set<String> getCompoundKeys(net.minecraft.nbt.CompoundTag tag) {
        if (tag == null) return Collections.emptySet();
        try {
            return tag.keySet();
        } catch (Throwable ignored) {}
        try {
            for (Method m : tag.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && Set.class.isAssignableFrom(m.getReturnType())) {
                    Object res = m.invoke(tag);
                    if (res instanceof Set<?> s) {
                        Set<String> strSet = new HashSet<>();
                        for (Object o : s) {
                            if (o != null) strSet.add(o.toString());
                        }
                        return strSet;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return Collections.emptySet();
    }

    public static net.minecraft.core.particles.ParticleOptions getParticleType(String name, net.minecraft.core.particles.ParticleOptions fallback) {
        try {
            net.minecraft.resources.Identifier loc = net.minecraft.resources.Identifier.parse(name);
            java.util.Optional<net.minecraft.core.particles.ParticleType<?>> opt = net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.getOptional(loc);
            if (opt.isPresent() && opt.get() instanceof net.minecraft.core.particles.ParticleOptions po) {
                return po;
            }
        } catch (Throwable ignored) {}
        return fallback;
    }

    public static net.minecraft.sounds.SoundEvent getSound(String name, net.minecraft.sounds.SoundEvent fallback) {
        try {
            net.minecraft.resources.Identifier loc = net.minecraft.resources.Identifier.parse(name);
            java.util.Optional<net.minecraft.sounds.SoundEvent> opt = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getOptional(loc);
            if (opt.isPresent()) return opt.get();
        } catch (Throwable ignored) {}
        return fallback;
    }
}
