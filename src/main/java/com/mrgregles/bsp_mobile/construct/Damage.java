package com.mrgregles.bsp_mobile.construct;

import com.mrgregles.bsp_mobile.MobileConfig;
import com.mrgregles.bsp_mobile.compat.ValkyrienSkiesBridge;
import com.mrgregles.bsp_mobile.compat.ValkyrienSkiesBridge.Construct;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Damage (D44, D47): an impact faster than the threshold knocks blocks off a construct at the point of contact, one
 * for every so many blocks a second over the threshold, up to a cap, the nearest blocks first, never the core or a
 * helm. The maths is client-free for the GameTests; the knocking breaks real blocks in the shipyard, which drop as if
 * mined. One impact counts per construct per cooldown.
 */
public final class Damage {
    private static final double REACH = 4.0;
    /** Ship id -> the server tick until which further impacts are ignored. */
    private static final Map<Long, Long> COOLDOWN = new HashMap<>();

    private Damage() {}

    /** How many blocks an impact at {@code speed} knocks off: none up to the threshold, then one per {@code perBlock} over it, at most {@code max}. */
    public static int blocksToKnock(double speed, double threshold, double perBlock, int max) {
        if (speed <= threshold || perBlock <= 0 || max <= 0) {
            return 0;
        }
        return (int) Math.min(max, Math.ceil((speed - threshold) / perBlock));
    }

    public static int blocksToKnock(double speed) {
        return blocksToKnock(speed, MobileConfig.damageImpactSpeed(), MobileConfig.damageBlocksPerSpeed(), MobileConfig.damageMaxBlocks());
    }

    /** True when this construct may take an impact now; marks the cooldown when it may. */
    static boolean impactAllowed(long shipId, long gameTick) {
        Long until = COOLDOWN.get(shipId);
        if (until != null && gameTick < until) {
            return false;
        }
        COOLDOWN.put(shipId, gameTick + MobileConfig.damageCooldownTicks());
        return true;
    }

    /**
     * Knocks up to {@code count} blocks off the construct nearest to {@code contactWorld} (a world position), never the
     * core or a helm. Returns the shipyard positions broken.
     */
    public static List<BlockPos> knock(ServerLevel level, long shipId, Vec3 contactWorld, int count) {
        List<BlockPos> broken = new ArrayList<>();
        if (count <= 0) {
            return broken;
        }
        Construct c = ValkyrienSkiesBridge.construct(level.getServer(), shipId).orElse(null);
        if (c == null || c.shipyardMin() == null || c.shipyardMax() == null) {
            return broken;
        }
        Vec3 contact = ValkyrienSkiesBridge.toShip(level.getServer(), shipId, contactWorld).orElse(null);
        if (contact == null) {
            return broken;
        }
        BlockPos centre = BlockPos.containing(contact);
        int r = (int) Math.ceil(REACH);
        List<BlockPos> candidates = new ArrayList<>();
        for (int x = Math.max(c.shipyardMin().getX(), centre.getX() - r); x < Math.min(c.shipyardMax().getX(), centre.getX() + r + 1); x++) {
            for (int y = Math.max(c.shipyardMin().getY(), centre.getY() - r); y < Math.min(c.shipyardMax().getY(), centre.getY() + r + 1); y++) {
                for (int z = Math.max(c.shipyardMin().getZ(), centre.getZ() - r); z < Math.min(c.shipyardMax().getZ(), centre.getZ() + r + 1); z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (level.getBlockState(p).isAir() || level.getBlockEntity(p) instanceof MobileCoreBlockEntity || level.getBlockEntity(p) instanceof HelmBlockEntity) {
                        continue;
                    }
                    if (Vec3.atCenterOf(p).distanceTo(contact) <= REACH) {
                        candidates.add(p);
                    }
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(p -> Vec3.atCenterOf(p).distanceToSqr(contact)));
        for (BlockPos p : candidates) {
            if (broken.size() >= count) {
                break;
            }
            if (level.destroyBlock(p, true)) {
                broken.add(p);
            }
        }
        return broken;
    }
}
