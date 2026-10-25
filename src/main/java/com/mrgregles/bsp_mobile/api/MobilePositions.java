package com.mrgregles.bsp_mobile.api;

import com.mrgregles.bsp_mobile.compat.ValkyrienSkiesBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Where a block aboard a construct really is. A construct's blocks stand in the engine's shipyard, real blocks far
 * away in the same dimension, drawn where the construct is; a mod that measures distances around such a block (BSP-Core
 * around a Shatter Totem, request M-R1) projects it into the world first. Every call is the identity, or false, for a
 * position that is not aboard, so a caller may use them for every block. Plain static methods with these exact
 * signatures, so a mod may find them by name with {@code MethodHandles.publicLookup().findStatic} instead of compiling
 * against this jar. Since 0.1.0.
 */
public final class MobilePositions {
    private MobilePositions() {}

    /** The world position a point aboard a construct is drawn at; {@code pos} itself when it is not aboard. */
    public static Vec3 toWorld(Level level, Vec3 pos) {
        return ValkyrienSkiesBridge.toWorld(level, pos);
    }

    /** The world block a block aboard a construct is drawn at (its centre projected); {@code pos} itself when not aboard. */
    public static BlockPos toWorld(Level level, BlockPos pos) {
        return isAboard(level, pos) ? BlockPos.containing(toWorld(level, Vec3.atCenterOf(pos))) : pos;
    }

    /** True when the position lies in a construct (the engine's shipyard), not in the ordinary world. */
    public static boolean isAboard(Level level, BlockPos pos) {
        return ValkyrienSkiesBridge.isInShipyard(level, pos);
    }
}
