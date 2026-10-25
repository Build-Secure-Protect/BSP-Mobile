package com.mrgregles.bsp_mobile.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mrgregles.bsp_mobile.BSPMobile;
import com.mrgregles.bsp_mobile.compat.ValkyrienSkiesBridge;
import com.mrgregles.bsp_mobile.network.CoreOutlinePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The outline in the world (D17): while the core's screen is open with SHOW OUTLINE on, every block the core takes is
 * boxed in teal and every block it refuses in red. Shipyard positions are drawn where the construct is seen.
 */
@Mod.EventBusSubscriber(modid = BSPMobile.MODID, value = Dist.CLIENT)
public final class OutlineRenderer {
    private static final float[] TEAL = {0x19 / 255f, 0xD3 / 255f, 0xB0 / 255f}, RED = {0xFF / 255f, 0x6B / 255f, 0x5C / 255f};

    private OutlineRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        CoreOutlinePacket outline = ClientCore.outline();
        if (outline == null || mc.level == null || !(mc.screen instanceof CoreScreen screen) || !screen.outlineOn()) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        draw(pose, lines, mc.level, outline.taken(), TEAL, 0.9f);
        draw(pose, lines, mc.level, outline.refused(), RED, 1.0f);
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static void draw(PoseStack pose, VertexConsumer lines, Level level, long[] positions, float[] rgb, float alpha) {
        for (long packed : positions) {
            BlockPos p = BlockPos.of(packed);
            Vec3 lo = ValkyrienSkiesBridge.toWorld(level, new Vec3(p.getX() + 0.01, p.getY() + 0.01, p.getZ() + 0.01));
            Vec3 hi = ValkyrienSkiesBridge.toWorld(level, new Vec3(p.getX() + 0.99, p.getY() + 0.99, p.getZ() + 0.99));
            LevelRenderer.renderLineBox(pose, lines, Math.min(lo.x, hi.x), Math.min(lo.y, hi.y), Math.min(lo.z, hi.z),
                    Math.max(lo.x, hi.x), Math.max(lo.y, hi.y), Math.max(lo.z, hi.z), rgb[0], rgb[1], rgb[2], alpha);
        }
    }
}
