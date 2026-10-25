package com.mrgregles.bsp_mobile.compat;

import com.mrgregles.bsp_mobile.BSPMobile;
import net.minecraft.core.BlockPos;
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
import org.valkyrienskies.core.api.ships.ServerShip;
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
