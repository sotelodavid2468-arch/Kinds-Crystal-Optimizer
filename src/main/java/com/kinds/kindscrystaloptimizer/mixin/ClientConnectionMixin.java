package com.kinds.kindscrystaloptimizer.mixin;

import com.kinds.kindscrystaloptimizer.KindsCrystalOptimizer;
import com.kinds.kindscrystaloptimizer.cache.OptOutCache;
import com.kinds.kindscrystaloptimizer.handler.InteractHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class ClientConnectionMixin {

    @Unique
    private KindsCrystalOptimizer optimizer;

    @Unique
    private OptOutCache optOutCache;

    @Unique
    private InteractHandler cachedHandler;

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
    private void onPacketSend(Packet<?> packet, CallbackInfo ci) {
        if (packet instanceof ServerboundInteractPacket interactionPacket) {
            if (optimizer == null) {
                optimizer = KindsCrystalOptimizer.getInstance();
            }
            if (optimizer == null) {
                return;
            }

            if (optOutCache == null) {
                optOutCache = optimizer.getOptOutCache();
            }
            if (optOutCache.isOptedOut()) return;

            if (cachedHandler == null) {
                cachedHandler = new InteractHandler(Minecraft.getInstance());
            }
            interactionPacket.dispatch(cachedHandler);
        }
    }
}
