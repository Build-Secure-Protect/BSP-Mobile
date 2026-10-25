package com.mrgregles.bsp_mobile.construct;

import com.mrgregles.bsp_mobile.MobileConfig;
import com.mrgregles.bsp_mobile.compat.ValkyrienSkiesBridge;
import com.mrgregles.bsp_mobile.compat.ValkyrienSkiesBridge.Construct;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Every construct on this server with its core (D59, D61): the list the admin command shows and the caps assembly
 * checks. A construct is an engine ship whose shipyard box holds a Mobile Core.
 */
public final class Constructs {
    private Constructs() {}

    /** One construct as the admins see it. */
    public record Entry(long id, ServerLevel level, MobileCoreBlockEntity core, Construct construct) {
        public int blocks() {
            return Gather.gather(level, core.getBlockPos(), Tiers.allowance(core.tier())).count();
        }
    }

    /** Every construct with a core, in every dimension. */
    public static List<Entry> all(MinecraftServer server) {
        List<Entry> out = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (long id : ValkyrienSkiesBridge.constructIds(level)) {
                CoreService.coreOf(level, id).ifPresent(core -> ValkyrienSkiesBridge.construct(server, id)
                        .ifPresent(c -> out.add(new Entry(id, level, core, c))));
            }
        }
        return out;
    }

    @Nullable
    public static Entry byId(MinecraftServer server, long id) {
        for (Entry e : all(server)) {
            if (e.id() == id) {
                return e;
            }
        }
        return null;
    }

    /** Why one more construct for {@code owner} would break a cap, or an empty string (D61). */
    public static String capRefused(MinecraftServer server, @Nullable UUID owner) {
        int perServer = MobileConfig.limitPerServer(), perPlayer = MobileConfig.limitPerPlayer();
        if (perServer <= 0 && perPlayer <= 0) {
            return "";
        }
        List<Entry> all = all(server);
        if (perServer > 0 && all.size() >= perServer) {
            return "limit_server";
        }
        if (perPlayer > 0 && owner != null && all.stream().filter(e -> owner.equals(e.core().owner())).count() >= perPlayer) {
            return "limit_player";
        }
        return "";
    }

    /** The world block a construct's core is drawn at. */
    public static BlockPos whereInWorld(Entry e) {
        return BlockPos.containing(ValkyrienSkiesBridge.toWorld(e.level(), net.minecraft.world.phys.Vec3.atCenterOf(e.core().getBlockPos())));
    }
}
