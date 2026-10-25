package com.mrgregles.bsp_mobile.construct;

import com.mrgregles.bsp_mobile.BSPMobile;
import com.mrgregles.bsp_mobile.MobileConfig;
import com.mrgregles.bsp_mobile.compat.BspCore;
import com.mrgregles.bsp_mobile.compat.ValkyrienSkiesBridge;
import com.mrgregles.bsp_mobile.compat.ValkyrienSkiesBridge.Construct;
import com.mrgregles.bsp_mobile.network.CoreOutlinePacket;
import com.mrgregles.bsp_mobile.network.CoreViewPacket;
import com.mrgregles.bsp_mobile.network.MobileNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What the Mobile Core does, server side: the screen's data, assembling the gather into a construct, releasing it,
 * renaming, and buying a tier. Commands and screens go through here so they behave the same. Every action takes the
 * acting player, refused with {@code locked} when the core's lock does not admit them (D30); a null actor is the server
 * itself. Valkyrien Skies is reached only through the bridge; BSP-Core only through its adapter.
 */
public final class CoreService {
    /** How far a player may be from a core (in the world) to open or act on its screen. */
    private static final double REACH = 8.0;
    private static final int FIND_CORE_TICKS = 100;
    /** Constructs just assembled whose core has not been found in the shipyard yet (the engine may finish a tick later). */
    private static final List<Pending> PENDING = new ArrayList<>();

    private record Pending(ServerLevel level, long shipId, int ticksLeft) {}

    /** What an action did; {@code corePos} is where the core sits afterwards when it moved (the shipyard, or the world again). */
    public record Outcome(boolean ok, String reason, long shipId, @Nullable BlockPos corePos) {
        static Outcome failed(String reason) {
            return new Outcome(false, reason, 0, null);
        }
    }

    private CoreService() {}

    public static Optional<MobileCoreBlockEntity> core(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof MobileCoreBlockEntity be ? Optional.of(be) : Optional.empty();
    }

    // ------------------------------------------------------------------ the screen

    /** Sends a player the core's screen (opening it) with the outline. */
    public static void open(ServerPlayer player, ServerLevel level, BlockPos pos) {
        send(player, level, pos, true, "");
    }

    /** Sends a fresh view to a player whose screen is open. */
    public static void refresh(ServerPlayer player, ServerLevel level, BlockPos pos, String note) {
        send(player, level, pos, false, note);
    }

    private static void send(ServerPlayer player, ServerLevel level, BlockPos pos, boolean open, String note) {
        Optional<MobileCoreBlockEntity> core = core(level, pos);
        if (core.isEmpty()) {
            return;
        }
        MobileCoreBlockEntity be = core.get();
        Gather.Result gather = Gather.gather(level, pos, Tiers.allowance(be.tier()));
        Construct c = be.isAssembled() ? ValkyrienSkiesBridge.construct(level.getServer(), be.shipId()).orElse(null) : null;
        if (be.isAssembled() && c == null) {
            be.setShipId(0); // the construct is gone (an admin removed it, or the world lost it)
        }
        ConstructState state = ConstructState.of(c != null, c == null ? 0 : c.speed(), MobileConfig.movingSpeed());
        Aboard aboard = aboard(level, gather);
        List<CoreViewPacket.Refused> refused = new ArrayList<>();
        java.util.Map<String, int[]> byKind = new java.util.LinkedHashMap<>();
        for (Gather.Refusal r : gather.refused()) {
            byKind.computeIfAbsent(r.block() + "|" + r.reason().ordinal(), k -> new int[]{0})[0]++;
        }
        for (var e : byKind.entrySet()) {
            String[] parts = e.getKey().split("\\|");
            refused.add(new CoreViewPacket.Refused(parts[0], Integer.parseInt(parts[1]), e.getValue()[0]));
        }
        List<CoreViewPacket.Kind> kinds = new ArrayList<>();
        gather.kinds().forEach((id, n) -> kinds.add(new CoreViewPacket.Kind(id.toString(), n)));
        int tier = be.tier();
        int nextAllowance = tier < Tiers.count() ? Tiers.allowance(tier + 1) : 0;
        boolean owner = be.owner() != null && be.owner().equals(player.getUUID());
        boolean coins = BspCore.coinsAvailable();
        int worth = coins ? BspCore.coinWorth(player) : 0;
        boolean admitted = Access.admitted(level, be, player);
        // the helms aboard (MB-4): the driver, the heading along the first helm's facing, the top speed with the totem's cap
        int helms = 0;
        String driverName = "";
        net.minecraft.core.Direction helmFacing = null;
        for (BlockPos p : gather.blocks()) {
            if (level.getBlockEntity(p) instanceof HelmBlockEntity h) {
                helms++;
                if (helmFacing == null) {
                    helmFacing = h.facing();
                }
                if (driverName.isEmpty() && h.hasDriver()) {
                    driverName = h.driverName();
                }
            }
        }
        int heading = c == null ? -1 : HelmService.heading(level, be.shipId(), helmFacing);
        double topSpeed = Math.min(aboard.parts().maxSpeed(), aboard.rules().maxSpeed());
        boolean anchored = c != null && c.parked();
        boolean closedRegion = Where.closedRegion(level, pos);
        Parts parts = aboard.parts();
        double mass = c == null ? 0 : c.mass();
        int height = c == null ? 0 : (int) Math.floor(c.positionInWorld().y);
        int ceiling = c == null || !aboard.rules().capped() ? -1 : HelmService.ceiling(level, c, aboard.rules());
        long[] plan = be.waypoints().stream().mapToLong(BlockPos::asLong).toArray();
        CoreViewPacket view = new CoreViewPacket(pos, open, be.name(), tier, gather.allowance(), gather.count(), gather.tooMany(),
                state.ordinal(), c == null ? 0 : c.speed(), refused, kinds, aboard.totems, aboard.chests, aboard.machines,
                be.ownerName(), owner, nextAllowance, Tiers.count(), coins, Prices.ladder(), worth, coins ? Prices.breakdown(worth) : List.of(),
                be.locked(), admitted, be.stealRunning() ? be.thiefName() : "", be.stealLeft(), be.stealTotal(), be.stealOutside(),
                driverName, helms, heading, topSpeed, anchored, closedRegion,
                parts.engines(), parts.floaters(), parts.balloons(), parts.wings(), parts.jets(), parts.liftCapacity(), mass, height, ceiling,
                plan, be.cruise(), be.planNext(), be.planRunning(), PlanService.distanceToNext(level, be), note);
        MobileNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), view);
        long[] taken = gather.blocks().stream().mapToLong(BlockPos::asLong).toArray();
        long[] left = gather.refused().stream().mapToLong(r -> r.pos().asLong()).toArray();
        MobileNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new CoreOutlinePacket(pos, taken, left));
    }

    /** What the gather carries that the rules care about: placed totems (D2, D34), containers, and other block entities. */
    public record Aboard(int totems, int chests, int machines, Parts parts) {
        public ConstructRules rules() {
            return ConstructRules.of(totems);
        }
    }

    public static Aboard aboard(ServerLevel level, Gather.Result gather) {
        int totems = 0, chests = 0, machines = 0;
        for (BlockPos p : gather.blocks()) {
            BlockState state = level.getBlockState(p);
            if (BspCore.isTotem(state)) {
                totems++;
                continue;
            }
            BlockEntity be = level.getBlockEntity(p);
            if (be == null || be instanceof MobileCoreBlockEntity) {
                continue;
            }
            if (be instanceof Container) {
                chests++;
            } else {
                machines++;
            }
        }
        return new Aboard(totems, chests, machines, Parts.count(level, gather.blocks()));
    }

    // ------------------------------------------------------------------ actions

    /** True when the player may act on the core at {@code pos}: it exists and is within reach where it is drawn. */
    public static boolean canReach(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (core(level, pos).isEmpty()) {
            return false;
        }
        Vec3 where = ValkyrienSkiesBridge.toWorld(level, Vec3.atCenterOf(pos));
        return player.position().distanceTo(where) <= REACH + 1.0;
    }

    /** Assembles the core's gather into a construct, parked where it stood. */
    public static Outcome assemble(ServerLevel level, BlockPos pos) {
        return assemble(level, pos, null);
    }

    public static Outcome assemble(ServerLevel level, BlockPos pos, @Nullable ServerPlayer actor) {
        Optional<MobileCoreBlockEntity> core = core(level, pos);
        if (core.isEmpty()) {
            return Outcome.failed("no_core");
        }
        MobileCoreBlockEntity be = core.get();
        if (!Access.admitted(level, be, actor)) {
            return Outcome.failed("locked");
        }
        if (be.isAssembled()) {
            return Outcome.failed("assembled");
        }
        String where = Where.assemblyRefused(level, pos, actor);
        if (!where.isEmpty()) {
            return Outcome.failed(where);
        }
        String cap = Constructs.capRefused(level.getServer(), be.owner());
        if (!cap.isEmpty()) {
            return Outcome.failed(cap);
        }
        if (!ValkyrienSkiesBridge.isPresent()) {
            return Outcome.failed("engine");
        }
        Gather.Result gather = Gather.gather(level, pos, Tiers.allowance(be.tier()));
        if (gather.tooMany()) {
            return Outcome.failed("too_many");
        }
        if (gather.blocks().size() < 2) {
            return Outcome.failed("alone");
        }
        Optional<Long> id = ValkyrienSkiesBridge.assemble(level, gather.blocks());
        if (id.isEmpty()) {
            return Outcome.failed("engine");
        }
        ValkyrienSkiesBridge.setParked(level, id.get(), true);
        Optional<MobileCoreBlockEntity> moved = coreOf(level, id.get());
        if (moved.isPresent()) {
            moved.get().setShipId(id.get());
        } else {
            PENDING.add(new Pending(level, id.get(), FIND_CORE_TICKS));
        }
        return new Outcome(true, "", id.get(), moved.map(MobileCoreBlockEntity::getBlockPos).orElse(null));
    }

    /** Finds the construct's core in the shipyard and gives it the ship id. */
    private static boolean markCore(ServerLevel level, long shipId) {
        Optional<MobileCoreBlockEntity> core = coreOf(level, shipId);
        core.ifPresent(be -> be.setShipId(shipId));
        return core.isPresent();
    }

    /** The core block entity of a construct, found in its shipyard box. */
    public static Optional<MobileCoreBlockEntity> coreOf(ServerLevel level, long shipId) {
        Construct c = ValkyrienSkiesBridge.construct(level.getServer(), shipId).orElse(null);
        if (c == null || c.shipyardMin() == null || c.shipyardMax() == null) {
            return Optional.empty();
        }
        for (int x = c.shipyardMin().getX(); x < c.shipyardMax().getX(); x++) {
            for (int y = c.shipyardMin().getY(); y < c.shipyardMax().getY(); y++) {
                for (int z = c.shipyardMin().getZ(); z < c.shipyardMax().getZ(); z++) {
                    if (level.getBlockEntity(new BlockPos(x, y, z)) instanceof MobileCoreBlockEntity be) {
                        return Optional.of(be);
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** Puts the construct's blocks back into the world (D19: only while parked). */
    public static Outcome release(ServerLevel level, BlockPos pos) {
        return release(level, pos, null);
    }

    public static Outcome release(ServerLevel level, BlockPos pos, @Nullable ServerPlayer actor) {
        Optional<MobileCoreBlockEntity> core = core(level, pos);
        if (core.isEmpty()) {
            return Outcome.failed("no_core");
        }
        MobileCoreBlockEntity be = core.get();
        if (!Access.admitted(level, be, actor)) {
            return Outcome.failed("locked");
        }
        if (!be.isAssembled()) {
            return Outcome.failed("not_assembled");
        }
        long shipId = be.shipId();
        Construct c = ValkyrienSkiesBridge.construct(level.getServer(), shipId).orElse(null);
        if (c == null) {
            be.setShipId(0);
            return Outcome.failed("gone");
        }
        if (c.speed() > MobileConfig.movingSpeed()) {
            return Outcome.failed("moving");
        }
        ValkyrienSkiesBridge.Release done = ValkyrienSkiesBridge.release(level, shipId);
        if (!done.ok()) {
            return Outcome.failed(done.reason());
        }
        BlockPos corePos = done.moved().get(pos);
        if (corePos != null && level.getBlockEntity(corePos) instanceof MobileCoreBlockEntity moved) {
            moved.setShipId(0);
        }
        return new Outcome(true, "", shipId, corePos);
    }

    /**
     * Buys the next tier for the core (D20, D23: whoever stands at the core pays, in BSP-Core's Shatter Coins, with
     * change). Refused at the top tier, without BSP-Core's coins, without a price for the step, or when the buyer is short.
     */
    public static Outcome upgrade(ServerLevel level, BlockPos pos, ServerPlayer buyer) {
        Optional<MobileCoreBlockEntity> core = core(level, pos);
        if (core.isEmpty()) {
            return Outcome.failed("no_core");
        }
        MobileCoreBlockEntity be = core.get();
        if (!Access.admitted(level, be, buyer)) {
            return Outcome.failed("locked");
        }
        int tier = be.tier();
        if (tier >= Tiers.count()) {
            return Outcome.failed("top");
        }
        if (!BspCore.coinsAvailable()) {
            return Outcome.failed("no_coins");
        }
        Prices.Coins price = Prices.of(tier + 1);
        if (price.isNone()) {
            return Outcome.failed("price");
        }
        if (BspCore.coinWorth(buyer) < price.value() || !BspCore.pay(buyer, price.value())) {
            return Outcome.failed("short");
        }
        be.setTier(tier + 1);
        return new Outcome(true, "", be.shipId(), null);
    }

    public static Outcome rename(ServerLevel level, BlockPos pos, String name) {
        return rename(level, pos, name, null);
    }

    public static Outcome rename(ServerLevel level, BlockPos pos, String name, @Nullable ServerPlayer actor) {
        Optional<MobileCoreBlockEntity> core = core(level, pos);
        if (core.isEmpty()) {
            return Outcome.failed("no_core");
        }
        if (!Access.admitted(level, core.get(), actor)) {
            return Outcome.failed("locked");
        }
        String clean = name.strip().replaceAll("[\\p{Cntrl}§]", "");
        if (clean.isEmpty()) {
            return Outcome.failed("name");
        }
        core.get().setName(clean);
        return new Outcome(true, "", core.get().shipId(), null);
    }

    // ------------------------------------------------------------------ the tick: cores not yet found

    /** Constructs whose core was destroyed while assembled (D45), broken up on the next tick. */
    private static final List<BreakUp> BREAK_UPS = new ArrayList<>();

    private record BreakUp(ServerLevel level, long shipId, String name, @Nullable java.util.UUID owner) {}

    /** The core was destroyed while assembled: the construct breaks up on the next tick (not while the destroyer is still at work). */
    public static void coreDestroyed(ServerLevel level, MobileCoreBlockEntity be) {
        if (be.isAssembled() && !ValkyrienSkiesBridge.isBusy(be.shipId())) {
            BREAK_UPS.add(new BreakUp(level, be.shipId(), be.name(), be.owner()));
        }
    }

    private static void breakUp(BreakUp b) {
        ValkyrienSkiesBridge.Release done = ValkyrienSkiesBridge.release(b.level(), b.shipId());
        String key = done.ok() ? "message.bsp_mobile.core.broken" : "message.bsp_mobile.core.derelict";
        if (!done.ok()) {
            BSPMobile.LOGGER.info("BSP-Mobile: construct {} ({}) lost its core and could not be set down ({}); it lies where it is", b.shipId(), b.name(), done.reason());
        }
        if (b.owner() != null) {
            ServerPlayer p = b.level().getServer().getPlayerList().getPlayer(b.owner());
            if (p != null) {
                p.displayClientMessage(net.minecraft.network.chat.Component.translatable(key, b.name()).withStyle(net.minecraft.ChatFormatting.RED), false);
            }
        }
    }

    /** An impact the engine reported: each construct in it that is ours loses blocks at the contact (D44). */
    private static void impact(net.minecraft.server.MinecraftServer server, ValkyrienSkiesBridge.Impact i) {
        int n = Damage.blocksToKnock(i.speed());
        if (n <= 0) {
            return;
        }
        for (long id : new long[]{i.shipA(), i.shipB()}) {
            if (id == 0) {
                continue;
            }
            for (ServerLevel level : server.getAllLevels()) {
                Optional<MobileCoreBlockEntity> core = coreOf(level, id);
                if (core.isPresent()) {
                    if (Damage.impactAllowed(id, server.getTickCount())) {
                        Damage.knock(level, id, i.contactWorld(), n);
                    }
                    break;
                }
            }
        }
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!BREAK_UPS.isEmpty()) {
            List<BreakUp> now = new ArrayList<>(BREAK_UPS);
            BREAK_UPS.clear();
            now.forEach(CoreService::breakUp);
        }
        for (ValkyrienSkiesBridge.Impact i : ValkyrienSkiesBridge.drainImpacts()) {
            impact(event.getServer(), i);
        }
        if (PENDING.isEmpty()) {
            return;
        }
        List<Pending> again = new ArrayList<>();
        for (Pending p : PENDING) {
            if (markCore(p.level(), p.shipId())) {
                continue;
            }
            if (p.ticksLeft() > 1) {
                again.add(new Pending(p.level(), p.shipId(), p.ticksLeft() - 1));
            } else {
                BSPMobile.LOGGER.warn("BSP-Mobile: no Mobile Core found in construct {} after assembly", p.shipId());
            }
        }
        PENDING.clear();
        PENDING.addAll(again);
    }

    @Nullable
    public static Construct construct(ServerLevel level, MobileCoreBlockEntity be) {
        return be.isAssembled() ? ValkyrienSkiesBridge.construct(level.getServer(), be.shipId()).orElse(null) : null;
    }
}
