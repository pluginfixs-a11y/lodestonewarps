// src/main/java/com/example/lodestonewarps/data/PendingCreation.java
package com.example.lodestonewarps.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

public class PendingCreation {
    private final Identifier dimension;
    private final BlockPos entrancePos;
    private final long createdAt;
    private final long expiresAt;

    public PendingCreation(Identifier dimension, BlockPos entrancePos, long createdAt, long expiresAt) {
        this.dimension = dimension;
        this.entrancePos = entrancePos;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public Identifier getDimension() {
        return dimension;
    }

    public BlockPos getEntrancePos() {
        return entrancePos;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired(long currentTime) {
        return currentTime >= expiresAt;
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", dimension.toString());
        tag.putInt("entranceX", entrancePos.getX());
        tag.putInt("entranceY", entrancePos.getY());
        tag.putInt("entranceZ", entrancePos.getZ());
        tag.putLong("createdAt", createdAt);
        tag.putLong("expiresAt", expiresAt);
        return tag;
    }

    public static PendingCreation fromNbt(CompoundTag tag) {
        Identifier dim = Identifier.parse(tag.getStringOr("dimension", ""));
        BlockPos ent = new BlockPos(tag.getIntOr("entranceX", 0), tag.getIntOr("entranceY", 0), tag.getIntOr("entranceZ", 0));
        long created = tag.getLongOr("createdAt", 0L);
        long expires = tag.getLongOr("expiresAt", 0L);
        return new PendingCreation(dim, ent, created, expires);
    }
}