package com.mrgregles.bsp_mobile.construct;

import com.mrgregles.bsp_mobile.BSPMobile;
import com.mrgregles.bsp_mobile.MobileConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The gather (D3): from the core, every block touching the structure, through faces, edges and corners (the 3 x 3 x 3
 * neighbourhood), up to the allowance. Air and liquids are not blocks of the construct and are not crossed. A block the
 * core refuses (R5) is reported with its reason and not crossed either: bedrock and anything else that cannot be mined,
 * another Mobile Core, the blocks in the tag {@code bsp_mobile:never_mobile} (BSP-Core's admin blocks) or the config's
 * list, and the blocks Valkyrien Skies lists as unmovable. Works the same in the world and in a construct's shipyard.
 * No client classes here, so the GameTests can call it.
 */
public final class Gather {
    public static final TagKey<Block> NEVER_MOBILE = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(BSPMobile.MODID, "never_mobile"));
    public static final TagKey<Block> ENGINE_BLACKLIST = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("valkyrienskies", "assemble_blacklist"));
    private static final int[][] NEIGHBOURS = neighbours();

    public enum Reason { CANNOT_MOVE, ANOTHER_CORE, ADMIN_BLOCK, ENGINE_REFUSES }

    public record Refusal(BlockPos pos, ResourceLocation block, Reason reason) {}

    /**
     * @param blocks   the blocks the core takes, the core among them
     * @param kinds    block id to count, most first
     * @param refused  the blocks touching the structure that the core refuses
     * @param tooMany  true when more than {@code allowance} blocks touch the core (then {@code blocks} holds the first allowance + 1)
     */
    public record Result(BlockPos core, Set<BlockPos> blocks, Map<ResourceLocation, Integer> kinds, List<Refusal> refused, boolean tooMany, int allowance) {
        public int count() {
            return blocks.size();
        }

        public boolean ok() {
            return !tooMany && !blocks.isEmpty();
        }
    }

    private Gather() {}

    public static Result gather(Level level, BlockPos core, int allowance) {
        Set<BlockPos> blocks = new HashSet<>();
        Map<ResourceLocation, Integer> counts = new LinkedHashMap<>();
        List<Refusal> refused = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        List<? extends String> configured = configuredList();
        boolean tooMany = false;
        queue.add(core);
        seen.add(core);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.getBlock() instanceof LiquidBlock) {
                continue;
            }
            Reason reason = refuse(level, pos, state, core, configured);
            if (reason != null) {
                refused.add(new Refusal(pos, id(state), reason));
                continue;
            }
            blocks.add(pos);
            counts.merge(id(state), 1, Integer::sum);
            if (blocks.size() > allowance) {
                tooMany = true;
                break;
            }
            for (int[] d : NEIGHBOURS) {
                BlockPos next = pos.offset(d[0], d[1], d[2]);
                if (seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        Map<ResourceLocation, Integer> kinds = new LinkedHashMap<>();
        counts.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).forEach(e -> kinds.put(e.getKey(), e.getValue()));
        return new Result(core, blocks, kinds, refused, tooMany, allowance);
    }

    /** Why the core refuses this block, or null when it takes it. */
    public static Reason refuse(Level level, BlockPos pos, BlockState state, BlockPos core, List<? extends String> configured) {
        if (!pos.equals(core) && state.getBlock() instanceof MobileCoreBlock) {
            return Reason.ANOTHER_CORE;
        }
        // the admin tag first: an admin block that is also unbreakable is reported as what it is
        if (state.is(NEVER_MOBILE) || configured.contains(id(state).toString())) {
            return Reason.ADMIN_BLOCK;
        }
        if (state.getDestroySpeed(level, pos) < 0) {
            return Reason.CANNOT_MOVE;
        }
        if (state.is(ENGINE_BLACKLIST)) {
            return Reason.ENGINE_REFUSES;
        }
        return null;
    }

    public static ResourceLocation id(BlockState state) {
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return key == null ? ResourceLocation.fromNamespaceAndPath("minecraft", "air") : key;
    }

    private static List<? extends String> configuredList() {
        try {
            return MobileConfig.SPEC.isLoaded() ? MobileConfig.NEVER_MOBILE.get() : List.of();
        } catch (IllegalStateException e) {
            return List.of();
        }
    }

    private static int[][] neighbours() {
        int[][] out = new int[26][];
        int i = 0;
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x != 0 || y != 0 || z != 0) {
                        out[i++] = new int[]{x, y, z};
                    }
                }
            }
        }
        return out;
    }
}
