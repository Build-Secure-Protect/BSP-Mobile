package com.mrgregles.bsp_mobile.construct;

import com.mrgregles.bsp_mobile.MobileConfig;

import java.util.List;

/** The tier ladder: tier 1 is the first entry of the config's allowance list. */
public final class Tiers {
    private static final List<Integer> DEFAULTS = List.of(200, 500, 1200, 2500, 5000);

    private Tiers() {}

    private static List<? extends Integer> ladder() {
        try {
            if (MobileConfig.SPEC.isLoaded()) {
                List<? extends Integer> l = MobileConfig.TIER_ALLOWANCE.get();
                if (!l.isEmpty()) {
                    return l;
                }
            }
        } catch (IllegalStateException ignored) {
            // the config is not loaded yet
        }
        return DEFAULTS;
    }

    public static int count() {
        return ladder().size();
    }

    public static int clamp(int tier) {
        return Math.max(1, Math.min(count(), tier));
    }

    public static int allowance(int tier) {
        return ladder().get(clamp(tier) - 1);
    }
}
