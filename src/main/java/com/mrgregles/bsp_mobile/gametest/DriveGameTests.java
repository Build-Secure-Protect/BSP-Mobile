package com.mrgregles.bsp_mobile.gametest;

import com.mrgregles.bsp_mobile.BSPMobile;
import com.mrgregles.bsp_mobile.MobileConfig;
import com.mrgregles.bsp_mobile.compat.ValkyrienSkiesBridge;
import com.mrgregles.bsp_mobile.compat.ValkyrienSkiesBridge.Inputs;
import com.mrgregles.bsp_mobile.construct.CoreService;
import com.mrgregles.bsp_mobile.construct.Drive;
import com.mrgregles.bsp_mobile.construct.HelmBlock;
import com.mrgregles.bsp_mobile.construct.HelmBlockEntity;
import com.mrgregles.bsp_mobile.construct.HelmService;
import com.mrgregles.bsp_mobile.construct.MobileCoreBlockEntity;
import com.mrgregles.bsp_mobile.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/** MB-4: the drive maths, taking the helm (and its refusals), a driven construct moving, and parking itself when left. */
@GameTestHolder(BSPMobile.MODID)
@PrefixGameTestTemplate(false)
public class DriveGameTests {
    private static final BlockPos CORE = new BlockPos(10, 3, 10), HELM = new BlockPos(11, 3, 10);
    private static final int TIMEOUT = 600;

    @GameTest(template = SetupGameTests.EMPTY)
    public void the_drive_maths_thrust_caps_and_drags(GameTestHelper h) {
        Drive.Command go = Drive.Command.ground(1, 0, false, 0, -1, 4.0, 8.0, 30.0, 0.35, 4.0); // facing north
        Drive.Forces f = Drive.forces(100, 50, 0, 0, 0, 0, go);
        if (Math.abs(f.fz() + 400) > 1e-9 || f.fx() != 0 || f.torqueY() != 0) {
            h.fail("full thrust north on 100 mass must be 400 along -z: " + f);
            return;
        }
        Drive.Forces sprint = Drive.forces(100, 50, 0, 0, 0, 0, Drive.Command.ground(1, 0, true, 0, -1, 4.0, 8.0, 30.0, 0.35, 4.0));
        if (Math.abs(sprint.fz() + 800) > 1e-9) {
            h.fail("sprint must double the thrust: " + sprint);
            return;
        }
        // at the top speed forward the thrust stops and only drag remains
        Drive.Forces capped = Drive.forces(100, 50, 0, 0, -8.0, 0, go);
        if (Math.abs(capped.fz() - 0.35 * 100 * 8.0) > 1e-9) {
            h.fail("at the top speed only the drag (opposing the motion) may remain: " + capped);
            return;
        }
        // coasting: no input, drag opposes the velocity on both axes, no torque without yaw
        Drive.Forces coast = Drive.forces(100, 50, 2.0, 0, -4.0, 0, Drive.Command.idle(0, -1, 0.35, 4.0));
        if (Math.abs(coast.fx() + 70) > 1e-9 || Math.abs(coast.fz() - 140) > 1e-9 || coast.torqueY() != 0) {
            h.fail("coasting must only drag: " + coast);
            return;
        }
        // turning left seeks the turn rate; an ongoing yaw at the wanted rate gets no torque
        Drive.Forces turn = Drive.forces(100, 50, 0, 0, 0, 0, Drive.Command.ground(0, 1, false, 0, -1, 4.0, 8.0, 30.0, 0.35, 4.0));
        if (turn.torqueY() <= 0) {
            h.fail("a left turn must be a positive torque about up: " + turn);
            return;
        }
        Drive.Forces steady = Drive.forces(100, 50, 0, 0, 0, Math.toRadians(30), Drive.Command.ground(0, 1, false, 0, -1, 4.0, 8.0, 30.0, 0.35, 4.0));
        if (Math.abs(steady.torqueY()) > 1e-9) {
            h.fail("yawing at the turn rate needs no more torque: " + steady);
            return;
        }
        h.succeed();
    }

    private static MobileCoreBlockEntity build(GameTestHelper h) {
        h.setBlock(CORE, ModBlocks.MOBILE_CORE.get());
        h.setBlock(HELM, ModBlocks.MOBILE_HELM.get().defaultBlockState().setValue(HelmBlock.FACING, Direction.NORTH));
        for (int x = 9; x <= 12; x++) {
            for (int z = 9; z <= 11; z++) {
                h.setBlock(new BlockPos(x, 2, z), Blocks.OAK_PLANKS);
            }
        }
        return (MobileCoreBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(CORE));
    }

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        p.setPos(h.absoluteVec(new Vec3(11.5, 3, 11.5)));
        return p;
    }

    @GameTest(template = SetupGameTests.EMPTY, timeoutTicks = 1600)
    public void the_helm_refuses_an_unassembled_structure_and_a_locked_core_then_takes_a_driver(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        MobileCoreBlockEntity core = build(h);
        BlockPos corePos = h.absolutePos(CORE), helmPos = h.absolutePos(HELM);
        ServerPlayer owner = player(h), stranger = player(h);
        CoreService.Outcome out = HelmService.take(level, helmPos, stranger);
        if (out.ok() || !out.reason().equals("assemble_first")) {
            h.fail("a helm on an unassembled structure must say assemble first: " + out);
            return;
        }
        core.lock(owner.getUUID(), owner.getGameProfile().getName());
        Waits.afterTicking(h, 1, () -> {
            CoreService.Outcome a = CoreService.assemble(level, corePos);
            if (!a.ok()) {
                h.fail("assembly refused: " + a.reason());
                return;
            }
            long id = a.shipId();
            Waits.pollThen(h, TIMEOUT, () -> CoreService.coreOf(level, id).map(be -> be.shipId() == id).orElse(false) && !HelmService.helmsOf(level, id).isEmpty()
                    && ValkyrienSkiesBridge.isLoaded(level.getServer(), id), () -> {
                HelmBlockEntity helm = HelmService.helmsOf(level, id).get(0);
                BlockPos hp = helm.getBlockPos();
                CoreService.Outcome s = HelmService.take(level, hp, stranger);
                if (s.ok() || !s.reason().equals("locked")) {
                    ValkyrienSkiesBridge.remove(level, id, true);
                    h.fail("a locked core keeps the helm from a stranger: " + s);
                    return;
                }
                CoreService.Outcome o = HelmService.take(level, hp, owner);
                if (!o.ok() || !helm.hasDriver() || owner.getVehicle() == null || !owner.getUUID().equals(helm.driver())) {
                    ValkyrienSkiesBridge.remove(level, id, true);
                    h.fail("the owner must take the helm and be seated: " + o + ", driver " + helm.driver() + ", vehicle " + owner.getVehicle());
                    return;
                }
                CoreService.Outcome t = HelmService.take(level, hp, owner);
                if (t.ok() || !t.reason().equals("taken")) {
                    ValkyrienSkiesBridge.remove(level, id, true);
                    h.fail("a taken helm is refused: " + t);
                    return;
                }
                Optional<ValkyrienSkiesBridge.Construct> c = ValkyrienSkiesBridge.construct(level.getServer(), id);
                boolean parked = c.map(ValkyrienSkiesBridge.Construct::parked).orElse(true);
                owner.stopRiding();
                HelmService.leave(level, helm);
                ValkyrienSkiesBridge.remove(level, id, true);
                if (parked) {
                    h.fail("taking the helm must unpark the construct");
                    return;
                }
                h.succeed();
            }, () -> h.fail("the core and helm never arrived in the shipyard, or the engine never loaded the construct: loaded "
                    + ValkyrienSkiesBridge.isLoaded(level.getServer(), id)));
        });
    }

    /**
     * Optional: the headless test server never gives a construct a physics body (the engine's physics tick runs, but
     * its level has no ship for the id), so this cannot pass here; it reports what it sees and is checked in game.
     */
    @GameTest(template = SetupGameTests.EMPTY, timeoutTicks = 3000, required = false)
    public void a_driven_construct_moves_forward_and_parks_itself_when_left(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        build(h);
        BlockPos corePos = h.absolutePos(CORE);
        ServerPlayer driver = player(h);
        level.addNewPlayer(driver); // the engine loads a construct's physics around the players it can see
        Waits.afterTicking(h, 1, () -> {
            CoreService.Outcome a = CoreService.assemble(level, corePos);
            if (!a.ok()) {
                level.removePlayerImmediately(driver, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                h.fail("assembly refused: " + a.reason());
                return;
            }
            long id = a.shipId();
            Waits.pollThen(h, TIMEOUT, () -> CoreService.coreOf(level, id).map(be -> be.shipId() == id).orElse(false) && !HelmService.helmsOf(level, id).isEmpty()
                    && ValkyrienSkiesBridge.isLoaded(level.getServer(), id), () -> {
                HelmBlockEntity helm = HelmService.helmsOf(level, id).get(0);
                CoreService.Outcome o = HelmService.take(level, helm.getBlockPos(), driver);
                if (!o.ok()) {
                    ValkyrienSkiesBridge.remove(level, id, true);
                    level.removePlayerImmediately(driver, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                    h.fail("the driver could not take the helm: " + o);
                    return;
                }
                Vec3 start = ValkyrienSkiesBridge.construct(level.getServer(), id).map(ValkyrienSkiesBridge.Construct::positionInWorld).orElse(Vec3.ZERO);
                ValkyrienSkiesBridge.setInputs(level.getServer(), id, Direction.NORTH, new Inputs(1, 0, 0, true));
                Waits.pollThen(h, 800, () -> moved(level, id, start) >= 1.0, () -> {
                    // the driver lets go and steps off: the construct must coast to a halt and park itself
                    ValkyrienSkiesBridge.setInputs(level.getServer(), id, Direction.NORTH, Inputs.NONE);
                    driver.stopRiding();
                    Waits.pollThen(h, 1200, () -> ValkyrienSkiesBridge.construct(level.getServer(), id).map(ValkyrienSkiesBridge.Construct::parked).orElse(false), () -> {
                        boolean driverGone = !helm.hasDriver();
                        ValkyrienSkiesBridge.remove(level, id, true);
                        level.removePlayerImmediately(driver, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                        if (!driverGone) {
                            h.fail("the helm must forget a driver who stepped off");
                            return;
                        }
                        h.succeed();
                    }, () -> {
                        double speed = ValkyrienSkiesBridge.construct(level.getServer(), id).map(ValkyrienSkiesBridge.Construct::speed).orElse(-1.0);
                        ValkyrienSkiesBridge.remove(level, id, true);
                        level.removePlayerImmediately(driver, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                        h.fail("the construct never parked itself; speed " + speed);
                    });
                }, () -> {
                    double d = moved(level, id, start);
                    ValkyrienSkiesBridge.DriveStats stats = ValkyrienSkiesBridge.driveStats(id);
                    ValkyrienSkiesBridge.remove(level, id, true);
                    level.removePlayerImmediately(driver, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                    h.fail("the construct did not move north under full thrust: " + d + " blocks; hook " + stats + "; physics ticks seen "
                            + ValkyrienSkiesBridge.physTicksSeen() + ", lookup errors " + ValkyrienSkiesBridge.lookupErrors());
                });
            }, () -> h.fail("the core and helm never arrived in the shipyard, or the engine never loaded the construct: loaded "
                    + ValkyrienSkiesBridge.isLoaded(level.getServer(), id)));
        });
    }

    /** How far the construct has gone north (the helm's facing) from where it started. */
    private static double moved(ServerLevel level, long id, Vec3 start) {
        return ValkyrienSkiesBridge.construct(level.getServer(), id).map(c -> start.z - c.positionInWorld().z).orElse(0.0);
    }
}
