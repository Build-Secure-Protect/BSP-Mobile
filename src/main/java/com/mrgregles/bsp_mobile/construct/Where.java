package com.mrgregles.bsp_mobile.construct;

import com.mrgregles.bsp_mobile.MobileConfig;
import com.mrgregles.bsp_mobile.api.MobilePositions;
import com.mrgregles.bsp_mobile.compat.BspCore;
import com.mrgregles.bsp_mobile.compat.RegionGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Where constructs may go (D49, D50): a closed server (its own switch, or a no-totem server of BSP-Core) allows none;
 * with Region Guard the flag {@code mobile-constructs} decides per region. Assembly is refused where constructs are
 * not allowed; a construct whose core stands in a closed region may only back out; releasing is allowed anywhere.
 */
public final class Where {
    private Where() {}

    /** True when this server allows no constructs at all. */
    public static boolean serverClosed() {
        return MobileConfig.regionsClosed() || (MobileConfig.regionsClosedOnNoTotemServers() && BspCore.isNoTotemServer());
    }

    /** Why assembling at {@code pos} (a world or shipyard position; it is projected) is refused, or an empty string when it is allowed. */
    public static String assemblyRefused(ServerLevel level, BlockPos pos, @Nullable ServerPlayer actor) {
        if (serverClosed()) {
            return "closed_server";
        }
        if (!RegionGuard.allowsConstructs(level, MobilePositions.toWorld(level, pos), actor)) {
            return "closed_region";
        }
        return "";
    }

    /** True when the construct whose core stands at {@code corePos} is inside a region that denies constructs. */
    public static boolean closedRegion(ServerLevel level, BlockPos corePos) {
        return !RegionGuard.allowsConstructs(level, MobilePositions.toWorld(level, corePos), null);
    }

    /** The driver's forward input inside a closed region: no thrust forward, backing out allowed. */
    public static float clampForward(float forward, boolean closedRegion) {
        return closedRegion && forward > 0 ? 0 : forward;
    }
}
