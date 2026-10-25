package com.mrgregles.bsp_mobile.construct;

import com.mrgregles.bsp_mobile.MobileConfig;
import com.mrgregles.bsp_mobile.compat.ValkyrienSkiesBridge;
import com.mrgregles.bsp_mobile.compat.ValkyrienSkiesBridge.Construct;
import com.mrgregles.bsp_mobile.compat.ValkyrienSkiesBridge.Inputs;
import com.mrgregles.bsp_mobile.construct.CoreService.Outcome;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Driving (D37 to D42): taking a helm, the helm's tick (the driver's keys become the construct's command, the
 * construct unparks under a driver and parks itself when left standing), the anchor, and which helms a construct
 * carries. Valkyrien Skies only through the bridge.
 */
public final class HelmService {
    private static final int REFRESH_TICKS = 40;

    private HelmService() {}

    private static Optional<HelmBlockEntity> helm(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof HelmBlockEntity be ? Optional.of(be) : Optional.empty();
    }

    /** Right-click on the helm: the player takes it when the construct is assembled, has a core whose lock admits them, and the helm is free. */
    public static Outcome take(ServerLevel level, BlockPos pos, ServerPlayer player) {
        Optional<HelmBlockEntity> helm = helm(level, pos);
        if (helm.isEmpty()) {
            return Outcome.failed("no_helm");
        }
        HelmBlockEntity be = helm.get();
        Construct c = ValkyrienSkiesBridge.constructAt(level, pos).orElse(null);
        if (c == null) {
            return Outcome.failed("assemble_first");
        }
        Optional<MobileCoreBlockEntity> core = CoreService.coreOf(level, c.id());
        if (core.isEmpty()) {
            return Outcome.failed("no_core");
        }
        if (!Access.admitted(level, core.get(), player)) {
            return Outcome.failed("locked");
        }
        if (ValkyrienSkiesBridge.driverOf(level, be.seat()).isPresent()) {
            return Outcome.failed("taken");
        }
        Direction facing = be.facing();
        BlockPos standAt = pos.relative(facing.getOpposite());
        CoreService.Aboard aboardNow = CoreService.aboard(level, Gather.gather(level, core.get().getBlockPos(), Tiers.allowance(core.get().tier())));
        Optional<UUID> seat = ValkyrienSkiesBridge.mount(level, c.id(), standAt, facing, player);
        if (seat.isEmpty()) {
            return Outcome.failed("engine");
        }
        be.setSeat(seat.get(), player.getUUID(), player.getGameProfile().getName());
        be.setShipId(c.id());
        be.setCore(core.get().getBlockPos(), aboardNow.rules());
        be.setParts(aboardNow.parts());
        ValkyrienSkiesBridge.setParked(level, c.id(), false);
        return new Outcome(true, "", c.id(), null);
    }

    /** The helm is removed or its construct released: the driver steps off and the construct stops being driven from it. */
    public static void leave(ServerLevel level, HelmBlockEntity be) {
        if (be.shipId() != 0) {
            ValkyrienSkiesBridge.removeSeat(level, be.shipId(), be.seat());
            ValkyrienSkiesBridge.stopDriving(be.shipId());
        }
        be.clearSeat();
    }

    /** The helm's ticker. */
    public static void tick(ServerLevel level, HelmBlockEntity be) {
        Construct c = ValkyrienSkiesBridge.constructAt(level, be.getBlockPos()).orElse(null);
        if (c == null) {
            if (be.shipId() != 0) {
                leave(level, be);
                be.setShipId(0);
            }
            return;
        }
        be.setShipId(c.id());
        if (be.refreshDue(REFRESH_TICKS) || be.corePos() == null) {
            Optional<MobileCoreBlockEntity> core = CoreService.coreOf(level, c.id());
            if (core.isPresent()) {
                MobileCoreBlockEntity k = core.get();
                CoreService.Aboard aboard = CoreService.aboard(level, Gather.gather(level, k.getBlockPos(), Tiers.allowance(k.tier())));
                be.setCore(k.getBlockPos(), aboard.rules());
                be.setClosedRegion(Where.closedRegion(level, k.getBlockPos()));
                Parts was = be.parts();
                be.setParts(aboard.parts());
                if (!was.wingPositions().equals(aboard.parts().wingPositions())) {
                    List<BlockPos> gone = new ArrayList<>(was.wingPositions());
                    gone.removeAll(aboard.parts().wingPositions());
                    ValkyrienSkiesBridge.setWings(level, c.id(), aboard.parts().wingPositions(), gone, MobileConfig.partsWingLift(), MobileConfig.partsWingDrag());
                }
                be.setCeiling(aboard.rules().capped() ? ceiling(level, c, aboard.rules()) : Integer.MAX_VALUE);
            } else {
                be.setCore(null, ConstructRules.FREE);
                be.setClosedRegion(false);
                be.setParts(Parts.NONE);
                be.setCeiling(Integer.MAX_VALUE);
            }
        }
        // the driver, if still seated
        Optional<ServerPlayer> driver = ValkyrienSkiesBridge.driverOf(level, be.seat());
        if (be.hasDriver() && driver.isEmpty()) {
            ValkyrienSkiesBridge.removeSeat(level, c.id(), be.seat());
            be.clearSeat();
        }
        Inputs in = driver.isPresent() || be.autopilot() ? ValkyrienSkiesBridge.inputs(level.getServer(), c.id()) : Inputs.NONE;
        Drive.Command cmd = command(in, be.facing(), be.parts(), be.rules(), be.closedRegion(), c.positionInWorld().y, be.ceiling());
        ValkyrienSkiesBridge.drive(level, c.id(), cmd);
        if (driver.isPresent() || be.autopilot()) {
            if (cmd.driving() && c.parked()) {
                ValkyrienSkiesBridge.setParked(level, c.id(), false);
            }
            be.setStillTicks(0);
            return;
        }
        // nobody driving: park once it has stood still long enough (D40)
        if (!c.parked()) {
            if (c.speed() < MobileConfig.movingSpeed()) {
                be.setStillTicks(be.stillTicks() + 1);
                if (be.stillTicks() >= MobileConfig.driveParkTicks()) {
                    ValkyrienSkiesBridge.setParked(level, c.id(), true);
                    be.setStillTicks(0);
                }
            } else {
                be.setStillTicks(0);
            }
        }
    }

    /** The helm's command from the driver's keys and what is aboard (D37, D52 to D55); client-free, for the tests. */
    public static Drive.Command command(Inputs in, Direction facing, Parts parts, ConstructRules rules, boolean closedRegion, double y, double ceilingY) {
        return new Drive.Command(Where.clampForward(in.forward(), closedRegion), in.left(), in.sprint(), in.up(), facing.getStepX(), facing.getStepZ(),
                parts.accel(), Math.min(parts.maxSpeed(), rules.maxSpeed()), MobileConfig.driveTurnRate(), MobileConfig.driveDrag(), parts.buoyancy(),
                parts.liftCapacity(), parts.hovers(), MobileConfig.partsClimb(), MobileConfig.partsGravity(), y, ceilingY);
    }

    /** The height a capped construct may not climb past: the ground beneath its position plus the cap (D55). */
    public static int ceiling(ServerLevel level, Construct c, ConstructRules rules) {
        if (!rules.capped()) {
            return Integer.MAX_VALUE;
        }
        int x = (int) Math.floor(c.positionInWorld().x), z = (int) Math.floor(c.positionInWorld().z);
        int ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return ground + rules.maxHeight();
    }

    /** ANCHOR on the HELM tab: parks the construct, or frees it. */
    public static Outcome anchor(ServerLevel level, BlockPos corePos, ServerPlayer player, boolean down) {
        Optional<MobileCoreBlockEntity> core = CoreService.core(level, corePos);
        if (core.isEmpty()) {
            return Outcome.failed("no_core");
        }
        MobileCoreBlockEntity be = core.get();
        if (!Access.admitted(level, be, player)) {
            return Outcome.failed("locked");
        }
        if (!be.isAssembled()) {
            return Outcome.failed("not_assembled");
        }
        if (!ValkyrienSkiesBridge.setParked(level, be.shipId(), down)) {
            return Outcome.failed("gone");
        }
        for (HelmBlockEntity h : helmsOf(level, be.shipId())) {
            h.setStillTicks(0);
        }
        return new Outcome(true, "", be.shipId(), null);
    }

    /** The helms in a construct's shipyard box. */
    public static List<HelmBlockEntity> helmsOf(ServerLevel level, long shipId) {
        List<HelmBlockEntity> out = new ArrayList<>();
        Construct c = ValkyrienSkiesBridge.construct(level.getServer(), shipId).orElse(null);
        if (c == null || c.shipyardMin() == null || c.shipyardMax() == null) {
            return out;
        }
        for (int x = c.shipyardMin().getX(); x < c.shipyardMax().getX(); x++) {
            for (int y = c.shipyardMin().getY(); y < c.shipyardMax().getY(); y++) {
                for (int z = c.shipyardMin().getZ(); z < c.shipyardMax().getZ(); z++) {
                    if (level.getBlockEntity(new BlockPos(x, y, z)) instanceof HelmBlockEntity be) {
                        out.add(be);
                    }
                }
            }
        }
        return out;
    }

    /** The compass heading (0 north, 90 east) the helm's facing points to in the world, or -1 without a construct. */
    public static int heading(ServerLevel level, long shipId, @Nullable Direction facing) {
        Optional<net.minecraft.world.phys.Vec3> f = ValkyrienSkiesBridge.forwardInWorld(level.getServer(), shipId, facing == null ? Direction.NORTH : facing);
        if (f.isEmpty()) {
            return -1;
        }
        double yaw = Math.toDegrees(Math.atan2(-f.get().x, f.get().z)); // Minecraft's yaw: 0 south, 90 west
        int compass = (int) Math.round(yaw + 180) % 360;              // 0 north, clockwise
        return compass < 0 ? compass + 360 : compass;
    }
}
