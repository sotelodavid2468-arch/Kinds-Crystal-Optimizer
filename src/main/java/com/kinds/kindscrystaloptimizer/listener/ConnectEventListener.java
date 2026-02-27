package com.kinds.kindscrystaloptimizer.listener;

import com.kinds.kindscrystaloptimizer.KindsCrystalOptimizer;
import com.kinds.kindscrystaloptimizer.cache.OptOutCache;
import com.kinds.kindscrystaloptimizer.util.ConnectionUtil;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.jspecify.annotations.NonNull;

public final class ConnectEventListener implements ClientPlayConnectionEvents.Join {

    private final OptOutCache optOutCache;

    public ConnectEventListener() {
        this.optOutCache = KindsCrystalOptimizer.getInstance().getOptOutCache();
    }

    @Override
    public void onPlayReady(@NonNull ClientPacketListener handler, @NonNull PacketSender sender, @NonNull Minecraft client) {
        if (client.isLocalServer()) return;

        sender.sendPacket(KindsCrystalOptimizer.getInstance().getVersionPacket());

        String key = ConnectionUtil.currentServerKey(client);
        boolean shouldOptOut = optOutCache.isServerOptedOut(key);

        if (shouldOptOut) {
            optOutCache.setOptedOut(true);
        }
    }
}
