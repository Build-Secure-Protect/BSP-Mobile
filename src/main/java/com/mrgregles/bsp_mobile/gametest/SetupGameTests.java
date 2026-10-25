package com.mrgregles.bsp_mobile.gametest;

import com.mrgregles.bsp_mobile.BSPMobile;
import com.mrgregles.bsp_mobile.compat.ValkyrienSkiesBridge;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Proves the GameTest wiring: the mod is loaded in the test server with Valkyrien Skies and BSP-Core beside it (as dev
 * runs load them), and a mock player can be made. Every server-side rule added later gets its own test class next to
 * this one.
 */
@GameTestHolder(BSPMobile.MODID)
@PrefixGameTestTemplate(false)
public class SetupGameTests {
    static final String EMPTY = "empty";

    @GameTest(template = EMPTY)
    public void mod_is_loaded_beside_valkyrien_skies_and_bsp_core_and_a_mock_player_can_be_made(GameTestHelper h) {
        if (!ModList.get().isLoaded(BSPMobile.MODID)) {
            h.fail("bsp_mobile is not loaded");
            return;
        }
        if (!ValkyrienSkiesBridge.isPresent()) {
            h.fail("valkyrienskies is not loaded in the dev run");
            return;
        }
        if (!ModList.get().isLoaded("bsp_core")) {
            h.fail("bsp_core is not loaded in the dev run");
            return;
        }
        ServerPlayer player = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        if (player.getGameProfile().getName().isEmpty()) {
            h.fail("mock player has no name");
            return;
        }
        h.succeed();
    }
}
