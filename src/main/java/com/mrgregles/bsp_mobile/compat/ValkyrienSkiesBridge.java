package com.mrgregles.bsp_mobile.compat;

import com.mrgregles.bsp_mobile.BSPMobile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.primitives.AABBic;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.PhysShip;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.core.api.world.PhysLevel;
import org.valkyrienskies.mod.api.SeatedControllingPlayer;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.entity.ShipMountingEntity;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.core.api.world.ServerShipWorld;
import org.valkyrienskies.mod.api.ValkyrienSkies;
import org.valkyrienskies.mod.common.assembly.ShipAssembler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The one class that talks to Valkyrien Skies. Everything the rest of the mod needs from a ship comes back as a plain
 * value ({@link Construct}), so no other class sees a Valkyrien Skies type and the engine could be swapped behind this
 * class. Every call is guarded: a missing or older Valkyrien Skies costs the feature, never a crash.
 *
 * <p>Words: Valkyrien Skies calls a moving block set a <em>ship</em> and keeps its blocks in the <em>shipyard</em>, real
 * chunks far away in the same dimension that are drawn at the ship's position. This mod calls a ship a construct.
 */
public final class ValkyrienSkiesBridge {
    public static final String MOD_ID = "valkyrienskies";

    private ValkyrienSkiesBridge() {}

    /**
     * A construct as the rest of the mod sees it. {@code shipyardMin} and {@code shipyardMax} are the blocks' box in
     * shipyard coordinates (null when the construct has no blocks); the far corner is one past the last block, as
     * Valkyrien Skies reports it. {@code speed} is in blocks a second. {@code mass} is known on the server only.
     */
    public record Construct(long id, Vec3 positionInWorld, @Nullable BlockPos shipyardMin, @Nullable BlockPos shipyardMax, double mass, double speed, boolean parked) {}

    /** What a release did: the blocks' old shipyard position to their new world position, or why it did nothing. */
    public record Release(boolean ok, String reason, Map<BlockPos, BlockPos> moved) {
        static Release failed(String reason) {
            return new Release(false, reason, Map.of());
        }
    }

    public static boolean isPresent() {
        return ModList.get().isLoaded(MOD_ID);
    }

    // ------------------------------------------------------------------ driving (MB-4, D37 to D41)

    /** The driver's keys as the engine reports them for a construct's seat. */
    public record Inputs(float forward, float left, float up, boolean sprint) {
        public static final Inputs NONE = new Inputs(0, 0, 0, false);
    }

    /** The commands the helms set, read on the physics thread by {@link Control}; one per construct. */
    private static final java.util.concurrent.ConcurrentHashMap<Long, com.mrgregles.bsp_mobile.construct.Drive.Command> COMMANDS = new java.util.concurrent.ConcurrentHashMap<>();
    /** What the hook last did per construct, for admin tools and tests. */
    private static final java.util.concurrent.ConcurrentHashMap<Long, DriveStats> STATS = new java.util.concurrent.ConcurrentHashMap<>();

    /** The physics hook's last tick for a construct: how many ticks it has run, the force it applied, the speed it saw. */
    public record DriveStats(long ticks, double force, double speed, boolean sleeping, boolean parked) {
        public static final DriveStats NONE = new DriveStats(0, 0, 0, false, false);
    }

    public static DriveStats driveStats(long id) {
        return STATS.getOrDefault(id, DriveStats.NONE);
    }

    /**
     * The physics hook: on every physics tick of the engine (its global event, which needs no per-ship attachment) it
     * looks up each driven construct's physics body, reads the helm's latest command, applies the drive maths as a
     * force and a torque in the construct's own frame, and sets the buoyancy. Physics thread: it touches only the
     * concurrent maps.
     */
    /** How often the engine's physics tick has reached this mod, and how often a ship lookup threw (admin tools and tests). */
    private static final java.util.concurrent.atomic.AtomicLong PHYS_TICKS = new java.util.concurrent.atomic.AtomicLong(), LOOKUP_ERRORS = new java.util.concurrent.atomic.AtomicLong();

    public static long physTicksSeen() {
        return PHYS_TICKS.get();
    }

    public static long lookupErrors() {
        return LOOKUP_ERRORS.get();
    }

    private static void onPhysTick(PhysLevel level) {
        PHYS_TICKS.incrementAndGet();
        for (var e : COMMANDS.entrySet()) {
            PhysShip ship;
            try {
                ship = level.getShipById(e.getKey());
            } catch (RuntimeException ex) {
                LOOKUP_ERRORS.incrementAndGet();
                continue; // another dimension's physics level
            }
            if (ship != null) {
                Control.physTick(ship, e.getValue());
            }
        }
    }

    static final class Control {
        private Control() {}

        static void physTick(PhysShip ship, com.mrgregles.bsp_mobile.construct.Drive.Command cmd) {
            DriveStats last = STATS.getOrDefault(ship.getId(), DriveStats.NONE);
            ship.setBuoyantFactor(cmd.buoyancy());
            ship.setDoFluidDrag(true);
            if (ship.isStatic()) {
                STATS.put(ship.getId(), new DriveStats(last.ticks() + 1, 0, 0, ship.isSleeping(), true));
                return;
            }
            ShipTransform t = ship.getTransform();
            Vector3d vShip = t.transformDirectionNoScalingFromWorldToShip(ship.getVelocity(), new Vector3d());
            Vector3d omegaShip = t.transformDirectionNoScalingFromWorldToShip(ship.getOmega(), new Vector3d());
            double inertiaY = ship.getMomentOfInertia().m11();
            com.mrgregles.bsp_mobile.construct.Drive.Forces f = com.mrgregles.bsp_mobile.construct.Drive.forces(ship.getMass(), inertiaY, vShip.x, ship.getVelocity().y(), vShip.z, omegaShip.y, cmd);
            if (f.fx() != 0 || f.fz() != 0) {
                ship.applyRotDependentForce(new Vector3d(f.fx(), 0, f.fz()));
            }
            if (f.fy() != 0) {
                ship.applyInvariantForce(new Vector3d(0, f.fy(), 0)); // lift works against gravity in the world, whatever the tilt
            }
            if (f.torqueY() != 0) {
                ship.applyRotDependentTorque(new Vector3d(0, f.torqueY(), 0));
            }
            STATS.put(ship.getId(), new DriveStats(last.ticks() + 1, Math.sqrt(f.fx() * f.fx() + f.fz() * f.fz()), ship.getVelocity().length(), ship.isSleeping(), false));
        }
    }

    // ------------------------------------------------------------------ impacts (MB-5, D44)

    /** A collision the engine reported: the two bodies (0 for the ground), the hardest contact's world position and relative speed. */
    public record Impact(long shipA, long shipB, Vec3 contactWorld, double speed) {}

    private static final java.util.concurrent.ConcurrentLinkedQueue<Impact> IMPACTS = new java.util.concurrent.ConcurrentLinkedQueue<>();
    /** Constructs this mod is taking apart right now (a release or a removal): their blocks vanishing is not damage. */
    private static final java.util.Set<Long> BUSY = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static boolean isBusy(long id) {
        return BUSY.contains(id);
    }

    /** The impacts since the last call (the server thread drains them every tick). */
    public static List<Impact> drainImpacts() {
        List<Impact> out = new ArrayList<>();
        Impact i;
        while ((i = IMPACTS.poll()) != null) {
            out.add(i);
        }
        return out;
    }

    private static void onCollision(org.valkyrienskies.core.api.events.CollisionEvent event) {
        org.valkyrienskies.core.api.physics.ContactPoint hardest = null;
        double speed = 0;
        for (org.valkyrienskies.core.api.physics.ContactPoint p : event.getContactPoints()) {
            double s = p.getVelocity().length();
            if (hardest == null || s > speed) {
                hardest = p;
                speed = s;
            }
        }
        if (hardest == null) {
            return;
        }
        Vector3dc at = hardest.getPosition();
        IMPACTS.add(new Impact(event.getShipIdA(), event.getShipIdB(), new Vec3(at.x(), at.y(), at.z()), speed));
    }

    /** A world position turned into the construct's own space; empty when the construct is gone. */
    public static Optional<Vec3> toShip(MinecraftServer server, long id, Vec3 world) {
        if (!isPresent()) {
            return Optional.empty();
        }
        try {
            ServerShip ship = serverShip(server, id);
            return ship == null ? Optional.empty() : Optional.of(ValkyrienSkies.positionToShip(ship, world));
        } catch (LinkageError | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Hooks the drive into the engine's physics tick and the impacts into its collision event (once, at common setup). */
    public static void registerPhysics() {
        if (!isPresent()) {
            return;
        }
        try {
            ValkyrienSkies.api().getPhysTickEvent().on(event -> onPhysTick(event.getWorld()));
            ValkyrienSkies.api().getCollisionStartEvent().on(ValkyrienSkiesBridge::onCollision);
            BSPMobile.LOGGER.info("BSP-Mobile: hooked into Valkyrien Skies' physics tick");
        } catch (LinkageError | RuntimeException e) {
            BSPMobile.LOGGER.error("BSP-Mobile: Valkyrien Skies refused the physics hook; constructs will not drive", e);
        }
    }

    @Nullable
    private static LoadedServerShip loadedShip(MinecraftServer server, long id) {
        return ValkyrienSkies.getShipWorld(server).getLoadedShips().getById(id);
    }

    /** True once the engine has the construct loaded (its physics object exists); a fresh construct takes a few ticks. */
    public static boolean isLoaded(MinecraftServer server, long id) {
        if (!isPresent()) {
            return false;
        }
        try {
            return loadedShip(server, id) != null;
        } catch (LinkageError | RuntimeException e) {
            return false;
        }
    }

    /**
     * Puts the player at a helm: a seat entity in the shipyard at {@code standAt}, the player riding it as its
     * controller, and the construct's seat attachment set so the engine routes the player's movement keys to it.
     * Returns the seat entity's id.
     */
    public static Optional<UUID> mount(ServerLevel level, long id, BlockPos standAt, Direction facing, ServerPlayer player) {
        if (!isPresent()) {
            return Optional.empty();
        }
        try {
            LoadedServerShip ship = loadedShip(level.getServer(), id);
            if (ship == null) {
                BSPMobile.LOGGER.warn("BSP-Mobile: construct {} is not loaded; nobody can take its helm", id);
                return Optional.empty();
            }
            ShipMountingEntity seat = new ShipMountingEntity(ValkyrienSkiesMod.SHIP_MOUNTING_ENTITY_TYPE, level);
            Vec3 at = Vec3.atBottomCenterOf(standAt);
            seat.setPos(at.x, at.y, at.z);
            seat.setYRot(facing.toYRot());
            seat.setController(true);
            if (!level.addFreshEntity(seat)) {
                BSPMobile.LOGGER.warn("BSP-Mobile: the seat at {} on construct {} could not be added", standAt, id);
                return Optional.empty();
            }
            if (!player.startRiding(seat, true)) {
                BSPMobile.LOGGER.warn("BSP-Mobile: {} could not sit at the helm of construct {}", player.getGameProfile().getName(), id);
                seat.discard();
                return Optional.empty();
            }
            ship.setAttachment(SeatedControllingPlayer.class, new SeatedControllingPlayer(facing));
            return Optional.of(seat.getUUID());
        } catch (LinkageError | RuntimeException e) {
            BSPMobile.LOGGER.error("BSP-Mobile: Valkyrien Skies could not seat a driver on construct {}", id, e);
            return Optional.empty();
        }
    }

    /** The player riding the seat entity, if the seat still exists and is ridden. */
    public static Optional<ServerPlayer> driverOf(ServerLevel level, @Nullable UUID seat) {
        if (seat == null) {
            return Optional.empty();
        }
        net.minecraft.world.entity.Entity e = level.getEntity(seat);
        return e != null && e.getFirstPassenger() instanceof ServerPlayer p ? Optional.of(p) : Optional.empty();
    }

    /** Removes a seat entity (its rider dismounts) and the construct's seat attachment. */
    public static void removeSeat(ServerLevel level, long id, @Nullable UUID seat) {
        if (!isPresent()) {
            return;
        }
        try {
            if (seat != null) {
                net.minecraft.world.entity.Entity e = level.getEntity(seat);
                if (e != null) {
                    e.ejectPassengers();
                    e.discard();
                }
            }
            LoadedServerShip ship = loadedShip(level.getServer(), id);
            if (ship != null) {
                ship.removeAttachment(SeatedControllingPlayer.class);
            }
        } catch (LinkageError | RuntimeException ignored) {
            // the construct is gone
        }
    }

    /** The driver's keys for the construct, as the engine has them. */
    public static Inputs inputs(MinecraftServer server, long id) {
        if (!isPresent()) {
            return Inputs.NONE;
        }
        try {
            LoadedServerShip ship = loadedShip(server, id);
            SeatedControllingPlayer s = ship == null ? null : ship.getAttachment(SeatedControllingPlayer.class);
            return s == null ? Inputs.NONE : new Inputs(s.getForwardImpulse(), s.getLeftImpulse(), s.getUpImpulse(), s.getSprintOn());
        } catch (LinkageError | RuntimeException e) {
            return Inputs.NONE;
        }
    }

    /** Sets the driver's keys on the construct's seat (an autopilot, admin tools and tests; a real driver's keys come from the engine). */
    public static boolean setInputs(MinecraftServer server, long id, @Nullable Direction facing, Inputs in) {
        if (!isPresent()) {
            return false;
        }
        try {
            LoadedServerShip ship = loadedShip(server, id);
            if (ship == null) {
                return false;
            }
            SeatedControllingPlayer s = ship.getAttachment(SeatedControllingPlayer.class);
            if (s == null) {
                if (facing == null) {
                    return true; // nothing to clear
                }
                s = new SeatedControllingPlayer(facing);
                ship.setAttachment(SeatedControllingPlayer.class, s);
            }
            s.setForwardImpulse(in.forward());
            s.setLeftImpulse(in.left());
            s.setUpImpulse(in.up());
            s.setSprintOn(in.sprint());
            return true;
        } catch (LinkageError | RuntimeException e) {
            return false;
        }
    }

    /** Gives the construct this tick's command (and the control hook, when it has none yet). */
    public static boolean drive(ServerLevel level, long id, com.mrgregles.bsp_mobile.construct.Drive.Command cmd) {
        if (!isPresent()) {
            return false;
        }
        try {
            LoadedServerShip ship = loadedShip(level.getServer(), id);
            if (ship == null) {
                COMMANDS.remove(id);
                return false;
            }
            COMMANDS.put(id, cmd);
            return true;
        } catch (LinkageError | RuntimeException e) {
            BSPMobile.LOGGER.error("BSP-Mobile: Valkyrien Skies refused the drive command for construct {}", id, e);
            return false;
        }
    }

    /** Stops driving the construct: no more forces from this mod (the engine's own physics go on). */
    public static void stopDriving(long id) {
        COMMANDS.remove(id);
        STATS.remove(id);
    }

    /**
     * Gives the construct its wings (D52): one wing per position, lift and drag power in the engine's units; positions
     * no longer in the list lose their wing. The engine's aerodynamics do the rest. Needs the construct loaded.
     */
    public static boolean setWings(ServerLevel level, long id, java.util.Collection<BlockPos> wings, java.util.Collection<BlockPos> gone, double lift, double drag) {
        if (!isPresent()) {
            return false;
        }
        try {
            LoadedServerShip ship = loadedShip(level.getServer(), id);
            if (ship == null) {
                return false;
            }
            org.valkyrienskies.core.api.ships.WingManager wm = ship.getWingManager();
            int group = wm.getFirstWingGroupId();
            if (group < 0) {
                group = wm.createWingGroup(true);
            }
            org.valkyrienskies.core.api.ships.Wing wing = new org.valkyrienskies.core.api.ships.Wing(new Vector3d(0, 1, 0), lift, drag, null, 0.0);
            org.valkyrienskies.core.api.ships.Wing none = new org.valkyrienskies.core.api.ships.Wing(new Vector3d(0, 1, 0), 0.0, 0.0, null, 0.0);
            for (BlockPos p : gone) {
                wm.setWing(group, p.getX(), p.getY(), p.getZ(), none);
            }
            for (BlockPos p : wings) {
                wm.setWing(group, p.getX(), p.getY(), p.getZ(), wing);
            }
            return true;
        } catch (LinkageError | RuntimeException e) {
            BSPMobile.LOGGER.error("BSP-Mobile: Valkyrien Skies refused the wings of construct {}", id, e);
            return false;
        }
    }

    /** The helm's facing turned into the world, for the heading; empty when the construct is gone. */
    public static Optional<Vec3> forwardInWorld(MinecraftServer server, long id, Direction facing) {
        if (!isPresent()) {
            return Optional.empty();
        }
        try {
            ServerShip ship = serverShip(server, id);
            if (ship == null) {
                return Optional.empty();
            }
            Vector3d f = ship.getTransform().transformDirectionNoScalingFromShipToWorld(
                    new Vector3d(facing.getStepX(), facing.getStepY(), facing.getStepZ()), new Vector3d());
            return Optional.of(new Vec3(f.x, f.y, f.z));
        } catch (LinkageError | RuntimeException e) {
            return Optional.empty();
        }
    }

    public static String version() {
        return ModList.get().getModContainerById(MOD_ID)
                .map(c -> c.getModInfo().getVersion().toString()).orElse("not loaded");
    }

    /**
     * Turns the given world blocks into one new construct. The blocks move into the shipyard (the originals become
     * air) and the construct appears where they stood. Returns the construct's id, or empty when Valkyrien Skies is not
     * there or refused.
     */
    public static Optional<Long> assemble(ServerLevel level, Set<BlockPos> blocks) {
        if (!isPresent() || blocks.isEmpty()) {
            return Optional.empty();
        }
        try {
            ServerShip ship = ShipAssembler.assembleToShip(level, blocks, 1.0);
            return Optional.of(ship.getId());
        } catch (LinkageError | RuntimeException e) {
            BSPMobile.LOGGER.error("BSP-Mobile: Valkyrien Skies could not assemble {} blocks", blocks.size(), e);
            return Optional.empty();
        }
    }

    /** The ids of every engine ship whose chunks lie in this level (constructs or not). */
    public static List<Long> constructIds(ServerLevel level) {
        List<Long> out = new ArrayList<>();
        if (!isPresent()) {
            return out;
        }
        try {
            String dim = ValkyrienSkies.getDimensionId(level);
            for (ServerShip ship : ValkyrienSkies.getShipWorld(level.getServer()).getAllShips()) {
                if (dim.equals(ship.getChunkClaimDimension())) {
                    out.add(ship.getId());
                }
            }
        } catch (LinkageError | RuntimeException e) {
            BSPMobile.LOGGER.error("BSP-Mobile: Valkyrien Skies could not list its ships", e);
        }
        return out;
    }

    /** The construct with this id, if it still exists. */
    public static Optional<Construct> construct(MinecraftServer server, long id) {
        if (!isPresent() || id == 0) {
            return Optional.empty();
        }
        try {
            ServerShip ship = serverShip(server, id);
            return ship == null ? Optional.empty() : Optional.of(describe(ship));
        } catch (LinkageError | RuntimeException e) {
            BSPMobile.LOGGER.error("BSP-Mobile: Valkyrien Skies lookup of construct {} failed", id, e);
            return Optional.empty();
        }
    }

    /** The construct whose shipyard holds this block, if the block is part of one. Works on the client too. */
    public static Optional<Construct> constructAt(Level level, BlockPos pos) {
        if (!isPresent()) {
            return Optional.empty();
        }
        try {
            Ship ship = ValkyrienSkies.getShipManagingBlock(level, pos);
            return ship == null ? Optional.empty() : Optional.of(describe(ship));
        } catch (LinkageError | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** True when the block position lies in the shipyard (so it belongs to a construct, not to the world). */
    public static boolean isInShipyard(Level level, BlockPos pos) {
        if (!isPresent()) {
            return false;
        }
        try {
            return ValkyrienSkies.isBlockInShipyard(level, pos);
        } catch (LinkageError | RuntimeException e) {
            return false;
        }
    }

    /** A position that may lie in a construct's shipyard, turned into the place it is drawn in the world. */
    public static Vec3 toWorld(Level level, Vec3 pos) {
        if (!isPresent()) {
            return pos;
        }
        try {
            return ValkyrienSkies.positionToWorld(level, pos);
        } catch (LinkageError | RuntimeException e) {
            return pos;
        }
    }

    /** Parks a construct (no physics: it stands where it is) or lets it go again. */
    public static boolean setParked(ServerLevel level, long id, boolean parked) {
        if (!isPresent()) {
            return false;
        }
        try {
            ServerShip ship = serverShip(level.getServer(), id);
            if (ship == null) {
                return false;
            }
            ship.setStatic(parked);
            return true;
        } catch (LinkageError | RuntimeException e) {
            BSPMobile.LOGGER.error("BSP-Mobile: Valkyrien Skies could not park construct {}", id, e);
            return false;
        }
    }

    /**
     * Removes a construct. With {@code removeBlocks} its blocks in the shipyard are cleared too (nothing is dropped);
     * without it they stay in the shipyard as plain world blocks. Returns false when Valkyrien Skies refused.
     */
    public static boolean remove(ServerLevel level, long id, boolean removeBlocks) {
        BUSY.add(id);
        try {
            return removeNow(level, id, removeBlocks);
        } finally {
            BUSY.remove(id);
        }
    }

    private static boolean removeNow(ServerLevel level, long id, boolean removeBlocks) {
        if (!isPresent()) {
            return false;
        }
        try {
            ServerShip ship = serverShip(level.getServer(), id);
            if (ship == null) {
                return false;
            }
            ShipAssembler.INSTANCE.deleteShip(level, ship, removeBlocks, false);
            return true;
        } catch (LinkageError | RuntimeException e) {
            BSPMobile.LOGGER.error("BSP-Mobile: Valkyrien Skies could not remove construct {}", id, e);
            return false;
        }
    }

    /**
     * Puts a construct's blocks back into the world where it stands, turned to the nearest 90 degrees (D19), block
     * entities and their contents with them, and removes the construct. Nothing moves when a target cell is not free.
     */
    public static Release release(ServerLevel level, long id) {
        BUSY.add(id);
        try {
            return releaseNow(level, id);
        } finally {
            BUSY.remove(id);
        }
    }

    private static Release releaseNow(ServerLevel level, long id) {
        if (!isPresent()) {
            return Release.failed("engine");
        }
        try {
            ServerShip ship = serverShip(level.getServer(), id);
            if (ship == null) {
                return Release.failed("gone");
            }
            AABBic box = ship.getShipAABB();
            if (box == null) {
                ShipAssembler.INSTANCE.deleteShip(level, ship, false, false);
                return new Release(true, "", Map.of());
            }
            ShipTransform t = ship.getTransform();
            Rotation rotation = snapped(t.getShipToWorldRotation());
            Matrix4d m = new Matrix4d().translate(t.getPositionInWorld()).rotate(quaternion(rotation)).translate(new Vector3d(t.getPositionInShip()).negate());
            // pass 1: every block of the construct and where it goes
            List<BlockPos> froms = new ArrayList<>();
            Map<BlockPos, BlockPos> moved = new HashMap<>();
            for (int x = box.minX(); x < box.maxX(); x++) {
                for (int y = box.minY(); y < box.maxY(); y++) {
                    for (int z = box.minZ(); z < box.maxZ(); z++) {
                        BlockPos from = new BlockPos(x, y, z);
                        if (level.getBlockState(from).isAir()) {
                            continue;
                        }
                        Vector3d w = m.transformPosition(new Vector3d(x + 0.5, y + 0.5, z + 0.5));
                        BlockPos to = BlockPos.containing(w.x, w.y, w.z);
                        if (to.getY() < level.getMinBuildHeight() || to.getY() >= level.getMaxBuildHeight()) {
                            return Release.failed("height");
                        }
                        froms.add(from);
                        moved.put(from, to);
                    }
                }
            }
            for (BlockPos to : moved.values()) {
                BlockState there = level.getBlockState(to);
                if (!there.isAir() && !there.canBeReplaced()) {
                    return Release.failed("blocked");
                }
            }
            // pass 2: lift every block out of the shipyard with its block entity's data
            List<BlockState> states = new ArrayList<>(froms.size());
            List<CompoundTag> tags = new ArrayList<>(froms.size());
            for (BlockPos from : froms) {
                states.add(level.getBlockState(from));
                BlockEntity be = level.getBlockEntity(from);
                tags.add(be == null ? null : be.saveWithoutMetadata());
            }
            for (BlockPos from : froms) {
                level.removeBlockEntity(from);
                level.setBlock(from, Blocks.AIR.defaultBlockState(), 2 | 16);
            }
            // pass 3: set them down in the world, turned, then let their neighbours know
            for (int i = 0; i < froms.size(); i++) {
                BlockPos to = moved.get(froms.get(i));
                BlockState state = states.get(i).rotate(level, to, rotation);
                level.setBlock(to, state, 2 | 16);
                CompoundTag tag = tags.get(i);
                if (tag != null) {
                    BlockEntity be = level.getBlockEntity(to);
                    if (be != null) {
                        be.load(tag);
                        be.setChanged();
                    }
                }
            }
            for (BlockPos to : moved.values()) {
                level.blockUpdated(to, level.getBlockState(to).getBlock());
            }
            ShipAssembler.INSTANCE.deleteShip(level, ship, false, false);
            return new Release(true, "", moved);
        } catch (LinkageError | RuntimeException e) {
            BSPMobile.LOGGER.error("BSP-Mobile: Valkyrien Skies could not release construct {}", id, e);
            return Release.failed("engine");
        }
    }

    /** The world rotation nearest to the ship's: which way the ship's +x axis points now. */
    static Rotation snapped(Quaterniondc q) {
        Vector3d fwd = q.transform(new Vector3d(1, 0, 0));
        Rotation best = Rotation.NONE;
        double bestDot = -2;
        for (Rotation r : Rotation.values()) {
            Vector3d axis = quaternion(r).transform(new Vector3d(1, 0, 0));
            double dot = axis.dot(fwd);
            if (dot > bestDot) {
                bestDot = dot;
                best = r;
            }
        }
        return best;
    }

    /** Minecraft's rotation as a turn about +y: CLOCKWISE_90 takes +x to +z, as {@link Rotation#rotate} takes north to east. */
    static Quaterniond quaternion(Rotation r) {
        double angle = switch (r) {
            case NONE -> 0;
            case CLOCKWISE_90 -> -Math.PI / 2;
            case CLOCKWISE_180 -> Math.PI;
            case COUNTERCLOCKWISE_90 -> Math.PI / 2;
        };
        return new Quaterniond().rotateY(angle);
    }

    @Nullable
    private static ServerShip serverShip(MinecraftServer server, long id) {
        ServerShipWorld world = ValkyrienSkies.getShipWorld(server);
        return world == null ? null : world.getAllShips().getById(id);
    }

    private static Construct describe(Ship ship) {
        Vector3dc p = ship.getTransform().getPositionInWorld();
        @Nullable AABBic box = ship.getShipAABB();
        BlockPos min = box == null ? null : new BlockPos(box.minX(), box.minY(), box.minZ());
        BlockPos max = box == null ? null : new BlockPos(box.maxX(), box.maxY(), box.maxZ());
        double mass = ship instanceof ServerShip s ? s.getInertiaData().getMass() : 0;
        boolean parked = ship instanceof ServerShip s && s.isStatic();
        Vector3dc v = ship.getVelocity();
        return new Construct(ship.getId(), new Vec3(p.x(), p.y(), p.z()), min, max, mass, v.length(), parked);
    }
}
