package com.mrgregles.bsp_mobile.gametest;

import com.mrgregles.bsp_mobile.BSPMobile;
import com.mrgregles.bsp_mobile.MobileConfig;
import com.mrgregles.bsp_mobile.compat.ValkyrienSkiesBridge.Inputs;
import com.mrgregles.bsp_mobile.construct.ConstructRules;
import com.mrgregles.bsp_mobile.construct.CoreService;
import com.mrgregles.bsp_mobile.construct.Drive;
import com.mrgregles.bsp_mobile.construct.Gather;
import com.mrgregles.bsp_mobile.construct.HelmService;
import com.mrgregles.bsp_mobile.construct.Parts;
import com.mrgregles.bsp_mobile.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** MB-7: the flight maths, the parts counted aboard, the helm's command from the parts, and the parts' recipes. */
@GameTestHolder(BSPMobile.MODID)
@PrefixGameTestTemplate(false)
public class PartsGameTests {
    private static final BlockPos CORE = new BlockPos(10, 2, 10);

    private static Drive.Command lift(double up, boolean sprint, double capacity, boolean hover, double y, double ceiling) {
        return new Drive.Command(0, 0, sprint, up, 0, -1, 4, 8, 30, 0.35, 4, capacity, hover, 3, 10, y, ceiling);
    }

    @GameTest(template = SetupGameTests.EMPTY)
    public void the_flight_maths_cancels_gravity_climbs_descends_and_holds_the_ceiling(GameTestHelper h) {
        double m = 100;
        if (Drive.lift(m, 0, lift(0, false, 0, false, 10, 1000)) != 0) {
            h.fail("no lift aboard, no upward force");
            return;
        }
        if (Math.abs(Drive.lift(m, 0, lift(0, false, 50, false, 10, 1000)) - 500) > 1e-9) {
            h.fail("half the lift needed carries half the weight");
            return;
        }
        if (Math.abs(Drive.lift(m, 0, lift(0, false, 200, true, 10, 1000)) - 1000) > 1e-9) {
            h.fail("balloons with no input cancel the weight exactly when still");
            return;
        }
        if (Math.abs(Drive.lift(m, 2, lift(0, false, 200, true, 10, 1000)) - (1000 - 400)) > 1e-9) {
            h.fail("hovering damps a rise");
            return;
        }
        if (Math.abs(Drive.lift(m, 0, lift(1, false, 200, true, 10, 1000)) - 1300) > 1e-9) {
            h.fail("space climbs at the climb rate");
            return;
        }
        if (Math.abs(Drive.lift(m, 0, lift(1, true, 200, true, 10, 1000)) - 700) > 1e-9) {
            h.fail("sprint and space descend at the climb rate");
            return;
        }
        if (Math.abs(Drive.lift(m, 0, lift(1, false, 200, true, 1000, 1000)) - 1000) > 1e-9) {
            h.fail("at the ceiling climbing only holds the weight");
            return;
        }
        if (Math.abs(Drive.lift(m, 0, lift(0, false, 200, false, 10, 1000)) - 900) > 1e-9) {
            h.fail("jets alone with no input sink slowly");
            return;
        }
        Drive.Forces f = Drive.forces(m, 50, 0, 0, 0, 0, lift(1, false, 200, true, 10, 1000));
        if (Math.abs(f.fy() - 1300) > 1e-9 || f.fx() != 0 || f.fz() != 0) {
            h.fail("the forces carry the lift upward only: " + f);
            return;
        }
        h.succeed();
    }

    @GameTest(template = SetupGameTests.EMPTY)
    public void the_parts_aboard_are_counted_and_add_up(GameTestHelper h) {
        h.setBlock(CORE, ModBlocks.MOBILE_CORE.get());
        h.setBlock(CORE.east(), ModBlocks.MOBILE_ENGINE.get());
        h.setBlock(CORE.east(2), ModBlocks.MOBILE_ENGINE.get());
        h.setBlock(CORE.west(), ModBlocks.MOBILE_FLOATER.get());
        h.setBlock(CORE.north(), ModBlocks.MOBILE_BALLOON.get());
        h.setBlock(CORE.north(2), ModBlocks.MOBILE_BALLOON.get());
        h.setBlock(CORE.south(), ModBlocks.MOBILE_WING.get());
        h.setBlock(CORE.above(), ModBlocks.MOBILE_JET_ENGINE.get());
        Gather.Result g = Gather.gather(h.getLevel(), h.absolutePos(CORE), 200);
        CoreService.Aboard aboard = CoreService.aboard(h.getLevel(), g);
        Parts p = aboard.parts();
        if (p.engines() != 2 || p.floaters() != 1 || p.balloons() != 2 || p.wings() != 1 || p.jets() != 1 || p.wingPositions().size() != 1
                || !p.wingPositions().get(0).equals(h.absolutePos(CORE.south()))) {
            h.fail("the parts must be counted from the gather: " + p);
            return;
        }
        double accel = MobileConfig.driveAccel() + 2 * MobileConfig.partsEngineAccel() + MobileConfig.partsJetAccel();
        double speed = MobileConfig.driveMaxSpeed() + 2 * MobileConfig.partsEngineSpeed() + MobileConfig.partsJetSpeed();
        double buoy = MobileConfig.driveBuoyancy() + MobileConfig.partsFloaterBuoyancy();
        double capacity = 2 * MobileConfig.partsBalloonLift() + MobileConfig.partsJetLift();
        if (p.accel() != accel || p.maxSpeed() != speed || p.buoyancy() != buoy || p.liftCapacity() != capacity || !p.hovers()) {
            h.fail("the parts must add up: " + p.accel() + " " + p.maxSpeed() + " " + p.buoyancy() + " " + p.liftCapacity());
            return;
        }
        Drive.Command cmd = HelmService.command(new Inputs(1, 0, 1, false), Direction.NORTH, p, ConstructRules.of(1), false, 70, 100);
        if (cmd.accel() != accel || cmd.maxSpeed() != Math.min(speed, MobileConfig.totemMaxSpeed()) || cmd.buoyancy() != buoy || cmd.liftCapacity() != capacity
                || !cmd.hover() || cmd.up() != 1 || cmd.y() != 70 || cmd.ceilingY() != 100 || cmd.climb() != MobileConfig.partsClimb()) {
            h.fail("the helm's command must carry the parts' figures and the totem's cap: " + cmd);
            return;
        }
        Drive.Command closed = HelmService.command(new Inputs(1, 0, 0, false), Direction.NORTH, Parts.NONE, ConstructRules.FREE, true, 70, Integer.MAX_VALUE);
        if (closed.forward() != 0 || closed.liftCapacity() != 0 || closed.hover() || closed.maxSpeed() != MobileConfig.driveMaxSpeed()) {
            h.fail("without parts the command is the helm alone, and a closed region clamps forward: " + closed);
            return;
        }
        h.succeed();
    }

    @GameTest(template = SetupGameTests.EMPTY)
    public void the_parts_have_their_recipes(GameTestHelper h) {
        Map<String, Integer> expected = Map.of("mobile_engine", 1, "mobile_floater", 1, "mobile_balloon", 1, "mobile_wing", 2, "mobile_jet_engine", 1, "mobile_helm", 1);
        for (var e : expected.entrySet()) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(BSPMobile.MODID, e.getKey());
            Optional<? extends Recipe<?>> recipe = h.getLevel().getRecipeManager().byKey(id);
            if (recipe.isEmpty()) {
                h.fail("no recipe " + id);
                return;
            }
            var result = recipe.get().getResultItem(h.getLevel().registryAccess());
            if (!id.equals(ForgeRegistries.ITEMS.getKey(result.getItem())) || result.getCount() != e.getValue()) {
                h.fail(id + " must make " + e.getValue() + ", makes " + result);
                return;
            }
        }
        List<String> parts = List.of("mobile_engine", "mobile_floater", "mobile_balloon", "mobile_wing", "mobile_jet_engine");
        for (String name : parts) {
            if (ForgeRegistries.BLOCKS.getValue(ResourceLocation.fromNamespaceAndPath(BSPMobile.MODID, name)) == null) {
                h.fail("the block " + name + " is not registered");
                return;
            }
        }
        h.succeed();
    }
}
