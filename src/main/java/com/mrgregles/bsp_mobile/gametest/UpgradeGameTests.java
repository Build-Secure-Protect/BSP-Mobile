package com.mrgregles.bsp_mobile.gametest;

import com.mrgregles.bsp_mobile.BSPMobile;
import com.mrgregles.bsp_mobile.compat.BspCore;
import com.mrgregles.bsp_mobile.construct.CoreService;
import com.mrgregles.bsp_mobile.construct.MobileCoreBlockEntity;
import com.mrgregles.bsp_mobile.construct.Prices;
import com.mrgregles.bsp_mobile.construct.Tiers;
import com.mrgregles.bsp_mobile.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.Optional;

/** MB-2: buying a tier (the price taken with change, a refusal when short, the top tier) and the recipes of the parts and the core. */
@GameTestHolder(BSPMobile.MODID)
@PrefixGameTestTemplate(false)
public class UpgradeGameTests {

    private static MobileCoreBlockEntity core(GameTestHelper h, BlockPos rel) {
        h.setBlock(rel, ModBlocks.MOBILE_CORE.get());
        return (MobileCoreBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(rel));
    }

    private static ServerPlayer buyer(GameTestHelper h, int worth) {
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        p.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(10.5, 2, 10.5)));
        if (worth > 0) {
            BspCore.giveCoins(p, worth);
        }
        return p;
    }

    private static boolean coinsOrFail(GameTestHelper h) {
        if (!BspCore.coinsAvailable()) {
            h.fail("BSP-Core's coins are not available on the test server");
            return false;
        }
        return true;
    }

    @GameTest(template = SetupGameTests.EMPTY)
    public void buying_a_tier_takes_the_price_with_change(GameTestHelper h) {
        if (!coinsOrFail(h)) {
            return;
        }
        BlockPos rel = new BlockPos(10, 2, 10);
        MobileCoreBlockEntity be = core(h, rel);
        Prices.Coins price = Prices.of(2);
        if (price.isNone() || price.value() <= 0) {
            h.fail("no price for Tier II: " + price);
            return;
        }
        int copper = BspCore.coinValue("copper");
        // one copper more than the price, given as the largest coins: paying needs change
        ServerPlayer p = buyer(h, price.value() + copper);
        int before = BspCore.coinWorth(p);
        if (before != price.value() + copper) {
            h.fail("the buyer should carry " + (price.value() + copper) + " copper units, carries " + before);
            return;
        }
        CoreService.Outcome out = CoreService.upgrade(h.getLevel(), h.absolutePos(rel), p);
        if (!out.ok()) {
            h.fail("the upgrade was refused: " + out.reason());
            return;
        }
        if (be.tier() != 2) {
            h.fail("the core should be Tier II, is " + be.tier());
            return;
        }
        int after = BspCore.coinWorth(p);
        if (after != copper) {
            h.fail("the buyer should have " + copper + " copper units of change, has " + after);
            return;
        }
        h.succeed();
    }

    @GameTest(template = SetupGameTests.EMPTY)
    public void buying_is_refused_when_the_buyer_is_short(GameTestHelper h) {
        if (!coinsOrFail(h)) {
            return;
        }
        BlockPos rel = new BlockPos(10, 2, 10);
        MobileCoreBlockEntity be = core(h, rel);
        Prices.Coins price = Prices.of(2);
        ServerPlayer p = buyer(h, price.value() - 1);
        int before = BspCore.coinWorth(p);
        CoreService.Outcome out = CoreService.upgrade(h.getLevel(), h.absolutePos(rel), p);
        if (out.ok() || !out.reason().equals("short")) {
            h.fail("a short buyer must be refused with 'short': " + out);
            return;
        }
        if (be.tier() != 1 || BspCore.coinWorth(p) != before) {
            h.fail("nothing may change on a refusal: tier " + be.tier() + ", worth " + BspCore.coinWorth(p) + " of " + before);
            return;
        }
        h.succeed();
    }

    @GameTest(template = SetupGameTests.EMPTY)
    public void the_top_tier_cannot_be_bought(GameTestHelper h) {
        if (!coinsOrFail(h)) {
            return;
        }
        BlockPos rel = new BlockPos(10, 2, 10);
        MobileCoreBlockEntity be = core(h, rel);
        be.setTier(Tiers.count());
        ServerPlayer p = buyer(h, 1000);
        int before = BspCore.coinWorth(p);
        CoreService.Outcome out = CoreService.upgrade(h.getLevel(), h.absolutePos(rel), p);
        if (out.ok() || !out.reason().equals("top")) {
            h.fail("the top tier must be refused with 'top': " + out);
            return;
        }
        if (be.tier() != Tiers.count() || BspCore.coinWorth(p) != before) {
            h.fail("nothing may change at the top: tier " + be.tier() + ", worth " + BspCore.coinWorth(p) + " of " + before);
            return;
        }
        h.succeed();
    }

    @GameTest(template = SetupGameTests.EMPTY)
    public void the_recipes_make_the_parts_and_the_core(GameTestHelper h) {
        // name -> expected ingredient counts (D24)
        Map<String, Map<String, Integer>> expected = Map.of(
                "mobile_core_processor", Map.of("bsp_core:tetrium_ingot", 4, "minecraft:redstone", 4, "minecraft:diamond", 1),
                "mobile_core_gps", Map.of("minecraft:glass_pane", 4, "bsp_core:tetrium_ingot", 4, "minecraft:compass", 1),
                "mobile_core_gyroscope", Map.of("minecraft:iron_bars", 4, "bsp_core:magnatite_ingot", 4, "minecraft:gold_ingot", 1),
                "mobile_core", Map.of("minecraft:iron_bars", 4, "bsp_mobile:mobile_core_gps", 1, "bsp_mobile:mobile_core_processor", 1,
                        "bsp_mobile:mobile_core_gyroscope", 1, "bsp_core:tetrium_ingot", 1, "minecraft:amethyst_block", 1));
        for (var e : expected.entrySet()) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(BSPMobile.MODID, e.getKey());
            Optional<? extends Recipe<?>> recipe = h.getLevel().getRecipeManager().byKey(id);
            if (recipe.isEmpty()) {
                h.fail("no recipe " + id);
                return;
            }
            ItemStack result = recipe.get().getResultItem(h.getLevel().registryAccess());
            if (!ForgeRegistries.ITEMS.getKey(result.getItem()).equals(id) || result.getCount() != 1) {
                h.fail(id + " should make one " + id + ", makes " + result);
                return;
            }
            int filled = 0;
            for (Ingredient ing : recipe.get().getIngredients()) {
                if (!ing.isEmpty()) {
                    filled++;
                }
            }
            if (filled != 9) {
                h.fail(id + " should fill all nine slots, fills " + filled);
                return;
            }
            for (var want : e.getValue().entrySet()) {
                var item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(want.getKey()));
                if (item == null || item == Items.AIR) {
                    h.fail(id + ": the ingredient " + want.getKey() + " is not registered");
                    return;
                }
                int n = 0;
                for (Ingredient ing : recipe.get().getIngredients()) {
                    if (ing.test(new ItemStack(item))) {
                        n++;
                    }
                }
                if (n != want.getValue()) {
                    h.fail(id + " should take " + want.getValue() + " x " + want.getKey() + ", takes " + n);
                    return;
                }
            }
        }
        h.succeed();
    }
}
