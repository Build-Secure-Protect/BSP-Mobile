package com.mrgregles.bsp_mobile.gametest;

import com.mrgregles.bsp_mobile.BSPMobile;
import com.mrgregles.bsp_mobile.MobileConfig;
import com.mrgregles.bsp_mobile.compat.ValkyrienSkiesBridge;
import com.mrgregles.bsp_mobile.construct.Blueprints;
import com.mrgregles.bsp_mobile.construct.Constructs;
import com.mrgregles.bsp_mobile.construct.CoreService;
import com.mrgregles.bsp_mobile.construct.MobileCoreBlockEntity;
import com.mrgregles.bsp_mobile.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

/** MB-8: the caps, save and paste, and the admin command's list and remove. */
@GameTestHolder(BSPMobile.MODID)
@PrefixGameTestTemplate(false)
public class AdminGameTests {
    private static final int TIMEOUT = 600;

    private static MobileCoreBlockEntity build(GameTestHelper h, BlockPos core) {
        h.setBlock(core, ModBlocks.MOBILE_CORE.get());
        h.setBlock(core.below(), Blocks.STONE_BRICKS);
        h.setBlock(core.east(), Blocks.STONE_BRICKS);
        return (MobileCoreBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(core));
    }

    @GameTest(template = SetupGameTests.EMPTY, timeoutTicks = 1600)
    public void the_per_player_cap_refuses_a_second_construct(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer owner = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        MobileCoreBlockEntity first = build(h, new BlockPos(5, 3, 5));
        MobileCoreBlockEntity second = build(h, new BlockPos(15, 3, 15));
        first.setOwner(owner.getUUID(), owner.getGameProfile().getName());
        second.setOwner(owner.getUUID(), owner.getGameProfile().getName());
        BlockPos firstPos = h.absolutePos(new BlockPos(5, 3, 5)), secondPos = h.absolutePos(new BlockPos(15, 3, 15));
        Waits.afterTicking(h, 1, () -> {
            CoreService.Outcome a = CoreService.assemble(level, firstPos);
            if (!a.ok()) {
                h.fail("the first assembly must go through: " + a.reason());
                return;
            }
            Waits.pollThen(h, TIMEOUT, () -> CoreService.coreOf(level, a.shipId()).isPresent(), () -> {
                int before = MobileConfig.LIMIT_PER_PLAYER.get();
                CoreService.Outcome b;
                try {
                    MobileConfig.LIMIT_PER_PLAYER.set(1); // set and restored within this tick
                    b = CoreService.assemble(level, secondPos);
                } finally {
                    MobileConfig.LIMIT_PER_PLAYER.set(before);
                }
                ValkyrienSkiesBridge.remove(level, a.shipId(), true);
                if (b.ok()) {
                    ValkyrienSkiesBridge.remove(level, b.shipId(), true);
                    h.fail("a second construct for the same owner must be refused by the cap");
                    return;
                }
                if (!b.reason().equals("limit_player")) {
                    h.fail("the refusal must name the player cap: " + b.reason());
                    return;
                }
                h.succeed();
            }, () -> h.fail("the first construct never formed"));
        });
    }

    @GameTest(template = SetupGameTests.EMPTY, timeoutTicks = 2000)
    public void save_and_paste_make_a_working_copy(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer admin = MockPlayers.serverPlayer(h, GameType.CREATIVE);
        MobileCoreBlockEntity core = build(h, new BlockPos(5, 3, 5));
        core.setName("Blueprint Test");
        core.setTier(2);
        BlockPos corePos = h.absolutePos(new BlockPos(5, 3, 5));
        BlockPos pasteAt = h.absolutePos(new BlockPos(14, 2, 14));
        String name = "gametest_copy_" + (System.nanoTime() % 100000);
        Waits.afterTicking(h, 1, () -> {
            CoreService.Outcome a = CoreService.assemble(level, corePos);
            if (!a.ok()) {
                h.fail("assembly refused: " + a.reason());
                return;
            }
            long id = a.shipId();
            Waits.pollThen(h, TIMEOUT, () -> CoreService.coreOf(level, id).map(be -> be.shipId() == id).orElse(false), () -> {
                Optional<Blueprints.Saved> saved = Blueprints.save(level, id, name);
                ValkyrienSkiesBridge.remove(level, id, true);
                if (saved.isEmpty() || saved.get().blocks() != 3 || !Blueprints.names().contains(name)) {
                    h.fail("the save must hold the three blocks and be listed: " + saved + " " + Blueprints.names());
                    return;
                }
                Optional<Blueprints.Pasted> pasted = Blueprints.paste(level, name, pasteAt, admin);
                try {
                    java.nio.file.Files.deleteIfExists(com.mrgregles.bsp_mobile.construct.Blueprints.file(name));
                } catch (java.io.IOException ignored) {
                    // the file is gone or never was
                }
                if (pasted.isEmpty() || pasted.get().shipId() == 0) {
                    h.fail("the paste must set the blocks down and assemble them: " + pasted);
                    return;
                }
                long copy = pasted.get().shipId();
                Waits.pollThen(h, TIMEOUT, () -> CoreService.coreOf(level, copy).map(be -> be.shipId() == copy).orElse(false), () -> {
                    MobileCoreBlockEntity be = CoreService.coreOf(level, copy).orElseThrow();
                    boolean good = be.name().equals("Blueprint Test") && be.tier() == 2 && admin.getUUID().equals(be.owner())
                            && ValkyrienSkiesBridge.construct(level.getServer(), copy).map(ValkyrienSkiesBridge.Construct::parked).orElse(false);
                    ValkyrienSkiesBridge.remove(level, copy, true);
                    if (!good) {
                        h.fail("the copy must keep the name and tier, belong to the admin and be parked: " + be.name() + " " + be.tier() + " " + be.owner());
                        return;
                    }
                    h.succeed();
                }, () -> {
                    ValkyrienSkiesBridge.remove(level, copy, true);
                    h.fail("the pasted construct never formed");
                });
            }, () -> h.fail("the construct never formed"));
        });
    }

    @GameTest(template = SetupGameTests.EMPTY, timeoutTicks = 2000)
    public void the_command_lists_a_construct_and_sets_it_down(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        MinecraftServer server = level.getServer();
        build(h, new BlockPos(10, 3, 10));
        BlockPos corePos = h.absolutePos(new BlockPos(10, 3, 10));
        BlockPos brick = h.absolutePos(new BlockPos(10, 2, 10));
        Waits.afterTicking(h, 1, () -> {
            CoreService.Outcome a = CoreService.assemble(level, corePos);
            if (!a.ok()) {
                h.fail("assembly refused: " + a.reason());
                return;
            }
            long id = a.shipId();
            Waits.pollThen(h, TIMEOUT, () -> level.getBlockState(brick).isAir() && CoreService.coreOf(level, id).map(be -> be.shipId() == id).orElse(false), () -> {
                boolean listed = Constructs.all(server).stream().anyMatch(e -> e.id() == id);
                int rows = server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "mobile list");
                if (!listed || rows < 1) {
                    ValkyrienSkiesBridge.remove(level, id, true);
                    h.fail("the construct must be listed: listed " + listed + ", rows " + rows);
                    return;
                }
                int done = server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "mobile remove " + id);
                Waits.pollThen(h, TIMEOUT, () -> ValkyrienSkiesBridge.construct(server, id).isEmpty(), () -> {
                    if (done != 1 || !level.getBlockState(brick).is(Blocks.STONE_BRICKS) || !(level.getBlockEntity(corePos) instanceof MobileCoreBlockEntity be) || be.isAssembled()) {
                        h.fail("remove must set the blocks back down with the core unassembled: " + done + " " + level.getBlockState(brick));
                        return;
                    }
                    h.succeed();
                }, () -> {
                    ValkyrienSkiesBridge.remove(level, id, true);
                    h.fail("remove did not take the construct away");
                });
            }, () -> h.fail("the construct never formed"));
        });
    }
}
