package com.mrgregles.bsp_mobile.compat;

import com.mrgregles.bsp_mobile.BSPMobile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * The one class that talks to BSP Region Guard, through its {@code api} package only, and only when it is loaded
 * (D48, D51): it registers the state flag {@code mobile-constructs} (allow by default) and asks whether a position
 * allows constructs. Without Region Guard every position allows them.
 */
public final class RegionGuard {
    public static final String MOD_ID = "bsp_region_guard";
    public static final String FLAG = "mobile-constructs";

    private RegionGuard() {}

    public static boolean isPresent() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /** Registers the flag (common setup, before any server starts). */
    public static void registerFlag() {
        if (!isPresent()) {
            return;
        }
        try {
            Impl.register();
            BSPMobile.LOGGER.info("BSP-Mobile: Region Guard flag {} registered", FLAG);
        } catch (LinkageError | RuntimeException e) {
            BSPMobile.LOGGER.error("BSP-Mobile: Region Guard refused the flag {}; regions will not close constructs out", FLAG, e);
        }
    }

    /** True when constructs may be assembled or driven at {@code pos}; {@code player} may be null (the construct itself). */
    public static boolean allowsConstructs(ServerLevel level, BlockPos pos, @Nullable Player player) {
        if (!isPresent()) {
            return true;
        }
        try {
            return Impl.allows(level, pos, player);
        } catch (LinkageError | RuntimeException e) {
            return true;
        }
    }

    /** Holds every Region Guard type, so nothing of it loads when Region Guard is absent. */
    private static final class Impl {
        @Nullable
        private static com.mrgregles.bsp_region_guard.flag.StateFlag flag;

        static void register() {
            flag = com.mrgregles.bsp_region_guard.api.RegionGuardApi.get().regions().registerFlag(
                    new com.mrgregles.bsp_region_guard.flag.StateFlag(FLAG, com.mrgregles.bsp_region_guard.flag.State.ALLOW, false, null));
        }

        static boolean allows(ServerLevel level, BlockPos pos, @Nullable Player player) {
            if (flag == null) {
                return true;
            }
            return com.mrgregles.bsp_region_guard.api.RegionGuardApi.get().regions().allows(level, pos, flag, player);
        }
    }
}
