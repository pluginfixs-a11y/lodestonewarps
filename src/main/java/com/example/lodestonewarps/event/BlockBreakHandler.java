// src/main/java/com/example/lodestonewarps/event/BlockBreakHandler.java
package com.example.lodestonewarps.event;

import com.example.lodestonewarps.LodestoneWarps;
import com.example.lodestonewarps.config.ModConfig;
import com.example.lodestonewarps.data.Warp;
import com.example.lodestonewarps.data.WarpDataState;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class BlockBreakHandler implements PlayerBlockBreakEvents.Before {

    @Override
    public boolean beforeBlockBreak(Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (level.isClientSide()) return true;

        if (state.is(Blocks.LODESTONE)) {
            String dimLoc = com.example.lodestonewarps.util.ReflectionUtils.getDimensionLocation(level);
            String blockKey = dimLoc + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
            WarpDataState dataState = WarpDataState.get(((ServerLevel) level).getServer());

            Warp warp = dataState.getWarpByEntranceKey(blockKey);
            if (warp != null) {
                dataState.removeWarpByEntranceKey(blockKey);

                ModConfig config = LodestoneWarps.getConfig();
                if (!config.entrance_drop_on_break) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    return false;
                }
            }
        }
        return true;
    }
}