package com.mrgregles.bsp_mobile.construct;

import com.mrgregles.bsp_mobile.MobileConfig;

/**
 * The limits a construct obeys because of what it carries (D2, D34): with a placed Shatter Totem aboard it is slowed
 * and height-capped. Stored here, client-free, for the driving task to enforce on the physics thread; the screen only
 * says that the caps apply. {@code maxSpeed} in blocks a second and {@code maxHeight} in blocks above the ground
 * beneath; {@code Double.MAX_VALUE} / {@code Integer.MAX_VALUE} when uncapped.
 */
public record ConstructRules(boolean capped, double maxSpeed, int maxHeight) {
    public static final ConstructRules FREE = new ConstructRules(false, Double.MAX_VALUE, Integer.MAX_VALUE);

    /** The rules for a construct carrying {@code totemsAboard} placed totems. */
    public static ConstructRules of(int totemsAboard) {
        return totemsAboard > 0 ? new ConstructRules(true, MobileConfig.totemMaxSpeed(), MobileConfig.totemMaxHeight()) : FREE;
    }
}
