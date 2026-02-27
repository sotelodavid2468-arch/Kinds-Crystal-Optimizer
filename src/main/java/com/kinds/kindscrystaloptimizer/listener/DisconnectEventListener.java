package com.kinds.kindscrystaloptimizer.listener;

import com.kinds.kindscrystaloptimizer.KindsCrystalOptimizer;
import com.kinds.kindscrystaloptimizer.cache.OptOutCache;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.jspecify.annotations.NonNull;

public final class DisconnectEventListener implements ClientPlayConnectionEvents.Disconnect {

    private final OptOutCache optOutCache;

    public DisconnectEventListener() {
        optOutCache = KindsCrystalOptimizer.getInstance().getOptOutCache();
    }

    @Override
    public void onPlayDisconnect(@NonNull ClientPacketListener handler, @NonNull Minecraft client) {
        optOutCache.clearCurrentSession();
    }
}