package com.mrgregles.bsp_mobile;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/** The server config: the tier ladder's allowances and prices, extra blocks a core never takes, and when a construct counts as moving. */
public final class MobileConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> TIER_ALLOWANCE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> TIER_PRICES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> NEVER_MOBILE;
    public static final ForgeConfigSpec.DoubleValue MOVING_SPEED;

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
        SPEC = b.build();
    }

    private MobileConfig() {}

    /** The moving threshold, or its default before the config is loaded. */
    public static double movingSpeed() {
        try {
            return SPEC.isLoaded() ? MOVING_SPEED.get() : 0.2;
        } catch (IllegalStateException e) {
            return 0.2;
        }
    }
}
