package com.kinds.kindscrystaloptimizer.mixin;

import com.kinds.kindscrystaloptimizer.KindsCrystalOptimizer;
import com.kinds.kindscrystaloptimizer.util.PerformanceGuard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftUseDelayMixin {

    @Shadow
    public LocalPlayer player;

    @Shadow
    public Options options;

    @Shadow
    private int rightClickDelay;

    @Inject(method = "startUseItem", at = @At("TAIL"))
    private void removeCrystalUseDelay(CallbackInfo ci) {
        if (!isHoldingEndCrystal()) return;
        if (!canBoostPlacement()) return;

        rightClickDelay = 0;
    }

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void clearCrystalUseDelayTick(CallbackInfo ci) {
        PerformanceGuard guard = resolveGuard();
        if (guard == null) return;
        boolean useDown = options.keyUse.isDown();
        guard.observeUseKeyState(useDown);
        if (!isHoldingEndCrystal()) return;
        if (!useDown) return;
        if (!canBoostPlacement(guard)) return;

        rightClickDelay = 0;
    }

    @Unique
    private boolean canBoostPlacement() {
        PerformanceGuard guard = resolveGuard();
        if (guard == null) {
            return false;
        }

        return canBoostPlacement(guard);
    }

    @Unique
    private boolean canBoostPlacement(PerformanceGuard guard) {
        if (guard == null) {
            return false;
        }

        KindsCrystalOptimizer instance = KindsCrystalOptimizer.getInstance();
        if (instance == null) {
            return false;
        }
        if (instance.getOptOutCache().isOptedOut()) {
            return false;
        }

        return guard.allowPlaceBoost();
    }

    @Unique
    private PerformanceGuard resolveGuard() {
        KindsCrystalOptimizer instance = KindsCrystalOptimizer.getInstance();
        if (instance == null) {
            return null;
        }
        if (instance.getOptOutCache().isOptedOut()) {
            return null;
        }

        return instance.getPerformanceGuard();
    }

    @Unique
    private boolean isHoldingEndCrystal() {
        if (player == null) {
            return false;
        }

        return player.getMainHandItem().is(Items.END_CRYSTAL)
                || player.getOffhandItem().is(Items.END_CRYSTAL);
    }
}
