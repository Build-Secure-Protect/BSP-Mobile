package com.mrgregles.bsp_mobile.gametest;

import com.mrgregles.bsp_mobile.BSPMobile;
import com.mrgregles.bsp_mobile.compat.ValkyrienSkiesBridge;
import com.mrgregles.bsp_mobile.compat.ValkyrienSkiesBridge.Inputs;
import com.mrgregles.bsp_mobile.construct.Autopilot;
import com.mrgregles.bsp_mobile.construct.CoreService;
import com.mrgregles.bsp_mobile.construct.HelmBlock;
import com.mrgregles.bsp_mobile.construct.HelmBlockEntity;
import com.mrgregles.bsp_mobile.construct.HelmService;
import com.mrgregles.bsp_mobile.construct.MobileCoreBlockEntity;
import com.mrgregles.bsp_mobile.construct.PlanService;
import com.mrgregles.bsp_mobile.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** MB-9: the autopilot's steering, the plan kept on the core, and a running plan stopping for a driver. */
@GameTestHolder(BSPMobile.MODID)
@PrefixGameTestTemplate(false)
public class PlanGameTests {
    private static final BlockPos CORE = new BlockPos(10, 3, 10), HELM = new BlockPos(11, 3, 10);
    private static final int TIMEOUT = 600;

    @GameTest(template = SetupGameTests.EMPTY)
    public void the_steering_turns_thrusts_and_holds_the_cruise_height(GameTestHelper h) {
        Vec3 at = new Vec3(0, 64, 0);
        if (Math.abs(Autopilot.bearing(at, new Vec3(0, 64, -10))) > 1e-9 || Math.abs(Autopilot.bearing(at, new Vec3(10, 64, 0)) - 90) > 1e-9
                || Math.abs(Autopilot.bearing(at, new Vec3(0, 64, 10)) - 180) > 1e-9 || Math.abs(Autopilot.bearing(at, new Vec3(-10, 64, 0)) - 270) > 1e-9) {
            h.fail("bearings: north 0, east 90, south 180, west 270");
            return;
        }
        Inputs ahead = Autopilot.steer(at, 0, new Vec3(0, 64, -100), -1, false);
        if (ahead.forward() != 1f || ahead.left() != 0f || ahead.up() != 0f) {
            h.fail("a waypoint dead ahead: full thrust, no turn: " + ahead);
            return;
        }
        Inputs right = Autopilot.steer(at, 0, new Vec3(100, 64, 0), -1, false);
        if (right.forward() != 0f || right.left() != -1f) {
            h.fail("a waypoint to the right: turn right (negative left), no thrust yet: " + right);
            return;
        }
        Inputs leftish = Autopilot.steer(at, 0, new Vec3(-20, 64, -100), -1, false);
        if (leftish.forward() != 1f || leftish.left() <= 0f) {
            h.fail("a waypoint a little to the left within the cone: thrust and a gentle left turn: " + leftish);
            return;
        }
        Inputs near = Autopilot.steer(at, 0, new Vec3(0, 64, -8), -1, false);
        if (near.forward() != 0.5f) {
            h.fail("close to the waypoint: half thrust: " + near);
            return;
        }
        Inputs climb = Autopilot.steer(at, 0, new Vec3(0, 64, -100), 80, true);
        Inputs descend = Autopilot.steer(at, 0, new Vec3(0, 64, -100), 50, true);
        Inputs hold = Autopilot.steer(at, 0, new Vec3(0, 64, -100), 64.5, true);
        Inputs noLift = Autopilot.steer(at, 0, new Vec3(0, 64, -100), 80, false);
        if (climb.up() != 1f || climb.sprint() || descend.up() != 1f || !descend.sprint() || hold.up() != 0f || noLift.up() != 0f) {
            h.fail("below the cruise height: climb; above: sprint and up; within the band: nothing; no lift: nothing: " + climb + " " + descend + " " + hold + " " + noLift);
            return;
        }
        if (!Autopilot.reached(at, new Vec3(3, 70, 2)) || Autopilot.reached(at, new Vec3(5, 64, 0))) {
            h.fail("a waypoint counts within 4 blocks on the plane");
            return;
        }
        h.succeed();
    }

    @GameTest(template = SetupGameTests.EMPTY)
    public void the_plan_is_kept_on_the_core_and_survives_a_reload(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        h.setBlock(CORE, ModBlocks.MOBILE_CORE.get());
        MobileCoreBlockEntity be = (MobileCoreBlockEntity) level.getBlockEntity(h.absolutePos(CORE));
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        p.setPos(h.absoluteVec(new Vec3(3.5, 2, 3.5)));
        CoreService.Outcome out = PlanService.go(level, h.absolutePos(CORE), p);
        if (out.ok() || !out.reason().equals("not_assembled")) {
            h.fail("GO on an unassembled core is refused: " + out);
            return;
        }
        if (!PlanService.addHere(level, h.absolutePos(CORE), p).ok() || be.waypoints().size() != 1 || !be.waypoints().get(0).equals(h.absolutePos(new BlockPos(3, 2, 3)))) {
            h.fail("ADD HERE must add the player's block: " + be.waypoints());
            return;
        }
        PlanService.setCruise(level, h.absolutePos(CORE), p, 30);
        for (int i = 0; i < PlanService.MAX_WAYPOINTS + 2; i++) {
            PlanService.addHere(level, h.absolutePos(CORE), p);
        }
        if (be.waypoints().size() != PlanService.MAX_WAYPOINTS) {
            h.fail("the plan holds at most " + PlanService.MAX_WAYPOINTS + " waypoints: " + be.waypoints().size());
            return;
        }
        PlanService.removeLast(level, h.absolutePos(CORE), p);
        CompoundTag saved = be.saveWithoutMetadata();
        MobileCoreBlockEntity copy = new MobileCoreBlockEntity(be.getBlockPos(), be.getBlockState());
        copy.load(saved);
        if (copy.waypoints().size() != PlanService.MAX_WAYPOINTS - 1 || copy.cruise() != 30 || !copy.waypoints().get(0).equals(be.waypoints().get(0))) {
            h.fail("the plan must survive save and load: " + copy.waypoints().size() + " " + copy.cruise());
            return;
        }
        be.lock(java.util.UUID.randomUUID(), "Someone");
        CoreService.Outcome locked = PlanService.addHere(level, h.absolutePos(CORE), p);
        if (locked.ok() || !locked.reason().equals("locked")) {
            h.fail("a stranger cannot edit a locked core's plan: " + locked);
            return;
        }
        h.succeed();
    }

    @GameTest(template = SetupGameTests.EMPTY, timeoutTicks = 1600)
    public void a_running_plan_drives_the_helm_and_stops_for_a_driver(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        h.setBlock(CORE, ModBlocks.MOBILE_CORE.get());
        h.setBlock(HELM, ModBlocks.MOBILE_HELM.get().defaultBlockState().setValue(HelmBlock.FACING, Direction.NORTH));
        for (int x = 9; x <= 12; x++) {
            for (int z = 9; z <= 11; z++) {
                h.setBlock(new BlockPos(x, 2, z), Blocks.OAK_PLANKS);
            }
        }
        BlockPos corePos = h.absolutePos(CORE);
        ServerPlayer owner = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        owner.setPos(h.absoluteVec(new Vec3(10.5, 3, 0.5)));
        CoreService.Outcome noHelmYet = PlanService.go(level, corePos, owner);
        if (noHelmYet.ok() || !noHelmYet.reason().equals("not_assembled")) {
            h.fail("GO needs an assembled construct: " + noHelmYet);
            return;
        }
        PlanService.addHere(level, corePos, owner); // a waypoint 10 blocks north
        Waits.afterTicking(h, 1, () -> {
            CoreService.Outcome a = CoreService.assemble(level, corePos);
            if (!a.ok()) {
                h.fail("assembly refused: " + a.reason());
                return;
            }
            long id = a.shipId();
            Waits.pollThen(h, TIMEOUT, () -> CoreService.coreOf(level, id).map(be -> be.shipId() == id).orElse(false) && !HelmService.helmsOf(level, id).isEmpty()
                    && ValkyrienSkiesBridge.isLoaded(level.getServer(), id), () -> {
                MobileCoreBlockEntity be = CoreService.coreOf(level, id).orElseThrow();
                HelmBlockEntity helm = HelmService.helmsOf(level, id).get(0);
                CoreService.Outcome go = PlanService.go(level, be.getBlockPos(), owner);
                if (!go.ok() || !be.planRunning()) {
                    ValkyrienSkiesBridge.remove(level, id, true);
                    h.fail("GO must start the plan on an assembled construct with a helm: " + go);
                    return;
                }
                // one step of the plan (the core's ticker does not run in the shipyard on the headless server; in game it does):
                // the helm comes under the autopilot, the seat carries thrust toward the waypoint, the construct is freed
                PlanService.tick(level, be);
                Inputs in = ValkyrienSkiesBridge.inputs(level.getServer(), id);
                boolean parked = ValkyrienSkiesBridge.construct(level.getServer(), id).map(ValkyrienSkiesBridge.Construct::parked).orElse(true);
                if (in.forward() <= 0 || parked || !be.planRunning()) {
                    ValkyrienSkiesBridge.remove(level, id, true);
                    h.fail("a step of the plan must thrust toward the waypoint and free the construct: " + in + ", parked " + parked);
                    return;
                }
                CoreService.Outcome take = HelmService.take(level, helm.getBlockPos(), owner);
                PlanService.tick(level, be);
                boolean stopped = !be.planRunning();
                boolean cleared = ValkyrienSkiesBridge.inputs(level.getServer(), id).forward() == 0;
                owner.stopRiding();
                HelmService.leave(level, helm);
                ValkyrienSkiesBridge.remove(level, id, true);
                if (!take.ok() || !stopped || !cleared) {
                    h.fail("a driver taking the helm stops the plan and clears its inputs: " + take + ", stopped " + stopped + ", cleared " + cleared);
                    return;
                }
                h.succeed();
            }, () -> h.fail("the core and helm never arrived, or the engine never loaded the construct"));
        });
    }
}
