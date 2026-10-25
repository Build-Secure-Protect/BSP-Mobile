package com.mrgregles.bsp_mobile.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Waiting the way the other BSP mods learnt to: never assert after a fixed delay counted from the test's start. A test
 * late in a batch can wait hundreds of ticks before its chunk ticks, and Valkyrien Skies finishes an assembly a few
 * ticks after the call, on its own schedule. So a test polls for its condition with a generous timeout.
 */
final class Waits {
    private Waits() {}

    /** Runs {@code then} once {@code ok} holds, checking every tick for up to {@code ticks} ticks; else {@code fail}. */
    static void pollThen(GameTestHelper h, int ticks, BooleanSupplier ok, Runnable then, Runnable fail) {
        if (ok.getAsBoolean()) {
            then.run();
        } else if (ticks <= 0) {
            fail.run();
        } else {
            h.runAfterDelay(1, () -> pollThen(h, ticks - 1, ok, then, fail));
        }
    }

    /** Runs {@code then} {@code ticks} ticks after the test's area has started ticking blocks and entities. */
    static void afterTicking(GameTestHelper h, int ticks, Runnable then) {
        List<BlockPos> corners = List.of(h.absolutePos(new BlockPos(0, 1, 0)), h.absolutePos(new BlockPos(23, 1, 0)),
                h.absolutePos(new BlockPos(0, 1, 23)), h.absolutePos(new BlockPos(23, 1, 23)));
        pollThen(h, 700, () -> corners.stream().allMatch(c ->
                        h.getLevel().shouldTickBlocksAt(ChunkPos.asLong(c.getX() >> 4, c.getZ() >> 4))
                                && h.getLevel().isPositionEntityTicking(c)),
                () -> h.runAfterDelay(ticks, then), () -> h.fail("the test's area never started ticking"));
    }
}
