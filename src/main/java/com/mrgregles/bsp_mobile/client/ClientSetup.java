package com.mrgregles.bsp_mobile.client;

import com.mrgregles.bsp_mobile.BSPMobile;
import com.mrgregles.bsp_mobile.registry.ModBlocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only wiring: the core's ring renderer. */
@Mod.EventBusSubscriber(modid = BSPMobile.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlocks.MOBILE_CORE_ENTITY.get(), MobileCoreRenderer::new);
    }
}
