package com.kinds.kindscrystaloptimizer.packet;

import com.kinds.kindscrystaloptimizer.KindsCrystalOptimizer;
import net.minecraft.resources.ResourceLocation;

public final class ModPackets {

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(KindsCrystalOptimizer.MOD_ID, path);
    }
}
