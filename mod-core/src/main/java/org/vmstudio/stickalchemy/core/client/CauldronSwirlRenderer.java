package org.vmstudio.stickalchemy.core.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Map;

public final class CauldronSwirlRenderer {

    private static final float INNER_RADIUS = 0.12f;
    private static final float OUTER_RADIUS = 0.295f;
    private static final int BAND_SEGMENTS = 36;
    private static int renderDebugCooldown = 0;

    private CauldronSwirlRenderer() {
    }

    public static void renderSwirls(PoseStack poseStack, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || StickAlchemyLogic.STIR_VISUALS.isEmpty()) {
            return;
        }

        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        float time = (mc.level.getGameTime() + partialTicks) * 0.05f;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        for (Map.Entry<BlockPos, StickAlchemyLogic.StirVisualState> entry : StickAlchemyLogic.STIR_VISUALS.entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos.distToCenterSqr(cameraPos.x, cameraPos.y, cameraPos.z) > 144.0) {
                continue;
            }

            BlockState state = mc.level.getBlockState(pos);
            if (!state.is(Blocks.WATER_CAULDRON)) {
                continue;
            }

            if (renderDebugCooldown <= 0) {
                StickAlchemyLogic.debugSwirlChat("render " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " | energy=" + String.format("%.3f", entry.getValue().energy));
                renderDebugCooldown = 30;
            }

            renderSingleCauldron(poseStack, cameraPos, pos, state, entry.getValue(), time);
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void renderSingleCauldron(
        PoseStack poseStack,
        Vec3 cameraPos,
        BlockPos pos,
        BlockState state,
        StickAlchemyLogic.StirVisualState visual,
        float time
    ) {
        float energy = (float) Mth.clamp(visual.energy, 0.0, 1.6);
        if (energy < 0.03f) {
            return;
        }

        int tint = StickAlchemyLogic.CAULDRON_COLORS.getOrDefault(pos, 0x3F76E4);
        float red = ((tint >> 16) & 255) / 255.0f;
        float green = ((tint >> 8) & 255) / 255.0f;
        float blue = (tint & 255) / 255.0f;
        int waterLevel = state.getValue(LayeredCauldronBlock.LEVEL);
        float surfaceY = (6.0f + waterLevel * 3.0f) / 16.0f;
        float flowX = (float) visual.flowX;
        float flowZ = (float) visual.flowZ;
        float spinAngle = (float) visual.swirlAngle;
        float spinVelocity = (float) visual.spinVelocity;
        float spinSign = Math.signum(spinVelocity == 0.0f ? 1.0f : spinVelocity);

        poseStack.pushPose();
        poseStack.translate(pos.getX() - cameraPos.x, pos.getY() - cameraPos.y, pos.getZ() - cameraPos.z);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        renderBand(
            builder,
            poseStack,
            surfaceY + 0.010f,
            energy,
            Mth.lerp(0.18f, red, 0.82f),
            Mth.lerp(0.22f, green, 0.78f),
            Mth.lerp(0.30f, blue, 0.82f),
            flowX * 0.72f,
            flowZ * 0.72f,
            spinAngle,
            spinSign,
            time,
            1.0f,
            0.072f,
            0.52f
        );
        renderBand(
            builder,
            poseStack,
            surfaceY + 0.017f,
            energy * 0.82f,
            Math.min(1.0f, red * 1.08f + 0.08f),
            Math.min(1.0f, green * 1.08f + 0.10f),
            Math.min(1.0f, blue * 1.12f + 0.14f),
            flowX * 0.62f,
            flowZ * 0.62f,
            spinAngle * 0.78f,
            spinSign,
            time + 0.55f,
            0.88f,
            0.058f,
            0.40f
        );
        renderBand(
            builder,
            poseStack,
            surfaceY + 0.023f,
            energy * 0.62f,
            0.90f,
            0.96f,
            0.99f,
            flowX * 0.45f,
            flowZ * 0.45f,
            spinAngle * 0.54f,
            spinSign,
            time + 1.05f,
            0.76f,
            0.042f,
            0.24f
        );
        BufferUploader.drawWithShader(builder.end());

        poseStack.popPose();
    }

    private static void renderBand(
        BufferBuilder builder,
        PoseStack poseStack,
        float surfaceY,
        float energy,
        float red,
        float green,
        float blue,
        float flowX,
        float flowZ,
        float swirlAngle,
        float spinSign,
        float time,
        float radiusScale,
        float bandWidth,
        float alphaScale
    ) {
        float minRadius = INNER_RADIUS * radiusScale;
        float maxRadius = OUTER_RADIUS * radiusScale;
        float directionalAngle = (float) Math.atan2(flowZ, flowX + 1.0e-4f);
        float directionalStrength = Mth.clamp((Math.abs(flowX) + Math.abs(flowZ)) * 1.45f, 0.0f, 1.0f);

        for (int i = 0; i < BAND_SEGMENTS; i++) {
            float t0 = i / (float) BAND_SEGMENTS;
            float t1 = (i + 1) / (float) BAND_SEGMENTS;

            float angle0 = swirlAngle * 1.55f + time * (0.95f + energy * 0.55f) * spinSign + t0 * Mth.TWO_PI * 1.32f;
            float angle1 = swirlAngle * 1.55f + time * (0.95f + energy * 0.55f) * spinSign + t1 * Mth.TWO_PI * 1.32f;

            float radius0 = Mth.lerp(t0, minRadius, maxRadius) + Mth.sin(time * 1.8f + t0 * 5.8f) * 0.0045f * energy;
            float radius1 = Mth.lerp(t1, minRadius, maxRadius) + Mth.sin(time * 1.8f + t1 * 5.8f) * 0.0045f * energy;

            float push0 = Mth.sin(angle0 - directionalAngle) * directionalStrength * 0.026f * energy;
            float push1 = Mth.sin(angle1 - directionalAngle) * directionalStrength * 0.026f * energy;

            float centerX0 = 0.5f + Mth.cos(angle0) * radius0 + flowX * 0.10f * energy + Mth.cos(directionalAngle) * push0;
            float centerZ0 = 0.5f + Mth.sin(angle0) * radius0 + flowZ * 0.10f * energy + Mth.sin(directionalAngle) * push0;
            float centerX1 = 0.5f + Mth.cos(angle1) * radius1 + flowX * 0.10f * energy + Mth.cos(directionalAngle) * push1;
            float centerZ1 = 0.5f + Mth.sin(angle1) * radius1 + flowZ * 0.10f * energy + Mth.sin(directionalAngle) * push1;

            float tangentX0 = -Mth.sin(angle0) * bandWidth;
            float tangentZ0 = Mth.cos(angle0) * bandWidth;
            float tangentX1 = -Mth.sin(angle1) * bandWidth;
            float tangentZ1 = Mth.cos(angle1) * bandWidth;

            float y0 = surfaceY + waveHeight(centerX0, centerZ0, flowX, flowZ, time + t0, energy);
            float y1 = surfaceY + waveHeight(centerX1, centerZ1, flowX, flowZ, time + t1, energy);
            float fade = 1.0f - smoothstep(0.0f, 1.0f, t0);
            float alpha = Mth.clamp((0.35f + fade * 0.65f) * alphaScale * energy, 0.0f, 0.78f);

            addVertex(builder, poseStack, centerX0 - tangentX0, y0, centerZ0 - tangentZ0, red, green, blue, alpha);
            addVertex(builder, poseStack, centerX0 + tangentX0, y0, centerZ0 + tangentZ0, red, green, blue, alpha);
            addVertex(builder, poseStack, centerX1 + tangentX1, y1, centerZ1 + tangentZ1, red, green, blue, alpha * 0.96f);
            addVertex(builder, poseStack, centerX1 - tangentX1, y1, centerZ1 - tangentZ1, red, green, blue, alpha * 0.96f);
        }
    }

    private static float waveHeight(float x, float z, float flowX, float flowZ, float time, float energy) {
        float localX = x - 0.5f;
        float localZ = z - 0.5f;
        float radial = Mth.sqrt(localX * localX + localZ * localZ);
        float directionalWave = Mth.sin((localX * flowX * 10.0f + localZ * flowZ * 10.0f) + time * 1.8f);
        float orbitalWave = Mth.cos((radial * 10.0f) - time * 1.45f);
        float softCrossWave = Mth.sin((localX + localZ) * 5.2f + time * 1.2f);
        return (directionalWave * 0.0065f + orbitalWave * 0.0045f + softCrossWave * 0.0028f) * energy;
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = Mth.clamp((value - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static void addVertex(
        BufferBuilder builder,
        PoseStack poseStack,
        float x,
        float y,
        float z,
        float red,
        float green,
        float blue,
        float alpha
    ) {
        Vector3f transformed = poseStack.last().pose().transformPosition(x, y, z, new Vector3f());
        builder.vertex(transformed.x(), transformed.y(), transformed.z())
            .color(red, green, blue, alpha)
            .endVertex();
    }
}
