package com.mrgregles.bsp_mobile.construct;

import com.mrgregles.bsp_mobile.MobileConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** The parts aboard a construct (D52), counted from the gather's blocks, and what they add up to (D54). */
public record Parts(int engines, int floaters, int balloons, int wings, int jets, List<BlockPos> wingPositions) {
    public static final Parts NONE = new Parts(0, 0, 0, 0, 0, List.of());

    public static Parts count(ServerLevel level, Collection<BlockPos> blocks) {
        int e = 0, f = 0, b = 0, w = 0, j = 0;
        List<BlockPos> wingsAt = new ArrayList<>();
        for (BlockPos p : blocks) {
            if (level.getBlockState(p).getBlock() instanceof PartBlock part) {
                switch (part.kind()) {
                    case ENGINE -> e++;
                    case FLOATER -> f++;
                    case BALLOON -> b++;
                    case WING -> {
                        w++;
                        wingsAt.add(p.immutable());
                    }
                    case JET -> j++;
                }
            }
        }
        return new Parts(e, f, b, w, j, List.copyOf(wingsAt));
    }

    public boolean any() {
        return engines + floaters + balloons + wings + jets > 0;
    }

    /** Acceleration with the engines, blocks a second each second. */
    public double accel() {
        return MobileConfig.driveAccel() + engines * MobileConfig.partsEngineAccel() + jets * MobileConfig.partsJetAccel();
    }

    /** Top speed with the engines, before the totem's cap. */
    public double maxSpeed() {
        return MobileConfig.driveMaxSpeed() + engines * MobileConfig.partsEngineSpeed() + jets * MobileConfig.partsJetSpeed();
    }

    public double buoyancy() {
        return MobileConfig.driveBuoyancy() + floaters * MobileConfig.partsFloaterBuoyancy();
    }

    /** How many blocks of mass the lift aboard can carry. */
    public double liftCapacity() {
        return balloons * MobileConfig.partsBalloonLift() + jets * MobileConfig.partsJetLift();
    }

    /** True when the construct holds its height without input (balloons aboard). */
    public boolean hovers() {
        return balloons > 0;
    }
}
