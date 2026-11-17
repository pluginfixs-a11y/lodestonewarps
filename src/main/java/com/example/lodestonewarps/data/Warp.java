// src/main/java/com/example/lodestonewarps/data/Warp.java
package com.example.lodestonewarps.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

public class Warp {
    private final Identifier dimension;
    private final BlockPos entrancePos;
    private final Identifier exitDimension;
    private final BlockPos exitPos;
    private final String creatorName;
    private final long createdAt;

    public Warp(Identifier dimension, BlockPos entrancePos, Identifier exitDimension, BlockPos exitPos, String creatorName, long createdAt) {
        this.dimension = dimension;
        this.entrancePos = entrancePos;
        this.exitDimension = exitDimension;
        this.exitPos = exitPos;
        this.creatorName = creatorName;
        this.createdAt = createdAt;
    }

    public Identifier getDimension() {
        return dimension;
    }

    public BlockPos getEntrancePos() {
        return entrancePos;
    }

    public Identifier getExitDimension() {
        return exitDimension;
    }

    public BlockPos getExitPos() {
        return exitPos;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public String getEntranceKey() {
        return dimension.toString() + ":" + entrancePos.getX() + "," + entrancePos.getY() + "," + entrancePos.getZ();
    }

    public String getExitKey() {
        return exitDimension.toString() + ":" + exitPos.getX() + "," + exitPos.getY() + "," + exitPos.getZ();
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", dimension.toString());
        tag.putInt("entranceX", entrancePos.getX());
        tag.putInt("entranceY", entrancePos.getY());
        tag.putInt("entranceZ", entrancePos.getZ());
        tag.putString("exitDimension", exitDimension.toString());
        tag.putInt("exitX", exitPos.getX());
        tag.putInt("exitY", exitPos.getY());
        tag.putInt("exitZ", exitPos.getZ());
        tag.putString("creatorName", creatorName);
        tag.putLong("createdAt", createdAt);
        return tag;
    }

    public static Warp fromNbt(CompoundTag tag) {
        Identifier dim = Identifier.parse(tag.getStringOr("dimension", ""));
        BlockPos ent = new BlockPos(tag.getIntOr("entranceX", 0), tag.getIntOr("entranceY", 0), tag.getIntOr("entranceZ", 0));
        Identifier exitDim = Identifier.parse(tag.getStringOr("exitDimension", ""));
        BlockPos ex = new BlockPos(tag.getIntOr("exitX", 0), tag.getIntOr("exitY", 0), tag.getIntOr("exitZ", 0));
        String creator = tag.getStringOr("creatorName", "");
        long created = tag.getLongOr("createdAt", 0L);
        return new Warp(dim, ent, exitDim, ex, creator, created);
    }
}