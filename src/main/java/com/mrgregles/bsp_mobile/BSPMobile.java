package com.mrgregles.bsp_mobile;

import com.mojang.logging.LogUtils;
import com.mrgregles.bsp_mobile.compat.ValkyrienSkiesBridge;
import com.mrgregles.bsp_mobile.construct.CoreService;
import com.mrgregles.bsp_mobile.network.MobileNetwork;
import com.mrgregles.bsp_mobile.registry.ModBlocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Entry point for BSP-Mobile: moving bases for the Build Secure Protect network. A Mobile Core turns the blocks around
 * it into a construct that drives, floats or flies, with every machine aboard still working. The moving blocks
 * themselves are Valkyrien Skies ships, reached only through {@link ValkyrienSkiesBridge}; everything else (the core,
 * the allowance, the rules aboard, damage, parts) lives in this mod's own packages and is wired up here.
 */
@Mod(BSPMobile.MODID)
public class BSPMobile {
    /** Mod id. Must match {@code mod_id} in gradle.properties and {@code modId} in mods.toml. */
    public static final String MODID = "bsp_mobile";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BSPMobile(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.SERVER, MobileConfig.SPEC);
        ModBlocks.register(context.getModEventBus());
        MobileNetwork.register();
        MinecraftForge.EVENT_BUS.addListener(CoreService::onServerTick);
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent e) -> {
            if (ValkyrienSkiesBridge.isPresent()) {
                LOGGER.info("BSP-Mobile: constructs run on Valkyrien Skies {}", ValkyrienSkiesBridge.version());
            } else {
                LOGGER.error("BSP-Mobile: Valkyrien Skies is not loaded; no construct can move");
            }
        });
    }
}
