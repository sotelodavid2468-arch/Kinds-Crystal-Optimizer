package com.kinds.kindscrystaloptimizer;

import com.kinds.kindscrystaloptimizer.cache.OptOutCache;
import com.kinds.kindscrystaloptimizer.listener.ConnectEventListener;
import com.kinds.kindscrystaloptimizer.listener.DisconnectEventListener;
import com.kinds.kindscrystaloptimizer.listener.OptOutPacketListener;
import com.kinds.kindscrystaloptimizer.packet.impl.OptOutAckPacket;
import com.kinds.kindscrystaloptimizer.packet.impl.OptOutPacket;
import com.kinds.kindscrystaloptimizer.packet.impl.VersionPacket;
import com.kinds.kindscrystaloptimizer.util.Logger;
import com.kinds.kindscrystaloptimizer.util.PerformanceGuard;
import com.kinds.kindscrystaloptimizer.util.VersionUtil;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public class KindsCrystalOptimizer implements ClientModInitializer {

    public static final String MOD_ID = "kindscrystaloptimizer";

    public static final Component PREFIX = Component.literal("[").withStyle(ChatFormatting.GRAY)
            .append(Component.literal("Kind's Crystal Optimizer").withStyle(ChatFormatting.AQUA))
            .append(Component.literal("] ").withStyle(ChatFormatting.GRAY));

    private static KindsCrystalOptimizer instance;
    private static Logger logger;

    private final OptOutCache optOutCache;
    private final PerformanceGuard performanceGuard;
    private VersionPacket versionPacket;

    public KindsCrystalOptimizer() {
        instance = this;
        logger = new Logger();
        optOutCache = new OptOutCache();
        performanceGuard = new PerformanceGuard();
    }

    public static KindsCrystalOptimizer getInstance() {
        return instance;
    }

    public static Logger getLogger() {
        return logger;
    }

    public OptOutCache getOptOutCache() {
        return optOutCache;
    }

    public VersionPacket getVersionPacket() {
        return versionPacket;
    }

    public PerformanceGuard getPerformanceGuard() {
        return performanceGuard;
    }

    @Override
    public void onInitializeClient() {
        versionPacket = VersionUtil.createVersionPacket();

        PayloadTypeRegistry.configurationS2C().register(OptOutPacket.TYPE, OptOutPacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(OptOutPacket.TYPE, OptOutPacket.STREAM_CODEC);

        PayloadTypeRegistry.playC2S().register(OptOutAckPacket.TYPE, OptOutAckPacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(VersionPacket.TYPE, VersionPacket.STREAM_CODEC);

        ClientPlayConnectionEvents.JOIN.register(new ConnectEventListener());
        ClientPlayConnectionEvents.DISCONNECT.register(new DisconnectEventListener());
        OptOutPacketListener.register();

        logger.info("Mod initialized");
    }
}
