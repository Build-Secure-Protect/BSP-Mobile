package com.mrgregles.bsp_mobile.gametest;

import com.mrgregles.bsp_mobile.BSPMobile;
import com.mrgregles.bsp_mobile.MobileConfig;
import com.mrgregles.bsp_mobile.compat.BspCore;
import com.mrgregles.bsp_mobile.construct.ConstructRules;
import com.mrgregles.bsp_mobile.construct.CoreService;
import com.mrgregles.bsp_mobile.construct.Gather;
import com.mrgregles.bsp_mobile.construct.LockService;
import com.mrgregles.bsp_mobile.construct.MobileCoreBlockEntity;
import com.mrgregles.bsp_mobile.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

/** MB-3: the Mobile Core Lock (binding, who is admitted, unlocking, the steal and its failure) and the totem's caps. */
@GameTestHolder(BSPMobile.MODID)
@PrefixGameTestTemplate(false)
public class LockGameTests {
    private static final BlockPos CORE = new BlockPos(10, 2, 10);
    private static final int TIMEOUT = 600;

    private static MobileCoreBlockEntity core(GameTestHelper h) {
        h.setBlock(CORE, ModBlocks.MOBILE_CORE.get());
        return (MobileCoreBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(CORE));
    }

    /** A survival player standing {@code away} blocks from the core. */
    private static ServerPlayer player(GameTestHelper h, double away) {
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        p.setPos(h.absoluteVec(new Vec3(10.5 + away, 2, 10.5)));
        return p;
    }

    /** Right-clicks the core with a lock in hand, as a player does. */
    private static boolean lockWith(GameTestHelper h, ServerPlayer p) {
        BlockPos pos = h.absolutePos(CORE);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModBlocks.MOBILE_CORE_LOCK.get()));
        ModBlocks.MOBILE_CORE.get().use(h.getLevel().getBlockState(pos), h.getLevel(), pos, p, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        return h.getLevel().getBlockEntity(pos) instanceof MobileCoreBlockEntity be && be.locked();
    }

    @GameTest(template = SetupGameTests.EMPTY)
    public void the_lock_binds_the_core_to_the_player_and_is_used_up(GameTestHelper h) {
        MobileCoreBlockEntity be = core(h);
        ServerPlayer owner = player(h, 1);
        if (!lockWith(h, owner)) {
            h.fail("the core did not lock");
            return;
        }
        if (!owner.getUUID().equals(be.owner()) || !owner.getMainHandItem().isEmpty()) {
            h.fail("the lock must bind the core to the player and be used up: owner " + be.owner() + ", hand " + owner.getMainHandItem());
            return;
        }
        if (lockWith(h, owner)) {
            // a second lock is refused and not consumed
            if (owner.getMainHandItem().isEmpty()) {
                h.fail("a second lock on a locked core must not be used up");
                return;
            }
        }
        h.succeed();
    }

    @GameTest(template = SetupGameTests.EMPTY)
    public void a_locked_core_refuses_strangers_and_admits_its_owner(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos pos = h.absolutePos(CORE);
        MobileCoreBlockEntity be = core(h);
        h.setBlock(CORE.below(), Blocks.OAK_PLANKS);
        ServerPlayer owner = player(h, 1), stranger = player(h, 1);
        lockWith(h, owner);
        BspCore.giveCoins(stranger, 1000);
        for (String what : new String[]{"assemble", "rename", "upgrade"}) {
            CoreService.Outcome out = switch (what) {
                case "assemble" -> CoreService.assemble(level, pos, stranger);
                case "rename" -> CoreService.rename(level, pos, "Taken", stranger);
                default -> CoreService.upgrade(level, pos, stranger);
            };
            if (out.ok() || !out.reason().equals("locked")) {
                h.fail(what + " by a stranger must be refused as locked: " + out);
                return;
            }
        }
        if (be.isAssembled() || be.name().equals("Taken") || be.tier() != 1) {
            h.fail("a refusal must change nothing");
            return;
        }
        if (!CoreService.rename(level, pos, "Harbour Lantern", owner).ok() || !be.name().equals("Harbour Lantern")) {
            h.fail("the owner must be admitted");
            return;
        }
        float strangerMining = ModBlocks.MOBILE_CORE.get().getDestroyProgress(level.getBlockState(pos), stranger, level, pos);
        float ownerMining = ModBlocks.MOBILE_CORE.get().getDestroyProgress(level.getBlockState(pos), owner, level, pos);
        if (strangerMining != 0f || ownerMining <= 0f) {
            h.fail("a stranger must not be able to mine a locked core (" + strangerMining + "), the owner must (" + ownerMining + ")");
            return;
        }
        h.succeed();
    }

    @GameTest(template = SetupGameTests.EMPTY)
    public void unlocking_gives_the_lock_back_and_strangers_cannot(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos pos = h.absolutePos(CORE);
        MobileCoreBlockEntity be = core(h);
        ServerPlayer owner = player(h, 1), stranger = player(h, 1);
        lockWith(h, owner);
        CoreService.Outcome out = LockService.unlock(level, pos, stranger);
        if (out.ok() || !out.reason().equals("locked") || !be.locked()) {
            h.fail("a stranger must not unlock: " + out);
            return;
        }
        out = LockService.unlock(level, pos, owner);
        if (!out.ok() || be.locked()) {
            h.fail("the owner must unlock: " + out);
            return;
        }
        if (owner.getInventory().countItem(ModBlocks.MOBILE_CORE_LOCK.get()) != 1) {
            h.fail("the lock must be back in the owner's inventory");
            return;
        }
        out = LockService.unlock(level, pos, owner);
        if (out.ok() || !out.reason().equals("not_locked")) {
            h.fail("unlocking an unlocked core must be refused: " + out);
            return;
        }
        h.succeed();
    }

    @GameTest(template = SetupGameTests.EMPTY)
    public void the_steal_refuses_the_far_the_admitted_and_a_second_thief(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos pos = h.absolutePos(CORE);
        MobileCoreBlockEntity be = core(h);
        ServerPlayer owner = player(h, 1), near = player(h, 1.5), far = player(h, 10), second = player(h, 1.5);
        CoreService.Outcome out = LockService.startSteal(level, pos, near);
        if (out.ok() || !out.reason().equals("not_locked")) {
            h.fail("an unlocked core cannot be stolen: " + out);
            return;
        }
        lockWith(h, owner);
        out = LockService.startSteal(level, pos, far);
        if (out.ok() || !out.reason().equals("far")) {
            h.fail("a thief ten blocks away must be refused as far: " + out);
            return;
        }
        out = LockService.startSteal(level, pos, owner);
        if (out.ok() || !out.reason().equals("yours")) {
            h.fail("the owner cannot steal their own core: " + out);
            return;
        }
        out = LockService.startSteal(level, pos, near);
        if (!out.ok() || !be.stealRunning() || !near.getUUID().equals(be.thief()) || be.stealTotal() != MobileConfig.lockStealTicks()) {
            h.fail("the steal must start for a near stranger with the configured length: " + out + ", total " + be.stealTotal());
            return;
        }
        out = LockService.startSteal(level, pos, second);
        if (out.ok() || !out.reason().equals("stealing")) {
            h.fail("one steal at a time: " + out);
            return;
        }
        out = LockService.unlock(level, pos, owner);
        if (out.ok() || !out.reason().equals("stealing")) {
            h.fail("the owner cannot unlock during a steal: " + out);
            return;
        }
        be.endSteal();
        h.succeed();
    }

    @GameTest(template = SetupGameTests.EMPTY, timeoutTicks = 1600)
    public void a_steal_ends_with_the_thief_owning_the_core(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos pos = h.absolutePos(CORE);
        MobileCoreBlockEntity be = core(h);
        ServerPlayer owner = player(h, 1), thief = player(h, 1.5);
        lockWith(h, owner);
        level.addNewPlayer(thief); // the steal's ticker looks the thief up in the level
        UUID ownerId = owner.getUUID();
        Waits.afterTicking(h, 1, () -> {
            CoreService.Outcome out = LockService.startSteal(level, pos, thief);
            if (!out.ok()) {
                drop(level, thief);
                h.fail("the steal did not start: " + out);
                return;
            }
            be.setStealTicks(40, MobileConfig.lockGraceTicks());
            Waits.pollThen(h, TIMEOUT, () -> !be.stealRunning(), () -> {
                drop(level, thief);
                if (!thief.getUUID().equals(be.owner()) || ownerId.equals(be.owner()) || !be.locked()) {
                    h.fail("after the steal the thief must own the core and the lock must stay on: owner " + be.owner() + ", locked " + be.locked());
                    return;
                }
                if (!admitted(level, be, thief) || admitted(level, be, owner)) {
                    h.fail("the lock must now admit the thief and refuse the old owner");
                    return;
                }
                h.succeed();
            }, () -> {
                drop(level, thief);
                h.fail("the steal never ended: " + be.stealLeft() + " ticks left, outside " + be.stealOutside());
            });
        });
    }

    @GameTest(template = SetupGameTests.EMPTY, timeoutTicks = 1600)
    public void a_steal_fails_when_the_thief_stays_away(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos pos = h.absolutePos(CORE);
        MobileCoreBlockEntity be = core(h);
        ServerPlayer owner = player(h, 1), thief = player(h, 1.5);
        lockWith(h, owner);
        level.addNewPlayer(thief);
        UUID ownerId = owner.getUUID();
        Waits.afterTicking(h, 1, () -> {
            CoreService.Outcome out = LockService.startSteal(level, pos, thief);
            if (!out.ok()) {
                drop(level, thief);
                h.fail("the steal did not start: " + out);
                return;
            }
            thief.setPos(h.absoluteVec(new Vec3(10.5 + 30, 2, 10.5))); // out of the radius for good
            be.setStealTicks(6000, 20); // one second of grace
            Waits.pollThen(h, TIMEOUT, () -> !be.stealRunning(), () -> {
                drop(level, thief);
                if (!ownerId.equals(be.owner()) || !be.locked()) {
                    h.fail("a failed steal must leave the owner and the lock: owner " + be.owner() + ", locked " + be.locked());
                    return;
                }
                h.succeed();
            }, () -> {
                drop(level, thief);
                h.fail("the steal never failed: " + be.stealLeft() + " ticks left, grace " + be.stealGrace() + ", outside " + be.stealOutside());
            });
        });
    }

    @GameTest(template = SetupGameTests.EMPTY)
    public void a_totem_aboard_caps_the_construct(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        core(h);
        h.setBlock(CORE.east(), Blocks.OAK_PLANKS);
        if (ConstructRules.of(0).capped() || !ConstructRules.of(1).capped()) {
            h.fail("a construct is capped exactly when a totem is aboard");
            return;
        }
        ConstructRules capped = ConstructRules.of(1);
        if (capped.maxSpeed() != MobileConfig.totemMaxSpeed() || capped.maxHeight() != MobileConfig.totemMaxHeight()) {
            h.fail("the caps must be the config's: " + capped);
            return;
        }
        Gather.Result before = Gather.gather(level, h.absolutePos(CORE), 200);
        if (CoreService.aboard(level, before).totems() != 0) {
            h.fail("no totem yet");
            return;
        }
        Block totem = ForgeRegistries.BLOCKS.getValue(ResourceLocation.fromNamespaceAndPath("bsp_core", "shatter_totem"));
        if (totem == null || totem == Blocks.AIR) {
            h.fail("BSP-Core's Shatter Totem block is not registered");
            return;
        }
        h.setBlock(CORE.west(), totem);
        try {
            Gather.Result with = Gather.gather(level, h.absolutePos(CORE), 200);
            CoreService.Aboard aboard = CoreService.aboard(level, with);
            if (aboard.totems() != 1 || !aboard.rules().capped()) {
                h.fail("a placed totem touching the core must be counted aboard and cap the construct: " + aboard);
                return;
            }
        } finally {
            h.setBlock(CORE.west(), Blocks.AIR);
        }
        h.succeed();
    }

    private static boolean admitted(ServerLevel level, MobileCoreBlockEntity be, ServerPlayer p) {
        return com.mrgregles.bsp_mobile.construct.Access.admitted(level, be, p);
    }

    private static void drop(ServerLevel level, ServerPlayer p) {
        level.removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
    }
}
