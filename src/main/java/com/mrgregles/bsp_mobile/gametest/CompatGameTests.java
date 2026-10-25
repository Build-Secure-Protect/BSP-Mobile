package com.mrgregles.bsp_mobile.gametest;

import com.mrgregles.bsp_mobile.BSPMobile;
import com.mrgregles.bsp_mobile.compat.ValkyrienSkiesBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Canary beside the BSP build of Valkyrien Skies. The test server runs Canary at its defaults, the pairing that stopped
 * the pack on world load with upstream Valkyrien Skies 2.4.11 (Canary replaces {@code PoiManager.getInRange}; the
 * upstream mixin wrapped a call inside it and was refused). The BSP build overwrites that method above Canary's
 * priority instead. So: the server is up at all; both mods' patches are on the class; the ordinary search still works;
 * and a bed aboard a construct is found from a world position, the feature the patch keeps.
 */
@GameTestHolder(BSPMobile.MODID)
@PrefixGameTestTemplate(false)
public class CompatGameTests {
    private static final String EMPTY = SetupGameTests.EMPTY;
    private static final String CANARY_POI = "com.abdelaziz.canary.common.world.interests.PointOfInterestStorageExtended";
    private static final String VS_OF_LEVEL = "org.valkyrienskies.mod.mixinducks.world.OfLevel";
    private static final Predicate<Holder<PoiType>> HOMES = t -> t.is(PoiTypes.HOME);

    @GameTest(template = EMPTY)
    public void canary_and_the_bsp_build_of_valkyrien_skies_both_patch_the_poi_manager(GameTestHelper h) {
        if (!ModList.get().isLoaded("canary")) {
            h.fail("canary is not loaded in the dev run");
            return;
        }
        Set<String> interfaces = Arrays.stream(PoiManager.class.getInterfaces()).map(Class::getName).collect(Collectors.toSet());
        if (!interfaces.contains(CANARY_POI)) {
            h.fail("Canary's POI mixin is not on PoiManager (is mixin.ai.poi off?): " + interfaces);
            return;
        }
        if (!interfaces.contains(VS_OF_LEVEL)) {
            h.fail("Valkyrien Skies' POI mixin is not on PoiManager: " + interfaces);
            return;
        }
        if (!ValkyrienSkiesBridge.version().startsWith("2.4.11.")) {
            h.fail("the dev run does not load the BSP build of Valkyrien Skies: " + ValkyrienSkiesBridge.version());
            return;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void a_bed_is_found_by_the_point_of_interest_search_with_canary_at_its_defaults(GameTestHelper h) {
        BlockPos foot = new BlockPos(11, 2, 11);
        BlockPos head = foot.north();
        h.setBlock(foot, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.PART, BedPart.FOOT));
        h.setBlock(head, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.PART, BedPart.HEAD));
        BlockPos headInWorld = h.absolutePos(head);
        Waits.pollThen(h, 200,
                () -> h.getLevel().getPoiManager().getInRange(HOMES, headInWorld, 8, PoiManager.Occupancy.ANY)
                        .anyMatch(r -> r.getPos().equals(headInWorld)),
                h::succeed,
                () -> h.fail("the bed's point of interest was not found within 8 blocks"));
    }

    @GameTest(template = EMPTY, timeoutTicks = 1400)
    public void a_bed_aboard_a_construct_is_found_from_the_world_with_canary_at_its_defaults(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Set<BlockPos> blocks = new HashSet<>();
        for (int x = 10; x <= 11; x++) {
            for (int z = 9; z <= 11; z++) {
                h.setBlock(new BlockPos(x, 2, z), Blocks.OAK_PLANKS);
                blocks.add(h.absolutePos(new BlockPos(x, 2, z)));
            }
        }
        BlockPos foot = new BlockPos(10, 3, 10);
        BlockPos head = foot.north();
        h.setBlock(foot, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.PART, BedPart.FOOT));
        h.setBlock(head, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.PART, BedPart.HEAD));
        blocks.add(h.absolutePos(foot));
        blocks.add(h.absolutePos(head));
        BlockPos headInWorld = h.absolutePos(head);
        Vec3 headCentre = Vec3.atCenterOf(headInWorld);

        Waits.afterTicking(h, 1, () -> {
            Optional<Long> id = ValkyrienSkiesBridge.assemble(level, blocks);
            if (id.isEmpty()) {
                h.fail("Valkyrien Skies did not assemble the blocks");
                return;
            }
            long shipId = id.get();
            Waits.pollThen(h, 600,
                    () -> blocks.stream().allMatch(p -> level.getBlockState(p).isAir())
                            && ValkyrienSkiesBridge.construct(level.getServer(), shipId).map(c -> c.shipyardMin() != null).orElse(false),
                    () -> checkAboard(h, level, shipId, headInWorld, headCentre),
                    () -> h.fail("the blocks never moved into a construct (id " + shipId + ")"));
        });
    }

    private static void checkAboard(GameTestHelper h, ServerLevel level, long shipId, BlockPos headInWorld, Vec3 headCentre) {
        // The bed now lives in the shipyard; its point of interest must still be found from where it is drawn in the world.
        Waits.pollThen(h, 200,
                () -> level.getPoiManager().getInRange(HOMES, headInWorld, 8, PoiManager.Occupancy.ANY)
                        .anyMatch(r -> aboardNear(level, r, headCentre)),
                () -> {
                    boolean removed = ValkyrienSkiesBridge.remove(level, shipId, true);
                    if (!removed) {
                        h.fail("Valkyrien Skies refused to remove the construct");
                        return;
                    }
                    h.succeed();
                },
                () -> {
                    String seen = level.getPoiManager().getInRange(HOMES, headInWorld, 8, PoiManager.Occupancy.ANY)
                            .map(r -> r.getPos() + "->" + ValkyrienSkiesBridge.toWorld(level, Vec3.atCenterOf(r.getPos())))
                            .collect(Collectors.joining(", "));
                    ValkyrienSkiesBridge.remove(level, shipId, true);
                    h.fail("no point of interest aboard the construct was found from the world near " + headCentre + "; seen: [" + seen + "]");
                });
    }

    private static boolean aboardNear(ServerLevel level, PoiRecord r, Vec3 headCentre) {
        return ValkyrienSkiesBridge.isInShipyard(level, r.getPos())
                && ValkyrienSkiesBridge.toWorld(level, Vec3.atCenterOf(r.getPos())).distanceTo(headCentre) <= 2.5;
    }
}
