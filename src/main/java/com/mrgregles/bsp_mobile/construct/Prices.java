package com.mrgregles.bsp_mobile.construct;

import com.mrgregles.bsp_mobile.MobileConfig;
import com.mrgregles.bsp_mobile.compat.BspCore;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What a step up the tier ladder costs (D20): the config's {@code tiers.prices}, one entry per step as
 * {@code <coin tier>:<count>}, paid in BSP-Core's Shatter Coins. Client-free, so the GameTests can read it.
 */
public final class Prices {
    private static final List<String> DEFAULTS = List.of("gold:6", "diamond:3", "netherite:2", "illyrium:1");

    /** A number of coins of one tier. {@code value} is the same in copper units (0 when BSP-Core's coins are unknown). */
    public record Coins(String tier, int count, int value) {
        public static final Coins NONE = new Coins("", 0, 0);

        public boolean isNone() {
            return count <= 0 || tier.isEmpty();
        }
    }

    private Prices() {}

    private static List<? extends String> entries() {
        try {
            if (MobileConfig.SPEC.isLoaded()) {
                return MobileConfig.TIER_PRICES.get();
            }
        } catch (IllegalStateException ignored) {
            // the config is not loaded yet
        }
        return DEFAULTS;
    }

    /** The price of reaching {@code toTier} from the tier below it, or {@link Coins#NONE} when no entry or no such tier. */
    public static Coins of(int toTier) {
        int step = toTier - 2;
        List<? extends String> list = entries();
        if (toTier < 2 || toTier > Tiers.count() || step >= list.size()) {
            return Coins.NONE;
        }
        return parse(list.get(step));
    }

    /** The price of every step, Tier I to II first; a missing or broken entry is {@link Coins#NONE}. */
    public static List<Coins> ladder() {
        List<Coins> out = new ArrayList<>();
        for (int t = 2; t <= Tiers.count(); t++) {
            out.add(of(t));
        }
        return out;
    }

    @Nullable
    static Coins parse(String entry) {
        String[] parts = entry.trim().toLowerCase(Locale.ROOT).split(":");
        if (parts.length != 2) {
            return Coins.NONE;
        }
        int count;
        try {
            count = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return Coins.NONE;
        }
        if (count <= 0 || !BspCore.coinTiers().contains(parts[0])) {
            return Coins.NONE;
        }
        return new Coins(parts[0], count, count * BspCore.coinValue(parts[0]));
    }

    /** {@code worth} copper units expressed in the fewest coins, largest tier first (for the coins-carried line). */
    public static List<Coins> breakdown(int worth) {
        List<Coins> out = new ArrayList<>();
        List<String> tiers = BspCore.coinTiers();
        int left = Math.max(0, worth);
        for (int i = tiers.size() - 1; i >= 0 && left > 0; i--) {
            int value = BspCore.coinValue(tiers.get(i));
            if (value <= 0) {
                continue;
            }
            int n = left / value;
            if (n > 0) {
                out.add(new Coins(tiers.get(i), n, n * value));
                left -= n * value;
            }
        }
        return out;
    }
}
