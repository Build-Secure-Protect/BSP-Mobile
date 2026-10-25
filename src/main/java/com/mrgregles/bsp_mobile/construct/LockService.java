package com.mrgregles.bsp_mobile.construct;

import com.mrgregles.bsp_mobile.MobileConfig;
import com.mrgregles.bsp_mobile.compat.BspCore;
import com.mrgregles.bsp_mobile.compat.ValkyrienSkiesBridge;
import com.mrgregles.bsp_mobile.construct.CoreService.Outcome;
import com.mrgregles.bsp_mobile.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The Mobile Core Lock's rules (D27 to D33): locking a core with the item, unlocking it, and the steal: an enemy
 * within the radius starts it, it runs for the configured time, pauses while the thief is farther than the radius
 * and fails when they stay away past the grace, die or log out; the owner and their team are told when it starts and
 * ends; at the end the thief owns the core (and so its construct) and the lock stays on. Distances are measured
 * from the core's world position, so a steal works aboard a moving construct.
 */
public final class LockService {
    private LockService() {}

    private static Optional<MobileCoreBlockEntity> core(ServerLevel level, BlockPos pos) {
        return CoreService.core(level, pos);
    }

    /** Right-click with the lock (D29): the lock is used up (survival) and the core binds to the player. */
    public static Outcome lock(ServerLevel level, BlockPos pos, ServerPlayer player, ItemStack held) {
        Optional<MobileCoreBlockEntity> core = core(level, pos);
        if (core.isEmpty()) {
            return Outcome.failed("no_core");
        }
        MobileCoreBlockEntity be = core.get();
        if (be.locked()) {
            return Outcome.failed("locked_already");
        }
        be.lock(player.getUUID(), player.getGameProfile().getName());
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        player.displayClientMessage(Component.translatable("message.bsp_mobile.lock.on", be.name()).withStyle(ChatFormatting.LIGHT_PURPLE), false);
        return new Outcome(true, "", be.shipId(), null);
    }

    /** UNLOCK (D29, D33): the owner gets the lock back; an admin who is not the owner drops it at the core. Not while a steal runs. */
    public static Outcome unlock(ServerLevel level, BlockPos pos, ServerPlayer player) {
        Optional<MobileCoreBlockEntity> core = core(level, pos);
        if (core.isEmpty()) {
            return Outcome.failed("no_core");
        }
        MobileCoreBlockEntity be = core.get();
        if (!be.locked()) {
            return Outcome.failed("not_locked");
        }
        if (!Access.admitted(level, be, player)) {
            return Outcome.failed("locked");
        }
        if (be.stealRunning()) {
            return Outcome.failed("stealing");
        }
        be.unlock();
        ItemStack lock = new ItemStack(ModBlocks.MOBILE_CORE_LOCK.get());
        boolean owner = be.owner() != null && be.owner().equals(player.getUUID());
        if (!owner || !player.getInventory().add(lock)) {
            dropAt(level, pos, lock);
        }
        return new Outcome(true, "", be.shipId(), null);
    }

    /** STEAL (D31): starts the steal for a player the lock does not admit, standing within the radius. */
    public static Outcome startSteal(ServerLevel level, BlockPos pos, ServerPlayer thief) {
        Optional<MobileCoreBlockEntity> core = core(level, pos);
        if (core.isEmpty()) {
            return Outcome.failed("no_core");
        }
        MobileCoreBlockEntity be = core.get();
        if (!be.locked()) {
            return Outcome.failed("not_locked");
        }
        if (Access.admitted(level, be, thief)) {
            return Outcome.failed("yours");
        }
        if (be.stealRunning()) {
            return Outcome.failed("stealing");
        }
        if (distance(level, pos, thief) > MobileConfig.lockStealRadius()) {
            return Outcome.failed("far");
        }
        int ticks = MobileConfig.lockStealTicks();
        be.startSteal(thief.getUUID(), thief.getGameProfile().getName(), ticks, MobileConfig.lockGraceTicks());
        String minutes = minutes(ticks);
        thief.displayClientMessage(Component.translatable("message.bsp_mobile.steal.begun", be.name(), be.ownerName(),
                String.format(Locale.ROOT, "%.0f", MobileConfig.lockStealRadius()), minutes).withStyle(ChatFormatting.GOLD), false);
        tellOwners(level.getServer(), be, Component.translatable("message.bsp_mobile.steal.warning", thief.getGameProfile().getName(), be.name(), minutes)
                .withStyle(ChatFormatting.RED));
        return new Outcome(true, "", be.shipId(), null);
    }

    /** The core's ticker: runs the steal. */
    public static void tick(ServerLevel level, MobileCoreBlockEntity be) {
        if (!be.stealRunning()) {
            return;
        }
        UUID id = be.thief();
        ServerPlayer thief = player(level, id);
        if (thief == null || !thief.isAlive() || thief.level() != level) {
            fail(level, be);
            return;
        }
        boolean outside = distance(level, be.getBlockPos(), thief) > MobileConfig.lockStealRadius();
        int grace = outside ? be.stealGrace() - 1 : MobileConfig.lockGraceTicks();
        if (outside && grace < 0) {
            fail(level, be);
            return;
        }
        int left = outside ? be.stealLeft() : be.stealLeft() - 1;
        if (left <= 0) {
            succeed(level, be, thief);
            return;
        }
        be.stealTick(left, grace, outside);
    }

    private static void succeed(ServerLevel level, MobileCoreBlockEntity be, ServerPlayer thief) {
        String name = be.name();
        tellOwners(level.getServer(), be, Component.translatable("message.bsp_mobile.steal.lost", thief.getGameProfile().getName(), name).withStyle(ChatFormatting.RED));
        be.setOwner(thief.getUUID(), thief.getGameProfile().getName());
        be.endSteal();
        thief.displayClientMessage(Component.translatable("message.bsp_mobile.steal.won", name).withStyle(ChatFormatting.GREEN), false);
    }

    private static void fail(ServerLevel level, MobileCoreBlockEntity be) {
        ServerPlayer thief = player(level, be.thief());
        String name = be.name();
        be.endSteal();
        if (thief != null) {
            thief.displayClientMessage(Component.translatable("message.bsp_mobile.steal.failed", name).withStyle(ChatFormatting.GOLD), false);
        }
        tellOwners(level.getServer(), be, Component.translatable("message.bsp_mobile.steal.saved", name).withStyle(ChatFormatting.GREEN));
    }

    // ------------------------------------------------------------------ helpers

    /** Straight-line distance from the player to the core where it is drawn in the world. */
    public static double distance(ServerLevel level, BlockPos pos, Player player) {
        return player.position().distanceTo(ValkyrienSkiesBridge.toWorld(level, Vec3.atCenterOf(pos)));
    }

    @Nullable
    private static ServerPlayer player(ServerLevel level, @Nullable UUID id) {
        if (id == null) {
            return null;
        }
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
        if (p == null && level.getPlayerByUUID(id) instanceof ServerPlayer sp) {
            p = sp;
        }
        return p;
    }

    /** Tells the owner and their teammates (those online here). */
    private static void tellOwners(MinecraftServer server, MobileCoreBlockEntity be, Component message) {
        UUID owner = be.owner();
        if (owner == null) {
            return;
        }
        tell(server, owner, message);
        for (UUID mate : BspCore.teammates(server, owner)) {
            tell(server, mate, message);
        }
    }

    private static void tell(MinecraftServer server, UUID id, Component message) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p != null) {
            p.displayClientMessage(message, false);
        }
    }

    private static void dropAt(ServerLevel level, BlockPos pos, ItemStack stack) {
        Vec3 at = ValkyrienSkiesBridge.toWorld(level, Vec3.atCenterOf(pos));
        ItemEntity item = new ItemEntity(level, at.x, at.y + 0.5, at.z, stack);
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
    }

    private static String minutes(int ticks) {
        double m = ticks / 1200.0;
        return m == Math.floor(m) ? Integer.toString((int) m) : String.format(Locale.ROOT, "%.1f", m);
    }
}
