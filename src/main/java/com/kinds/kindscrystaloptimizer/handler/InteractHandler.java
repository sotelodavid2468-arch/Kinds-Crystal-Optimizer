package com.kinds.kindscrystaloptimizer.handler;

import com.kinds.kindscrystaloptimizer.KindsCrystalOptimizer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Unique;

public class InteractHandler implements ServerboundInteractPacket.Handler {

    @Unique
    private final Minecraft client;

    public InteractHandler(Minecraft client) {
        this.client = client;
    }

    @Override
    public void onInteraction(@NotNull InteractionHand interactionHand) {
    }

    @Override
    public void onInteraction(@NotNull InteractionHand interactionHand, @NotNull Vec3 vec3) {
    }

    @Override
    public void onAttack() {
        HitResult hitResult = client.hitResult;
        if (!(hitResult instanceof EntityHitResult entityHitResult)) {
            return;
        }

        Entity entity = entityHitResult.getEntity();
        if (!(entity instanceof EndCrystal crystal)) {
            return;
        }

        if (client.player == null) {
            return;
        }

        if (client.options.keyUse.isDown() && (client.player.getMainHandItem().is(Items.END_CRYSTAL)
                || client.player.getOffhandItem().is(Items.END_CRYSTAL))) {
            return;
        }

        KindsCrystalOptimizer optimizer = KindsCrystalOptimizer.getInstance();
        if (optimizer == null) {
            return;
        }
        if (!optimizer.getPerformanceGuard().allowBreakPrediction(crystal.getId())) {
            return;
        }

        destroyCrystal(crystal);
    }

    private void destroyCrystal(Entity crystal) {
        crystal.remove(Entity.RemovalReason.KILLED);

        crystal.gameEvent(GameEvent.ENTITY_DIE);
    }
}
