// src/main/java/com/example/lodestonewarps/data/PlayerData.java
package com.example.lodestonewarps.data;

import net.minecraft.nbt.CompoundTag;

public class PlayerData {
    private long cooldownUntil;
    private long weaknessUntil;
    private long blindnessUntil;
    private boolean blindnessCancelledByMilk;
    /** 0 — только слабость, 1 — плюс замедление, 2 — плюс усталость (уровень подобран по дистанции варпа). */
    private int penaltyTier;

    public PlayerData() {
        this.cooldownUntil = 0;
        this.weaknessUntil = 0;
        this.blindnessUntil = 0;
        this.blindnessCancelledByMilk = false;
        this.penaltyTier = 0;
    }

    public PlayerData(long cooldownUntil, long weaknessUntil, long blindnessUntil, boolean blindnessCancelledByMilk, int penaltyTier) {
        this.cooldownUntil = cooldownUntil;
        this.weaknessUntil = weaknessUntil;
        this.blindnessUntil = blindnessUntil;
        this.blindnessCancelledByMilk = blindnessCancelledByMilk;
        this.penaltyTier = penaltyTier;
    }

    public long getCooldownUntil() {
        return cooldownUntil;
    }

    public void setCooldownUntil(long cooldownUntil) {
        this.cooldownUntil = cooldownUntil;
    }

    public long getWeaknessUntil() {
        return weaknessUntil;
    }

    public void setWeaknessUntil(long weaknessUntil) {
        this.weaknessUntil = weaknessUntil;
    }

    public long getBlindnessUntil() {
        return blindnessUntil;
    }

    public void setBlindnessUntil(long blindnessUntil) {
        this.blindnessUntil = blindnessUntil;
    }

    public boolean isBlindnessCancelledByMilk() {
        return blindnessCancelledByMilk;
    }

    public void setBlindnessCancelledByMilk(boolean blindnessCancelledByMilk) {
        this.blindnessCancelledByMilk = blindnessCancelledByMilk;
    }

    public int getPenaltyTier() {
        return penaltyTier;
    }

    public void setPenaltyTier(int penaltyTier) {
        this.penaltyTier = penaltyTier;
    }

    public boolean isExpired(long currentTime) {
        return currentTime >= cooldownUntil && currentTime >= weaknessUntil && currentTime >= blindnessUntil;
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("cooldownUntil", cooldownUntil);
        tag.putLong("weaknessUntil", weaknessUntil);
        tag.putLong("blindnessUntil", blindnessUntil);
        tag.putBoolean("blindnessCancelledByMilk", blindnessCancelledByMilk);
        tag.putInt("penaltyTier", penaltyTier);
        return tag;
    }

    public static PlayerData fromNbt(CompoundTag tag) {
        long cooldown = tag.getLongOr("cooldownUntil", 0L);
        long weakness = tag.getLongOr("weaknessUntil", 0L);
        long blindness = tag.getLongOr("blindnessUntil", 0L);
        boolean milk = tag.getBooleanOr("blindnessCancelledByMilk", false);
        int tier = tag.getIntOr("penaltyTier", 0);
        return new PlayerData(cooldown, weakness, blindness, milk, tier);
    }
}