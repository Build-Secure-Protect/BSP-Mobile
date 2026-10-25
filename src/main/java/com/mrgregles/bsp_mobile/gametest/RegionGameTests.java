package com.mrgregles.bsp_mobile.gametest;

import com.mrgregles.bsp_mobile.BSPMobile;
import com.mrgregles.bsp_mobile.MobileConfig;
import com.mrgregles.bsp_mobile.compat.RegionGuard;
import com.mrgregles.bsp_mobile.compat.ValkyrienSkiesBridge;
import com.mrgregles.bsp_mobile.construct.CoreService;
import com.mrgregles.bsp_mobile.construct.Where;
import com.mrgregles.bsp_mobile.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** MB-6: a closed server refuses assembly; a region denying mobile-constructs refuses assembly and lets a construct only back out. */
@GameTestHolder(BSPMobile.MODID)
@PrefixGameTestTemplate(false)
public class RegionGameTests {
    private static final BlockPos CORE = new BlockPos(10, 3, 10);

    private static void build(GameTestHelper h) {
        h.setBlock(CORE, ModBlocks.MOBILE_CORE.get());
        h.setBlock(CORE.below(), Blocks.STONE_BRICKS);
        h.setBlock(CORE.east(), Blocks.STONE_BRICKS);
    }

    @GameTest(template = SetupGameTests.EMPTY)
    public void a_closed_server_refuses_assembly(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        build(h);
        BlockPos corePos = h.absolutePos(CORE);
        boolean before = MobileConfig.REGIONS_CLOSED.get();
        MobileConfig.REGIONS_CLOSED.set(true); // set and restored within this tick
        CoreService.Outcome out;
        boolean closed;
        try {
            closed = Where.serverClosed();
            out = CoreService.assemble(level, corePos);
        } finally {
            MobileConfig.REGIONS_CLOSED.set(before);
        }
        if (!closed || out.ok() || !out.reason().equals("closed_server")) {
            if (out.ok()) {
                ValkyrienSkiesBridge.remove(level, out.shipId(), true);
            }
            h.fail("a closed server must refuse assembly with closed_server: closed " + closed + ", " + out);
            return;
        }
        if (Where.serverClosed()) {
            h.fail("the switch must be back off");
            return;
        }
        h.succeed();
    }

    @GameTest(template = SetupGameTests.EMPTY)
    public void the_back_out_rule_refuses_forward_thrust_only(GameTestHelper h) {
        if (Where.clampForward(1f, true) != 0f || Where.clampForward(-1f, true) != -1f || Where.clampForward(1f, false) != 1f || Where.clampForward(0.5f, false) != 0.5f) {
            h.fail("inside a closed region forward thrust is refused and backing out allowed; outside nothing changes");
            return;
        }
        h.succeed();
    }

    @GameTest(template = SetupGameTests.EMPTY, timeoutTicks = 1200)
    public void a_region_denying_constructs_refuses_assembly_until_allowed_again(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        if (!RegionGuard.isPresent()) {
            h.fail("Region Guard is not on the test server; the flag cannot be checked");
            return;
        }
        build(h);
        BlockPos corePos = h.absolutePos(CORE);
        MinecraftServer server = level.getServer();
        // the world default denies constructs: assembly is refused; allow again and it goes through
        run(server, "rg flag __global__ " + RegionGuard.FLAG + " deny");
        try {
            if (RegionGuard.allowsConstructs(level, corePos, null)) {
                h.fail("after denying the flag on the world default the position must refuse constructs");
                return;
            }
            if (!Where.closedRegion(level, corePos)) {
                h.fail("a core in a denying region counts as in a closed region");
                return;
            }
            CoreService.Outcome out = CoreService.assemble(level, corePos);
            if (out.ok() || !out.reason().equals("closed_region")) {
                if (out.ok()) {
                    ValkyrienSkiesBridge.remove(level, out.shipId(), true);
                }
                h.fail("assembly in a denying region must be refused with closed_region: " + out);
                return;
            }
        } finally {
            run(server, "rg flag __global__ " + RegionGuard.FLAG + " allow");
        }
        if (!RegionGuard.allowsConstructs(level, corePos, null) || Where.closedRegion(level, corePos)) {
            h.fail("after allowing the flag again the position must allow constructs");
            return;
        }
        Waits.afterTicking(h, 1, () -> {
            CoreService.Outcome ok = CoreService.assemble(level, corePos);
            if (!ok.ok()) {
                h.fail("assembly must go through once the region allows constructs: " + ok.reason());
                return;
            }
            Waits.pollThen(h, 600, () -> CoreService.coreOf(level, ok.shipId()).isPresent(), () -> {
                ValkyrienSkiesBridge.remove(level, ok.shipId(), true);
                h.succeed();
            }, () -> {
                ValkyrienSkiesBridge.remove(level, ok.shipId(), true);
                h.fail("the construct never formed");
            });
        });
    }

    private static void run(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }
}
