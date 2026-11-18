package com.example.lodestonewarps.mixin;

import com.example.lodestonewarps.LodestoneWarps;
import com.example.lodestonewarps.data.PlayerData;
import com.example.lodestonewarps.data.WarpDataState;
import com.example.lodestonewarps.util.ReflectionUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "removeAllEffects", at = @At("HEAD"))
    private void onRemoveAllEffects(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        // Молоко выключается конфигом: тогда слепота ведёт себя как слабость — её никто не снимает.
        if (!LodestoneWarps.getConfig().blindness_removable_by_milk) {
            return;
        }
        if (ReflectionUtils.hasMobEffect(entity, MobEffects.BLINDNESS)) {
            WarpDataState dataState = WarpDataState.get(ReflectionUtils.getLevel(player).getServer());
            PlayerData pData = dataState.getPlayerData(ReflectionUtils.getPlayerName(player));
            pData.setBlindnessCancelledByMilk(true);
            dataState.setDirty();
        }
    }
}