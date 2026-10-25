package com.mrgregles.bsp_mobile.gametest;

import com.mrgregles.bsp_mobile.BSPMobile;
import com.mrgregles.bsp_mobile.compat.ValkyrienSkiesBridge;
import com.mrgregles.bsp_mobile.construct.CoreService;
import com.mrgregles.bsp_mobile.construct.Damage;
import com.mrgregles.bsp_mobile.construct.Gather;
import com.mrgregles.bsp_mobile.construct.MobileCoreBlockEntity;
import com.mrgregles.bsp_mobile.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** MB-5: the impact rule, knocking blocks off at a contact (never the core), and a construct breaking up when its core is destroyed. */
@GameTestHolder(BSPMobile.MODID)
@PrefixGameTestTemplate(false)
public class DamageGameTests {
    private static final BlockPos CORE = new BlockPos(10, 3, 10);
    private static final int TIMEOUT = 600;

    @GameTest(template = SetupGameTests.EMPTY)
    public void the_impact_rule_counts_blocks_over_the_threshold(GameTestHelper h) {
        if (Damage.blocksToKnock(5.9, 6, 2, 8) != 0 || Damage.blocksToKnock(6.0, 6, 2, 8) != 0) {
            h.fail("at or below the threshold nothing is knocked off");
            return;
        }
        if (Damage.blocksToKnock(6.1, 6, 2, 8) != 1 || Damage.blocksToKnock(8.0, 6, 2, 8) != 1 || Damage.blocksToKnock(8.1, 6, 2, 8) != 2) {
            h.fail("one block for every two blocks a second over the threshold, rounded up");
            return;
        }
        if (Damage.blocksToKnock(100, 6, 2, 8) != 8 || Damage.blocksToKnock(100, 6, 2, 0) != 0) {
            h.fail("the cap holds, and a cap of 0 switches impacts off");
            return;
        }
        h.succeed();
    }

    private static MobileCoreBlockEntity build(GameTestHelper h) {
        h.setBlock(CORE, ModBlocks.MOBILE_CORE.get());
        for (int x = 9; x <= 11; x++) {
            for (int z = 9; z <= 11; z++) {
                h.setBlock(new BlockPos(x, 2, z), Blocks.STONE_BRICKS);
            }
        }
        return (MobileCoreBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(CORE));
    }

    @GameTest(template = SetupGameTests.EMPTY, timeoutTicks = 1600)
    public void an_impact_knocks_the_nearest_blocks_off_but_never_the_core(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        build(h);
        BlockPos corePos = h.absolutePos(CORE);
        Waits.afterTicking(h, 1, () -> {
            CoreService.Outcome a = CoreService.assemble(level, corePos);
            if (!a.ok()) {
                h.fail("assembly refused: " + a.reason());
                return;
            }
            long id = a.shipId();
            Waits.pollThen(h, TIMEOUT, () -> CoreService.coreOf(level, id).map(be -> be.shipId() == id).orElse(false), () -> {
                MobileCoreBlockEntity be = CoreService.coreOf(level, id).orElseThrow();
                int before = Gather.gather(level, be.getBlockPos(), 200).count();
                // the contact: the world position of the corner block farthest from the core (10,3,10 -> 9,2,9)
                Vec3 corner = ValkyrienSkiesBridge.toWorld(level, Vec3.atCenterOf(be.getBlockPos().offset(-1, -1, -1)));
                List<BlockPos> broken = Damage.knock(level, id, corner, 2);
                int after = Gather.gather(level, be.getBlockPos(), 200).count();
                boolean coreStands = level.getBlockEntity(be.getBlockPos()) instanceof MobileCoreBlockEntity;
                boolean cornerGone = level.getBlockState(be.getBlockPos().offset(-1, -1, -1)).isAir();
                List<BlockPos> many = Damage.knock(level, id, ValkyrienSkiesBridge.toWorld(level, Vec3.atCenterOf(be.getBlockPos())), 50);
                boolean coreStillStands = level.getBlockEntity(be.getBlockPos()) instanceof MobileCoreBlockEntity;
                ValkyrienSkiesBridge.remove(level, id, true);
                if (broken.size() != 2 || after != before - 2 || !cornerGone) {
                    h.fail("two blocks nearest the corner must go: broke " + broken.size() + ", count " + before + " -> " + after + ", corner gone " + cornerGone);
                    return;
                }
                if (!coreStands || !coreStillStands) {
                    h.fail("the core is never knocked off");
                    return;
                }
                if (many.size() != before - 3) {
                    h.fail("a huge impact takes every block but the core: took " + many.size() + " of " + (before - 3) + " left");
                    return;
                }
                h.succeed();
            }, () -> h.fail("the core never arrived in the shipyard"));
        });
    }

    @GameTest(template = SetupGameTests.EMPTY, timeoutTicks = 2000)
    public void a_construct_breaks_up_when_its_core_is_destroyed(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        build(h);
        BlockPos corePos = h.absolutePos(CORE);
        BlockPos brick = h.absolutePos(new BlockPos(9, 2, 9));
        Waits.afterTicking(h, 1, () -> {
            CoreService.Outcome a = CoreService.assemble(level, corePos);
            if (!a.ok()) {
                h.fail("assembly refused: " + a.reason());
                return;
            }
            long id = a.shipId();
            Waits.pollThen(h, TIMEOUT, () -> level.getBlockState(brick).isAir() && CoreService.coreOf(level, id).map(be -> be.shipId() == id).orElse(false), () -> {
                MobileCoreBlockEntity be = CoreService.coreOf(level, id).orElseThrow();
                level.destroyBlock(be.getBlockPos(), false); // an explosion or a creative player takes the core
                Waits.pollThen(h, TIMEOUT, () -> ValkyrienSkiesBridge.construct(level.getServer(), id).isEmpty(), () -> {
                    if (!level.getBlockState(brick).is(Blocks.STONE_BRICKS)) {
                        h.fail("the blocks must be back in the world where the construct stood: " + level.getBlockState(brick));
                        return;
                    }
                    if (level.getBlockEntity(corePos) instanceof MobileCoreBlockEntity) {
                        h.fail("the destroyed core must not come back");
                        return;
                    }
                    h.succeed();
                }, () -> {
                    ValkyrienSkiesBridge.remove(level, id, true);
                    h.fail("the construct did not break up after its core was destroyed");
                });
            }, () -> h.fail("the blocks never moved into the construct"));
        });
    }
}
