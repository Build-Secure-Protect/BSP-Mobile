package com.mrgregles.bsp_mobile.compat;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;

import java.util.List;
import java.util.UUID;

/**
 * The one class that talks to BSP-Core, through its {@code api} package only. BSP-Core is a required mod (D21), but
 * every call is still guarded: an older BSP-Core without a method costs the feature, never a crash.
 */
public final class BspCore {
    public static final String MOD_ID = "bsp_core";

    private BspCore() {}

    public static boolean isPresent() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /** True for a placed Shatter Totem. */
    public static boolean isTotem(BlockState state) {
        if (!isPresent()) {
            return false;
        }
        try {
            return Impl.isTotem(state);
        } catch (LinkageError | RuntimeException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ teams and admins (the lock, D30 and D33)

    /** True when the two players are on one BSP-Core team (a player is not their own teammate). */
    public static boolean sameTeam(MinecraftServer server, UUID a, UUID b) {
        if (!isPresent() || a == null || b == null) {
            return false;
        }
        try {
            return Impl.sameTeam(server, a, b);
        } catch (LinkageError | RuntimeException e) {
            return false;
        }
    }

    /** The player's teammates, empty without a team or BSP-Core. */
    public static List<UUID> teammates(MinecraftServer server, UUID player) {
        if (!isPresent() || player == null) {
            return List.of();
        }
        try {
            return List.copyOf(Impl.teammates(server, player));
        } catch (LinkageError | RuntimeException e) {
            return List.of();
        }
    }

    /** True for a BSP-Core admin. */
    public static boolean isAdmin(Player player) {
        if (!isPresent()) {
            return false;
        }
        try {
            return Impl.isAdmin(player);
        } catch (LinkageError | RuntimeException e) {
            return false;
        }
    }

    /** True when BSP-Core lists this server as one without totem placement (D49: such a server allows no constructs). */
    public static boolean isNoTotemServer() {
        if (!isPresent()) {
            return false;
        }
        try {
            return Impl.isNoTotemServer();
        } catch (LinkageError | RuntimeException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ Shatter Coins (the tiers' prices, D20)

    /** True when BSP-Core's coins can be read and taken. */
    public static boolean coinsAvailable() {
        if (!isPresent()) {
            return false;
        }
        try {
            return !Impl.coinTiers().isEmpty();
        } catch (LinkageError | RuntimeException e) {
            return false;
        }
    }

    /** The coin tiers, cheapest first ({@code copper, gold, diamond, netherite, illyrium}); empty without BSP-Core's coins. */
    public static List<String> coinTiers() {
        if (!isPresent()) {
            return List.of();
        }
        try {
            return List.copyOf(Impl.coinTiers());
        } catch (LinkageError | RuntimeException e) {
            return List.of();
        }
    }

    /** One coin of {@code tier} in copper units; 0 for an unknown tier or without BSP-Core's coins. */
    public static int coinValue(String tier) {
        if (!isPresent()) {
            return 0;
        }
        try {
            return Impl.coinValue(tier);
        } catch (LinkageError | RuntimeException e) {
            return 0;
        }
    }

    /** The coins the player carries, in copper units. */
    public static int coinWorth(Player player) {
        if (!isPresent()) {
            return 0;
        }
        try {
            return Impl.coinWorth(player);
        } catch (LinkageError | RuntimeException e) {
            return 0;
        }
    }

    /** Takes {@code value} copper units of coins from the player, with change; false and nothing taken when they are short. */
    public static boolean pay(Player player, int value) {
        if (!isPresent()) {
            return false;
        }
        try {
            return Impl.pay(player, value);
        } catch (LinkageError | RuntimeException e) {
            return false;
        }
    }

    /** Gives the player {@code value} copper units as coins (for tests and admin tools). */
    public static void giveCoins(Player player, int value) {
        if (!isPresent()) {
            return;
        }
        try {
            Impl.give(player, value);
        } catch (LinkageError | RuntimeException ignored) {
            // an older BSP-Core
        }
    }

    /** Holds every BSP-Core type, so nothing of it loads when BSP-Core is absent. */
    private static final class Impl {
        static boolean isTotem(BlockState state) {
            return com.mrgregles.bsp_core.api.BspTotems.isTotem(state);
        }

        static boolean sameTeam(MinecraftServer server, UUID a, UUID b) {
            return com.mrgregles.bsp_core.api.BspTeams.sameTeam(server, a, b);
        }

        static List<UUID> teammates(MinecraftServer server, UUID player) {
            return com.mrgregles.bsp_core.api.BspTeams.teammates(server, player);
        }

        static boolean isAdmin(Player player) {
            return com.mrgregles.bsp_core.api.BspPlayers.isAdmin(player);
        }

        static boolean isNoTotemServer() {
            return com.mrgregles.bsp_core.api.BspZones.isNoTotemServer();
        }

        static List<String> coinTiers() {
            return com.mrgregles.bsp_core.api.BspCoins.tiers();
        }

        static int coinValue(String tier) {
            return com.mrgregles.bsp_core.api.BspCoins.value(tier);
        }

        static int coinWorth(Player player) {
            return com.mrgregles.bsp_core.api.BspCoins.worth(player);
        }

        static boolean pay(Player player, int value) {
            return com.mrgregles.bsp_core.api.BspCoins.pay(player, value);
        }

        static void give(Player player, int value) {
            com.mrgregles.bsp_core.api.BspCoins.give(player, value);
        }
    }
}
