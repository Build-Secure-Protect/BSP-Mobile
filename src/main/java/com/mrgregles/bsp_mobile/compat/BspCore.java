package com.mrgregles.bsp_mobile.compat;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;

/**
 * The one class that talks to BSP-Core, through its {@code api} package only, and only when it is loaded. An older or
 * missing BSP-Core costs the feature, never a crash.
 */
public final class BspCore {
    public static final String MOD_ID = "bsp_core";

    private BspCore() {}

    public static boolean isPresent() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /** True for a placed Shatter Totem. */
    public static boolean isTotem(BlockState state) {
        if (!isPresent()) {
            return false;
        }
        try {
            return Impl.isTotem(state);
        } catch (LinkageError | RuntimeException e) {
            return false;
        }
    }

    /** Holds every BSP-Core type, so nothing of it loads when BSP-Core is absent. */
    private static final class Impl {
        static boolean isTotem(BlockState state) {
            return com.mrgregles.bsp_core.api.BspTotems.isTotem(state);
        }
    }
}
