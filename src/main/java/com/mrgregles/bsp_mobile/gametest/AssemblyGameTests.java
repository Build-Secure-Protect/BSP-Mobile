package com.mrgregles.bsp_mobile.gametest;

import com.mrgregles.bsp_mobile.BSPMobile;
import com.mrgregles.bsp_mobile.compat.ValkyrienSkiesBridge;
import com.mrgregles.bsp_mobile.compat.ValkyrienSkiesBridge.Construct;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * The feasibility check of MB-0: on the headless test server, Valkyrien Skies (with its physics natives) takes a block
 * set out of the world, keeps it as a construct drawn where the blocks stood, and gives it back. Nothing of this mod's
 * own gameplay yet; the test proves the engine under it works where the GameTests run.
 */
@GameTestHolder(BSPMobile.MODID)
@PrefixGameTestTemplate(false)
public class AssemblyGameTests {
    private static final int TIMEOUT = 600;

    @GameTest(template = SetupGameTests.EMPTY, timeoutTicks = 1400)
    public void eight_blocks_become_a_construct_where_they_stood_and_can_be_taken_back_out(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Set<BlockPos> blocks = new HashSet<>();
        for (int x = 10; x <= 11; x++) {
            for (int y = 2; y <= 3; y++) {
                for (int z = 10; z <= 11; z++) {
                    h.setBlock(new BlockPos(x, y, z), Blocks.OAK_PLANKS);
                    blocks.add(h.absolutePos(new BlockPos(x, y, z)));
                }
            }
        }
        Vec3 centre = new Vec3(h.absolutePos(new BlockPos(11, 3, 11)).getX(), h.absolutePos(new BlockPos(11, 3, 11)).getY(),
                h.absolutePos(new BlockPos(11, 3, 11)).getZ());

        Waits.afterTicking(h, 1, () -> {
            Optional<Long> id = ValkyrienSkiesBridge.assemble(level, blocks);
            if (id.isEmpty()) {
                h.fail("Valkyrien Skies did not assemble the blocks");
                return;
            }
            long shipId = id.get();
            // The blocks leave the world and the construct gets its shipyard box a few ticks later, on VS's schedule.
            Waits.pollThen(h, TIMEOUT,
                    () -> blocks.stream().allMatch(p -> level.getBlockState(p).isAir())
                            && ValkyrienSkiesBridge.construct(level.getServer(), shipId).map(c -> c.shipyardMin() != null).orElse(false),
                    () -> checkAssembled(h, level, shipId, centre),
                    () -> h.fail("the blocks never moved into a construct (id " + shipId + ")"));
        });
    }

    private static void checkAssembled(GameTestHelper h, ServerLevel level, long shipId, Vec3 centre) {
        Construct c = ValkyrienSkiesBridge.construct(level.getServer(), shipId).orElse(null);
        if (c == null) {
            h.fail("the construct vanished after assembly");
            return;
        }
        // Valkyrien Skies reports the construct's box in shipyard coordinates; whatever its far corner means, the box
        // must hold exactly the eight planks, packed from the near corner, and nothing else.
        BlockPos min = c.shipyardMin();
        int planks = 0;
        int others = 0;
        for (int x = min.getX(); x <= c.shipyardMax().getX(); x++) {
            for (int y = min.getY(); y <= c.shipyardMax().getY(); y++) {
                for (int z = min.getZ(); z <= c.shipyardMax().getZ(); z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    boolean expected = x - min.getX() < 2 && y - min.getY() < 2 && z - min.getZ() < 2;
                    if (level.getBlockState(p).is(Blocks.OAK_PLANKS)) {
                        planks++;
                        if (!expected) {
                            others++;
                        }
                    } else if (expected) {
                        others++;
                    }
                }
            }
        }
        if (planks != 8 || others != 0) {
            h.fail("the shipyard holds " + planks + " planks (" + others + " out of place) in the box " + min + " to " + c.shipyardMax());
            return;
        }
        if (!ValkyrienSkiesBridge.isInShipyard(level, c.shipyardMin())) {
            h.fail("the construct's blocks are not reported as being in the shipyard");
            return;
        }
        if (ValkyrienSkiesBridge.constructAt(level, c.shipyardMin()).map(Construct::id).orElse(-1L) != shipId) {
            h.fail("the shipyard block is not managed by the construct that was made");
            return;
        }
        if (c.positionInWorld().distanceTo(centre) > 4.0) {
            h.fail("the construct sits at " + c.positionInWorld() + ", far from where the blocks stood at " + centre);
            return;
        }
        // The shipyard block, projected into the world, lies where the construct is drawn.
        Vec3 projected = ValkyrienSkiesBridge.toWorld(level, Vec3.atCenterOf(c.shipyardMin()));
        if (projected.distanceTo(centre) > 4.0) {
            h.fail("a shipyard block projects to " + projected + ", far from " + centre);
            return;
        }
        if (c.mass() <= 0.0) {
            h.fail("the construct has no mass");
            return;
        }
        BlockPos shipyardBlock = c.shipyardMin();
        if (!ValkyrienSkiesBridge.remove(level, shipId, true)) {
            h.fail("Valkyrien Skies refused to remove the construct");
            return;
        }
        Waits.pollThen(h, TIMEOUT,
                () -> ValkyrienSkiesBridge.construct(level.getServer(), shipId).isEmpty() && level.getBlockState(shipyardBlock).isAir(),
                h::succeed,
                () -> h.fail("the construct or its shipyard blocks stayed behind after removal"));
    }
}
