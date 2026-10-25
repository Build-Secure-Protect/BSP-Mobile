package com.mrgregles.bsp_mobile.construct;

import com.mrgregles.bsp_mobile.compat.ValkyrienSkiesBridge;
import com.mrgregles.bsp_mobile.compat.ValkyrienSkiesBridge.Construct;
import com.mrgregles.bsp_mobile.compat.ValkyrienSkiesBridge.Inputs;
import com.mrgregles.bsp_mobile.construct.CoreService.Outcome;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * The flight plan (D63 to D66): waypoints and a cruise height kept on the core; GO runs the autopilot from the core's
 * ticker, which drives the helms' inputs like a driver (so every rule of driving holds) until the last waypoint, a
 * driver taking a helm, STOP, or a closed region; arrival at the last waypoint pulses the core's redstone.
 */
public final class PlanService {
    public static final int MAX_WAYPOINTS = 16;
    public static final int PULSE_TICKS = 40;

    private PlanService() {}

    private static Optional<MobileCoreBlockEntity> core(ServerLevel level, BlockPos pos) {
        return CoreService.core(level, pos);
    }

    private static Optional<MobileCoreBlockEntity> admitted(ServerLevel level, BlockPos pos, ServerPlayer player) {
        Optional<MobileCoreBlockEntity> core = core(level, pos);
        return core.isPresent() && Access.admitted(level, core.get(), player) ? core : Optional.empty();
    }

    /** ADD HERE: the player's own position becomes the next waypoint (D64). */
    public static Outcome addHere(ServerLevel level, BlockPos pos, ServerPlayer player) {
        Optional<MobileCoreBlockEntity> core = admitted(level, pos, player);
        if (core.isEmpty()) {
            return Outcome.failed("locked");
        }
        if (core.get().waypoints().size() >= MAX_WAYPOINTS) {
            return Outcome.failed("plan_full");
        }
        core.get().addWaypoint(BlockPos.containing(ValkyrienSkiesBridge.toWorld(level, player.position())));
        return new Outcome(true, "", core.get().shipId(), null);
    }

    public static Outcome removeLast(ServerLevel level, BlockPos pos, ServerPlayer player) {
        Optional<MobileCoreBlockEntity> core = admitted(level, pos, player);
        if (core.isEmpty()) {
            return Outcome.failed("locked");
        }
        if (core.get().waypoints().isEmpty()) {
            return Outcome.failed("plan_empty");
        }
        core.get().removeLastWaypoint();
        return new Outcome(true, "", core.get().shipId(), null);
    }

    public static Outcome setCruise(ServerLevel level, BlockPos pos, ServerPlayer player, int height) {
        Optional<MobileCoreBlockEntity> core = admitted(level, pos, player);
        if (core.isEmpty()) {
            return Outcome.failed("locked");
        }
        core.get().setCruise(Math.max(0, Math.min(320, height)));
        return new Outcome(true, "", core.get().shipId(), null);
    }

    /** GO: runs the plan from its first waypoint; needs an assembled construct with a helm and at least one waypoint. */
    public static Outcome go(ServerLevel level, BlockPos pos, ServerPlayer player) {
        Optional<MobileCoreBlockEntity> core = admitted(level, pos, player);
        if (core.isEmpty()) {
            return Outcome.failed("locked");
        }
        MobileCoreBlockEntity be = core.get();
        if (!be.isAssembled()) {
            return Outcome.failed("not_assembled");
        }
        if (be.waypoints().isEmpty()) {
            return Outcome.failed("plan_empty");
        }
        if (HelmService.helmsOf(level, be.shipId()).isEmpty()) {
            return Outcome.failed("no_helm");
        }
        if (Where.closedRegion(level, pos)) {
            return Outcome.failed("closed_region");
        }
        be.setPlanRunning(true, 0);
        return new Outcome(true, "", be.shipId(), null);
    }

    public static Outcome stop(ServerLevel level, BlockPos pos, ServerPlayer player) {
        Optional<MobileCoreBlockEntity> core = admitted(level, pos, player);
        if (core.isEmpty()) {
            return Outcome.failed("locked");
        }
        halt(level, core.get());
        return new Outcome(true, "", core.get().shipId(), null);
    }

    private static void halt(ServerLevel level, MobileCoreBlockEntity be) {
        be.setPlanRunning(false, be.planNext());
        for (HelmBlockEntity h : HelmService.helmsOf(level, be.shipId())) {
            h.setAutopilot(false);
        }
        if (be.isAssembled()) {
            ValkyrienSkiesBridge.setInputs(level.getServer(), be.shipId(), null, Inputs.NONE);
        }
    }

    /** The core's ticker: one step of the running plan. */
    public static void tick(ServerLevel level, MobileCoreBlockEntity be) {
        be.tickPulse();
        if (!be.planRunning()) {
            return;
        }
        Construct c = be.isAssembled() ? ValkyrienSkiesBridge.construct(level.getServer(), be.shipId()).orElse(null) : null;
        List<HelmBlockEntity> helms = c == null ? List.of() : HelmService.helmsOf(level, be.shipId());
        if (c == null || helms.isEmpty() || helms.stream().anyMatch(HelmBlockEntity::hasDriver) || Where.closedRegion(level, be.getBlockPos())
                || be.planNext() >= be.waypoints().size()) {
            halt(level, be);
            return;
        }
        HelmBlockEntity helm = helms.get(0);
        helms.forEach(h -> h.setAutopilot(true));
        Vec3 pos = c.positionInWorld();
        Vec3 target = Vec3.atCenterOf(be.waypoints().get(be.planNext()));
        if (Autopilot.reached(pos, target)) {
            int next = be.planNext() + 1;
            if (next >= be.waypoints().size()) {
                halt(level, be);
                be.pulse(PULSE_TICKS);
                return;
            }
            be.setPlanRunning(true, next);
            return;
        }
        double cruiseY = -1;
        if (be.cruise() > 0 && helm.parts().liftCapacity() > 0) {
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(pos.x), (int) Math.floor(pos.z));
            cruiseY = Math.min(ground + be.cruise(), helm.ceiling());
        }
        int heading = HelmService.heading(level, be.shipId(), helm.facing());
        Inputs in = Autopilot.steer(pos, heading < 0 ? 0 : heading, target, cruiseY, helm.parts().liftCapacity() > 0);
        ValkyrienSkiesBridge.setInputs(level.getServer(), be.shipId(), helm.facing(), in);
        if (c.parked()) {
            ValkyrienSkiesBridge.setParked(level, be.shipId(), false);
        }
    }

    /** The distance from the construct to its next waypoint, for the screen; -1 without one. */
    public static int distanceToNext(ServerLevel level, MobileCoreBlockEntity be) {
        if (!be.isAssembled() || be.planNext() >= be.waypoints().size()) {
            return -1;
        }
        return ValkyrienSkiesBridge.construct(level.getServer(), be.shipId())
                .map(c -> (int) Math.round(Autopilot.distance(c.positionInWorld(), Vec3.atCenterOf(be.waypoints().get(be.planNext()))))).orElse(-1);
    }
}
