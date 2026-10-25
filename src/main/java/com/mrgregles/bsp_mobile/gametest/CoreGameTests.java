package com.mrgregles.bsp_mobile.gametest;

import com.mrgregles.bsp_mobile.BSPMobile;
import com.mrgregles.bsp_mobile.compat.ValkyrienSkiesBridge;
import com.mrgregles.bsp_mobile.construct.CoreService;
import com.mrgregles.bsp_mobile.construct.Gather;
import com.mrgregles.bsp_mobile.construct.MobileCoreBlockEntity;
import com.mrgregles.bsp_mobile.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Optional;

/** The Mobile Core's rules (MB-1): the gather, its refusals and allowance, assembling, releasing, and the item that keeps its name and tier. */
@GameTestHolder(BSPMobile.MODID)
@PrefixGameTestTemplate(false)
public class CoreGameTests {
    private static final int TIMEOUT = 600;

    private static MobileCoreBlockEntity core(GameTestHelper h, BlockPos rel) {
        h.setBlock(rel, ModBlocks.MOBILE_CORE.get());
        return (MobileCoreBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(rel));
    }

    @GameTest(template = SetupGameTests.EMPTY)
    public void the_gather_counts_every_block_touching_the_core_and_stops_at_air(GameTestHelper h) {
        core(h, new BlockPos(10, 2, 10));
        for (int x = 9; x <= 11; x++) {
            for (int z = 9; z <= 11; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.OAK_PLANKS);
            }
        }
        h.setBlock(new BlockPos(14, 1, 14), Blocks.OAK_PLANKS); // touches nothing of the structure
        Gather.Result r = Gather.gather(h.getLevel(), h.absolutePos(new BlockPos(10, 2, 10)), 200);
        if (r.count() != 10 || r.tooMany() || !r.refused().isEmpty()) {
            h.fail("expected 10 blocks, no refusals: got " + r.count() + ", tooMany " + r.tooMany() + ", refused " + r.refused().size());
            return;
        }
        if (r.kinds().getOrDefault(ResourceLocation.fromNamespaceAndPath("minecraft", "oak_planks"), 0) != 9
                || r.kinds().getOrDefault(ResourceLocation.fromNamespaceAndPath(BSPMobile.MODID, "mobile_core"), 0) != 1) {
            h.fail("the kinds are wrong: " + r.kinds());
            return;
        }
        if (!r.blocks().contains(h.absolutePos(new BlockPos(10, 2, 10))) || r.blocks().contains(h.absolutePos(new BlockPos(14, 1, 14)))) {
            h.fail("the core must be counted and the loose block must not");
            return;
        }
        h.succeed();
    }

    @GameTest(template = SetupGameTests.EMPTY)
    public void the_gather_refuses_bedrock_other_cores_and_admin_blocks_with_their_reasons(GameTestHelper h) {
        core(h, new BlockPos(10, 2, 10));
        h.setBlock(new BlockPos(11, 2, 10), Blocks.OAK_PLANKS);
        h.setBlock(new BlockPos(12, 2, 10), Blocks.BEDROCK);
        h.setBlock(new BlockPos(10, 2, 11), ModBlocks.MOBILE_CORE.get());
        Block antiTotem = ForgeRegistries.BLOCKS.getValue(ResourceLocation.fromNamespaceAndPath("bsp_core", "anti_totem"));
        boolean withAdmin = antiTotem != null && antiTotem != Blocks.AIR;
        if (withAdmin) {
            h.setBlock(new BlockPos(10, 2, 9), antiTotem);
        }
        Gather.Result r = Gather.gather(h.getLevel(), h.absolutePos(new BlockPos(10, 2, 10)), 200);
        if (r.count() != 2 || r.tooMany()) {
            h.fail("expected the core and the planks only: got " + r.count());
            return;
        }
        if (!has(r, new BlockPos(12, 2, 10), h, Gather.Reason.CANNOT_MOVE) || !has(r, new BlockPos(10, 2, 11), h, Gather.Reason.ANOTHER_CORE)) {
            h.fail("bedrock or the second core is missing from the refusals: " + r.refused());
            return;
        }
        if (withAdmin && !has(r, new BlockPos(10, 2, 9), h, Gather.Reason.ADMIN_BLOCK)) {
            h.fail("the Anti Totem block is not refused as an admin block: " + r.refused());
            return;
        }
        if (r.refused().size() != (withAdmin ? 3 : 2)) {
            h.fail("unexpected refusals: " + r.refused());
            return;
        }
        h.succeed();
    }

    private static boolean has(Gather.Result r, BlockPos rel, GameTestHelper h, Gather.Reason reason) {
        BlockPos abs = h.absolutePos(rel);
        return r.refused().stream().anyMatch(x -> x.pos().equals(abs) && x.reason() == reason);
    }

    @GameTest(template = SetupGameTests.EMPTY)
    public void the_gather_refuses_more_blocks_than_the_allowance(GameTestHelper h) {
        core(h, new BlockPos(2, 2, 10));
        for (int x = 3; x <= 10; x++) {
            h.setBlock(new BlockPos(x, 2, 10), Blocks.OAK_PLANKS);
        }
        Gather.Result r = Gather.gather(h.getLevel(), h.absolutePos(new BlockPos(2, 2, 10)), 5);
        if (!r.tooMany() || r.count() != 6 || r.ok()) {
            h.fail("nine blocks against an allowance of five must be too many: tooMany " + r.tooMany() + ", count " + r.count());
            return;
        }
        Gather.Result fits = Gather.gather(h.getLevel(), h.absolutePos(new BlockPos(2, 2, 10)), 9);
        if (fits.tooMany() || fits.count() != 9) {
            h.fail("nine blocks against an allowance of nine must fit: got " + fits.count());
            return;
        }
        h.succeed();
    }

    @GameTest(template = SetupGameTests.EMPTY, timeoutTicks = 1400)
    public void assembling_makes_a_parked_construct_whose_core_knows_its_ship(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos corePos = h.absolutePos(new BlockPos(10, 3, 10));
        core(h, new BlockPos(10, 3, 10));
        for (int x = 9; x <= 10; x++) {
            for (int z = 10; z <= 11; z++) {
                h.setBlock(new BlockPos(x, 2, z), Blocks.STONE_BRICKS);
            }
        }
        Waits.afterTicking(h, 1, () -> {
            CoreService.Outcome out = CoreService.assemble(level, corePos);
            if (!out.ok()) {
                h.fail("assembly refused: " + out.reason());
                return;
            }
            long id = out.shipId();
            Waits.pollThen(h, TIMEOUT,
                    () -> level.getBlockState(corePos).isAir() && CoreService.coreOf(level, id).map(be -> be.shipId() == id).orElse(false),
                    () -> {
                        Optional<ValkyrienSkiesBridge.Construct> c = ValkyrienSkiesBridge.construct(level.getServer(), id);
                        if (c.isEmpty() || !c.get().parked()) {
                            h.fail("the new construct is not parked");
                            return;
                        }
                        MobileCoreBlockEntity be = CoreService.coreOf(level, id).orElseThrow();
                        if (!ValkyrienSkiesBridge.isInShipyard(level, be.getBlockPos())) {
                            h.fail("the core did not move into the shipyard");
                            return;
                        }
                        Gather.Result r = Gather.gather(level, be.getBlockPos(), 200);
                        if (r.count() != 5) {
                            h.fail("the gather in the shipyard should find the five blocks: " + r.count());
                            return;
                        }
                        ValkyrienSkiesBridge.remove(level, id, true);
                        h.succeed();
                    },
                    () -> h.fail("the core was never found in the shipyard with its ship id"));
        });
    }

    @GameTest(template = SetupGameTests.EMPTY, timeoutTicks = 2000)
    public void releasing_puts_every_block_back_where_the_construct_stands(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos corePos = h.absolutePos(new BlockPos(10, 3, 10));
        BlockPos chestPos = h.absolutePos(new BlockPos(11, 3, 10));
        core(h, new BlockPos(10, 3, 10));
        h.setBlock(new BlockPos(11, 3, 10), Blocks.CHEST);
        h.setBlock(new BlockPos(10, 2, 10), Blocks.STONE_BRICKS);
        h.setBlock(new BlockPos(11, 2, 10), Blocks.STONE_BRICKS);
        h.setBlock(new BlockPos(10, 2, 11), Blocks.OAK_STAIRS);
        BlockState stairs = level.getBlockState(h.absolutePos(new BlockPos(10, 2, 11)));
        if (level.getBlockEntity(chestPos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
            chest.setItem(3, new ItemStack(net.minecraft.world.item.Items.DIAMOND, 7));
        }
        CoreService.rename(level, corePos, "Harbour Lantern");
        List<BlockPos> all = List.of(corePos, chestPos, h.absolutePos(new BlockPos(10, 2, 10)), h.absolutePos(new BlockPos(11, 2, 10)), h.absolutePos(new BlockPos(10, 2, 11)));
        Waits.afterTicking(h, 1, () -> {
            CoreService.Outcome out = CoreService.assemble(level, corePos);
            if (!out.ok()) {
                h.fail("assembly refused: " + out.reason());
                return;
            }
            long id = out.shipId();
            Waits.pollThen(h, TIMEOUT,
                    () -> all.stream().allMatch(p -> level.getBlockState(p).isAir()) && CoreService.coreOf(level, id).map(be -> be.shipId() == id).orElse(false),
                    () -> {
                        MobileCoreBlockEntity be = CoreService.coreOf(level, id).orElseThrow();
                        CoreService.Outcome rel = CoreService.release(level, be.getBlockPos());
                        if (!rel.ok()) {
                            h.fail("release refused: " + rel.reason());
                            return;
                        }
                        Waits.pollThen(h, TIMEOUT,
                                () -> ValkyrienSkiesBridge.construct(level.getServer(), id).isEmpty(),
                                () -> checkBack(h, level, corePos, chestPos, stairs),
                                () -> h.fail("the construct stayed after the release"));
                    },
                    () -> h.fail("the blocks never moved into the construct"));
        });
    }

    private static void checkBack(GameTestHelper h, ServerLevel level, BlockPos corePos, BlockPos chestPos, BlockState stairs) {
        if (!(level.getBlockEntity(corePos) instanceof MobileCoreBlockEntity be)) {
            h.fail("the core is not back at " + corePos + ": " + level.getBlockState(corePos));
            return;
        }
        if (be.isAssembled() || !be.name().equals("Harbour Lantern")) {
            h.fail("the core back in the world must be unassembled and keep its name: " + be.shipId() + " " + be.name());
            return;
        }
        if (!level.getBlockState(h.absolutePos(new BlockPos(10, 2, 10))).is(Blocks.STONE_BRICKS) || !level.getBlockState(h.absolutePos(new BlockPos(11, 2, 10))).is(Blocks.STONE_BRICKS)) {
            h.fail("the stone bricks are not back");
            return;
        }
        if (!level.getBlockState(h.absolutePos(new BlockPos(10, 2, 11))).equals(stairs)) {
            h.fail("the stairs came back changed: " + level.getBlockState(h.absolutePos(new BlockPos(10, 2, 11))) + " vs " + stairs);
            return;
        }
        if (!(level.getBlockEntity(chestPos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest)
                || chest.getItem(3).getCount() != 7 || !chest.getItem(3).is(net.minecraft.world.item.Items.DIAMOND)) {
            h.fail("the chest or its diamonds did not come back");
            return;
        }
        h.succeed();
    }

    @GameTest(template = SetupGameTests.EMPTY)
    public void the_dropped_core_keeps_its_name_and_tier(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(10, 2, 10));
        MobileCoreBlockEntity be = core(h, new BlockPos(10, 2, 10));
        be.setName("Harbour Lantern");
        be.setTier(3);
        List<ItemStack> drops = Block.getDrops(level.getBlockState(pos), level, pos, be);
        if (drops.size() != 1 || !drops.get(0).is(ModBlocks.MOBILE_CORE_ITEM.get())) {
            h.fail("expected one Mobile Core item: " + drops);
            return;
        }
        CompoundTag tag = drops.get(0).getTagElement("BlockEntityTag");
        if (tag == null || !tag.getString("Name").equals("Harbour Lantern") || tag.getInt("Tier") != 3) {
            h.fail("the item does not carry the name and tier: " + drops.get(0).getTag());
            return;
        }
        h.succeed();
    }
}
