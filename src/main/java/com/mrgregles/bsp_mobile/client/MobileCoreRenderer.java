package com.mrgregles.bsp_mobile.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mrgregles.bsp_mobile.compat.ValkyrienSkiesBridge;
import com.mrgregles.bsp_mobile.construct.MobileCoreBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The core's ring (R1): a tilted band inside the cage, drawn full-bright in the state's colour: grey and still when not
 * assembled, teal and still while parked, gold and turning (one turn every two seconds) while the construct moves. The
 * state is read from the engine on the client: a core in a construct's shipyard belongs to that construct, and the
 * construct's speed says whether it moves.
 */
public class MobileCoreRenderer implements BlockEntityRenderer<MobileCoreBlockEntity> {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/white.png");
    private static final int GREY = 0x4A5160, TEAL = 0x19D3B0, GOLD = 0xFFD23A;
    /** The client's copy of the moving threshold (the server's is in its config). */
    private static final double MOVING_SPEED = 0.2;
    private static final int SEGMENTS = 24;
    private static final float R_IN = 0.29f, R_OUT = 0.37f, HALF_H = 0.025f;

    public MobileCoreRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(MobileCoreBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        var construct = ValkyrienSkiesBridge.constructAt(level, be.getBlockPos());
        boolean assembled = construct.isPresent();
        boolean moving = assembled && construct.get().speed() > MOVING_SPEED;
        int colour = !assembled ? GREY : moving ? GOLD : TEAL;
        float spin = moving ? (float) ((level.getGameTime() + partialTick) * (2 * Math.PI / 40.0)) : 0f;
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotation(0.63f + spin));
        pose.mulPose(Axis.XP.rotation(1.05f));
        VertexConsumer vc = buffers.getBuffer(RenderType.beaconBeam(WHITE, true));
        ring(pose, vc, colour);
        pose.popPose();
    }

    /** A flat band of {@link #SEGMENTS} pieces: top, bottom, inner and outer faces, both windings so it shows from every side. */
    private static void ring(PoseStack pose, VertexConsumer vc, int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = i * 2 * Math.PI / SEGMENTS, a1 = (i + 1) * 2 * Math.PI / SEGMENTS;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float[] io0 = {R_IN * c0, R_IN * s0}, oo0 = {R_OUT * c0, R_OUT * s0}, io1 = {R_IN * c1, R_IN * s1}, oo1 = {R_OUT * c1, R_OUT * s1};
            // top and bottom
            quad(vc, m, n, r, g, b, io0[0], HALF_H, io0[1], oo0[0], HALF_H, oo0[1], oo1[0], HALF_H, oo1[1], io1[0], HALF_H, io1[1]);
            quad(vc, m, n, r, g, b, io0[0], -HALF_H, io0[1], io1[0], -HALF_H, io1[1], oo1[0], -HALF_H, oo1[1], oo0[0], -HALF_H, oo0[1]);
            // outer and inner walls
            quad(vc, m, n, r, g, b, oo0[0], -HALF_H, oo0[1], oo1[0], -HALF_H, oo1[1], oo1[0], HALF_H, oo1[1], oo0[0], HALF_H, oo0[1]);
            quad(vc, m, n, r, g, b, io0[0], -HALF_H, io0[1], io0[0], HALF_H, io0[1], io1[0], HALF_H, io1[1], io1[0], -HALF_H, io1[1]);
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, int r, int g, int b,
                             float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3) {
        for (int pass = 0; pass < 2; pass++) {
            float[][] p = pass == 0 ? new float[][]{{x0, y0, z0}, {x1, y1, z1}, {x2, y2, z2}, {x3, y3, z3}}
                    : new float[][]{{x3, y3, z3}, {x2, y2, z2}, {x1, y1, z1}, {x0, y0, z0}};
            for (float[] v : p) {
                vc.vertex(m, v[0], v[1], v[2]).color(r, g, b, 255).uv(0, 0).uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 1, 0).endVertex();
            }
        }
    }
}
