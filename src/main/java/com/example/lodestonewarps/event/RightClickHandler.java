// src/main/java/com/example/lodestonewarps/event/RightClickHandler.java
package com.example.lodestonewarps.event;

import com.example.lodestonewarps.manager.WarpManager;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;

public class RightClickHandler implements UseBlockCallback {

    @Override
    public InteractionResult interact(Player player, Level level, InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide() || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }

        BlockPos pos = hitResult.getBlockPos();
        if (level.getBlockState(pos).is(Blocks.LODESTONE)) {
            if (player instanceof ServerPlayer serverPlayer) {
                net.minecraft.world.item.ItemStack mainHand = ((net.minecraft.world.entity.LivingEntity) player).getMainHandItem();
                if (mainHand.is(net.minecraft.world.item.Items.RECOVERY_COMPASS)) {
                    WarpManager.handleLodestoneInteract(serverPlayer, (ServerLevel) level, pos);
                    return InteractionResult.SUCCESS;
                }
            }
        }

        return InteractionResult.PASS;
    }
}