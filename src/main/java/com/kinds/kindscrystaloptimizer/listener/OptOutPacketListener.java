package com.kinds.kindscrystaloptimizer.listener;

import com.kinds.kindscrystaloptimizer.KindsCrystalOptimizer;
import com.kinds.kindscrystaloptimizer.cache.OptOutCache;
import com.kinds.kindscrystaloptimizer.packet.impl.OptOutAckPacket;
import com.kinds.kindscrystaloptimizer.packet.impl.OptOutPacket;
import com.kinds.kindscrystaloptimizer.util.ConnectionUtil;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.Util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class OptOutPacketListener {

    private OptOutPacketListener() {
    }

    private static Component optimizerDisabledMessage() {
        Component hover = Component.empty()
                .append(Component.literal("Why is this disabled?\n").withStyle(ChatFormatting.AQUA))
                .append(Component.literal("• This server requested Kind's Crystal Optimizer to be disabled.\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("• Usually for rules enforcement or compatibility.\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("\nApplies only while you stay on this server.").withStyle(ChatFormatting.DARK_GRAY));

        Style hoverStyle = Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover));
        Component message = Component.literal("Optimizer disabled on this server.")
                .withStyle(hoverStyle.withColor(ChatFormatting.RED));

        return KindsCrystalOptimizer.PREFIX.copy()
                .withStyle(hoverStyle)
                .append(message);
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(OptOutPacket.TYPE, (payload, context) -> {
            Minecraft client = context.client();
            OptOutCache cache = KindsCrystalOptimizer.getInstance().getOptOutCache();

            String key = ConnectionUtil.currentServerKey(client);

            if (key != null) {
                cache.markOptedOut(key);
            } else {
                cache.setOptedOut(true);
            }

            if (!cache.hasNotified(key)) {
                CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS, Util.backgroundExecutor()).execute(() -> client.execute(() -> {
                    if (client.player == null) return;
                    if (cache.hasNotified(key)) return;

                    cache.markNotified(key);
                    client.player.displayClientMessage(optimizerDisabledMessage(), false);
                }));
            }

            ClientPlayNetworking.send(OptOutAckPacket.INSTANCE);
        });
    }
}
