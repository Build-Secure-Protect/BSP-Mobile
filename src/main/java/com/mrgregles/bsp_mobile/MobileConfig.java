package com.mrgregles.bsp_mobile;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/** The server config: the tier ladder's allowances and prices, extra blocks a core never takes, when a construct counts as moving, the lock's steal, and the totem's caps. */
public final class MobileConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> TIER_ALLOWANCE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> TIER_PRICES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> NEVER_MOBILE;
    public static final ForgeConfigSpec.DoubleValue MOVING_SPEED;
    public static final ForgeConfigSpec.IntValue LOCK_STEAL_SECONDS, LOCK_GRACE_SECONDS;
    public static final ForgeConfigSpec.DoubleValue LOCK_STEAL_RADIUS;
    public static final ForgeConfigSpec.DoubleValue TOTEM_MAX_SPEED;
    public static final ForgeConfigSpec.IntValue TOTEM_MAX_HEIGHT;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("The Mobile Core's tiers: how many blocks a core of each tier may carry (Tier I first).").push("tiers");
        TIER_ALLOWANCE = b.comment("One number per tier, rising. A core's tier never exceeds the number of entries.")
                .defineList("allowance", List.of(200, 500, 1200, 2500, 5000), o -> o instanceof Integer i && i > 0);
        TIER_PRICES = b.comment("What each step up costs, bought at the core by whoever stands there, in BSP-Core's Shatter Coins:",
                        "one entry per step (Tier I to II first) as \"<coin tier>:<count>\", the coin tier being copper, gold, diamond,",
                        "netherite or illyrium. A missing entry makes that step unbuyable.")
                .defineList("prices", List.of("gold:6", "diamond:3", "netherite:2", "illyrium:1"), o -> o instanceof String s && s.matches("[a-z]+:[0-9]+"));
        b.pop();
        b.comment("What a core gathers.").push("gather");
        NEVER_MOBILE = b.comment("Block ids a core never takes, besides bedrock, other cores, the blocks in the tag bsp_mobile:never_mobile",
                        "and the blocks Valkyrien Skies lists as unmovable. Example: \"minecraft:spawner\".")
                .defineList("neverMobile", List.of(), o -> o instanceof String s && s.contains(":"));
        b.pop();
        b.comment("Constructs.").push("constructs");
        MOVING_SPEED = b.comment("A construct faster than this (blocks a second) counts as moving: its core's ring turns and it cannot be released.")
                .defineInRange("movingSpeed", 0.2, 0.0, 100.0);
        b.pop();
        b.comment("The Mobile Core Lock: a locked core admits its owner and their team; anyone else takes it by a timed steal at the core.").push("lock");
        LOCK_STEAL_SECONDS = b.comment("How long a steal takes, in seconds.").defineInRange("stealSeconds", 300, 1, 86400);
        LOCK_STEAL_RADIUS = b.comment("How close to the core (blocks) the thief must stay; the steal pauses while they are farther.")
                .defineInRange("stealRadius", 3.0, 1.0, 64.0);
        LOCK_GRACE_SECONDS = b.comment("How long the thief may stay away before the steal fails, in seconds.").defineInRange("graceSeconds", 30, 0, 3600);
        b.pop();
        b.comment("A construct carrying a placed Shatter Totem (enforced once constructs drive).").push("totem");
        TOTEM_MAX_SPEED = b.comment("Its top speed, blocks a second.").defineInRange("maxSpeed", 5.0, 0.0, 1000.0);
        TOTEM_MAX_HEIGHT = b.comment("How high above the ground beneath it it may go, in blocks.").defineInRange("maxHeight", 64, 0, 4096);
        b.pop();
        SPEC = b.build();
    }

    private MobileConfig() {}

    /** The moving threshold, or its default before the config is loaded. */
    public static double movingSpeed() {
        return doubleOr(MOVING_SPEED, 0.2);
    }

    public static int lockStealTicks() {
        return intOr(LOCK_STEAL_SECONDS, 300) * 20;
    }

    public static double lockStealRadius() {
        return doubleOr(LOCK_STEAL_RADIUS, 3.0);
    }

    public static int lockGraceTicks() {
        return intOr(LOCK_GRACE_SECONDS, 30) * 20;
    }

    public static double totemMaxSpeed() {
        return doubleOr(TOTEM_MAX_SPEED, 5.0);
    }

    public static int totemMaxHeight() {
        return intOr(TOTEM_MAX_HEIGHT, 64);
    }

    private static double doubleOr(ForgeConfigSpec.DoubleValue v, double dflt) {
        try {
            return SPEC.isLoaded() ? v.get() : dflt;
        } catch (IllegalStateException e) {
            return dflt;
        }
    }

    private static int intOr(ForgeConfigSpec.IntValue v, int dflt) {
        try {
            return SPEC.isLoaded() ? v.get() : dflt;
        } catch (IllegalStateException e) {
            return dflt;
        }
    }
}
