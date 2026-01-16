package yourscraft.jasdewstarfield.createkineticinterference.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.simibubi.create.content.contraptions.bearing.WindmillBearingBlockEntity;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import yourscraft.jasdewstarfield.createkineticinterference.Createkineticinterference;
import yourscraft.jasdewstarfield.createkineticinterference.CreatekineticinterferenceClientConfig;
import yourscraft.jasdewstarfield.createkineticinterference.common.IWindmillInterference;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = Createkineticinterference.MODID, value = Dist.CLIENT)
public class WindmillClientHandler {

    // 存储高亮目标及其过期时间
    private static final Map<BlockPos, Long> HIGHLIGHTS = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!CreatekineticinterferenceClientConfig.CLIENT.enableDebugHighlights.get()) {
            return;
        }

        // 判定条件：
        // 1. 必须是客户端
        // 2. 玩家必须蹲下 (Shift)
        // 3. 玩家必须戴着护目镜
        if (!event.getLevel().isClientSide
                || !event.getEntity().isShiftKeyDown()
                || !GogglesItem.isWearingGoggles(event.getEntity())) {
            return;
        }

        BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
        if (be instanceof WindmillBearingBlockEntity && be instanceof IWindmillInterference interference) {

            Set<BlockPos> sources = interference.createKineticInterference$getInterferenceSources();

            if (sources == null || sources.isEmpty()) {
                return;
            }

            // 渲染高亮
            long expiryTime = System.currentTimeMillis() + CreatekineticinterferenceClientConfig.CLIENT.debugHighlightsDuration.get();
            for (BlockPos sourcePos : sources) {
                HIGHLIGHTS.put(sourcePos, expiryTime);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!CreatekineticinterferenceClientConfig.CLIENT.enableDebugHighlights.get()) {
            HIGHLIGHTS.clear();
            return;
        }

        // 只在半透明方块渲染后进行绘制，确保能覆盖大多数物体
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        // 清理过期的条目
        long now = System.currentTimeMillis();
        HIGHLIGHTS.values().removeIf(expiry -> expiry < now);

        if (HIGHLIGHTS.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();

        // 使用世界绝对坐标渲染
        Vec3 cameraPos = event.getCamera().getPosition();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        // --- 开始渲染设置 ---
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.lineWidth(4.0f);

        Tesselator tesselator = Tesselator.getInstance();
        // 开启绘制：DEBUG_LINES 模式
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        // 橙红色 (R:1.0, G:0.27, B:0.0)
        float r = 1.0f;
        float g = 0.27f;
        float b = 0.0f;
        float a = 1.0f;

        for (BlockPos pos : HIGHLIGHTS.keySet()) {
            AABB aabb = new AABB(pos);
            // 使用 Minecraft 原生的工具方法绘制边框
            LevelRenderer.renderLineBox(poseStack, buffer, aabb, r, g, b, a);
        }

        // 结束绘制
        try {
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } catch (Exception e) {
            Createkineticinterference.LOGGER.error("Render error: ", e);
        }

        // --- 恢复渲染状态 ---
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();

        poseStack.popPose();
    }
}
