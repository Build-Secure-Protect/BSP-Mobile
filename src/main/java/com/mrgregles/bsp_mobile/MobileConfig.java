package com.mrgregles.bsp_mobile;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/** The server config: the tier ladder's allowances and prices, extra blocks a core never takes, when a construct counts as moving, the lock's steal, the totem's caps, driving, damage, where constructs may go, the parts, and the caps. */
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
    public static final ForgeConfigSpec.DoubleValue DRIVE_ACCEL, DRIVE_MAX_SPEED, DRIVE_TURN_RATE, DRIVE_DRAG, DRIVE_BUOYANCY;
    public static final ForgeConfigSpec.IntValue DRIVE_PARK_SECONDS;
    public static final ForgeConfigSpec.DoubleValue DAMAGE_IMPACT_SPEED, DAMAGE_BLOCKS_PER_SPEED;
    public static final ForgeConfigSpec.IntValue DAMAGE_MAX_BLOCKS, DAMAGE_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.BooleanValue REGIONS_CLOSED, REGIONS_CLOSED_ON_NO_TOTEM_SERVERS;
    public static final ForgeConfigSpec.IntValue LIMIT_PER_SERVER, LIMIT_PER_PLAYER;
    public static final ForgeConfigSpec.DoubleValue PARTS_ENGINE_ACCEL, PARTS_ENGINE_SPEED, PARTS_JET_ACCEL, PARTS_JET_SPEED, PARTS_FLOATER_BUOYANCY,
            PARTS_BALLOON_LIFT, PARTS_JET_LIFT, PARTS_CLIMB, PARTS_WING_LIFT, PARTS_WING_DRAG, PARTS_GRAVITY;

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
        b.comment("Driving from a Mobile Helm.").push("drive");
        DRIVE_ACCEL = b.comment("Acceleration, blocks a second each second (the thrust is the construct's mass times this; sprint doubles it).")
                .defineInRange("acceleration", 4.0, 0.0, 100.0);
        DRIVE_MAX_SPEED = b.comment("Top speed forward, blocks a second (half of it backward).").defineInRange("maxSpeed", 8.0, 0.0, 200.0);
        DRIVE_TURN_RATE = b.comment("Turn rate, degrees a second.").defineInRange("turnRate", 30.0, 0.0, 720.0);
        DRIVE_DRAG = b.comment("Drag: the fraction of the speed lost each second when coasting (0.35 halves it in about two seconds).")
                .defineInRange("drag", 0.35, 0.0, 10.0);
        DRIVE_BUOYANCY = b.comment("Buoyancy factor of a construct with a helm (1.0 is the engine's natural buoyancy, where most hulls sink).")
                .defineInRange("buoyancy", 4.0, 0.0, 100.0);
        DRIVE_PARK_SECONDS = b.comment("Seconds a construct must stand still with nobody at a helm before it parks itself.")
                .defineInRange("parkSeconds", 3, 0, 3600);
        b.pop();
        b.comment("Damage: an impact knocks blocks off a construct at the point of contact (never the core or a helm).").push("damage");
        DAMAGE_IMPACT_SPEED = b.comment("The relative speed (blocks a second) an impact must reach to knock anything off; 0 disables impact damage when maxBlocksPerImpact is 0.")
                .defineInRange("impactSpeed", 6.0, 0.0, 1000.0);
        DAMAGE_BLOCKS_PER_SPEED = b.comment("One block is knocked off for every this many blocks a second over the threshold.")
                .defineInRange("blocksPerSpeed", 2.0, 0.01, 1000.0);
        DAMAGE_MAX_BLOCKS = b.comment("The most blocks one impact knocks off (0 switches impact damage off).").defineInRange("maxBlocksPerImpact", 8, 0, 4096);
        DAMAGE_COOLDOWN_TICKS = b.comment("Ticks after an impact during which the same construct takes no further impact.").defineInRange("cooldownTicks", 20, 0, 12000);
        b.pop();
        b.comment("Where constructs may go. With BSP Region Guard the state flag mobile-constructs decides per region (/rg flag <region> mobile-constructs deny).").push("regions");
        REGIONS_CLOSED = b.comment("true: no construct may be assembled on this server at all.").define("closed", false);
        REGIONS_CLOSED_ON_NO_TOTEM_SERVERS = b.comment("true: a server BSP-Core lists as a no-totem server allows no constructs.").define("closedOnNoTotemServers", true);
        b.pop();
        b.comment("Caps, counted when a construct is assembled.").push("limits");
        LIMIT_PER_SERVER = b.comment("The most constructs this server carries at once (0: no cap).").defineInRange("perServer", 50, 0, 100000);
        LIMIT_PER_PLAYER = b.comment("The most constructs one player owns at once on this server (0: no cap).").defineInRange("perPlayer", 3, 0, 100000);
        b.pop();
        b.comment("The parts aboard a construct, counted by number anywhere in it.").push("parts");
        PARTS_ENGINE_ACCEL = b.comment("What one Mobile Engine adds to the acceleration, blocks a second each second.").defineInRange("engineAcceleration", 2.0, 0.0, 100.0);
        PARTS_ENGINE_SPEED = b.comment("What one Mobile Engine adds to the top speed, blocks a second.").defineInRange("engineSpeed", 2.0, 0.0, 200.0);
        PARTS_JET_ACCEL = b.comment("What one Mobile Jet Engine adds to the acceleration.").defineInRange("jetAcceleration", 8.0, 0.0, 100.0);
        PARTS_JET_SPEED = b.comment("What one Mobile Jet Engine adds to the top speed.").defineInRange("jetSpeed", 8.0, 0.0, 200.0);
        PARTS_FLOATER_BUOYANCY = b.comment("What one Mobile Floater adds to the buoyancy factor.").defineInRange("floaterBuoyancy", 1.0, 0.0, 100.0);
        PARTS_BALLOON_LIFT = b.comment("How many blocks of mass one Mobile Balloon carries.").defineInRange("balloonLift", 40.0, 0.0, 100000.0);
        PARTS_JET_LIFT = b.comment("How many blocks of mass one Mobile Jet Engine carries when climbing.").defineInRange("jetLift", 100.0, 0.0, 100000.0);
        PARTS_CLIMB = b.comment("How fast a construct with lift climbs or descends, blocks a second each second.").defineInRange("climb", 3.0, 0.0, 100.0);
        PARTS_WING_LIFT = b.comment("A Mobile Wing's lift power (the engine's aerodynamics).").defineInRange("wingLift", 100.0, 0.0, 100000.0);
        PARTS_WING_DRAG = b.comment("A Mobile Wing's drag power.").defineInRange("wingDrag", 20.0, 0.0, 100000.0);
        PARTS_GRAVITY = b.comment("The engine's gravity, blocks a second each second, which lift works against.").defineInRange("gravity", 10.0, 0.0, 100.0);
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

    public static double driveAccel() {
        return doubleOr(DRIVE_ACCEL, 4.0);
    }

    public static double driveMaxSpeed() {
        return doubleOr(DRIVE_MAX_SPEED, 8.0);
    }

    public static double driveTurnRate() {
        return doubleOr(DRIVE_TURN_RATE, 30.0);
    }

    public static double driveDrag() {
        return doubleOr(DRIVE_DRAG, 0.35);
    }

    public static double driveBuoyancy() {
        return doubleOr(DRIVE_BUOYANCY, 4.0);
    }

    public static int driveParkTicks() {
        return intOr(DRIVE_PARK_SECONDS, 3) * 20;
    }

    public static double damageImpactSpeed() {
        return doubleOr(DAMAGE_IMPACT_SPEED, 6.0);
    }

    public static double damageBlocksPerSpeed() {
        return doubleOr(DAMAGE_BLOCKS_PER_SPEED, 2.0);
    }

    public static int damageMaxBlocks() {
        return intOr(DAMAGE_MAX_BLOCKS, 8);
    }

    public static int damageCooldownTicks() {
        return intOr(DAMAGE_COOLDOWN_TICKS, 20);
    }

    public static boolean regionsClosed() {
        return boolOr(REGIONS_CLOSED, false);
    }

    public static boolean regionsClosedOnNoTotemServers() {
        return boolOr(REGIONS_CLOSED_ON_NO_TOTEM_SERVERS, true);
    }

    public static int limitPerServer() { return intOr(LIMIT_PER_SERVER, 50); }
    public static int limitPerPlayer() { return intOr(LIMIT_PER_PLAYER, 3); }

    public static double partsEngineAccel() { return doubleOr(PARTS_ENGINE_ACCEL, 2.0); }
    public static double partsEngineSpeed() { return doubleOr(PARTS_ENGINE_SPEED, 2.0); }
    public static double partsJetAccel() { return doubleOr(PARTS_JET_ACCEL, 8.0); }
    public static double partsJetSpeed() { return doubleOr(PARTS_JET_SPEED, 8.0); }
    public static double partsFloaterBuoyancy() { return doubleOr(PARTS_FLOATER_BUOYANCY, 1.0); }
    public static double partsBalloonLift() { return doubleOr(PARTS_BALLOON_LIFT, 40.0); }
    public static double partsJetLift() { return doubleOr(PARTS_JET_LIFT, 100.0); }
    public static double partsClimb() { return doubleOr(PARTS_CLIMB, 3.0); }
    public static double partsWingLift() { return doubleOr(PARTS_WING_LIFT, 100.0); }
    public static double partsWingDrag() { return doubleOr(PARTS_WING_DRAG, 20.0); }
    public static double partsGravity() { return doubleOr(PARTS_GRAVITY, 10.0); }

    private static boolean boolOr(ForgeConfigSpec.BooleanValue v, boolean dflt) {
        try {
            return SPEC.isLoaded() ? v.get() : dflt;
        } catch (IllegalStateException e) {
            return dflt;
        }
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
